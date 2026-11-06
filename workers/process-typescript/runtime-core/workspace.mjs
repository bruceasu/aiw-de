import { preparePlan } from "./plan.mjs";
import path from "node:path";
import { mkdir, mkdtemp, realpath, lstat, readdir, readFile, readlink } from "node:fs/promises";
import os from "node:os";
import { RuntimeFault, checkSignal, errorCode } from "./errors.mjs";
import { ResourceCoordinator, isInside, safePath } from "./resources.mjs";
import { runCommand } from "./process.mjs";
import { sha256 } from "./evidence.mjs";

function validRevision(revision) { return typeof revision === "string" && /^(?:[a-f0-9]{40}|[a-f0-9]{64})$/i.test(revision); }
function argument(value) { if (typeof value !== "string" || !value || value.startsWith("-") || /[\r\n\0]/.test(value)) throw new RuntimeFault("GIT_ARGUMENT_INVALID", "CONFIGURATION"); return value; }

export async function workspaceSnapshot(cwd, signal, publicationTarget = null) {
  publicationTarget = publicationTarget?.normalize("NFC") ?? null;
  const entries = [];
  let bytes = 0;
  const visit = async directory => {
    checkSignal(signal);
    for (const entry of (await readdir(directory, { withFileTypes: true })).sort((a, b) => a.name.localeCompare(b.name))) {
      if ([".git", ".ai"].includes(entry.name.toLowerCase()) || (directory === cwd && entry.name.toLowerCase() === ".aiw")) continue;
      const file = path.join(directory, entry.name);
      const relative = path.relative(cwd, file);
      const portable = relative.split(path.sep).join("/").normalize("NFC");
      if (portable === publicationTarget) continue;
      const info = await lstat(file);
      if (entries.length > 50000 || bytes > 128 * 1024 * 1024) throw new RuntimeFault("READ_ONLY_SNAPSHOT_BUDGET_EXCEEDED", "CONFIGURATION");
      if (info.isSymbolicLink()) entries.push([relative, "link", await readlink(file)]);
      else if (info.isDirectory()) {
        if (!publicationTarget?.startsWith(portable + "/")) entries.push([relative, "directory"]);
        await visit(file);
      }
      else if (info.isFile()) {
        bytes += info.size;
        if (bytes > 128 * 1024 * 1024) throw new RuntimeFault("READ_ONLY_SNAPSHOT_BUDGET_EXCEEDED", "CONFIGURATION");
        entries.push([relative, info.mode & 0o777, sha256(await readFile(file, { signal }))]);
      } else throw new RuntimeFault("WORKSPACE_SPECIAL_FILE_UNSUPPORTED", "CONFIGURATION");
    }
  };
  await visit(cwd);
  return sha256(JSON.stringify(entries));
}

export class WorkspaceSession {
  constructor(cwd, assignment, cfg, attempt) {
    Object.assign(this, { cwd, assignment, cfg, attempt, ownsWorkspace: false });
  }
  static async materialize(cfg, task, attempt) {
    const plan = await preparePlan(cfg, task, attempt);
    const assignment = plan.base ? { baseRevision: plan.base } : null;
    const session = new WorkspaceSession(plan.cwd, assignment, cfg, attempt);
    session.plan = plan;
    session.repositoryRoot = plan.source;
    session.coordinator = await ResourceCoordinator.forWorkspace(plan.cwd);
    return session;
  }
  async gitAt(cwd, args, signal = this.attempt.signal) {
    if (args.includes("push")) throw new RuntimeFault("GIT_PUSH_FORBIDDEN", "CONFIGURATION");
    const result = await runCommand("git", ["-C", cwd, ...args], { cwd, signal, timeoutMs: this.cfg.gitTimeoutMs ?? 120000, exact: true });
    return result.stdout;
  }
  async beforeExecution(readOnly) {
    if (!readOnly) return;
    this.initialSnapshot = await workspaceSnapshot(this.cwd, this.attempt.signal);
    this.initialHead = this.assignment?.baseRevision ? (await this.gitAt(this.cwd, ["rev-parse", "HEAD"])).trim() : null;
  }
  async validateReadOnly(readOnly) {
    if (!readOnly) return;
    if (await workspaceSnapshot(this.cwd, this.attempt.signal) !== this.initialSnapshot
      || (this.assignment?.baseRevision && (await this.gitAt(this.cwd, ["rev-parse", "HEAD"])).trim() !== this.initialHead)) {
      throw new RuntimeFault("READ_ONLY_WORKSPACE_CHANGED", "OUTPUT_INVALID");
    }
  }
  async finalize(task, readOnly) {
    if (!this.assignment) return null;
    if (!this.assignment.baseRevision) {
      await this.validateReadOnly(readOnly);
      return null;
    }
    return await this.coordinator.run({ mode: "X" }, this.attempt.signal, async () => {
      await this.validateReadOnly(readOnly);
      if (!readOnly) {
        await this.gitAt(this.cwd, ["add", "-A", "--", ".", ":(exclude).ai", ":(exclude).ai/**", ":(exclude).aiw", ":(exclude).aiw/**"]);
        const staged = await this.gitAt(this.cwd, ["diff", "--cached", "--name-only", "-z"]);
        if (staged.length) await this.gitAt(this.cwd, ["-c", "user.name=AI Dev Worker", "-c", "user.email=aidev-worker@localhost", "commit", "--no-gpg-sign", "-m", `aidev: ${task.taskId} attempt ${task.attemptNo}`]);
      }
      const resultRevision = (await this.gitAt(this.cwd, ["rev-parse", "HEAD"])).trim().toLowerCase();
      const changedFiles = (await this.gitAt(this.cwd, ["diff", "--name-only", "-z", this.assignment.baseRevision, resultRevision])).split("\0").filter(Boolean).sort();
      checkSignal(this.attempt.signal);
      await this.attempt.recorder.record("workspace-result", { resultRevision, count: changedFiles.length });
      return { resultRevision, changedFiles };
    });
  }
  async cleanup() {
    if (!this.attempt.resourceUncertain) await this.plan.release();
    await this.attempt.recorder.record("workspace-cleanup", { status: "preserved" });
  }
}
