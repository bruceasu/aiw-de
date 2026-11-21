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
        if (current.is_symlink() or getattr(current, "is_junction", lambda: False)()
                or not current.is_dir() or current.resolve().parent != current.parent.resolve()):
            raise RuntimeError("HANDOFF_DIRECTORY_UNSAFE")
    return current


class DirectoryLock:
    """Keep the shared directory lock and identify the exact Python attempt holding it."""
    def __init__(self, root, attempt, attempt_id, stopped_attempts=()):
        parent = directory(root, ".ai", "locks")
        self.path = parent / "execution.lock"
        self.owner = self.path / ("attempt-" + key(attempt_id))
        stopped = {"attempt-" + key(identity) for identity in stopped_attempts if identity != attempt_id}
        waiting = False
        recovered = False
        channel = getattr(attempt, "run_channel", None)
        while True:
            attempt.check()
            if channel:
                channel.enter()
            try:
                try:
                    self.path.mkdir()
                except FileExistsError:
                    if self.path.is_symlink() or getattr(self.path, "is_junction", lambda: False)():
                        raise RuntimeError("WORKSPACE_LOCK_PATH_UNSAFE")
                    if self.reclaim(stopped):
                        recovered = True
                        continue
                    if not waiting:
                        attempt.evidence.record("workspace-lock", status="waiting", code="WORKSPACE_LOCK_BUSY")
                        waiting = True
                else:
                    try:
                        self.owner.touch(exist_ok=False)
                    except OSError:
                        self.path.rmdir()
                        raise
                    self.held = True
                    break
            finally:
                if channel:
                    channel.leave()
            time.sleep(0.05)
        if waiting or recovered:
            try:
                attempt.evidence.record("workspace-lock", status="acquired",
                                        code="WORKSPACE_LOCK_RECOVERED" if recovered else "WORKSPACE_LOCK_ACQUIRED")
            except Exception:
                self.release()
                raise

    def reclaim(self, stopped):
        if not stopped:
            return False
        try:
            markers = list(self.path.iterdir())
            if len(markers) != 1:
                return False
            marker = markers[0]
            if marker.name not in stopped or marker.is_symlink() or not marker.is_file():
                return False
            # Only the contender that removes this exact attempt's marker may remove the directory.
            marker.unlink()
        except FileNotFoundError:
            return False
        self.path.rmdir()
        return True

    def release(self):
        if self.held:
            self.owner.unlink()
            self.path.rmdir()
            self.held = False


REPORT_FILE_MAX_BYTES = 1024 * 1024
REPORT_PREVIEW_CHARS = 4000


def input_manifests(task, reports=None):
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
        if manifest.get("report") != "report.md" or report.is_symlink() or not report.is_file() or report.stat().st_size > REPORT_FILE_MAX_BYTES:
            raise RuntimeError("HANDOFF_REPORT_INVALID")
        body = report.read_bytes()
        if len(body) > REPORT_FILE_MAX_BYTES or hashlib.sha256(body).hexdigest() != manifest.get("reportSha256"):
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
        if reports is not None and not checkpoint:
            reports.append((item["taskId"], body.decode("utf-8")))
    return result


def has_nested_repository(root, attempt):
    def raise_walk_error(error):
        raise error

    visited = 0
    for parent, names, _ in os.walk(root, followlinks=False, onerror=raise_walk_error):
        attempt.check()
        visited += 1
        if visited > 50000:
            raise RuntimeError("REPOSITORY_LAYOUT_LIMIT")
        current = Path(parent)
        if current != root and (current / ".git").exists():
            return True
        names[:] = [n for n in names if n not in {".git", ".ai", ".aiw", "node_modules", ".venv"}
                    and not (current / n).is_symlink()
                    and not getattr(current / n, "is_junction", lambda: False)()]
    return False


class PlanWorkspace:
    def __init__(self, root, cwd, attempt, git, lock, base=None, *, handoff_cwd=None):
        self.root, self.cwd, self.attempt = root, str(cwd), attempt
        self.git, self.lock, self.base = git, lock, base
        self.assignment = {"baseRevision": base} if base else None
        self.handoff_cwd = str(handoff_cwd) if handoff_cwd is not None else self.cwd
        self.text_workspace = handoff_cwd is not None

    @classmethod
    def materialize(cls, config, task, broker=None):
        from worker_runtime import current_attempt, git as runtime_git
        attempt = current_attempt()
        root = Path(task["location"])
        root.mkdir(parents=True, exist_ok=True)
        root = root.resolve(strict=True)
        text_only = (broker is None and config.runtime == "openai-agents" and config.process_role == "general"
                     and task.get("subjectType") == "flexible-plan" and "general-work" in task.get("requiredCapabilities", ()))
        sandbox = None
        if text_only and not task.get("checkpointInputs"):
            parent = directory(root, ".ai", "sandboxes")
            sandbox = parent / key(task["attemptId"])
            sandbox.mkdir()
        gate = DirectoryLock(sandbox or root, attempt, task["attemptId"], task.get("stoppedAttempts") or ())
        try:
            if broker:
                broker.source_root = root
                from formal_questions import verify_checkpoints
                verify_checkpoints(broker)
            attempt.handoff_reports = []
            manifests = input_manifests(task, attempt.handoff_reports if config.runtime == "openai-agents" else None)
            task["handoffInputs"] = manifests
            source = root
            latest = None
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

        def git(cwd, *args, workspace_finalization=False):
            if broker:
                from execution_guard import ControlledGit
                return broker.operate("GIT", ".", {"action": "workspace", "args": list(args)},
                                      lambda: ControlledGit(Path(cwd), attempt)._run(*args),
                                      workspace_finalization=workspace_finalization)
            return runtime_git(str(cwd), *args)

        try:
            if sandbox is not None:
                return cls(root, sandbox, attempt, git, gate, handoff_cwd=source)
            if text_only and latest and latest.get("checkpoint") and "projectDirectory" in latest:
                parent = directory(root, ".ai", "sandboxes")
                project = Path(latest["projectDirectory"]).resolve(strict=True)
                if source.parent != parent or not re.fullmatch(r"[0-9a-f]{64}", source.name) or not project.is_relative_to(root):
                    raise RuntimeError("HANDOFF_WORKING_DIRECTORY_UNSAFE")
                own_gate = DirectoryLock(source, attempt, task["attemptId"], task.get("stoppedAttempts") or ())
                gate.release()
                return cls(root, source, attempt, git, own_gate, handoff_cwd=project)
            if config.process_role == "goal-discussion" or task.get("subjectType") in {"goal-discussion", "workflow-recommendation"}:
                cwd = directory(root, ".ai", "sandboxes", key(task["attemptId"]))
                own_gate = DirectoryLock(cwd, attempt, task["attemptId"])
                gate.release()
                return cls(root, cwd, attempt, git, own_gate)
            has_git_permission = not broker or broker.allowed_tools is None or "GIT" in broker.allowed_tools
            if has_git_permission and (root / ".git").exists():
                try:
                    if has_nested_repository(root, attempt):
                        return cls(root, source, attempt, git, gate)
                    base = git(source, "rev-parse", "--verify", "HEAD^{commit}").strip()
                    dirty = git(source, "status", "--porcelain", "--untracked-files=normal", "--", ".", ":(exclude).ai", ":(exclude).ai/**")
                    if not dirty:
                        parent = directory(root, ".ai", "worktrees", key(task["planId"]))
                        cwd = parent / key(task["attemptId"])
                        git(root, "worktree", "add", "--detach", str(cwd), base)
                        if os.name == "nt" and not broker:
                            from windows_workspace_acl import permit_workspace_read
                            permit_workspace_read(parent, cwd, root, writable=config.process_role in {"coder", "integrator", "repairer"})
                        own_gate = DirectoryLock(cwd, attempt, task["attemptId"])
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
        def git(*args):
            return self.git(self.cwd, *args, workspace_finalization=True)

        if not read_only:
            git("add", "-A", "--", ".", ":(exclude).ai", ":(exclude).ai/**")
            if git("diff", "--cached", "--name-only"):
                git("-c", "user.name=AIW Worker", "-c", "user.email=worker@aiw.local",
                    "commit", "--no-gpg-sign", "-m", "AIW " + task["taskId"])
        revision = git("rev-parse", "HEAD").strip()
        if read_only and revision.lower() != self.base.lower(): raise RuntimeError("READ_ONLY_REVISION_CHANGED")
        changed = git("diff", "--name-only", "-z", self.base, revision)
        return {"resultRevision": revision, "changedFiles": sorted(filter(None, changed.split("\0")))}

    def report_summary(self, task, text):
        if len(text) <= REPORT_PREVIEW_CHARS:
            return text
        path = self.root / ".ai" / "handoffs" / key(task["taskId"]) / key(task["attemptId"]) / "report.md"
        reference = f"\n\nFull report on the Worker: {path}"
        # Decision nodes read choice/reason from the inline JSON to select a branch.
        structured = text.strip()
        if structured.startswith(("```json\n", "```json\r\n")) and structured.endswith("```"):
            structured = structured.split("\n", 1)[1][:-3].strip()
        if structured.startswith("{"):
            try:
                decision = json.loads(structured)
            except (ValueError, RecursionError):
                decision = None
            if isinstance(decision, dict) and isinstance(decision.get("choice"), str) and isinstance(decision.get("reason"), str):
                return json.dumps({"choice": decision["choice"],
                                   "reason": decision["reason"][:REPORT_PREVIEW_CHARS].rstrip() + reference},
                                  ensure_ascii=False)
        return text[:REPORT_PREVIEW_CHARS].rstrip() + "\n\n[Excerpt]" + reference

    def handoff(self, task, report, result, checkpoint_tree_digest=None, tool_approval=None, *, full_report=None):
        target = directory(self.root, ".ai", "handoffs", key(task["taskId"]), key(task["attemptId"]))
        # Carry prior outputs across nodes and approval/question continuations. Bodies stay on disk.
        outputs = {}
        for source in task.get("handoffInputs", []):
            for item in source.get("deliverables", []):
                if isinstance(item, dict) and isinstance(item.get("path"), str):
                    outputs[item["path"]] = item
        broker = getattr(self.attempt, "guard_broker", None)
        for item in report.get("outputFiles", list(broker.file_changes.values()) if broker else []):
            outputs[item["path"]] = item
        body = (report["summary"] if full_report is None else full_report).encode("utf-8")
        if len(body) > REPORT_FILE_MAX_BYTES:
            raise RuntimeError("HANDOFF_REPORT_TOO_LARGE")
        with (target / "report.md").open("xb") as file:
            file.write(body)
        manifest = {"protocol": "aiw.handoff.v1", "taskId": task["taskId"], "attemptId": task["attemptId"],
                    "planId": task["planId"], "workingDirectory": self.handoff_cwd, "completedAt": time.time_ns() // 1000000,
                    "report": "report.md", "reportSha256": hashlib.sha256(body).hexdigest(), "verdict": report["verdict"], "gitResult": result,
                    "deliverables": list(outputs.values())}
        if checkpoint_tree_digest is not None:
            manifest["checkpointTreeSha256"] = checkpoint_tree_digest
            if self.handoff_cwd != self.cwd:
                manifest["projectDirectory"] = manifest["workingDirectory"]
                manifest["workingDirectory"] = self.cwd
        if tool_approval is not None:
            manifest["toolApproval"] = tool_approval
        raw = json.dumps(manifest, ensure_ascii=False, indent=2).encode("utf-8")
        with (target / "manifest.json").open("xb") as file:
            file.write(raw)
        return {"manifest": (target / "manifest.json").relative_to(self.root).as_posix(),
                "sha256": hashlib.sha256(raw).hexdigest()}

    def cleanup(self):
        # Retain files and worktrees for retry/downstream tasks. Release only confirmed quiescent execution.
        if not self.attempt.resource_uncertain:
            self.lock.release()
