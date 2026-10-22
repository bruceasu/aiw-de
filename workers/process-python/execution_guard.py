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
        self.repository = (task.get("workspace") or {}).get("repositoryRef")
        frozen = self.context["snapshot"]["task"]
        if frozen["instruction"] != task.get("instruction") or frozen["instructionRevision"] != task.get("instructionRevision") or frozen.get("repositoryRef") != self.repository:
            raise RuntimeError("FIXED_TASK_CONTEXT_CHANGED")
        self.lock = threading.RLock()
        self.root = self.git = None
        self.writable = False
        self.usage = {"inputTokens": 0, "cachedTokens": 0, "outputTokens": 0}
        self.commands_run = []
        self._active = set()
        self.allowed_tools = None

    def attach(self, workspace):
        root = Path(workspace).absolute()
        for parent in [*reversed(root.parents), root]:
            info = parent.lstat()
            if stat.S_ISLNK(info.st_mode) or getattr(info, "st_file_attributes", 0) & 0x400: raise RuntimeError("GUARD_WORKSPACE_UNSAFE")
        self.root = root.resolve(strict=True)
        self.git = ControlledGit(self.root, self.attempt)

    def begin(self, tool, resource, arguments, call_id=None):
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
        self._active.discard(operation)

    def operate(self, tool, resource, arguments, action, call_id=None):
        with self.lock:
            operation = self.begin(tool, resource, arguments, call_id)
            try:
                result = action()
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

    def invoke(self, name, args, call_id=None):
        if self.root is None: raise RuntimeError("GUARD_WORKSPACE_NOT_ATTACHED")
        if not isinstance(args, dict): raise RuntimeError("GUARD_TOOL_ARGUMENTS_INVALID")
        schemas = {
            "read": ({"path", "offset", "limit"}, "READ"), "ls": ({"path"}, "READ"),
            "grep": ({"path", "text"}, "SEARCH"), "write": ({"path", "content", "expectedDigest"}, "WRITE"),
            "mkdir": ({"path"}, "WRITE"), "delete": ({"path", "expectedDigest"}, "WRITE"),
            "git_status": (set(), "GIT"), "git_diff": (set(), "GIT"),
            "git_commit": ({"message", "paths"}, "GIT"), "run_command": ({"commandId"}, "COMMAND"),
            "git_integrate": ({"sourceTaskId"}, "GIT"),
        }
        if name not in schemas or set(args) != schemas[name][0]: raise RuntimeError("GUARD_TOOL_ARGUMENTS_INVALID")
        tool = schemas[name][1]
        if (tool == "WRITE" or name in {"git_commit", "git_integrate"}) and not self.writable: raise RuntimeError("GUARD_ROLE_IS_READ_ONLY")
        resource = args.get("path", args.get("commandId", "."))
        return self.operate(tool, resource, args, lambda: self._execute(name, args), call_id)

    def _execute(self, name, args):
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

    def model_started(self, arguments): return self.begin("MODEL", ".", arguments)

    def model_finished(self, operation, output, usage):
        if usage is not None:
            for key in self.usage: self.usage[key] += usage[key]
        self.finish(operation, "SUCCEEDED", {"outputDigest": digest(output)}, dict(self.usage) if usage is not None else None)

    def record_delivery(self, result, report):
        delivery = {"scope": self.context["snapshot"]["task"]["instruction"],
                    "baseline": (self.task.get("workspace") or {}).get("baseRevision"),
                    "resultRevision": (result or {}).get("resultRevision"), "changes": (result or {}).get("changedFiles", []),
                    "commandsRun": [json.dumps(c, sort_keys=True) for c in self.commands_run],
                    "checksNotRun": [] if self.commands_run else ["No verification command was authorized and run"],
                    "unresolved": [] if report.get("verdict") == "PASS" else [report.get("summary", "Review required")],
                    "sources": self.context["snapshot"]["references"]}
        self.client._post(self.base + "/delivery", {"lease": self.lease, "delivery": delivery})


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
        if argv[0] not in {"status", "diff", "add", "commit", "rev-parse", "diff-tree"}: raise RuntimeError("GUARD_GIT_VERB_DENIED")
        return self._run(*argv)

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
