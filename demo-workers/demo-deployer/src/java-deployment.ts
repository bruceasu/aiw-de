import { exec, execFile } from "node:child_process";
import { createHash, randomUUID } from "node:crypto";
import { createReadStream } from "node:fs";
import { copyFile, link, readFile, readdir, realpath, rm, stat, writeFile } from "node:fs/promises";
import path from "node:path";
import { promisify } from "node:util";

const runFile = promisify(execFile);
const runCommand = promisify(exec);
const COMMIT = /^(?:[0-9a-f]{40}|[0-9a-f]{64})$/i;

export interface DeploymentTask {
  taskId: string;
  taskType: string;
  skillRef?: string | null;
  instruction: string;
  workspace: { repositoryRef: string; baseRevision: string } | null;
  inputs?: Array<{ taskId: string; taskType: string; completionSummary: string | null }>;
}

export interface PackageConfig {
  repositoryRef: string;
  repositoryRoot: string;
  stagingRoot: string;
  worktreeRoot: string;
  gitExecutable: string;
  mavenExecutable: string;
}

export interface UploadConfig {
  stagingRoot: string;
  allowedDeployRoot: string;
}

export interface DeploymentResult {
  commit: string;
  artifact: string;
  sha256: string;
}

export function parseDeploymentInput(value: string): { deployDirectory: string; artifactPath?: string } {
  const data: unknown = JSON.parse(value);
  if (!data || typeof data !== "object" || Array.isArray(data)) throw new Error("invalid deployment input");
  const input = data as Record<string, unknown>;
  if (typeof input.deployDirectory !== "string" || !path.isAbsolute(input.deployDirectory)) {
    throw new Error("deployDirectory must be absolute");
  }
  if (input.artifactPath != null && (typeof input.artifactPath !== "string" ||
      path.isAbsolute(input.artifactPath) || input.artifactPath.split(/[\\/]/).includes("..") ||
      !input.artifactPath.endsWith(".jar"))) {
    throw new Error("artifactPath must be a relative jar path");
  }
  return { deployDirectory: input.deployDirectory, ...(input.artifactPath ? { artifactPath: input.artifactPath as string } : {}) };
}

function parsePackageInput(value: string): { artifactPath?: string } {
  const input: unknown = JSON.parse(value);
  if (!input || typeof input !== "object" || Array.isArray(input)) throw new Error("invalid package input");
  const artifactPath = (input as Record<string, unknown>).artifactPath;
  if (artifactPath == null) return {};
  if (typeof artifactPath !== "string" || path.isAbsolute(artifactPath) ||
      artifactPath.split(/[\\/]/).includes("..") || !artifactPath.endsWith(".jar")) {
    throw new Error("artifactPath must be a relative jar path");
  }
  return { artifactPath };
}

export async function permittedDirectory(allowedRoot: string, requested: string): Promise<string> {
  const root = await realpath(allowedRoot);
  const target = await realpath(requested);
  const relative = path.relative(root, target);
  if (relative === ".." || relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative)) {
    throw new Error("directory is outside allowed root");
  }
  if (!(await stat(target)).isDirectory()) throw new Error("deployDirectory is not a directory");
  return target;
}

async function sha256(file: string): Promise<string> {
  const hash = createHash("sha256");
  for await (const chunk of createReadStream(file)) hash.update(chunk);
  return hash.digest("hex");
}

async function existingPublishedArtifact(destination: string, commit: string): Promise<DeploymentResult | null> {
  let actualHash: string;
  try {
    actualHash = await sha256(destination);
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === "ENOENT") return null;
    throw error;
  }
  let recorded: { commit?: string; sha256?: string };
  try {
    recorded = JSON.parse(await readFile(`${destination}.sha256.json`, "utf8")) as { commit?: string; sha256?: string };
  } catch {
    throw new Error("destination artifact exists without a valid deployment hash record");
  }
  if (recorded.commit !== commit || recorded.sha256 !== actualHash) {
    throw new Error("destination artifact does not match its deployment hash record");
  }
  return { commit, artifact: destination, sha256: actualHash };
}

async function publishArtifact(source: string, directory: string, commit: string, signal: AbortSignal,
                               expectedHash?: string): Promise<DeploymentResult> {
  const digest = await sha256(source);
  if (expectedHash && digest !== expectedHash) throw new Error("source artifact hash does not match package result");
  const stem = path.basename(source, ".jar");
  const filename = `${stem.endsWith(`-${commit}`) ? stem : `${stem}-${commit}`}.jar`;
  const destination = path.join(directory, filename);
  if (signal.aborted) throw new Error("task lease lost before artifact copy");
  const published = await existingPublishedArtifact(destination, commit);
  if (published) {
    if (expectedHash && published.sha256 !== expectedHash) throw new Error("destination differs from package result");
    return published;
  }
  const temporary = path.join(directory, `.${filename}.${randomUUID()}.tmp`);
  const recordTemporary = `${temporary}.json`;
  try {
    await copyFile(source, temporary);
    await writeFile(recordTemporary, JSON.stringify({ commit, sha256: digest }), "utf8");
    if (signal.aborted) throw new Error("task lease lost before artifact publish");
    try {
      await link(temporary, destination);
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== "EEXIST") throw error;
      const concurrent = await existingPublishedArtifact(destination, commit);
      if (concurrent && (!expectedHash || concurrent.sha256 === expectedHash)) return concurrent;
      throw new Error("destination differs from package result");
    }
    await link(recordTemporary, `${destination}.sha256.json`);
  } finally {
    await rm(temporary, { force: true });
    await rm(recordTemporary, { force: true });
  }
  return { commit, artifact: destination, sha256: digest };
}

async function chooseJar(worktree: string, requested?: string): Promise<string> {
  if (requested) {
    const candidate = path.resolve(worktree, requested);
    const relative = path.relative(worktree, candidate);
    if (relative === ".." || relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative)) throw new Error("artifact escapes checkout");
    if (!(await stat(candidate)).isFile()) throw new Error("artifactPath is not a file");
    const actual = await realpath(candidate);
    const actualRelative = path.relative(worktree, actual);
    if (actualRelative === ".." || actualRelative.startsWith(`..${path.sep}`) || path.isAbsolute(actualRelative)) {
      throw new Error("artifact symlink escapes checkout");
    }
    return candidate;
  }
  const target = path.join(worktree, "target");
  const jars = (await readdir(target)).filter(name => name.endsWith(".jar") && !/^(original-|.*-(sources|javadoc|tests)\.jar$)/.test(name));
  if (jars.length !== 1) throw new Error(`expected one deployable jar in target; found ${jars.length}. Specify artifactPath.`);
  const selected = path.join(target, jars[0]!);
  const actual = await realpath(selected);
  const relative = path.relative(worktree, actual);
  if (relative === ".." || relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative)) throw new Error("artifact symlink escapes checkout");
  return selected;
}

async function packageWithMaven(executable: string, cwd: string, signal: AbortSignal): Promise<void> {
  const options = { cwd, signal, timeout: 15 * 60_000, maxBuffer: 8 * 1024 * 1024 };
  if (process.platform === "win32" && /\.(cmd|bat)$/i.test(executable)) {
    if (executable.includes('"')) throw new Error("invalid Maven executable path");
    await runCommand(`"${executable}" -DskipTests package`, options);
  } else {
    await runFile(executable, ["-DskipTests", "package"], options);
  }
}

export async function packageJava(task: DeploymentTask, cfg: PackageConfig, signal: AbortSignal): Promise<DeploymentResult> {
  if (task.taskType !== "EXTERNAL_WORK" || task.skillRef !== "demo-deploy-package" || !task.workspace) {
    throw new Error("unexpected package task");
  }
  if (task.workspace.repositoryRef !== cfg.repositoryRef) throw new Error("repositoryRef does not match this worker");
  const commit = task.workspace.baseRevision.toLowerCase();
  if (!COMMIT.test(commit)) throw new Error("invalid baseRevision");
  const input = parsePackageInput(task.instruction);
  const stagingDirectory = await realpath(cfg.stagingRoot);
  const { stdout } = await runFile(cfg.gitExecutable, ["-C", cfg.repositoryRoot, "rev-parse", "--verify", `${commit}^{commit}`], { signal });
  if (stdout.trim().toLowerCase() !== commit) throw new Error("commit resolution mismatch");

  // The path is unique per attempt and is created by git worktree. Never touch a pre-existing checkout.
  const worktree = path.join(cfg.worktreeRoot, `deploy-${randomUUID()}`);
  let created = false;
  try {
    await runFile(cfg.gitExecutable, ["-C", cfg.repositoryRoot, "worktree", "add", "--detach", worktree, commit], { signal });
    created = true;
    await packageWithMaven(cfg.mavenExecutable, worktree, signal);
    const jar = await chooseJar(worktree, input.artifactPath);
    return await publishArtifact(jar, stagingDirectory, commit, signal);
  } finally {
    if (created) {
      await runFile(cfg.gitExecutable, ["-C", cfg.repositoryRoot, "worktree", "remove", "--force", worktree])
        .catch(error => console.error(`deployment worktree cleanup failed: ${String(error)}`));
    }
  }
}

export async function uploadJava(task: DeploymentTask, cfg: UploadConfig, signal: AbortSignal): Promise<DeploymentResult> {
  if (task.taskType !== "EXTERNAL_WORK" || task.skillRef !== "demo-deploy-upload" || task.workspace) {
    throw new Error("unexpected upload task");
  }
  const input = parseDeploymentInput(task.instruction);
  const destinationDirectory = await permittedDirectory(cfg.allowedDeployRoot, input.deployDirectory);
  const packageInputs = (task.inputs || []).filter(item => item.taskId.endsWith("~package"));
  if (packageInputs.length !== 1 || !packageInputs[0]?.completionSummary) throw new Error("one completed package input is required");
  const result = JSON.parse(packageInputs[0].completionSummary) as Partial<DeploymentResult>;
  if (!result.commit || !COMMIT.test(result.commit) || !result.artifact || !result.sha256 ||
      !/^[0-9a-f]{64}$/i.test(result.sha256)) throw new Error("invalid package result");
  const stagingFile = await realpath(result.artifact);
  const stagingRoot = await realpath(cfg.stagingRoot);
  const relative = path.relative(stagingRoot, stagingFile);
  if (relative === ".." || relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative)) {
    throw new Error("package artifact is outside staging root");
  }
  return await publishArtifact(stagingFile, destinationDirectory, result.commit, signal, result.sha256);
}
