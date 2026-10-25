"""Attempt-scoped Codex App Server stdio adapter. No SDK, login or download.

Protocol reference: the maintained Go and TypeScript App Server adapters in
this repository. Windows uses a kill-on-close Job Object. A temporary cwd is
not an operating-system sandbox.
"""
from __future__ import annotations

from collections import deque
import json
import os
from pathlib import Path
import queue
import re
import shutil
import signal
import subprocess
import sys
import threading
import time


HOST_ACTION_INSTRUCTIONS = """You run inside a host application.
The host can provide tools through JSON action messages instead of native tool calls.
When requesting a host tool, return exactly one JSON action using its protocol.
The host executes the action and supplies its result in the next input.
When the task allows a plain-text final answer, you may return ordinary text.
Use only the actions declared by the host. Do not execute native Codex tools.
An empty native tool list does not mean that host actions are unavailable.
Use current tool definitions and results, not capability claims in chat history.
Never claim that an action succeeded without a successful host result.
"""
MAX_INPUT_BYTES = 1024 * 1024
MAX_OUTPUT_BYTES = 1024 * 1024
CLEANUP_TIMEOUT_SECONDS = 10


def preference_rejected(error):
    import re
    return bool(re.search(
        r"(?:model|reasoning[_ .]effort|effort).{0,100}(?:not supported|unsupported|not found|does not exist|not available)|(?:unsupported|invalid).{0,30}(?:model|reasoning[_ .]effort)",
        str(error), re.I))


class CodexRpcError(RuntimeError):
    """Keep the failed method/code, never the raw RPC body or private text."""
    def __init__(self, code, method, rpc_code=None):
        super().__init__(code)
        self.method = method
        self.rpc_code = rpc_code if type(rpc_code) is int else None


def response_text(messages, model_only=False):
    """Keep Worker final-answer rules; allow a completed host action as commentary."""
    nonempty = [(text, phase) for text, phase in messages.values() if text.strip()]
    final = [text for text, phase in nonempty if phase == "final_answer"]
    if final:
        return "\n".join(final).strip()
    unphased = [text for text, phase in nonempty if phase is None]
    if unphased:
        return "\n".join(unphased).strip()
    if model_only:
        actions = {}
        for text, phase in nonempty:
            if phase != "commentary":
                continue
            cleaned = re.sub(r"^```(?:json)?\s*|\s*```$", "", text.strip())
            try:
                value = json.loads(cleaned)
            except json.JSONDecodeError:
                continue
            if isinstance(value, dict) and isinstance(value.get("action"), str) and value["action"]:
                actions[json.dumps(value, ensure_ascii=False, sort_keys=True)] = cleaned
        if len(actions) > 1:
            raise RuntimeError("CODEX_HOST_ACTION_AMBIGUOUS")
        if actions:
            return next(iter(actions.values()))
    return ""


def preflight(path=None):
    if sys.platform == "win32":
        from assistant_windows_process import executable
        return executable(path or os.getenv("AIW_CODEX_PATH", "codex"))
    executable = shutil.which(path or os.getenv("AIW_CODEX_PATH", "codex"))
    if not executable:
        raise ValueError("CODEX_EXECUTABLE_UNAVAILABLE: configure the installed AIW_CODEX_PATH")
    return executable


class StdioSession:
    """One bounded RPC channel for one attempt; reader never logs wire bodies."""

    def __init__(self, attempt, cwd, *, argv=None, environment=None, request_handler=None):
        self.attempt = attempt
        self.limit = MAX_INPUT_BYTES
        self.messages = queue.Queue(maxsize=1024)
        self.events = deque()
        self.fatal = threading.Event()
        self.interrupted = threading.Event()
        self.writer = None
        self.sequence = 0
        self.wire_bytes = 0
        self.request_handler = request_handler
        command = argv or [preflight(), "app-server", "--listen", "stdio://", "--config", "sandbox_workspace_write.network_access=false",
                          "--config", "sandbox_read_only.network_access=false"]
        self.job = None
        if sys.platform == "win32":
            from assistant_windows_process import WindowsJob
            self.job = WindowsJob()
            try:
                self.process = self.job.start(command, cwd, environment)
            except BaseException:
                self.job.close()
                raise
        else:
            self.process = subprocess.Popen(command, cwd=cwd, stdin=subprocess.PIPE,
                stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, start_new_session=True, env=environment)
        self.reader = threading.Thread(target=self._read, name="codex-app-server-reader", daemon=True)
        self.reader.start()

    def interrupt(self):
        self.interrupted.set()

    def _read(self):
        try:
            while True:
                line = self.process.stdout.readline(self.limit + 1)
                if not line:
                    return
                self.wire_bytes += len(line)
                if len(line) > self.limit or self.wire_bytes > 16 * self.limit or not line.endswith(b"\n"):
                    raise RuntimeError("CODEX_RPC_BUDGET_EXCEEDED")
                message = json.loads(line)
                if not isinstance(message, dict):
                    raise RuntimeError("CODEX_RPC_MESSAGE_INVALID")
                self.messages.put_nowait(message)
        except Exception:
            self.fatal.set()
        finally:
            try: self.messages.put_nowait(None)
            except queue.Full: self.fatal.set()

    def _check(self, deadline, check_attempt=True):
        if check_attempt:
            self.attempt.check()
            if self.interrupted.is_set():
                raise RuntimeError("ATTEMPT_CANCELLED")
        if self.fatal.is_set():
            raise RuntimeError("CODEX_RPC_CHANNEL_FAILED")
        if time.monotonic() >= deadline:
            raise RuntimeError("CODEX_RPC_TIMEOUT")

    def send(self, message, deadline, check_attempt=True):
        encoded = json.dumps(message, ensure_ascii=False, separators=(",", ":")).encode("utf-8") + b"\n"
        if len(encoded) > self.limit:
            raise RuntimeError("CODEX_RPC_INPUT_TOO_LARGE")
        if self.writer is not None and self.writer.is_alive():
            raise RuntimeError("CODEX_RPC_WRITE_UNSETTLED")
        done = threading.Event()
        failed = []

        def write():
            try:
                self.process.stdin.write(encoded)
                self.process.stdin.flush()
            except Exception:
                failed.append(True)
            finally:
                done.set()

        self.writer = threading.Thread(target=write, name="codex-app-server-writer", daemon=True)
        self.writer.start()
        while not done.wait(0.05):
            self._check(deadline, check_attempt)
        if failed:
            raise RuntimeError("CODEX_RPC_WRITE_FAILED")
        self._check(deadline, check_attempt)

    def receive(self, deadline):
        while True:
            self._check(deadline)
            try:
                message = self.messages.get(timeout=0.05)
            except queue.Empty:
                continue
            if message is None:
                raise RuntimeError("CODEX_APP_SERVER_DISCONNECTED")
            if "method" in message and "id" in message:
                method = message["method"]
                if self.request_handler is not None:
                    response = self.request_handler(message)
                    self.send(response, deadline)
                    after = getattr(self, "tool_response_event", None)
                    event = after() if after else None
                    if event is not None:
                        return event
                    continue
                if method in {"item/commandExecution/requestApproval", "item/fileChange/requestApproval"}:
                    response = {"id": message["id"], "result": {"decision": "decline"}}
                else:
                    response = {"id": message["id"], "error": {"code": -32601, "message": "Host interaction is not authorized"}}
                self.send(response, deadline)
                continue
            return message

    def rpc(self, method, params, timeout=30):
        try:
            return self._rpc(method, params, timeout)
        except CodexRpcError:
            raise
        except RuntimeError as error:
            raise CodexRpcError(str(error), method) from None

    def _rpc(self, method, params, timeout=30):
        deadline = min(time.monotonic() + timeout, time.monotonic() + self.attempt.remaining())
        self.sequence += 1
        request_id = self.sequence
        self.send({"id": request_id, "method": method, "params": params}, deadline)
        while True:
            message = self.receive(deadline)
            if "method" in message:
                if len(self.events) >= 1024:
                    raise RuntimeError("CODEX_EVENT_QUEUE_FULL")
                self.events.append(message)
                continue
            if message.get("id") != request_id:
                raise RuntimeError("CODEX_RPC_RESPONSE_UNEXPECTED")
            if message.get("error") is not None:
                if preference_rejected(message["error"]):
                    raise RuntimeError("AI_PREFERENCES_REJECTED")
                error = message["error"]
                raise CodexRpcError("CODEX_RPC_REJECTED", method,
                    error.get("code") if isinstance(error, dict) else None)
            result = message.get("result")
            if not isinstance(result, dict):
                raise RuntimeError("CODEX_RPC_RESPONSE_INVALID")
            return result

    def next_event(self):
        if self.events:
            self.attempt.check()
            return self.events.popleft()
        message = self.receive(time.monotonic() + self.attempt.remaining())
        if not isinstance(message.get("method"), str) or "id" in message:
            raise RuntimeError("CODEX_EVENT_INVALID")
        return message

    def close(self):
        """Confirm local process-group and I/O termination before releasing work."""
        deadline = time.monotonic() + CLEANUP_TIMEOUT_SECONDS
        try:
            if self.job:
                self.job.terminate(deadline)
            else:
                if self.process.pid <= 1 or self.process.pid == os.getpgrp():
                    raise RuntimeError("PROCESS_GROUP_INVALID")
                try: os.killpg(self.process.pid, signal.SIGKILL)
                except ProcessLookupError: pass
            self.process.wait(timeout=max(0.001, deadline - time.monotonic()))
            self.reader.join(timeout=max(0.001, deadline - time.monotonic()))
            if self.writer:
                self.writer.join(timeout=max(0.001, deadline - time.monotonic()))
            if self.reader.is_alive() or self.writer and self.writer.is_alive():
                raise RuntimeError("CODEX_IO_UNSETTLED")
            while not self.job:
                try: os.killpg(self.process.pid, 0)
                except ProcessLookupError: break
                if time.monotonic() >= deadline:
                    raise RuntimeError("PROCESS_TREE_UNCERTAIN")
                time.sleep(0.02)
            for stream in (self.process.stdin, self.process.stdout):
                stream.close()
            self.attempt.evidence.record("resource-terminal", status="confirmed", runtime="codex-app-server")
        except Exception:
            self.attempt.resource_uncertain = True
            raise RuntimeError("PROCESS_TREE_UNCERTAIN") from None
        finally:
            if self.job:
                self.job.close()


class CodexAppServerRuntime:
    type = "codex-app-server"

    def run(self, client, request, on_session):
        if request.broker is not None:
            raise RuntimeError("SECRETARY_MODEL_ONLY_RUNTIME")
        attempt = request.attempt
        if request.approval_mode not in {"deny-all", "never"}:
            raise RuntimeError("CODEX_APPROVAL_MODE_UNSUPPORTED: use deny-all")
        if request.service_tier not in {None, "default"}:
            raise RuntimeError("CODEX_APP_SERVER_FAST_UNSUPPORTED")
        sandbox = "workspace-write" if request.profile.writable else "read-only"
        if request.profile.writable:
            if request.sandbox not in {"read-only", "workspace-write"}:
                raise RuntimeError("CODEX_SANDBOX_UNSUPPORTED")
            sandbox = request.sandbox
        def resource_request(message):
            params = message.get("params", {})
            if message["method"] in {"item/commandExecution/requestApproval", "item/fileChange/requestApproval"}:
                return {"id": message["id"], "result": {"decision": "decline"}}
            if message["method"] != "item/tool/call" or params.get("threadId") != thread_id or not turn_id or params.get("turnId") != turn_id or params.get("tool") != "read_skill_file" or params.get("namespace") not in {None, ""}:
                raise RuntimeError("CODEX_RESOURCE_TOOL_SCOPE_DENIED")
            attempt.check()
            args = params.get("arguments")
            raise RuntimeError("CODEX_RESOURCE_TOOL_SCOPE_DENIED")
        model_only = getattr(request, "model_only", False)
        environment = None
        argv = None
        if model_only:
            environment = {key: value for key, value in os.environ.items() if not key.startswith("AIW_PERSONAL_")}
            argv = [preflight(request.executable), "app-server", "--listen", "stdio://",
                    "--config", "sandbox_read_only.network_access=false"]
        session = StdioSession(attempt, request.workspace, argv=argv, environment=environment, request_handler=resource_request)
        cancel_callback = session.interrupt
        attempt.add_cancel_callback(cancel_callback)
        thread_id = turn_id = None
        terminal = False
        try:
            initialized = session.rpc("initialize", {"clientInfo": {"name": "aiw_process_python", "title": "AIW Python Worker", "version": "1.0.0"},
                                                      "capabilities": {"experimentalApi": True}})
            if not isinstance(initialized.get("userAgent"), str):
                raise RuntimeError("CODEX_HANDSHAKE_INVALID")
            session.send({"method": "initialized", "params": {}}, time.monotonic() + min(30, attempt.remaining()))
            account = session.rpc("account/read", {"refreshToken": False}).get("account")
            if not isinstance(account, dict) or account.get("type") not in {"apiKey", "chatgpt"}:
                raise RuntimeError("CODEX_ACCOUNT_UNAVAILABLE")
            params = {"cwd": request.workspace, "ephemeral": True, "approvalPolicy": "never", "sandbox": sandbox,
                      "config": {"sandbox_workspace_write.network_access": False, "sandbox_read_only.network_access": False,
                                 "allow_login_shell": False, "features.shell_snapshot": False}}
            if model_only:
                params.update(baseInstructions=HOST_ACTION_INSTRUCTIONS, dynamicTools=[])
                params["config"].update({"features.shell_tool": False, "features.multi_agent": False, "web_search": "disabled"})
            if request.model:
                params["model"] = request.model
            started = session.rpc("thread/start", params)
            thread = started.get("thread", {})
            thread_id = thread.get("id")
            expected_type = "readOnly" if sandbox == "read-only" else "workspaceWrite"
            policy = started.get("sandbox", {})
            if (not isinstance(thread_id, str) or not thread_id or thread.get("ephemeral") is not True or thread.get("path") is not None
                    or started.get("cwd") != request.workspace or started.get("approvalPolicy") != "never"
                    or policy.get("type") != expected_type or policy.get("networkAccess") is not False):
                raise RuntimeError("CODEX_THREAD_POLICY_UNCONFIRMED")
            metadata = session.rpc("thread/read", {"threadId": thread_id, "includeTurns": False}).get("thread", {})
            if metadata.get("id") != thread_id or metadata.get("ephemeral") is not True or metadata.get("path") is not None:
                raise RuntimeError("CODEX_EPHEMERAL_THREAD_UNCONFIRMED")
            on_session(thread_id, session)
            turn_policy = {"type": expected_type, "networkAccess": False}
            if sandbox == "workspace-write":
                turn_policy.update(writableRoots=[request.workspace], excludeSlashTmp=True, excludeTmpdirEnvVar=True)
            turn_params = {"threadId": thread_id, "input": [{"type": "text", "text": request.instruction}],
                "serviceTierForTurn": "default", "cwd": request.workspace,
                "approvalPolicy": "never", "sandboxPolicy": turn_policy}
            if request.reasoning_effort:
                turn_params["effort"] = request.reasoning_effort
            started_turn = session.rpc("turn/start", turn_params)
            turn_id = started_turn.get("turn", {}).get("id")
            if not isinstance(turn_id, str) or not turn_id:
                raise RuntimeError("CODEX_TURN_ID_MISSING")
            messages = {}
            work_started = False
            output_bytes = 0
            limit = MAX_OUTPUT_BYTES
            while True:
                event = session.next_event()
                p = event.get("params", {})
                if p.get("threadId") != thread_id or p.get("turnId") not in {None, turn_id}:
                    continue
                if event["method"] in {"item/started", "item/completed", "item/agentMessage/delta"}:
                    work_started = True
                if event["method"] == "item/agentMessage/delta":
                    output_bytes += len(str(p.get("delta", "")).encode("utf-8"))
                    if output_bytes > limit:
                        raise RuntimeError("CODEX_OUTPUT_TOO_LARGE")
                if event["method"] == "item/completed":
                    item = p.get("item", {})
                    if item.get("status") in {"failed", "declined", "cancelled"} or item.get("exitCode") not in {None, 0}:
                        raise RuntimeError("CODEX_TOOL_FAILED")
                    if item.get("type") == "agentMessage":
                        key, text = item.get("id"), item.get("text")
                        if not isinstance(key, str) or not isinstance(text, str) or len(messages) >= 512:
                            raise RuntimeError("CODEX_MESSAGE_INVALID")
                        if key in messages and messages[key] != (text, item.get("phase")):
                            raise RuntimeError("CODEX_MESSAGE_CONFLICT")
                        messages[key] = (text, item.get("phase"))
                        if sum(len(m[0].encode("utf-8")) for m in messages.values()) > limit:
                            raise RuntimeError("CODEX_OUTPUT_TOO_LARGE")
                if event["method"] == "turn/completed" and p.get("turn", {}).get("id") == turn_id:
                    terminal = True
                    turn = p["turn"]
                    if turn.get("status") != "completed":
                        if not work_started and preference_rejected(turn.get("error")):
                            raise RuntimeError("AI_PREFERENCES_REJECTED")
                        raise RuntimeError("CODEX_TURN_NOT_COMPLETED")
                    # Some transports include completed items in the terminal
                    # Turn instead of sending every item/completed notification.
                    for item in turn.get("items", []) or []:
                        if not isinstance(item, dict) or item.get("type") != "agentMessage":
                            continue
                        key, text = item.get("id"), item.get("text")
                        if not isinstance(key, str) or not isinstance(text, str) or len(messages) >= 512:
                            raise RuntimeError("CODEX_MESSAGE_INVALID")
                        value = (text, item.get("phase"))
                        if key in messages and messages[key] != value:
                            raise RuntimeError("CODEX_MESSAGE_CONFLICT")
                        messages[key] = value
                        if sum(len(message[0].encode("utf-8")) for message in messages.values()) > limit:
                            raise RuntimeError("CODEX_OUTPUT_TOO_LARGE")
                    output = response_text(messages, model_only)
                    if not output:
                        raise RuntimeError("CODEX_OUTPUT_EMPTY")
                    attempt.evidence.record("model-terminal", status="completed", runtime=self.type, usageKnown=False, unknown="token-usage-and-inner-tool-policy")
                    return output
        finally:
            try:
                if thread_id and turn_id and not terminal and session.process.poll() is None:
                    try:
                        session.sequence += 1
                        session.send({"id": session.sequence, "method": "turn/interrupt", "params": {"threadId": thread_id, "turnId": turn_id}},
                                     time.monotonic() + 0.5, check_attempt=False)
                    except Exception: pass
            finally:
                try: session.close()
                finally: attempt.remove_cancel_callback(cancel_callback)
