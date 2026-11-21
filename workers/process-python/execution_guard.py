"""Trusted per-operation broker. Models never receive the platform credential or a filesystem handle."""
from __future__ import annotations

import hashlib
import json
import os
import re
import stat
import subprocess
import signal
import sys
import time
import threading
import uuid
from pathlib import Path

PROTOCOL = "aiw.execution.guard.v1"
CAPABILITY = "execution-guard-v1"
PROTECTED = {".git", ".ai", ".env", ".codex", ".venv", "__pycache__"}
TOOL_FAILURE_HINTS = {
    "GUARD_TOOL_ARGUMENTS_INVALID": "Use the declared tool parameters.",
    "GUARD_PATH_INVALID": "Use a workspace-relative path with forward slashes.",
    "GUARD_PATH_DENIED": "Use an allowed workspace file. Do not try to access the protected path.",
    "GUARD_PATH_ESCAPE": "Use a path inside the workspace.",
    "GUARD_LINK_OR_REPARSE_DENIED": "Choose a path that does not pass through a link.",
    "GUARD_HARDLINK_DENIED": "Choose a regular workspace file that does not share a hard link.",
    "GUARD_PARENT_OR_FILE_MISSING": "List the parent directory and check the file name.",
    "GUARD_PARENT_INVALID": "Choose an existing directory as the parent.",
    "GUARD_NOT_DIRECTORY": "Choose an existing directory.",
    "GUARD_READ_RANGE_INVALID": "Read from a line number of at least 1 with a limit between 1 and 500.",
    "GUARD_OUTPUT_LIMIT": "Read fewer lines at a time.",
    "GUARD_SEARCH_INVALID": "Search for non-empty text of at most 1024 characters.",
    "GUARD_READ_FAILED": "Check the file path, permissions and text encoding, or use another source.",
    "GUARD_FILE_INVALID_OR_TOO_LARGE": "Choose a regular workspace file of at most 2 MiB.",
    "GUARD_FILE_LIMIT": "Choose a file of at most 2 MiB.",
    "GUARD_FILE_VERSION_CONFLICT": "Read the current file again and use its exact digest before changing it.",
    "GUARD_WRITE_LIMIT": "Limit a single file write to 2 MiB.",
    "GUARD_EXISTING_FILE_REQUIRES_EDIT": "Write creates new files. Read the existing file and use edit with its current digest and the exact text to replace.",
    "GUARD_EDIT_MATCH_REQUIRED": "Use edit with an exact, non-empty oldText that occurs once. Read a larger unique fragment if needed.",
    "GUARD_EDIT_LIMIT": "Use smaller exact edits within the authorized workspace scope.",
    "GUARD_EDIT_OVERLAP": "Combine overlapping edits into one exact replacement or select separate original fragments.",
    "GUARD_RESTORE_TARGET_EXISTS": "The destination already exists. Inspect it before deciding how to proceed.",
    "GUARD_ROLE_IS_READ_ONLY": "Use read-only tools and report the findings.",
    "GUARD_COMMAND_DENIED": "Use only a command already allowed for this task.",
    "GUARD_SHELL_DENIED_OR_INVALID": "Use the permitted file tools, or report that the task needs a command.",
    "GUARD_GIT_REPOSITORY_REQUIRED": "No Git repository at the workspace root. Use the workspace file tools.",
    "GUARD_GIT_INSPECTION_FAILED": "Git inspection failed. Continue with workspace file tools if Git is optional.",
}


def digest(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def command_digest(command):
    values = ["aiw.guard.command.v1", command["id"], str(len(command["argv"])), *command["argv"],
              command["cwd"], command["permissionProfile"], str(command["timeoutMs"]),
              str(command["outputBytes"]), str(command["memoryBytes"]), str(command["processLimit"])]
    canonical = "".join(f"{len(value.encode('utf-8'))}:{value}\n" for value in values)
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def _command_path_escapes(value):
    normalized = value.replace("\\", "/")
    if (os.path.isabs(value) or re.search(r"(?:^|=)/", normalized)
            or re.search(r"(?:^|=)[a-z]:/", normalized, re.IGNORECASE)):
        return True
    return ".." in re.split(r"[/=]", normalized)


def _low_risk_command(command):
    argv = command.get("argv")
    if not isinstance(argv, list) or not argv or any(not isinstance(value, str) for value in argv):
        return False
    tokens = [value.lower() for value in argv]
    # A configured read/test command may still point outside the workspace.
    # Keep absolute and parent-relative targets on the approval path.
    if any(_command_path_escapes(value) or "\\" in value for value in argv[1:]):
        return False
    dangerous = {"rm", "del", "erase", "rmdir", "remove-item", "format", "mkfs", "dd", "push", "publish",
                 "deploy", "destroy", "prune", "reset", "clean", "restore", "--hard", "--force", "--delete",
                 "-delete", "-exec", "-execdir", "--exec", "--pre", "--pre-glob", "-d"}
    if any(re.fullmatch(r"[a-z0-9_-]+", token) and token in dangerous for token in tokens):
        return False
    executable = Path(argv[0]).name.lower()
    if executable in {"git", "git.exe"}:
        if len(tokens) < 2:
            return False
        command_name = tokens[1]
        args = tokens[2:]
        if command_name == "status":
            return all(arg in {"--short", "--porcelain", "--branch", "--untracked-files=normal"} for arg in args)
        if command_name == "diff":
            allowed = {"--stat", "--name-only", "--no-ext-diff", "--no-textconv", "--cached"}
            return all(arg in allowed for arg in args)
        if command_name == "ls-files":
            return not args
        return command_name == "rev-parse" and args in (["--verify", "head^{commit}"], ["head"])
    if executable == "rg":
        return len(argv) == 2 and bool(argv[1]) and not argv[1].startswith("-")
    if executable in {"ls", "dir", "pwd"}:
        return len(argv) == 1
    if executable in {"mvn", "mvnw", "mvnw.cmd"}:
        safe_options = {"-o", "--offline", "-q", "--quiet", "-b", "--batch-mode", "--no-transfer-progress"}
        goals = [token for token in tokens[1:] if token not in safe_options]
        return len(goals) == 1 and goals[0] in {"test", "compile", "test-compile"}
    if executable in {"gradle", "gradlew", "gradlew.bat"}:
        safe_options = {"--offline", "--no-daemon", "-q", "--quiet", "--console=plain"}
        tasks = [token for token in tokens[1:] if token not in safe_options]
        return len(tasks) == 1 and tasks[0] in {"test", "check", "classes", "compilejava", "compiletestjava"}
    if executable in {"python", "python3"}:
        if len(argv) < 3 or argv[1:3] != ["-m", "unittest"]:
            return False
        selectors = argv[3:]
        for selector in selectors:
            parts = selector.split(".")
            if not all(re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", part) for part in parts):
                return False
            test_module = parts[0].startswith("test_")
            packaged_test = parts[0] == "tests" and len(parts) > 1 and parts[1].startswith("test_")
            if not test_module and not packaged_test:
                return False
        return True
    if executable == "pytest":
        return len(tokens) == 1 or len(tokens) == 2 and tokens[1] in {"-q", "-v"}
    if executable == "go":
        return len(tokens) >= 2 and tokens[1] == "test" and all(not token.startswith("-") for token in tokens[2:])
    if executable == "cargo":
        return len(tokens) == 2 and tokens[1] in {"test", "check"}
    return False


def safe_path(root, resource, missing=False):
    if not isinstance(resource, str) or "\\" in resource or ":" in resource or "\0" in resource:
        raise RuntimeError("GUARD_PATH_INVALID")
    if resource in {"", "."}: return root
    parts = resource.split("/")
    if any(p in {"", ".", ".."} or p.lower() in PROTECTED for p in parts):
        raise RuntimeError("GUARD_PATH_DENIED")
    target = root
    for i, part in enumerate(parts):
        target = target / part
        try: info = target.lstat()
        except FileNotFoundError:
            if missing and i == len(parts) - 1: return target
            raise RuntimeError("GUARD_PARENT_OR_FILE_MISSING") from None
        if stat.S_ISLNK(info.st_mode) or getattr(info, "st_file_attributes", 0) & 0x400:
            raise RuntimeError("GUARD_LINK_OR_REPARSE_DENIED")
        if i < len(parts) - 1 and not stat.S_ISDIR(info.st_mode): raise RuntimeError("GUARD_PARENT_INVALID")
        if stat.S_ISREG(info.st_mode) and info.st_nlink != 1: raise RuntimeError("GUARD_HARDLINK_DENIED")
    if not target.resolve().is_relative_to(root): raise RuntimeError("GUARD_PATH_ESCAPE")
    return target


WORKSPACE_TOOLS = {
    "read": ({"path", "offset", "limit"}, "READ"), "ls": ({"path"}, "READ"),
    "grep": ({"path", "text"}, "SEARCH"), "write": ({"path", "content", "expectedDigest"}, "WRITE"),
    "edit": ({"path", "oldText", "newText", "expectedDigest"}, "WRITE"),
    "edit_many": ({"path", "edits", "expectedDigest"}, "WRITE"),
    "mkdir": ({"path"}, "WRITE"), "delete": ({"path", "expectedDigest"}, "WRITE"),
    "restore": ({"path", "trashId"}, "WRITE"),
    "git_status": (set(), "GIT"), "git_diff": (set(), "GIT"),
    "run_shell": ({"command"}, "SHELL"),
    "git_commit": ({"message", "paths"}, "GIT"), "run_command": ({"commandId"}, "COMMAND"),
    "git_integrate": ({"sourceTaskId"}, "GIT"),
    "ask_question": ({"question", "context", "options", "answerRequirements"}, "QUESTION"),
}


class GuardBroker:
    def __init__(self, client, task, worker_id, attempt):
        self.client, self.task, self.attempt = client, task, attempt
        self.base = f"/tasks/{task['taskId']}"
        self.lease = {"workerId": worker_id, "attemptId": task["attemptId"], "leaseToken": task["leaseToken"]}
        self.context = client._post(self.base + "/execution-context/read", self.lease)
        if self.context.get("protocol") != PROTOCOL: raise RuntimeError("GUARD_PROTOCOL_UNSUPPORTED")
        supplied = {item.get("attemptId"): item.get("handoff") for item in task.get("inputs", [])}
        task["inputs"] = [{"handoff": supplied.get(item.get("attemptId")), **item, "taskId": item["taskId"].get("value") if isinstance(item["taskId"], dict) else item["taskId"]} for item in self.context["snapshot"]["inputs"]]
        self.repository = self.context["snapshot"]["task"].get("repositoryRef")
        frozen = self.context["snapshot"]["task"]
        if frozen["instruction"] != task.get("instruction") or frozen["instructionRevision"] != task.get("instructionRevision") or frozen.get("repositoryRef") != self.repository:
            raise RuntimeError("FIXED_TASK_CONTEXT_CHANGED")
        self.lock = threading.RLock()
        self.root = self.git = None
        self.writable = False
        self.usage = {"inputTokens": 0, "cachedTokens": 0, "outputTokens": 0}
        self.commands_run = []
        self.required_checks = tuple(task.get("requiredCheckIds", ()))
        if "required-command-checks-v1" in task.get("requiredCapabilities", ()) and not self.required_checks:
            raise RuntimeError("REQUIRED_CHECKS_MISSING")
        self.check_receipts = {}
        self.shell_idle = threading.Event()
        self.shell_idle.set()
        self.created_paths = set()
        self.file_changes = {}
        self._active = set()
        self.allowed_tools = None
        self.accepting_tools = True
        self.pending_question = None
        self.pending_approval = None
        self.approved_operation = None
        self.approved_result_dir = None
        self.checkpoint = None
        self.workspace = None
        from tool_coordination import ToolCoordinator, parallel_enabled
        self.parallel_reads = parallel_enabled()
        self.tool_coordinator = ToolCoordinator(attempt)

    def attach(self, workspace):
        root = Path(workspace).absolute()
        for parent in [*reversed(root.parents), root]:
            info = parent.lstat()
            if stat.S_ISLNK(info.st_mode) or getattr(info, "st_file_attributes", 0) & 0x400: raise RuntimeError("GUARD_WORKSPACE_UNSAFE")
        self.root = root.resolve(strict=True)
        self.git = LazyControlledGit(self.root, self.attempt)

    def begin(self, tool, resource, arguments, call_id=None, *, workspace_finalization=False):
        channel = getattr(self.attempt, "run_channel", None)
        if channel:
            if workspace_finalization and tool == "GIT":
                channel.enter(workspace_finalization=True)
            else:
                channel.enter()
        try:
            return self._begin(tool, resource, arguments, call_id)
        except BaseException:
            if channel:
                channel.leave()
            raise

    def _begin(self, tool, resource, arguments, call_id=None):
        self.attempt.check()
        if self.allowed_tools is not None and tool not in self.allowed_tools: raise RuntimeError("CONFIGURATION_TOOL_NOT_DECLARED")
        operation = "OP-" + (digest({"attempt": self.task["attemptId"], "call": call_id}) if call_id else uuid.uuid4().hex)
        payload = {"protocol": PROTOCOL, "platformInstanceId": self.context["instanceId"], "lease": self.lease, "operationId": operation,
                   "tool": tool, "repositoryRef": self.repository, "resource": resource,
                   "parameterDigest": digest(arguments), "expectedGrantRevision": self.context["grantRevision"]}
        # A lost response is an unknown start, never a reason to resend under a new ID.
        self.attempt.evidence.record("guard-intent", operation=operation, tool=tool, path=resource, digest=payload["parameterDigest"], status="pending")
        try: receipt = self.client._post(self.base + "/operations/begin", payload)
        except Exception:
            self.attempt.resource_uncertain = True
            raise
        if receipt.get("execute") is not True or receipt.get("state") != "STARTED":
            raise RuntimeError("GUARD_REJECTED_OR_ORIGINAL_OPERATION_UNRESOLVED")
        self._active.add(operation)
        self.attempt.evidence.record("guard-start", operation=operation, tool=tool, status="authorized")
        return operation

    def finish(self, operation, state, result, usage=None):
        evidence = self.attempt.evidence.directory.name + "/events.jsonl"
        self.client._post(self.base + "/operations/finish", {
            "lease": self.lease, "operationId": operation, "state": state,
            "resultDigest": digest(result), "evidenceRef": evidence, "usage": usage,
        })
        was_active = operation in self._active
        self._active.discard(operation)
        channel = getattr(self.attempt, "run_channel", None)
        if channel and was_active:
            channel.leave()
        self.attempt.evidence.record("guard-finished", operation=operation, status=state.lower())

    def operate(self, tool, resource, arguments, action, call_id=None, *, workspace_finalization=False, git_inspection=False):
        from contextlib import nullcontext
        with nullcontext() if self.parallel_reads and tool in {"READ", "SEARCH"} else self.lock:
            if self._requires_approval(tool, resource, arguments):
                return self._request_approval(tool, resource, arguments, call_id)
            operation = self.begin(tool, resource, arguments, call_id,
                                   workspace_finalization=workspace_finalization)
            try:
                result = action()
                if tool == "WRITE" and self._file_action(arguments) == "write" and arguments.get("expectedDigest") == "ABSENT":
                    self.created_paths.add(resource)
            except Exception as error:
                # A write/process could already have happened. Keep the workspace and task occupied.
                read_only = git_inspection or tool in {"READ", "SEARCH"}
                code = str(error) if isinstance(error, RuntimeError) else ""
                known = (read_only or code in TOOL_FAILURE_HINTS or code.startswith(("GUARD_LINK_", "GUARD_TRASH_"))) and not self.attempt.resource_uncertain
                try: self.finish(operation, "FAILED" if known else "UNKNOWN", {"code": "OPERATION_FAILED" if known else "OPERATION_OUTCOME_REQUIRES_RECONCILIATION"})
                except Exception:
                    self.attempt.resource_uncertain = True
                    raise
                if not known: self.attempt.resource_uncertain = True
                if git_inspection and known:
                    self.attempt.check()
                    code = "GUARD_GIT_INSPECTION_FAILED"
                    return {"status": "error", "error": code, "hint": TOOL_FAILURE_HINTS[code]}
                if read_only and known and isinstance(error, (OSError, UnicodeError)):
                    raise RuntimeError("GUARD_READ_FAILED") from error
                raise
            try:
                failed = (tool == "SHELL" and result["status"] != "completed"
                          or tool == "COMMAND" and resource in self.required_checks and result["exitCode"] != 0)
                self.finish(operation, "FAILED" if failed else "SUCCEEDED", result)
                self._observe_check_operation(tool, resource, arguments, result, operation)
            except Exception:
                self.attempt.resource_uncertain = True
                raise
            return result

    def _requires_approval(self, tool, resource, arguments):
        if tool == "COMMAND":
            if not isinstance(arguments, dict) or set(arguments) != {"commandId"} or arguments["commandId"] != resource:
                raise RuntimeError("GUARD_COMMAND_DENIED")
            original = self.context.get("originalPolicy", {}).get("commands", [])
            current = self.context.get("currentPolicy", {}).get("commands", [])
            command = next((item for item in original if item.get("id") == resource), None)
            if not isinstance(command, dict) or command not in current:
                raise RuntimeError("GUARD_COMMAND_DENIED")
            if sys.platform != "win32" and self.context.get("originalPolicy", {}).get("riskStrategyVersion", 0) == 0:
                return False
            # Windows process/spawn runs without a filesystem/network sandbox.
            # Require explicit approval before any host command can run.
            needs_approval = sys.platform == "win32" or not _low_risk_command(command)
            self.attempt.evidence.record("guard-command-risk", path=resource,
                code="LOW_RISK_CONFIGURED_COMMAND" if not needs_approval else "OWNER_APPROVAL_REQUIRED")
            return needs_approval
        if tool != "WRITE" or not isinstance(arguments, dict):
            return False
        name = self._file_action(arguments)
        if name == "delete":
            path = safe_path(self.root, resource)
            if arguments.get("expectedDigest") != hashlib.sha256(self._read(path)).hexdigest():
                raise RuntimeError("GUARD_FILE_VERSION_CONFLICT")
            # Every deletion is moved to a protected local trash before it disappears.
            return self.context.get("originalPolicy", {}).get("riskStrategyVersion", 0) != 1 and resource not in self.created_paths
        return False

    def _ordinary_edit(self, resource):
        return (self.context.get("originalPolicy", {}).get("riskStrategyVersion", 0) == 1
                and (resource in self.file_changes or len(self.file_changes) < 20))

    @staticmethod
    def _file_action(arguments):
        # Actions come from the closed host argument schema, not a model-supplied risk label.
        if "content" in arguments: return "write"
        if "oldText" in arguments or "edits" in arguments: return "edit"
        if "expectedDigest" in arguments: return "delete"
        if "trashId" in arguments: return "restore"
        return "mkdir"

    def _request_approval(self, tool, resource, arguments, call_id):
        from formal_questions import YieldRequested
        if self.pending_approval or self.pending_question or self.checkpoint is None or self.workspace is None:
            raise RuntimeError("TOOL_APPROVAL_CHECKPOINT_UNAVAILABLE")
        name = self._file_action(arguments) if tool == "WRITE" else "command"
        command = None
        if tool == "COMMAND":
            if name != "command" or set(arguments) != {"commandId"} or arguments["commandId"] != resource:
                raise RuntimeError("TOOL_APPROVAL_ACTION_UNSUPPORTED")
            original = self.context.get("originalPolicy", {}).get("commands", [])
            current = self.context.get("currentPolicy", {}).get("commands", [])
            command = next((item for item in original if item.get("id") == resource), None)
            if not isinstance(command, dict) or command not in current:
                raise RuntimeError("GUARD_COMMAND_DENIED")
            version = command_digest(command)
        else:
            if name != "delete":
                raise RuntimeError("TOOL_APPROVAL_ACTION_UNSUPPORTED")
            path = safe_path(self.root, resource)
            expected = arguments.get("expectedDigest")
            version = hashlib.sha256(self._read(path)).hexdigest() if path.exists() else "ABSENT"
            if expected != version or version == "ABSENT":
                raise RuntimeError("GUARD_FILE_VERSION_CONFLICT")
        operation = "OP-" + (digest({"attempt": self.task["attemptId"], "call": call_id}) if call_id else uuid.uuid4().hex)
        payload = {"protocol": PROTOCOL, "platformInstanceId": self.context["instanceId"], "lease": self.lease,
                   "operationId": operation, "tool": tool, "repositoryRef": self.repository, "resource": resource,
                   "parameterDigest": digest(arguments), "expectedGrantRevision": self.context["grantRevision"]}
        self.accepting_tools = False
        self.attempt.evidence.record("approval-intent", operation=operation, tool=tool, path=resource,
                                     digest=payload["parameterDigest"], status="pending")
        result = self.checkpoint()
        from worker_runtime import snapshot
        approval_id = "A-" + uuid.uuid4().hex
        action_name = "EXECUTE_COMMAND" if name == "command" else "DELETE_FILE"
        artifact_operation = {key: value for key, value in payload.items() if key != "lease"}
        artifact_operation["sourceAttemptId"] = self.lease["attemptId"]
        artifact = {"operation": artifact_operation, "action": action_name,
                    "arguments": arguments, "targetVersion": version, "command": command}
        from plan_workspace import directory
        approval_dir = directory(Path(self.workspace.cwd), ".ai", "approvals")
        artifact_path = approval_dir / (approval_id + ".json")
        raw = json.dumps(artifact, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
        descriptor = os.open(artifact_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(raw);stream.flush();os.fsync(stream.fileno())
        handoff = self.workspace.handoff(self.task,
            {"verdict": "REPAIR_REQUIRED", "summary": "Waiting for approval of one fixed workspace operation."},
            result, snapshot(self.workspace.cwd, self.attempt),
            {"id": approval_id, "path": artifact_path.relative_to(Path(self.workspace.cwd)).as_posix(),
             "sha256": hashlib.sha256(raw).hexdigest()})
        self.client._post(f"/tasks/{self.task['taskId']}/handoff", {"workerId": self.lease["workerId"],
            "leaseToken": self.lease["leaseToken"], "handoff": handoff})
        checkpoint = {"kind": "tool-approval", "id": approval_id,
                      "revision": result["resultRevision"] if result and result.get("resultRevision") else digest(snapshot(self.workspace.cwd, self.attempt)),
                      "sha256": handoff["sha256"]}
        expires = int(time.time() * 1000) + 3_600_000
        binding = {"protocol": "aiw.tool.approval.v1", "approvalId": approval_id, "operation": payload,
                   "action": artifact["action"], "workspaceDigest": digest({"repositoryRef": self.repository,
                       "planId": self.task.get("planId"), "checkpoint": checkpoint["revision"]}),
                   "targetVersion": version, "expiresAt": expires, "command": command,
                   "gitCheckpoint": bool(result and re.fullmatch(r"[0-9a-f]{40}|[0-9a-f]{64}", str(result.get("resultRevision", "")))),
                   "checkpoint": checkpoint}
        try:
            receipt = self.client._post(self.base + "/operations/approval", binding)
            yield_payload = {"lease": self.lease, "operationId": approval_id, "checkpoint": checkpoint}
        except Exception:
            self.attempt.resource_uncertain = True
            raise
        self.pending_approval = {"approvalId": approval_id, "yield": yield_payload, "receipt": receipt}
        raise YieldRequested("TOOL_APPROVAL_WAITING")

    def prepare_approved_inputs(self, task):
        if not any(ref.get("kind") == "tool-approval" for ref in self.context["snapshot"].get("references", [])):
            return
        response = self.client._post(self.base + "/operations/approved", self.lease)
        if not isinstance(response, dict) or type(response.get("available")) is not bool:
            raise RuntimeError("APPROVED_OPERATION_LOOKUP_INVALID")
        if not response["available"]:
            if response.get("operation") is not None:
                raise RuntimeError("APPROVED_OPERATION_LOOKUP_INVALID")
            return
        approved = response.get("operation")
        fields = {"approvalId", "sourceTaskId", "sourceAttemptId", "bindingDigest", "operation", "action",
                  "workspaceDigest", "targetVersion", "checkpoint", "gitCheckpoint", "operationState", "execute", "resultDigest"}
        if (not isinstance(approved, dict) or set(approved) != fields
                or not re.fullmatch(r"[A-Za-z0-9_-]{1,128}", str(approved.get("approvalId", "")))
                or not isinstance(approved.get("sourceTaskId"), str) or not isinstance(approved.get("sourceAttemptId"), str)
                or type(approved.get("gitCheckpoint")) is not bool or type(approved.get("execute")) is not bool
                or approved.get("operationState") not in {"APPROVED", "CONSUMED", "SUCCEEDED", "FAILED", "EXPIRED", "INVALIDATED"}):
            raise RuntimeError("APPROVED_OPERATION_DESCRIPTOR_INVALID")
        matches = [item for item in task.get("inputs", [])
                   if item.get("taskId") == approved.get("sourceTaskId")
                   and item.get("attemptId") == approved.get("sourceAttemptId") and item.get("handoff")]
        if len(matches) != 1:
            raise RuntimeError("APPROVED_OPERATION_HANDOFF_MISSING")
        if task.get("checkpointInputs"):
            raise RuntimeError("APPROVED_OPERATION_CHECKPOINT_COLLISION")
        source = matches[0]
        checkpoint = approved.get("checkpoint")
        handoff = source.get("handoff")
        if (not isinstance(checkpoint, dict) or checkpoint.get("kind") != "tool-approval"
                or checkpoint.get("id") != approved.get("approvalId")
                or not isinstance(handoff, dict) or handoff.get("sha256") != checkpoint.get("sha256")
                or not re.fullmatch(r"[0-9a-f]{64}", str(approved.get("bindingDigest", "")))):
            raise RuntimeError("APPROVED_OPERATION_CHECKPOINT_BINDING_INVALID")
        task["inputs"] = [item for item in task.get("inputs", []) if item is not source]
        task["checkpointInputs"] = [source]
        self.approved_operation = approved

    def execute_approved(self):
        approved = self.approved_operation
        if approved is None:
            return None
        if not self.writable:
            raise RuntimeError("APPROVED_OPERATION_ROLE_IS_READ_ONLY")
        manifests = self.task.get("handoffInputs", [])
        matches = [manifest for manifest in manifests
                   if manifest.get("taskId") == approved.get("sourceTaskId")
                   and manifest.get("attemptId") == approved.get("sourceAttemptId")
                   and isinstance(manifest.get("toolApproval"), dict)
                   and manifest["toolApproval"].get("id") == approved.get("approvalId")]
        if len(matches) != 1:
            raise RuntimeError("APPROVED_OPERATION_HANDOFF_MISMATCH")
        manifest = matches[0]
        checkpoint = approved.get("checkpoint")
        metadata = manifest.get("toolApproval")
        if (not isinstance(checkpoint, dict) or checkpoint.get("kind") != "tool-approval"
                or checkpoint.get("id") != approved.get("approvalId") or manifest.get("checkpoint") is not True
                or set(metadata) != {"id", "path", "sha256"}
                or metadata.get("path") != ".ai/approvals/" + approved["approvalId"] + ".json"
                or not re.fullmatch(r"[0-9a-f]{64}", str(metadata.get("sha256", "")))):
            raise RuntimeError("APPROVED_OPERATION_CHECKPOINT_INVALID")
        handoff_root = Path(manifest["location"]).resolve(strict=True)
        original_cwd = Path(manifest["workingDirectory"])
        if not original_cwd.is_absolute():
            raise RuntimeError("APPROVED_OPERATION_WORKSPACE_INVALID")
        original_cwd = Path(os.path.abspath(original_cwd))
        if not original_cwd.is_relative_to(handoff_root):
            raise RuntimeError("APPROVED_OPERATION_WORKSPACE_INVALID")
        current = original_cwd
        while current != handoff_root:
            info = current.lstat()
            if stat.S_ISLNK(info.st_mode) or getattr(info, "st_file_attributes", 0) & 0x400:
                raise RuntimeError("APPROVED_OPERATION_WORKSPACE_INVALID")
            current = current.parent
        if original_cwd.resolve(strict=True) != original_cwd:
            raise RuntimeError("APPROVED_OPERATION_WORKSPACE_INVALID")
        artifact_path = original_cwd / ".ai" / "approvals" / (approved["approvalId"] + ".json")
        for parent in (original_cwd / ".ai", original_cwd / ".ai" / "approvals", artifact_path):
            info = parent.lstat()
            if stat.S_ISLNK(info.st_mode) or getattr(info, "st_file_attributes", 0) & 0x400:
                raise RuntimeError("APPROVED_OPERATION_ARTIFACT_UNSAFE")
        self.approved_result_dir = original_cwd / ".ai" / "approvals"
        descriptor = os.open(artifact_path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_NONBLOCK", 0))
        with os.fdopen(descriptor, "rb") as stream:
            info = os.fstat(stream.fileno())
            if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1 or info.st_size > 2 * 1024 * 1024:
                raise RuntimeError("APPROVED_OPERATION_ARTIFACT_INVALID")
            raw = stream.read(2 * 1024 * 1024 + 1)
        if len(raw) > 2 * 1024 * 1024 or hashlib.sha256(raw).hexdigest() != metadata["sha256"]:
            raise RuntimeError("APPROVED_OPERATION_ARTIFACT_HASH_MISMATCH")
        try:
            artifact = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise RuntimeError("APPROVED_OPERATION_ARTIFACT_INVALID") from error
        operation = artifact.get("operation") if isinstance(artifact, dict) else None
        fixed = approved.get("operation")
        if (not isinstance(artifact, dict) or set(artifact) != {"operation", "action", "arguments", "targetVersion", "command"}
                or not isinstance(operation, dict) or not isinstance(fixed, dict)
                or set(operation) != {"protocol", "platformInstanceId", "sourceAttemptId", "operationId", "tool", "repositoryRef", "resource", "parameterDigest", "expectedGrantRevision"}
                or operation.get("sourceAttemptId") != approved.get("sourceAttemptId")
                or operation.get("protocol") != PROTOCOL
                or operation.get("platformInstanceId") != self.context.get("instanceId")
                or any(operation.get(key) != fixed.get(key) for key in ("operationId", "tool", "repositoryRef", "resource", "parameterDigest", "expectedGrantRevision"))):
            raise RuntimeError("APPROVED_OPERATION_BINDING_MISMATCH")
        args = artifact.get("arguments")
        action = artifact.get("action")
        command_action = approved.get("action") == "EXECUTE_COMMAND"
        name = "run_command" if command_action else {"REPLACE_FILE": "write", "DELETE_FILE": "delete"}.get(approved.get("action"))
        if not name or action != approved.get("action") or not isinstance(args, dict):
            raise RuntimeError("APPROVED_OPERATION_PARAMETERS_CHANGED")
        if command_action:
            if fixed.get("tool") != "COMMAND" or set(args) != {"commandId"} or args.get("commandId") != fixed.get("resource"):
                raise RuntimeError("APPROVED_OPERATION_PARAMETERS_CHANGED")
            original = self.context.get("originalPolicy", {}).get("commands", [])
            current = self.context.get("currentPolicy", {}).get("commands", [])
            command = next((item for item in original if item.get("id") == fixed.get("resource")), None)
            if (not isinstance(command, dict) or artifact.get("command") != command
                    or command_digest(command) != approved.get("targetVersion")
                    or approved.get("execute") and command not in current):
                raise RuntimeError("APPROVED_OPERATION_COMMAND_CHANGED")
        elif (fixed.get("tool") != "WRITE" or artifact.get("command") is not None
              or set(args) != ({"path", "content", "expectedDigest"} if name == "write" else {"path", "expectedDigest"})
              or args.get("path") != fixed.get("resource") or args.get("expectedDigest") != approved.get("targetVersion")):
            raise RuntimeError("APPROVED_OPERATION_PARAMETERS_CHANGED")
        if artifact.get("targetVersion") != approved.get("targetVersion") or digest(args) != fixed.get("parameterDigest"):
            raise RuntimeError("APPROVED_OPERATION_PARAMETERS_CHANGED")
        if (approved.get("workspaceDigest") != digest({"repositoryRef": self.repository,
                "planId": self.task.get("planId"), "checkpoint": checkpoint.get("revision")})
                or approved.get("gitCheckpoint") is not (isinstance(manifest.get("gitResult"), dict))
                or approved.get("gitCheckpoint") and manifest["gitResult"].get("resultRevision") != checkpoint.get("revision")):
            raise RuntimeError("APPROVED_OPERATION_WORKSPACE_CHANGED")
        if not approved.get("execute"):
            state = approved.get("operationState")
            if state == "SUCCEEDED":
                if not re.fullmatch(r"[0-9a-f]{64}", str(approved.get("resultDigest", ""))):
                    raise RuntimeError("APPROVED_OPERATION_REPLAY_UNVERIFIED")
                self.attempt.evidence.record("approved-operation-already-completed", operation=fixed["operationId"], resultDigest=approved["resultDigest"])
                if command_action:
                    result = self._read_approved_command_result(approved["approvalId"], approved["resultDigest"], command)
                    self._record_approved_command(command, result)
                    return {"status": "already-completed", "target": fixed["resource"], "resultDigest": approved["resultDigest"], "result": result}
                return {"status": "already-completed", "target": fixed["resource"], "resultDigest": approved["resultDigest"]}
            if state == "FAILED" and command_action and fixed["resource"] in self.required_checks:
                try:
                    result = self._read_approved_command_result(approved["approvalId"], approved["resultDigest"], command)
                except FileNotFoundError:
                    return {"status": "failed", "target": fixed["resource"]}
                self._record_approved_command(command, result)
                return {"status": "already-completed", "target": fixed["resource"], "resultDigest": approved["resultDigest"], "result": result}
            if state not in {"FAILED", "EXPIRED", "INVALIDATED"}:
                raise RuntimeError("APPROVED_OPERATION_STATE_UNVERIFIED")
            self.attempt.evidence.record("approved-operation-not-applied", operation=fixed["operationId"], state=state)
            return {"status": "not-applied", "target": fixed["resource"]}
        if approved.get("operationState") != "APPROVED":
            raise RuntimeError("APPROVED_OPERATION_STATE_MISMATCH")
        if not command_action:
            target = safe_path(self.root, args["path"], missing=True)
            current_version = hashlib.sha256(self._read(target)).hexdigest() if target.exists() else "ABSENT"
            if current_version != approved["targetVersion"]:
                self.client._post(self.base + "/operations/approved-discard", {"lease": self.lease,
                    "discard": {"approvalId": approved["approvalId"], "bindingDigest": approved["bindingDigest"],
                                "reason": "TARGET_VERSION_CHANGED"}})
                self.attempt.evidence.record("approved-operation-invalidated", operation=fixed["operationId"], reason="target-version-changed")
                return {"status": "target-changed", "target": fixed["resource"]}
        channel = getattr(self.attempt, "run_channel", None)
        if channel:
            channel.enter()
        started = False
        try:
            operation_request = {"protocol": PROTOCOL, "platformInstanceId": self.context["instanceId"],
                "lease": self.lease, "operationId": fixed["operationId"], "tool": fixed["tool"],
                "repositoryRef": fixed["repositoryRef"], "resource": fixed["resource"],
                "parameterDigest": fixed["parameterDigest"], "expectedGrantRevision": fixed["expectedGrantRevision"]}
            consume = {"approvalId": approved["approvalId"], "bindingDigest": approved["bindingDigest"],
                "operation": operation_request, "action": approved["action"],
                "workspaceDigest": approved["workspaceDigest"], "targetVersion": approved["targetVersion"]}
            try:
                receipt = self.client._post(self.base + "/operations/approved-begin", consume)
            except Exception:
                self.attempt.resource_uncertain = True
                raise
            if receipt.get("execute") is not True or receipt.get("state") != "STARTED":
                if receipt.get("state") == "STARTED":
                    self.attempt.resource_uncertain = True
                raise RuntimeError("APPROVED_OPERATION_NOT_STARTED")
            self._active.add(fixed["operationId"])
            started = True
            self.attempt.evidence.record("approved-operation-started", operation=fixed["operationId"], target=fixed["resource"])
            self.attempt.check()
            result = self._execute(name, args)
            if command_action:
                self._store_approved_command_result(approved["approvalId"], result, command)
            check_failed = command_action and fixed["resource"] in self.required_checks and result["exitCode"] != 0
            self.finish(fixed["operationId"], "FAILED" if check_failed else "SUCCEEDED", result)
            self._observe_check_operation(fixed["tool"], fixed["resource"], args, result, fixed["operationId"])
            response = {"status": "applied", "target": fixed["resource"], "resultDigest": digest(result)}
            if command_action:
                response["result"] = result
            return response
        except Exception as error:
            if started:
                known = str(error).startswith(("GUARD_PATH_", "GUARD_LINK_", "GUARD_FILE_VERSION_", "GUARD_WRITE_LIMIT", "GUARD_ROLE_")) and not self.attempt.resource_uncertain
                try:
                    self.finish(fixed["operationId"], "FAILED" if known else "UNKNOWN",
                                {"code": "OPERATION_FAILED" if known else "OPERATION_OUTCOME_REQUIRES_RECONCILIATION"})
                except Exception:
                    self.attempt.resource_uncertain = True
                    raise
                if not known:
                    self.attempt.resource_uncertain = True
                elif str(error).startswith("GUARD_FILE_VERSION_CONFLICT"):
                    self.attempt.evidence.record("approved-operation-invalidated", operation=fixed["operationId"], reason="target-version-changed")
                    return {"status": "target-changed", "target": fixed["resource"]}
            raise
        finally:
            if channel and not started:
                channel.leave()

    def _approved_command_result_path(self, approval_id):
        if self.approved_result_dir is not None:
            return self.approved_result_dir / (approval_id + ".result.json")
        from plan_workspace import directory
        return directory(Path(self.workspace.cwd), ".ai", "approvals") / (approval_id + ".result.json")

    def _store_approved_command_result(self, approval_id, result, command):
        if (not isinstance(result, dict) or type(result.get("exitCode")) is not int
                or not isinstance(result.get("stdout"), str) or not isinstance(result.get("stderr"), str)
                or len(result["stdout"].encode("utf-8")) + len(result["stderr"].encode("utf-8")) > command["outputBytes"]):
            raise RuntimeError("APPROVED_COMMAND_RESULT_INVALID")
        raw = json.dumps(result, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
        path = self._approved_command_result_path(approval_id)
        descriptor = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(raw);stream.flush();os.fsync(stream.fileno())

    def _read_approved_command_result(self, approval_id, result_digest, command):
        path = self._approved_command_result_path(approval_id)
        descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_NONBLOCK", 0))
        with os.fdopen(descriptor, "rb") as stream:
            info = os.fstat(stream.fileno())
            if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1 or info.st_size > command["outputBytes"] + 65536:
                raise RuntimeError("APPROVED_COMMAND_RESULT_INVALID")
            raw = stream.read(command["outputBytes"] + 65537)
        if len(raw) > command["outputBytes"] + 65536:
            raise RuntimeError("APPROVED_COMMAND_RESULT_INVALID")
        try:
            result = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise RuntimeError("APPROVED_COMMAND_RESULT_INVALID") from error
        if (not isinstance(result, dict) or type(result.get("exitCode")) is not int
                or not isinstance(result.get("stdout"), str) or not isinstance(result.get("stderr"), str)
                or len(result["stdout"].encode("utf-8")) + len(result["stderr"].encode("utf-8")) > command["outputBytes"]
                or digest(result) != result_digest):
            raise RuntimeError("APPROVED_COMMAND_RESULT_DIGEST_MISMATCH")
        return result

    def _record_approved_command(self, command, result):
        self.commands_run.append({"commandId": command["id"], "exitCode": result["exitCode"], "outputDigest": digest(result)})

    def _observe_check_operation(self, tool, resource, arguments, result, operation):
        if not self.required_checks:
            return
        if tool == "COMMAND" and resource in self.required_checks:
            self.check_receipts[resource] = {"commandId": resource, "operationId": operation,
                "outputDigest": digest(result), "exitCode": result["exitCode"],
                "checkStatus": "passed" if result["exitCode"] == 0 else "failed"}
        elif tool in {"WRITE", "COMMAND", "SHELL"} or tool == "GIT" and "sourceTaskId" in arguments:
            for receipt in self.check_receipts.values():
                receipt["checkStatus"] = "stale"

    def unmet_required_checks(self):
        return ", ".join(f"{key} ({status})" for key in self.required_checks
                         if (status := self.check_receipts.get(key, {}).get("checkStatus", "not-run")) != "passed")

    def invoke(self, name, args, call_id=None):
        from tool_coordination import READ_ONLY
        with self.tool_coordinator.access(self.parallel_reads and name in READ_ONLY):
            if self.attempt.resource_uncertain:
                raise RuntimeError("GUARD_RESOURCE_OUTCOME_UNCONFIRMED")
            try:
                return self._invoke_checked(name, args, call_id)
            except RuntimeError as error:
                code = str(error)
                if self.attempt.resource_uncertain or code not in TOOL_FAILURE_HINTS:
                    raise
                self.attempt.check()
                return {"status": "error", "error": code, "hint": TOOL_FAILURE_HINTS[code]}

    def _invoke_checked(self, name, args, call_id):
        if not self.accepting_tools:
            from formal_questions import YieldRequested
            raise YieldRequested("QUESTION_TOOLS_CLOSED")
        if name in {"read_skill_file", "read_skill_text"}:
            self.attempt.check()
            if not isinstance(args, dict) or set(args) != {"ref", "path", "offset", "limit"} or not getattr(self.attempt, "skill_files", None):
                raise RuntimeError("SKILL_FILES_UNAVAILABLE")
            channel = getattr(self.attempt, "run_channel", None)
            if channel:
                channel.enter()
            try:
                reader = self.attempt.skill_files.read_text if name == "read_skill_text" else self.attempt.skill_files.read
                try:
                    result = reader(**args)
                    self.attempt.evidence.record("skill-file-read", path="skill:" + args["ref"] + "/" + args["path"],
                                                 digest=result["sha256"], status="succeeded")
                    return result
                except RuntimeError as error:
                    if str(error) not in {"SKILL_FILE_NOT_DECLARED", "SKILL_FILES_INVALID_OR_CHANGED",
                                          "SKILL_TEXT_RANGE_INVALID", "SKILL_TEXT_OFFSET_NOT_BOUNDARY"}:
                        raise
                    return {"error": "Use the exact Skill name without its version, a listed file path, and a byte limit of 1 to 8192."}
            finally:
                if channel:
                    channel.leave()
        return self._invoke(name, args, call_id)

    def shell_enabled(self):
        return (self.writable and "task-shell-v1" in self.attempt.required_capabilities
                and (self.allowed_tools is None or "SHELL" in self.allowed_tools)
                and all(any(rule["tool"] == "SHELL" for rule in self.context.get(policy, {}).get("rules", []))
                        for policy in ("originalPolicy", "currentPolicy")))

    def _invoke(self, name, args, call_id=None):
        if self.root is None: raise RuntimeError("GUARD_WORKSPACE_NOT_ATTACHED")
        if not isinstance(args, dict): raise RuntimeError("GUARD_TOOL_ARGUMENTS_INVALID")
        if name not in WORKSPACE_TOOLS or set(args) != WORKSPACE_TOOLS[name][0]: raise RuntimeError("GUARD_TOOL_ARGUMENTS_INVALID")
        tool = WORKSPACE_TOOLS[name][1]
        if tool == "SHELL":
            command = args["command"]
            if not self.shell_enabled() or not isinstance(command, str) or not command.strip() or len(command) > 4096 or "\0" in command:
                raise RuntimeError("GUARD_SHELL_DENIED_OR_INVALID")
        if (tool in {"WRITE", "COMMAND"} or name in {"git_commit", "git_integrate"}) and not self.writable: raise RuntimeError("GUARD_ROLE_IS_READ_ONLY")
        if tool == "GIT" and not (self.root / ".git").exists():
            self.attempt.check()
            code = "GUARD_GIT_REPOSITORY_REQUIRED"
            return {"status": "error", "error": code, "hint": TOOL_FAILURE_HINTS[code]}
        resource = args.get("path", args.get("commandId", "."))
        if "path" in args and (not isinstance(resource, str) or len(resource) > 1024
                or any(character in resource for character in ("\\", ":", "\0"))
                or (resource != "." and any(part in {"", ".", ".."} for part in resource.split("/")))):
            raise RuntimeError("GUARD_PATH_INVALID")
        if name == "write" and args["expectedDigest"] != "ABSENT":
            raise RuntimeError("GUARD_EXISTING_FILE_REQUIRES_EDIT")
        if tool != "SHELL":
            return self.operate(tool, resource, args, lambda: self._execute(name, args), call_id,
                                git_inspection=name in {"git_status", "git_diff"})
        self.shell_idle.clear()
        try:
            return self.operate(tool, resource, args, lambda: self._execute(name, args), call_id)
        finally:
            self.shell_idle.set()

    def _execute(self, name, args):
        if name == "ask_question":
            from formal_questions import ask
            return ask(self, args)
        if name.startswith("git_"):
            if name == "git_status": return self.git.run("status", "--porcelain=v1", "--untracked-files=normal")
            if name == "git_diff": return self.git.run("diff", "--no-ext-diff", "--no-textconv", "--")
            if name == "git_integrate":
                def identifier(source):
                    value = source["taskId"]
                    return value.get("value") if isinstance(value, dict) else value
                source = next((s for s in self.context["snapshot"]["inputs"] if identifier(s) == args["sourceTaskId"]), None)
                if source is None or source.get("repositoryRef") != self.repository or not re.fullmatch(r"[0-9a-f]{40}|[0-9a-f]{64}", source.get("resultRevision") or ""):
                    raise RuntimeError("GUARD_UPSTREAM_RESULT_NOT_FIXED")
                return self.git.integrate(source["resultRevision"])
            return self.git.commit(args["paths"], args["message"])
        if name == "run_shell":
            from task_shell import run_shell
            result = run_shell(self.root, args["command"], self.attempt)
            self.commands_run.append({"command": args["command"], "status": result["status"], "exitCode": result["exitCode"],
                                      "outputDigest": digest(result), "outputTruncated": result["outputTruncated"]})
            return result
        if name == "run_command":
            from guarded_command import run_command
            commands = self.context["originalPolicy"]["commands"]
            command = next((c for c in commands if c["id"] == args["commandId"]), None)
            if command is None or command not in self.context["currentPolicy"]["commands"]: raise RuntimeError("GUARD_COMMAND_DENIED")
            result = run_command(self.root, command, self.attempt)
            self.commands_run.append({"commandId": command["id"], "exitCode": result["exitCode"], "outputDigest": digest(result)})
            return result
        path = safe_path(self.root, args["path"], missing=name in {"write", "mkdir", "restore"})
        if name == "mkdir":
            if path.exists():
                if not path.is_dir(): raise RuntimeError("GUARD_NOT_DIRECTORY")
            else: path.mkdir(mode=0o755)
            self.file_changes[args["path"]] = {"path": args["path"], "state": "DIRECTORY"}
            return {"createdDirectory": args["path"]}
        if name == "delete":
            if hashlib.sha256(self._read(path)).hexdigest() != args["expectedDigest"]: raise RuntimeError("GUARD_FILE_VERSION_CONFLICT")
            from plan_workspace import directory
            trash_id = uuid.uuid4().hex
            trash = directory(getattr(self, "source_root", self.root), ".ai", "trash", trash_id)
            metadata = {"path": args["path"], "sha256": args["expectedDigest"], "trashId": trash_id}
            with (trash / "receipt.json").open("x", encoding="utf-8") as output:
                json.dump(metadata, output);output.flush();os.fsync(output.fileno())
            os.replace(path, trash / "content")
            self.file_changes[args["path"]] = {**metadata, "state": "DELETED"}
            return {"deletedFile": args["path"], "trashId": trash_id}
        if name == "restore":
            trash_id = args["trashId"]
            if not isinstance(trash_id, str) or not re.fullmatch(r"[0-9a-f]{32}", trash_id):
                raise RuntimeError("GUARD_TRASH_ID_INVALID")
            from plan_workspace import directory
            trash = directory(getattr(self, "source_root", self.root), ".ai", "trash", trash_id)
            receipt, content = trash / "receipt.json", trash / "content"
            if receipt.is_symlink() or content.is_symlink() or not receipt.is_file() or receipt.stat().st_size > 16384:
                raise RuntimeError("GUARD_TRASH_RECEIPT_INVALID")
            metadata = json.loads(receipt.read_text(encoding="utf-8"))
            if metadata.get("path") != args["path"] or metadata.get("sha256") != hashlib.sha256(self._read(content)).hexdigest():
                raise RuntimeError("GUARD_TRASH_CONTENT_CHANGED")
            if path.exists(): raise RuntimeError("GUARD_RESTORE_TARGET_EXISTS")
            try:
                os.link(content, path, follow_symlinks=False)  # Atomic no-clobber publication.
            except FileExistsError:
                raise RuntimeError("GUARD_RESTORE_TARGET_EXISTS") from None
            content.unlink()
            self.file_changes[args["path"]] = {"path": args["path"], "state": "RESTORED", "sha256": metadata["sha256"]}
            return {"restoredFile": args["path"], "sha256": metadata["sha256"]}
        if name == "ls":
            if not path.is_dir(): raise RuntimeError("GUARD_NOT_DIRECTORY")
            names = []
            for entry in sorted(path.iterdir(), key=lambda p: p.name):
                if len(names) == 1000: return {"entries": names, "complete": False}
                if entry.name.lower() in PROTECTED: continue
                try: safe_path(self.root, entry.relative_to(self.root).as_posix())
                except RuntimeError: continue
                names.append(entry.name)
            return {"entries": names, "complete": True}
        if name == "grep":
            text = args["text"]
            if not isinstance(text, str) or not text or len(text) > 1024: raise RuntimeError("GUARD_SEARCH_INVALID")
            # Search one exact file. Directory walking is deliberately explicit through ls + per-file calls.
            raw = self._read(path);lines = raw.decode("utf-8").splitlines()
            matches = [{"line": i, "text": line[:2000]} for i, line in enumerate(lines, 1) if text in line]
            return {"matches": matches[:100], "complete": len(matches) <= 100, "sourceDigest": hashlib.sha256(raw).hexdigest()}
        if name == "read":
            start, limit = args["offset"], args["limit"]
            if type(start) is not int or type(limit) is not int or start < 1 or not 1 <= limit <= 500: raise RuntimeError("GUARD_READ_RANGE_INVALID")
            raw = self._read(path);lines = raw.decode("utf-8").splitlines()
            output = "\n".join(lines[start - 1:start - 1 + limit])
            if len(output.encode()) > 65536: raise RuntimeError("GUARD_OUTPUT_LIMIT")
            return {"content": output, "totalLines": len(lines), "sourceDigest": hashlib.sha256(raw).hexdigest()}
        expected = args["expectedDigest"]
        original = self._read(path) if path.exists() else None
        if expected == "ABSENT" and original is not None and name == "write":
            raise RuntimeError("GUARD_EXISTING_FILE_REQUIRES_EDIT")
        actual = hashlib.sha256(original).hexdigest() if original is not None else "ABSENT"
        if expected != actual: raise RuntimeError("GUARD_FILE_VERSION_CONFLICT")
        if name in {"edit", "edit_many"}:
            if original is None:
                raise RuntimeError("GUARD_PARENT_OR_FILE_MISSING")
            edits = args["edits"] if name == "edit_many" else [{"oldText": args["oldText"], "newText": args["newText"]}]
            if not isinstance(edits, list) or not 1 <= len(edits) <= 16:
                raise RuntimeError("GUARD_TOOL_ARGUMENTS_INVALID")
            if not self._ordinary_edit(args["path"]):
                raise RuntimeError("GUARD_EDIT_LIMIT")
            crlf = b"\r\n" in original and b"\n" not in original.replace(b"\r\n", b"")
            regions = []
            before_size = after_size = 0
            for edit in edits:
                if not isinstance(edit, dict) or set(edit) != {"oldText", "newText"}:
                    raise RuntimeError("GUARD_TOOL_ARGUMENTS_INVALID")
                before, after = edit["oldText"], edit["newText"]
                if not isinstance(before, str) or not before or not isinstance(after, str):
                    raise RuntimeError("GUARD_EDIT_MATCH_REQUIRED")
                before, after = before.encode("utf-8"), after.encode("utf-8")
                if crlf:
                    before = before.replace(b"\r\n", b"\n").replace(b"\n", b"\r\n")
                    after = after.replace(b"\r\n", b"\n").replace(b"\n", b"\r\n")
                before_size += len(before)
                after_size += len(after)
                if max(before_size, after_size) > 65536:
                    raise RuntimeError("GUARD_EDIT_LIMIT")
                index = original.find(before)
                if index < 0 or original.find(before, index + 1) >= 0:
                    raise RuntimeError("GUARD_EDIT_MATCH_REQUIRED")
                regions.append((index, index + len(before), after))
            parts = []
            end = 0
            for start, stop, replacement in sorted(regions):
                if start < end:
                    raise RuntimeError("GUARD_EDIT_OVERLAP")
                parts.extend((original[end:start], replacement))
                end = stop
            parts.append(original[end:])
            data = b"".join(parts)
        else:
            content = args["content"]
            if not isinstance(content, str):
                raise RuntimeError("GUARD_TOOL_ARGUMENTS_INVALID")
            data = content.encode("utf-8")
        if len(data) > 2 * 1024 * 1024:
            raise RuntimeError("GUARD_WRITE_LIMIT")
        temporary = path.with_name(".aiw-write-" + uuid.uuid4().hex)
        descriptor = os.open(temporary, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        try:
            with os.fdopen(descriptor, "wb") as stream: stream.write(data);stream.flush();os.fsync(stream.fileno())
            safe_path(self.root, args["path"], missing=True)
            if expected == "ABSENT":
                try:
                    os.link(temporary, path, follow_symlinks=False)
                except FileExistsError:
                    raise RuntimeError("GUARD_EXISTING_FILE_REQUIRES_EDIT") from None
            else:
                current = hashlib.sha256(self._read(path)).hexdigest() if path.exists() else "ABSENT"
                if current != expected: raise RuntimeError("GUARD_FILE_VERSION_CONFLICT")
                os.replace(temporary, path)
        finally:
            if temporary.exists(): temporary.unlink()
        file_digest = hashlib.sha256(data).hexdigest()
        self.file_changes[args["path"]] = {"path": args["path"], "state": "WRITTEN", "sha256": file_digest}
        return {"resultDigest": file_digest}

    def _read(self, path):
        descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_NONBLOCK", 0))
        with os.fdopen(descriptor, "rb") as stream:
            info = os.fstat(stream.fileno())
            if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1 or info.st_size > 2 * 1024 * 1024: raise RuntimeError("GUARD_FILE_INVALID_OR_TOO_LARGE")
            raw = stream.read(2 * 1024 * 1024 + 1)
            if len(raw) > 2 * 1024 * 1024: raise RuntimeError("GUARD_FILE_LIMIT")
            return raw

    def model_started(self, arguments):
        if not self.accepting_tools:
            from formal_questions import YieldRequested
            raise YieldRequested("QUESTION_WAITING")
        return self.begin("MODEL", ".", arguments)

    def model_finished(self, operation, output, usage):
        if usage is not None:
            for key in self.usage: self.usage[key] += usage[key]
        self.finish(operation, "SUCCEEDED", {"outputDigest": digest(output)}, dict(self.usage) if usage is not None else None)

    def record_delivery(self, result, report):
        channel = getattr(self.attempt, "run_channel", None)
        if channel and channel.phase != "FINALIZING":
            if channel.seal("final-delivery") is not None:
                raise RuntimeError("RUN_UNCONSUMED_OPERATOR_INPUT")
        report["outputFiles"] = list(self.file_changes.values())
        delivery = {"scope": self.context["snapshot"]["task"]["instruction"][:8192],
                    "baseline": self.context["snapshot"]["task"].get("baseRevision"),
                    "resultRevision": (result or {}).get("resultRevision"), "changes": (result or {}).get("changedFiles", sorted(self.file_changes)),
                    "commandsRun": [json.dumps(c, sort_keys=True) for c in [*self.commands_run, *self.check_receipts.values()]],
                    "checksNotRun": ([key for key in self.required_checks if key not in self.check_receipts]
                                     if self.required_checks else ([] if self.commands_run else ["No verification command was authorized and run"])),
                    "unresolved": [] if report.get("verdict") == "PASS" else [report.get("summary", "Review required")],
                    "sources": self.context["snapshot"]["references"]}
        self.client._post(self.base + "/delivery", {"lease": self.lease, "delivery": delivery})


class LazyControlledGit:
    def __init__(self, root, attempt): self.root, self.attempt, self.value = root, attempt, None
    def __getattr__(self, name):
        if self.value is None: self.value = ControlledGit(self.root, self.attempt)
        return getattr(self.value, name)


class ControlledGit:
    def __init__(self, root, attempt):
        self.root, self.attempt = root, attempt
        self.environment = {k: os.environ[k] for k in ("PATH", "SystemRoot", "WINDIR") if k in os.environ}
        self.environment.update({"GIT_CONFIG_NOSYSTEM": "1", "GIT_CONFIG_GLOBAL": os.devnull, "GIT_TERMINAL_PROMPT": "0",
                                 "GIT_PAGER": "cat", "GIT_NO_REPLACE_OBJECTS": "1", "GIT_AUTHOR_NAME": "AIW Worker", "GIT_AUTHOR_EMAIL": "worker@aiw.local",
                                 "GIT_COMMITTER_NAME": "AIW Worker", "GIT_COMMITTER_EMAIL": "worker@aiw.local"})
        self.configuration = self._configuration()

    def _configuration(self):
        config = self._run("config", "--no-includes", "--null", "--list")
        for entry in config.split("\0"):
            key = entry.split("\n", 1)[0].lower()
            if key.startswith(("include", "filter.", "credential.", "url.", "submodule.", "diff.", "merge.")) or key in {"core.worktree", "core.sshcommand", "core.gitproxy", "core.alternateRefsCommand".lower()}:
                raise RuntimeError("GUARD_GIT_EXECUTABLE_CONFIG_UNSUPPORTED")
        return hashlib.sha256(config.encode()).hexdigest()

    def _run(self, *argv, allowed=(0,)):
        self.attempt.check()
        process = subprocess.Popen(["git", "--no-optional-locks", "-c", "core.hooksPath=" + os.devnull, "-c", "core.fsmonitor=false", "-c", "core.untrackedCache=false",
                                 "-c", "commit.gpgSign=false", "-c", "tag.gpgSign=false", "-c", "core.pager=cat", "-c", "gc.auto=0", "-c", "maintenance.auto=false", "-c", "rerere.enabled=false", *argv],
                                cwd=self.root, env=self.environment, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, start_new_session=sys.platform != "win32")
        output, errors, overflow = bytearray(), bytearray(), threading.Event()
        def collect(stream, destination):
            try:
                while True:
                    data = stream.read(8192)
                    if not data: return
                    if len(destination) + len(data) > 1024 * 1024: overflow.set();return
                    destination.extend(data)
            finally: stream.close()
        threads = [threading.Thread(target=collect, args=(process.stdout, output), daemon=True), threading.Thread(target=collect, args=(process.stderr, errors), daemon=True)]
        for thread in threads: thread.start()
        deadline = time.monotonic() + min(30, self.attempt.remaining())
        try:
            while process.poll() is None:
                self.attempt.check()
                if overflow.is_set(): raise RuntimeError("GUARD_GIT_OUTPUT_LIMIT")
                if time.monotonic() >= deadline: raise RuntimeError("GUARD_GIT_TIMEOUT")
                time.sleep(0.02)
            if process.returncode not in allowed: raise RuntimeError("GUARD_GIT_OPERATION_FAILED")
        finally:
            if process.poll() is None:
                if sys.platform == "win32": process.kill()
                else:
                    try: os.killpg(process.pid, signal.SIGKILL)
                    except ProcessLookupError: pass
                try: process.wait(timeout=5)
                except subprocess.TimeoutExpired: self.attempt.resource_uncertain = True
            for thread in threads:
                thread.join(timeout=5)
                if thread.is_alive(): self.attempt.resource_uncertain = True
            if self.attempt.resource_uncertain: raise RuntimeError("GUARD_GIT_RESOURCE_UNKNOWN")
        if overflow.is_set(): raise RuntimeError("GUARD_GIT_OUTPUT_LIMIT")
        return output.decode("utf-8")

    def run(self, *argv):
        if self._configuration() != self.configuration: raise RuntimeError("GUARD_GIT_CONFIG_CHANGED")
        if argv[0] not in {"status", "diff", "add", "commit", "rev-parse", "diff-tree", "ls-files"}: raise RuntimeError("GUARD_GIT_VERB_DENIED")
        return self._run(*argv)

    def is_tracked(self, path):
        if self._configuration() != self.configuration: raise RuntimeError("GUARD_GIT_CONFIG_CHANGED")
        result = self._run("ls-files", "--error-unmatch", "--", path, allowed=(0, 1)).strip()
        return result == path

    def commit(self, paths, message):
        if not isinstance(paths, list) or not 1 <= len(paths) <= 256 or not isinstance(message, str) or not 1 <= len(message) <= 2000: raise RuntimeError("GUARD_GIT_COMMIT_INVALID")
        for path in paths:
            if path in {"", "."}: raise RuntimeError("GUARD_GIT_EXPLICIT_FILES_REQUIRED")
            file = safe_path(self.root, path, missing=True)
            if file.exists() and not file.is_file(): raise RuntimeError("GUARD_GIT_EXPLICIT_FILES_REQUIRED")
        self.run("add", "--", *paths)
        self.run("commit", "--no-verify", "--no-gpg-sign", "-m", message, "--", *paths)
        return {"resultRevision": self.run("rev-parse", "HEAD").strip()}

    def integrate(self, revision):
        if self._configuration() != self.configuration: raise RuntimeError("GUARD_GIT_CONFIG_CHANGED")
        if self._run("rev-parse", "--verify", revision + "^{commit}").strip() != revision: raise RuntimeError("GUARD_UPSTREAM_COMMIT_UNAVAILABLE")
        self._run("merge", "--no-ff", "--no-commit", "--no-edit", "--no-verify", revision, allowed=(0, 1))
        conflicts = list(filter(None, self.run("diff", "--name-only", "--diff-filter=U", "-z", "--").split("\0")))
        return {"sourceRevision": revision, "conflicts": conflicts, "committed": False}
