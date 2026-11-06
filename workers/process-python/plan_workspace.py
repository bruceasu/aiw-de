"""Worker-owned shared locations, cross-process admission and immutable handoff files."""
import hashlib
import json
import os
import re
from pathlib import Path
import time


def key(value):
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def directory(root, *parts):
    current = root
    for part in parts:
        current = current / part
        current.mkdir(exist_ok=True)
        if current.is_symlink() or not current.is_dir() or current.resolve().parent != current.parent.resolve():
            raise RuntimeError("HANDOFF_DIRECTORY_UNSAFE")
    return current


class DirectoryLock:
    """Shared by all Worker languages. An orphan is retained for explicit reconciliation."""
    def __init__(self, root, attempt):
        parent = directory(root, ".ai", "locks")
        self.path = parent / "execution.lock"
        while True:
            attempt.check()
            try:
                self.path.mkdir()
                break
            except FileExistsError:
                time.sleep(0.05)
        self.held = True

    def release(self):
        if self.held:
            self.path.rmdir()
            self.held = False


def input_manifests(task):
    result = []
    for item, checkpoint in [*((item, False) for item in task.get("inputs", [])), *((item, True) for item in task.get("checkpointInputs", []))]:
        ref = item.get("handoff")
        if not ref:
            continue
        root = Path(ref["location"]).resolve(strict=True)
        relative = Path(ref["manifest"])
        if relative.is_absolute() or ".." in relative.parts:
            raise RuntimeError("HANDOFF_PATH_INVALID")
        path = root / relative
        for parent in [path, *path.parents]:
            if parent == root:
                break
            if parent.is_symlink() or getattr(parent, "is_junction", lambda: False)():
                raise RuntimeError("HANDOFF_PATH_UNSAFE")
        if not path.is_file() or path.stat().st_size > 1024 * 1024:
            raise RuntimeError("HANDOFF_CAPACITY_EXCEEDED")
        raw = path.read_bytes()
        if len(raw) > 1024 * 1024 or hashlib.sha256(raw).hexdigest() != ref["sha256"]:
            raise RuntimeError("HANDOFF_HASH_MISMATCH")
        manifest = json.loads(raw)
        if (manifest.get("protocol") != "aiw.handoff.v1" or manifest.get("taskId") != item["taskId"]
                or manifest.get("attemptId") != item["attemptId"]):
            raise RuntimeError("HANDOFF_SOURCE_MISMATCH")
        report = path.parent / "report.md"
        if manifest.get("report") != "report.md" or report.is_symlink() or not report.is_file() or report.stat().st_size > 1024 * 1024:
            raise RuntimeError("HANDOFF_REPORT_INVALID")
        if hashlib.sha256(report.read_bytes()).hexdigest() != manifest.get("reportSha256"):
            raise RuntimeError("HANDOFF_REPORT_HASH_MISMATCH")
        candidate = Path(manifest.get("workingDirectory", "")).resolve(strict=True)
        if not candidate.is_relative_to(root):
            raise RuntimeError("HANDOFF_WORKING_DIRECTORY_UNSAFE")
        if checkpoint:
            from worker_runtime import current_attempt, snapshot
            expected = manifest.get("checkpointTreeSha256")
            attempt = current_attempt()
            if attempt is None or not isinstance(expected, str) or not re.fullmatch(r"[0-9a-f]{64}", expected) or snapshot(str(candidate), attempt) != expected:
                raise RuntimeError("HANDOFF_CHECKPOINT_TREE_CHANGED")
            manifest["checkpoint"] = True
        manifest["location"] = str(root)
        result.append(manifest)
    return result


def repositories(root):
    result, visited = [], 0
    for parent, names, _ in os.walk(root, followlinks=False):
        visited += 1
        if visited > 50000:
            raise RuntimeError("REPOSITORY_LAYOUT_LIMIT")
        current = Path(parent)
        if (current / ".git").exists():
            result.append(current)
            if len(result) > 1:
                break
        names[:] = [n for n in names if n not in {".git", ".ai", ".aiw", "node_modules", ".venv"}
                    and not (current / n).is_symlink()]
    return result


class PlanWorkspace:
    def __init__(self, root, cwd, attempt, git, lock, base=None):
        self.root, self.cwd, self.attempt = root, str(cwd), attempt
        self.git, self.lock, self.base = git, lock, base
        self.assignment = {"baseRevision": base} if base else None

    @classmethod
    def materialize(cls, config, task, broker=None):
        from worker_runtime import current_attempt, git as runtime_git
        attempt = current_attempt()
        root = Path(task["location"])
        root.mkdir(parents=True, exist_ok=True)
        root = root.resolve(strict=True)
        gate = DirectoryLock(root, attempt)
        try:
            manifests = input_manifests(task)
            task["handoffInputs"] = manifests
            source = root
            # Check retained input while holding admission for this location.
            # A sole latest tree is a starting point, not an implicit branch merge.
            if manifests:
                candidates = sorted(manifests, key=lambda m: (bool(m.get("checkpoint")), m.get("completedAt", 0)))
                latest = candidates[-1]
                candidate = Path(latest["workingDirectory"]).resolve(strict=True)
                input_root = Path(latest["location"]).resolve(strict=True)
                if not candidate.is_relative_to(input_root):
                    raise RuntimeError("HANDOFF_WORKING_DIRECTORY_UNSAFE")
                if input_root == root:
                    source = candidate
        except BaseException:
            if not attempt.resource_uncertain:
                gate.release()
            raise

        def git(cwd, *args):
            if broker:
                from execution_guard import ControlledGit
                return broker.operate("GIT", ".", {"action": "workspace", "args": list(args)},
                                      lambda: ControlledGit(Path(cwd), attempt)._run(*args))
            return runtime_git(str(cwd), *args)

        try:
            if config.process_role == "goal-discussion" or task.get("subjectType") in {"goal-discussion", "workflow-recommendation"}:
                cwd = directory(root, ".ai", "sandboxes", key(task["attemptId"]))
                own_gate = DirectoryLock(cwd, attempt)
                gate.release()
                return cls(root, cwd, attempt, git, own_gate)
            repos = repositories(root)
            has_git_permission = not broker or broker.allowed_tools is None or "GIT" in broker.allowed_tools
            if len(repos) == 1 and repos[0] == root and has_git_permission:
                try:
                    base = git(source, "rev-parse", "--verify", "HEAD^{commit}").strip()
                    dirty = git(source, "status", "--porcelain", "--untracked-files=normal", "--", ".", ":(exclude).ai", ":(exclude).ai/**")
                    if not dirty:
                        parent = directory(root, ".ai", "worktrees", key(task["planId"]))
                        cwd = parent / key(task["attemptId"])
                        git(root, "worktree", "add", "--detach", str(cwd), base)
                        if os.name == "nt" and not broker:
                            from windows_workspace_acl import permit_workspace_read
                            permit_workspace_read(parent, cwd, root, writable=config.process_role in {"coder", "integrator", "repairer"})
                        own_gate = DirectoryLock(cwd, attempt)
                        gate.release()
                        return cls(root, cwd, attempt, git, own_gate, base)
                except (RuntimeError, FileNotFoundError):
                    if attempt.resource_uncertain:
                        raise
                    attempt.check()
                    # A missing commit/Git executable or failed preparation uses serial execution.
            return cls(root, source, attempt, git, gate)
        except BaseException:
            if not attempt.resource_uncertain:
                gate.release()
            raise

    def finalize(self, task, read_only=False):
        if not self.base:
            return None
        if not read_only:
            self.git(self.cwd, "add", "-A", "--", ".", ":(exclude).ai", ":(exclude).ai/**")
            if self.git(self.cwd, "diff", "--cached", "--name-only"):
                self.git(self.cwd, "-c", "user.name=AIW Worker", "-c", "user.email=worker@aiw.local",
                         "commit", "--no-gpg-sign", "-m", "AIW " + task["taskId"])
        revision = self.git(self.cwd, "rev-parse", "HEAD").strip()
        if read_only and revision.lower() != self.base.lower(): raise RuntimeError("READ_ONLY_REVISION_CHANGED")
        changed = self.git(self.cwd, "diff", "--name-only", "-z", self.base, revision)
        return {"resultRevision": revision, "changedFiles": sorted(filter(None, changed.split("\0")))}

    def handoff(self, task, report, result, checkpoint_tree_digest=None):
        target = directory(self.root, ".ai", "handoffs", key(task["taskId"]), key(task["attemptId"]))
        with (target / "report.md").open("xb") as file:
            file.write(report["summary"].encode("utf-8"))
        manifest = {"protocol": "aiw.handoff.v1", "taskId": task["taskId"], "attemptId": task["attemptId"],
                    "planId": task["planId"], "workingDirectory": self.cwd, "completedAt": time.time_ns() // 1000000,
                    "report": "report.md", "reportSha256": key(report["summary"]), "verdict": report["verdict"], "gitResult": result,
                    "deliverables": report.get("outputFiles", [])}
        if checkpoint_tree_digest is not None:
            manifest["checkpointTreeSha256"] = checkpoint_tree_digest
        raw = json.dumps(manifest, ensure_ascii=False, indent=2).encode("utf-8")
        with (target / "manifest.json").open("xb") as file:
            file.write(raw)
        return {"manifest": (target / "manifest.json").relative_to(self.root).as_posix(),
                "sha256": hashlib.sha256(raw).hexdigest()}

    def cleanup(self):
        # Retain files and worktrees for retry/downstream tasks. Release only confirmed quiescent execution.
        if not self.attempt.resource_uncertain:
            self.lock.release()
