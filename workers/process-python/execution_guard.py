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
        self.created_paths = set()
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

    def begin(self, tool, resource, arguments, call_id=None):
        channel = getattr(self.attempt, "run_channel", None)
        if channel:
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

    def operate(self, tool, resource, arguments, action, call_id=None):
        from contextlib import nullcontext
        with nullcontext() if self.parallel_reads and tool in {"READ", "SEARCH"} else self.lock:
            if self._requires_approval(tool, resource, arguments):
                return self._request_approval(tool, resource, arguments, call_id)
            operation = self.begin(tool, resource, arguments, call_id)
            try:
                result = action()
                if tool == "WRITE" and arguments.get("action") == "write" and arguments.get("expectedDigest") == "ABSENT":
                    self.created_paths.add(resource)
                self.finish(operation, "SUCCEEDED", result)
                return result
            except Exception as error:
                # A write/process could already have happened. Keep the workspace and task occupied.
                known = (tool in {"READ", "SEARCH"} or str(error).startswith(("GUARD_PATH_", "GUARD_LINK_", "GUARD_FILE_VERSION_", "GUARD_WRITE_LIMIT", "GUARD_READ_RANGE_", "GUARD_SEARCH_", "GUARD_ROLE_"))) and not self.attempt.resource_uncertain
                try: self.finish(operation, "FAILED" if known else "UNKNOWN", {"code": "OPERATION_FAILED" if known else "OPERATION_OUTCOME_REQUIRES_RECONCILIATION"})
                except Exception:
                    self.attempt.resource_uncertain = True
                    raise
                if not known: self.attempt.resource_uncertain = True
                raise

    def _requires_approval(self, tool, resource, arguments):
        if tool == "COMMAND":
            if not isinstance(arguments, dict) or set(arguments) != {"commandId"} or arguments["commandId"] != resource:
                raise RuntimeError("GUARD_COMMAND_DENIED")
            original = self.context.get("originalPolicy", {}).get("commands", [])
            current = self.context.get("currentPolicy", {}).get("commands", [])
            command = next((item for item in original if item.get("id") == resource), None)
            if not isinstance(command, dict) or command not in current:
                raise RuntimeError("GUARD_COMMAND_DENIED")
            if self.context.get("originalPolicy", {}).get("riskStrategyVersion", 0) == 0:
                return False
            needs_approval = not _low_risk_command(command)
            self.attempt.evidence.record("guard-command-risk", commandId=resource,
                classification="LOW_RISK_CONFIGURED_COMMAND" if not needs_approval else "OWNER_APPROVAL_REQUIRED")
            return needs_approval
        if tool != "WRITE" or not isinstance(arguments, dict):
            return False
        name = arguments.get("action")
        if name == "delete":
            path = safe_path(self.root, resource)
            actual = hashlib.sha256(self._read(path)).hexdigest()
            if arguments.get("expectedDigest") != actual:
                raise RuntimeError("GUARD_FILE_VERSION_CONFLICT")
            created_here = resource in self.created_paths
            instruction = self.context["snapshot"]["task"].get("instruction", "").replace("\\", "/")
            explicit_scope = self._explicitly_requests_deletion(instruction, resource)
            tracked = False
            if not created_here and explicit_scope and self.git is not None:
                try:
                    tracked = self.git.is_tracked(resource)
                except RuntimeError as error:
                    if str(error) != "GUARD_GIT_OPERATION_FAILED":
                        raise
            risk_version = self.context.get("originalPolicy", {}).get("riskStrategyVersion", 0)
            autonomous = created_here or (risk_version == 1 and explicit_scope and tracked)
            self.attempt.evidence.record("guard-delete-risk", path=resource,
                classification="AUTONOMOUS_SINGLE_FILE" if autonomous else "OWNER_APPROVAL_REQUIRED")
            return not autonomous
        if name != "write":
            return False
        path = safe_path(self.root, resource, missing=True)
        return path.exists()

    @staticmethod
    def _explicitly_requests_deletion(instruction, resource):
        target = re.escape(resource)
        mentions = list(re.finditer(r"(?<![A-Za-z0-9._/-])" + target + r"(?![A-Za-z0-9._/-])", instruction, re.IGNORECASE))
        if not mentions:
            return False
        verbs = r"(?:delete|remove|erase|unlink|discard|删除|移除|删掉|删去|清理|去掉|去除)"
        boundary = re.compile(r"[!?;；。！？\n]+|\.(?=\s|$)|,\s+")
        negative = re.compile(r"(?:\b(?:do\s+not|don't|never|must\s+not|should\s+not|avoid|cannot|can't|not|need\s+not|no\s+need\s+to)\b[^.!?;。！？；\n]{0,80}|(?:严禁|绝对不要|千万不要|不要|不得|禁止|勿|切勿|别|不|无须|无需|不必|没必要|不能|不可|无法|不会|不应|不该|尚未|未|没有|没|并非|并未)[^。！？；\n]{0,32})" + verbs, re.IGNORECASE)
        affirmative = re.compile(verbs + r"[^.!?;。！？；\n]{0,160}$", re.IGNORECASE)
        if negative.search(instruction):
            return False
        for mention in mentions:
            previous = list(boundary.finditer(instruction, 0, mention.start()))
            start = previous[-1].end() if previous else 0
            following = boundary.search(instruction, mention.end())
            end = following.start() if following else len(instruction)
            clause = instruction[start:end]
            if not affirmative.search(clause[:mention.start() - start]):
                return False
        return True

    def _request_approval(self, tool, resource, arguments, call_id):
        from formal_questions import YieldRequested
        if self.pending_approval or self.pending_question or self.checkpoint is None or self.workspace is None:
            raise RuntimeError("TOOL_APPROVAL_CHECKPOINT_UNAVAILABLE")
        name = arguments.get("action") if tool == "WRITE" else "command"
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
            if name not in {"write", "delete"}:
                raise RuntimeError("TOOL_APPROVAL_ACTION_UNSUPPORTED")
            path = safe_path(self.root, resource, missing=name == "write")
            expected = arguments.get("expectedDigest")
            version = hashlib.sha256(self._read(path)).hexdigest() if path.exists() else "ABSENT"
            if expected != version or version == "ABSENT":
                raise RuntimeError("GUARD_FILE_VERSION_CONFLICT")
            if name == "delete" and not re.fullmatch(r"[0-9a-f]{64}", version):
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
        action_name = "EXECUTE_COMMAND" if name == "command" else "REPLACE_FILE" if name == "write" else "DELETE_FILE"
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
            self.finish(fixed["operationId"], "SUCCEEDED", result)
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

    def invoke(self, name, args, call_id=None):
        from tool_coordination import READ_ONLY
        with self.tool_coordinator.access(self.parallel_reads and name in READ_ONLY):
            return self._invoke_checked(name, args, call_id)

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
                return reader(**args)
            finally:
                if channel:
                    channel.leave()
        return self._invoke(name, args, call_id)

    def _invoke(self, name, args, call_id=None):
        if self.root is None: raise RuntimeError("GUARD_WORKSPACE_NOT_ATTACHED")
        if not isinstance(args, dict): raise RuntimeError("GUARD_TOOL_ARGUMENTS_INVALID")
        schemas = {
            "read": ({"path", "offset", "limit"}, "READ"), "ls": ({"path"}, "READ"),
            "grep": ({"path", "text"}, "SEARCH"), "write": ({"path", "content", "expectedDigest"}, "WRITE"),
            "mkdir": ({"path"}, "WRITE"), "delete": ({"path", "expectedDigest"}, "WRITE"),
            "git_status": (set(), "GIT"), "git_diff": (set(), "GIT"),
            "git_commit": ({"message", "paths"}, "GIT"), "run_command": ({"commandId"}, "COMMAND"),
            "git_integrate": ({"sourceTaskId"}, "GIT"),
            "ask_question": ({"question", "context", "options", "answerRequirements"}, "QUESTION"),
        }
        if name not in schemas or set(args) != schemas[name][0]: raise RuntimeError("GUARD_TOOL_ARGUMENTS_INVALID")
        tool = schemas[name][1]
        if (tool in {"WRITE", "COMMAND"} or name in {"git_commit", "git_integrate"}) and not self.writable: raise RuntimeError("GUARD_ROLE_IS_READ_ONLY")
        resource = args.get("path", args.get("commandId", "."))
        return self.operate(tool, resource, args, lambda: self._execute(name, args), call_id)

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
        if name == "run_command":
            from guarded_command import run_command
            commands = self.context["originalPolicy"]["commands"]
            command = next((c for c in commands if c["id"] == args["commandId"]), None)
            if command is None or command not in self.context["currentPolicy"]["commands"]: raise RuntimeError("GUARD_COMMAND_DENIED")
            result = run_command(self.root, command, self.attempt)
            self.commands_run.append({"commandId": command["id"], "exitCode": result["exitCode"], "outputDigest": digest(result)})
            return result
        path = safe_path(self.root, args["path"], missing=name in {"write", "mkdir"})
        if name == "mkdir":
            if path.exists():
                if not path.is_dir(): raise RuntimeError("GUARD_NOT_DIRECTORY")
            else: path.mkdir(mode=0o755)
            return {"createdDirectory": args["path"]}
        if name == "delete":
            if hashlib.sha256(self._read(path)).hexdigest() != args["expectedDigest"]: raise RuntimeError("GUARD_FILE_VERSION_CONFLICT")
            path.unlink()
            return {"deletedFile": args["path"]}
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
        content, expected = args["content"], args["expectedDigest"]
        if not isinstance(content, str) or len(content.encode()) > 2 * 1024 * 1024: raise RuntimeError("GUARD_WRITE_LIMIT")
        actual = hashlib.sha256(self._read(path)).hexdigest() if path.exists() else "ABSENT"
        if expected != actual: raise RuntimeError("GUARD_FILE_VERSION_CONFLICT")
        temporary = path.with_name(".aiw-write-" + uuid.uuid4().hex)
        descriptor = os.open(temporary, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        try:
            with os.fdopen(descriptor, "wb") as stream: stream.write(content.encode());stream.flush();os.fsync(stream.fileno())
            safe_path(self.root, args["path"], missing=True)
            # Staging a large replacement can take time. Recheck the exact
            # baseline before publishing it, including ABSENT for creation.
            current = hashlib.sha256(self._read(path)).hexdigest() if path.exists() else "ABSENT"
            if current != expected: raise RuntimeError("GUARD_FILE_VERSION_CONFLICT")
            os.replace(temporary, path)
        finally:
            if temporary.exists(): temporary.unlink()
        return {"resultDigest": hashlib.sha256(content.encode()).hexdigest()}

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
        delivery = {"scope": self.context["snapshot"]["task"]["instruction"],
                    "baseline": self.context["snapshot"]["task"].get("baseRevision"),
                    "resultRevision": (result or {}).get("resultRevision"), "changes": (result or {}).get("changedFiles", []),
                    "commandsRun": [json.dumps(c, sort_keys=True) for c in self.commands_run],
                    "checksNotRun": [] if self.commands_run else ["No verification command was authorized and run"],
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
        process = subprocess.Popen(["git", "-c", "core.hooksPath=" + os.devnull, "-c", "core.fsmonitor=false", "-c", "core.untrackedCache=false",
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
