"""Local-only isolated Git workspace. No fetch, shared writable directory, hooks or publication."""
import re
from pathlib import Path

from execution_guard import ControlledGit, safe_path
from worker_runtime import safe_directories


class GuardedWorkspace:
    def __init__(self, broker, cwd, assignment): self.broker, self.cwd, self.assignment = broker, str(cwd), assignment

    @classmethod
    def materialize(cls, config, task, broker):
        assignment = task.get("workspace") or {}
        base = assignment.get("baseRevision", "")
        if not re.fullmatch(r"[0-9a-fA-F]{40}|[0-9a-fA-F]{64}", base): raise RuntimeError("GUARDED_IMMUTABLE_GIT_BASE_REQUIRED")
        source = Path(assignment.get("repositoryPath") or config.repository_root).absolute()
        if not source.is_dir(): raise RuntimeError("GUARDED_REPOSITORY_NOT_REGISTERED")
        safe_directories(source)
        broker.source_root = source.resolve(strict=True)
        safe_directories(config.worktree_root)
        root = Path(config.worktree_root).resolve(strict=True)
        key, branch = assignment.get("workspaceKey", ""), assignment.get("branchName", "")
        if not re.fullmatch(r"[A-Za-z0-9_-]{1,100}", key) or not re.fullmatch(r"aidev/[A-Za-z0-9_-]{1,80}/a[1-9][0-9]*", branch): raise RuntimeError("GUARDED_WORKSPACE_ID_INVALID")
        target = root / key
        if target.exists(): raise RuntimeError("GUARDED_WORKSPACE_ALREADY_EXISTS_QUERY_ORIGINAL_RESULT")

        def create():
            git = ControlledGit(source.resolve(strict=True), broker.attempt)
            if git._run("rev-parse", "--verify", base + "^{commit}").strip().lower() != base.lower(): raise RuntimeError("GUARDED_BASE_MISMATCH")
            # Git checkout may run configured filters. The broker rejects them before this operation.
            git._run("worktree", "add", "-b", branch, str(target), base)
            return {"workspaceKey": key, "baseRevision": base}

        broker.operate("GIT", ".", {"action": "prepare-worktree", "workspaceKey": key, "baseRevision": base}, create)
        broker.attach(target)
        return cls(broker, target, assignment)

    def finalize(self, task, read_only=False):
        def finish():
            git = self.broker.git
            paths = set(filter(None, (git.run("diff", "--name-only", "-z", "HEAD", "--") + git._run("ls-files", "--others", "--exclude-standard", "-z")).split("\0")))
            if read_only and paths: raise RuntimeError("GUARDED_READ_ONLY_WORKSPACE_CHANGED")
            if paths:
                git.commit(sorted(paths), f"AIW {task['taskId']} attempt {task['attemptNo']}")
            revision = git.run("rev-parse", "HEAD").strip()
            if read_only and revision.lower() != self.assignment["baseRevision"].lower(): raise RuntimeError("GUARDED_READ_ONLY_REVISION_CHANGED")
            changed = git.run("diff", "--name-only", "-z", self.assignment["baseRevision"], revision, "--")
            return {"resultRevision": revision, "changedFiles": sorted(filter(None, changed.split("\0")))}
        return self.broker.operate("GIT", ".", {"action": "finalize", "readOnly": read_only}, finish)

    def cleanup(self):
        # The local result branch and workspace are evidence. Removal is a separate explicit operation.
        self.broker.attempt.evidence.record("workspace-cleanup", status="preserved", code="GUARDED_LOCAL_RESULT_RETAINED")
