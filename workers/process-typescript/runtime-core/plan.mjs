import path from "node:path";
import { mkdir, realpath, lstat, readdir, readFile, writeFile, rmdir } from "node:fs/promises";
import { setTimeout as delay } from "node:timers/promises";
import { sha256 } from "./evidence.mjs";
import { runCommand } from "./process.mjs";
import { isInside } from "./resources.mjs";
import { RuntimeFault, errorCode } from "./errors.mjs";

async function directory(root, ...parts) {
  let current = root;
  for (const part of parts) {
    current = path.join(current, part);
    await mkdir(current, { recursive: true });
    if ((await lstat(current)).isSymbolicLink() || path.dirname(await realpath(current)) !== await realpath(path.dirname(current))) {
      throw new RuntimeFault("HANDOFF_DIRECTORY_UNSAFE");
    }
  }
  return current;
}
async function lock(root, signal) {
  const parent = await directory(root, ".ai", "locks");
  const target = path.join(parent, "execution.lock");
  for (;;) {
    signal.throwIfAborted();
    try { await mkdir(target); break; }
    catch (error) { if (error.code !== "EEXIST") throw error; await delay(50, undefined, { signal }); }
  }
  let held = true;
  return async () => { if (held) { await rmdir(target); held = false; } };
}
async function manifests(task, signal) {
  const result = [];
  for (const input of task.inputs ?? []) {
    const ref = input.handoff;
    if (!ref) continue;
    const root = await realpath(ref.location);
    const target = path.resolve(root, ref.manifest);
    if (!isInside(root, target)) throw new RuntimeFault("HANDOFF_PATH_INVALID");
    for (let cursor = target; cursor !== root; cursor = path.dirname(cursor)) {
      if ((await lstat(cursor)).isSymbolicLink()) throw new RuntimeFault("HANDOFF_PATH_UNSAFE");
    }
    if ((await lstat(target)).size > 1024 * 1024) throw new RuntimeFault("HANDOFF_CAPACITY_EXCEEDED");
    const raw = await readFile(target, { signal });
    if (sha256(raw) !== ref.sha256) throw new RuntimeFault("HANDOFF_HASH_MISMATCH");
    const data = JSON.parse(raw.toString("utf8"));
    if (data.protocol !== "aiw.handoff.v1" || data.taskId !== input.taskId || data.attemptId !== input.attemptId) throw new RuntimeFault("HANDOFF_SOURCE_MISMATCH");
    const report = path.join(path.dirname(target), "report.md");
    const reportInfo = await lstat(report);
    if (data.report !== "report.md" || !reportInfo.isFile() || reportInfo.isSymbolicLink() || reportInfo.size > 1024 * 1024) throw new RuntimeFault("HANDOFF_REPORT_INVALID");
    if (sha256(await readFile(report, { signal })) !== data.reportSha256) throw new RuntimeFault("HANDOFF_REPORT_HASH_MISMATCH");
    result.push({ ...data, location: root });
  }
  return result;
}
async function repositories(root) {
  const found = [];
  let count = 0;
  const walk = async cwd => {
    if (++count > 50000) throw new RuntimeFault("REPOSITORY_LAYOUT_LIMIT");
    const entries = await readdir(cwd, { withFileTypes: true });
    if (entries.some(entry => entry.name === ".git")) found.push(cwd);
    for (const entry of entries) {
      if (found.length > 1) break;
      if (entry.isDirectory() && ![".git", ".ai", ".aiw", "node_modules", ".venv"].includes(entry.name)) await walk(path.join(cwd, entry.name));
    }
  };
  await walk(root);
  return found;
}
export async function preparePlan(cfg, task, attempt) {
  if (typeof task.location !== "string" || !task.location) throw new RuntimeFault("PLAN_LOCATION_REQUIRED");
  await mkdir(task.location, { recursive: true });
  const root = await realpath(task.location);
  task.handoffInputs = await manifests(task, attempt.signal);
  let source = root;
  const latest = [...task.handoffInputs].sort((a, b) => a.completedAt - b.completedAt).at(-1);
  if (latest && latest.location === root) {
    const candidate = await realpath(latest.workingDirectory);
    if (!isInside(root, candidate, true)) throw new RuntimeFault("HANDOFF_WORKING_DIRECTORY_UNSAFE");
    source = candidate;
  }
  const release = await lock(root, attempt.signal);
  const git = async (cwd, args) => (await runCommand("git", ["-C", cwd, ...args], { cwd, signal: attempt.signal, timeoutMs: cfg.gitTimeoutMs ?? 120000, exact: true })).stdout;
  try {
    if (cfg.role === "goal-discussion" || ["goal-discussion", "workflow-recommendation"].includes(task.subjectType)) {
      const cwd = await directory(root, ".ai", "sandboxes", sha256(task.attemptId));
      const ownRelease = await lock(cwd, attempt.signal);
      await release();
      return { root, cwd, base: null, source: root, release: ownRelease };
    }
    const repos = await repositories(root);
    if (repos.length === 1 && repos[0] === root) {
      try {
        const base = (await git(source, ["rev-parse", "--verify", "HEAD^{commit}"])).trim();
        const dirty = await git(source, ["status", "--porcelain", "--", ".", ":(exclude).ai", ":(exclude).ai/**"]);
        if (!dirty) {
          const parent = await directory(root, ".ai", "worktrees", sha256(task.planId));
          const cwd = path.join(parent, sha256(task.attemptId));
          await git(root, ["worktree", "add", "--detach", cwd, base]);
          const ownRelease = await lock(cwd, attempt.signal);
          await release();
          return { root, cwd, base, source: root, release: ownRelease };
        }
      } catch (error) {
        if (["PROCESS_TREE_UNCERTAIN", "RESOURCE_TERMINATION_UNCERTAIN"].includes(errorCode(error))) attempt.resourceUncertain = true;
        if (attempt.resourceUncertain) throw error;
        attempt.signal.throwIfAborted();
      }
    }
    return { root, cwd: source, base: null, source: root, release };
  } catch (error) {
    if (!attempt.resourceUncertain) await release();
    throw error;
  }
}
export async function writeHandoff(task, workspace, report, gitResult) {
  const root = workspace.plan.root;
  const target = await directory(root, ".ai", "handoffs", sha256(task.taskId), sha256(task.attemptId));
  await writeFile(path.join(target, "report.md"), report.summary, { flag: "wx" });
  const manifest = { protocol: "aiw.handoff.v1", taskId: task.taskId, attemptId: task.attemptId, planId: task.planId,
    workingDirectory: workspace.cwd, completedAt: Date.now(), report: "report.md", reportSha256: sha256(report.summary), verdict: report.verdict,
    gitResult, deliverables: report.outputFiles ?? [] };
  const raw = JSON.stringify(manifest, null, 2);
  await writeFile(path.join(target, "manifest.json"), raw, { flag: "wx" });
  return { manifest: path.relative(root, path.join(target, "manifest.json")).split(path.sep).join("/"), sha256: sha256(raw) };
}
