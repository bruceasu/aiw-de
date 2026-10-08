import path from "node:path";
import { mkdir, realpath, lstat, readdir, readFile, readlink } from "node:fs/promises";
import { RuntimeFault, checkSignal, errorCode } from "./errors.mjs";
import { ResourceCoordinator, isInside, safePath } from "./resources.mjs";
import { runCommand } from "./process.mjs";
import { sha256 } from "./evidence.mjs";

function validRevision(revision) { return typeof revision === "string" && /^(?:[a-f0-9]{40}|[a-f0-9]{64})$/i.test(revision); }
function argument(value) { if (typeof value !== "string" || !value || value.startsWith("-") || /[\r\n\0]/.test(value)) throw new RuntimeFault("GIT_ARGUMENT_INVALID", "CONFIGURATION"); return value; }

export async function workspaceSnapshot(cwd, signal) {
  const entries = [];
  let bytes = 0;
  const visit = async directory => {
    checkSignal(signal);
    for (const entry of (await readdir(directory, { withFileTypes: true })).sort((a, b) => a.name.localeCompare(b.name))) {
      if (entry.name.toLowerCase() === ".git") continue;
      const file = path.join(directory, entry.name);
      const relative = path.relative(cwd, file);
      const info = await lstat(file);
      if (entries.length > 50000 || bytes > 128 * 1024 * 1024) throw new RuntimeFault("READ_ONLY_SNAPSHOT_BUDGET_EXCEEDED", "CONFIGURATION");
      if (info.isSymbolicLink()) entries.push([relative, "link", await readlink(file)]);
      else if (info.isDirectory()) { entries.push([relative, "directory"]); await visit(file); }
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
    checkSignal(attempt.signal);
    if (!task.workspace) {
      const session = new WorkspaceSession(await realpath(cfg.fallbackWorkspace), null, cfg, attempt);
      session.coordinator = await ResourceCoordinator.forWorkspace(session.cwd);
      return session;
    }
    const repositoryRoot = task.workspace.repositoryPath || cfg.repositoryRoot;
    if (!repositoryRoot || (!task.workspace.repositoryPath && task.workspace.repositoryRef !== cfg.repositoryRef)) throw new RuntimeFault("REPOSITORY_CONFIGURATION_MISMATCH", "CONFIGURATION");
    if (task.workspace.baseRevision == null) {
      const cwd = await realpath(repositoryRoot);
      const session = new WorkspaceSession(cwd, task.workspace, cfg, attempt);
      session.repositoryRoot = cwd;
      session.coordinator = await ResourceCoordinator.forWorkspace(cwd);
      await attempt.recorder.record("workspace-ready", { mode: "DIRECT", repositoryRef: task.workspace.repositoryRef });
      return session;
    }
    if (!validRevision(task.workspace.baseRevision)) throw new RuntimeFault("BASE_REVISION_INVALID", "CONFIGURATION");
    await mkdir(cfg.worktreeRoot, { recursive: true });
    const root = await realpath(cfg.worktreeRoot);
    const cwd = await safePath(root, task.workspace.workspaceKey, true);
    if (!isInside(root, cwd)) throw new RuntimeFault("WORKSPACE_KEY_INVALID", "CONFIGURATION");
    const session = new WorkspaceSession(cwd, task.workspace, cfg, attempt);
    session.repositoryRoot = await realpath(repositoryRoot);
    const repository = await ResourceCoordinator.forWorkspace(session.repositoryRoot);
    try {
      await repository.run({ mode: "X" }, attempt.signal, async () => {
        for (const input of task.inputs ?? []) {
          if (!(input.publicationRef ?? "").startsWith("git:")) continue;
          const parts = input.publicationRef.split(":");
          if (parts.length !== 3) throw new RuntimeFault("PUBLICATION_REFERENCE_INVALID", "CONFIGURATION");
          await session.gitAt(session.repositoryRoot, ["fetch", argument(parts[1]), argument(parts[2])]);
        }
        const resolved = (await session.gitAt(session.repositoryRoot, ["rev-parse", "--verify", `${task.workspace.baseRevision}^{commit}`])).trim().toLowerCase();
        if (resolved !== task.workspace.baseRevision.toLowerCase()) throw new RuntimeFault("BASE_REVISION_MISMATCH", "CONFIGURATION");
        await session.gitAt(session.repositoryRoot, ["check-ref-format", "--branch", argument(task.workspace.branchName)]);
        await session.gitAt(session.repositoryRoot, ["worktree", "add", "-b", task.workspace.branchName, cwd, task.workspace.baseRevision]);
        session.ownsWorkspace = true;
      });
      session.coordinator = await ResourceCoordinator.forWorkspace(cwd);
      await attempt.recorder.record("workspace-ready", { baseRevision: task.workspace.baseRevision, workspaceKey: task.workspace.workspaceKey });
      return session;
    } catch (error) {
      // Creation may have partially finished. Keep that path for reconciliation instead of deleting an unowned directory.
      error.workspace = session;
      throw error;
    }
  }
  async gitAt(cwd, args, signal = this.attempt.signal) {
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
        await this.gitAt(this.cwd, ["add", "-A"]);
        const staged = await this.gitAt(this.cwd, ["diff", "--cached", "--name-only", "-z"]);
        if (staged.length) await this.gitAt(this.cwd, ["-c", "user.name=AI Dev Worker", "-c", "user.email=aidev-worker@localhost", "commit", "--no-gpg-sign", "-m", `aidev: ${task.taskId} attempt ${task.attemptNo}`]);
      }
      const resultRevision = (await this.gitAt(this.cwd, ["rev-parse", "HEAD"])).trim().toLowerCase();
      const changedFiles = (await this.gitAt(this.cwd, ["diff", "--name-only", "-z", this.assignment.baseRevision, resultRevision])).split("\0").filter(Boolean).sort();
      let publicationRef = null;
      if (this.cfg.gitPublishRemote) {
        publicationRef = `git:${this.cfg.gitPublishRemote}:${this.assignment.branchName}`;
        await this.attempt.recorder.record("publication-started", { publicationRef, resultRevision, sideEffect: "possible" });
        try {
          checkSignal(this.attempt.signal);
          await this.gitAt(this.cwd, ["push", argument(this.cfg.gitPublishRemote), `${resultRevision}:refs/heads/${this.assignment.branchName}`]);
          await this.attempt.recorder.record("publication-terminal", { publicationRef, resultRevision, status: "completed", sideEffect: "completed" });
        } catch (error) {
          await this.attempt.recorder.record("publication-terminal", { publicationRef, resultRevision, status: "unknown", sideEffect: "reconcile" });
          throw error;
        }
      }
      checkSignal(this.attempt.signal);
      await this.attempt.recorder.record("workspace-result", { resultRevision, publicationRef, count: changedFiles.length });
      return { resultRevision, changedFiles, publicationRef };
    });
  }
  async cleanup() {
    if (!this.assignment || !this.ownsWorkspace || this.attempt.resourceUncertain) {
      await this.attempt.recorder.record("workspace-cleanup", { status: this.assignment ? "preserved" : "not-owned" });
      return;
    }
    const signal = AbortSignal.timeout(this.cfg.cleanupTimeoutMs ?? 10000);
    try {
      await this.coordinator.run({ mode: "X" }, signal, () => this.gitAt(this.repositoryRoot, ["worktree", "remove", "--force", this.cwd], signal));
    } catch (error) {
      const code = errorCode(error);
      if (["PROCESS_TREE_UNCERTAIN", "RESOURCE_TERMINATION_UNCERTAIN"].includes(code)) this.attempt.resourceUncertain = true;
      await this.attempt.recorder.record("workspace-cleanup", { status: "preserved", code });
      throw error;
    }
    await this.attempt.recorder.record("workspace-cleanup", { status: "completed" });
  }
}
