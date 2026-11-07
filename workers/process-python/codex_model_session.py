"""One ephemeral, tool-free Codex conversation owned by a single host attempt."""
from __future__ import annotations

from dataclasses import replace
import os
from pathlib import Path
import tempfile
import time

from agent_runtime import HOST_ACTION_INSTRUCTIONS
from app_server_runtime import (MODEL_ONLY_DISABLED, StdioSession, check_tool_result,
                               preflight, response_text, verify_model_only_tools)
from run_contract import observe_context
from worker_runtime import setting


class CodexModelSession:
    def __init__(self, request, on_session):
        self.original = request
        self.on_session = on_session
        self.session = None
        self.cwd = None
        self.thread_id = None
        self.closed = False
        self.turns = set()
        self.turn_active = False

    def __enter__(self):
        request = self.original
        if request.approval_mode not in {"deny-all", "never"} or request.service_tier not in {None, "default"}:
            raise RuntimeError("CODEX_MODEL_SESSION_POLICY_UNSUPPORTED")
        request.attempt.check()
        self.cwd = tempfile.mkdtemp(prefix="aiw-host-conversation-")
        self.request = replace(request, workspace=self.cwd, broker=None, model_only=True,
                               sandbox="read-only", network_access=False, candidate_validator=None)
        try:
            environment = {key: value for key, value in os.environ.items() if not key.startswith("AIW_PERSONAL_")}
            self.session = StdioSession(request.attempt, self.cwd,
                argv=[preflight(request.executable), "app-server", "--listen", "stdio://",
                      "--config", "sandbox_read_only.network_access=false"],
                environment=environment, request_handler=self._deny_tool,
                **({"credential_provider": request.credential_provider} if request.credential_provider is not None else {}))
            request.attempt.add_cancel_callback(self.session.interrupt)
            initialized = self.session.rpc("initialize", {"clientInfo": {"name": "aiw_host_conversation", "version": "1"},
                                                          "capabilities": {"experimentalApi": True}})
            if not isinstance(initialized.get("userAgent"), str):
                raise RuntimeError("CODEX_HANDSHAKE_INVALID")
            self.session.send({"method": "initialized", "params": {}}, time.monotonic() + min(30, request.attempt.remaining()))
            if request.credential_provider is not None:
                request.credential_provider.codex_login(self.session)
            account = self.session.rpc("account/read", {"refreshToken": False}).get("account")
            if not isinstance(account, dict) or account.get("type") not in {"apiKey", "chatgpt"}:
                raise RuntimeError("CODEX_ACCOUNT_UNAVAILABLE")
            params = {"cwd": self.cwd, "ephemeral": True, "approvalPolicy": "never", "sandbox": "read-only",
                      "baseInstructions": HOST_ACTION_INSTRUCTIONS, "dynamicTools": [], "runtimeWorkspaceRoots": [], "environments": [],
                      "config": {"sandbox_workspace_write.network_access": False, "sandbox_read_only.network_access": False,
                                 "allow_login_shell": False, "features.shell_snapshot": False, "web_search": "disabled"}}
            params["config"].update({"features." + name: False for name in MODEL_ONLY_DISABLED})
            if request.model:
                params["model"] = request.model
            started = self.session.rpc("thread/start", params)
            thread = started.get("thread", {})
            self.thread_id = thread.get("id")
            policy = started.get("sandbox", {})
            if (not isinstance(self.thread_id, str) or not self.thread_id or thread.get("ephemeral") is not True
                    or thread.get("path") is not None or started.get("cwd") != self.cwd
                    or started.get("approvalPolicy") != "never" or policy.get("type") != "readOnly"
                    or policy.get("networkAccess") is not False):
                raise RuntimeError("CODEX_THREAD_POLICY_UNCONFIRMED")
            verify_model_only_tools(self.session, self.thread_id)
            metadata = self.session.rpc("thread/read", {"threadId": self.thread_id, "includeTurns": False}).get("thread", {})
            if metadata.get("id") != self.thread_id or metadata.get("ephemeral") is not True or metadata.get("path") is not None:
                raise RuntimeError("CODEX_EPHEMERAL_THREAD_UNCONFIRMED")
            self.on_session(self.thread_id, self.session)
            return self
        except BaseException:
            self.close()
            raise

    @staticmethod
    def _deny_tool(_message):
        raise RuntimeError("CODEX_MODEL_ONLY_NATIVE_TOOL_DENIED")

    def run_turn(self, instruction, parts):
        if self.closed or self.turn_active or self.session is None:
            raise RuntimeError("CODEX_MODEL_SESSION_NOT_IDLE")
        request = replace(self.request, instruction=instruction, context_parts=parts, context_purpose="host-action")
        request.attempt.check()
        if self.session.process.poll() is not None:
            raise RuntimeError("CODEX_MODEL_SESSION_LOST")
        if len(self.turns) >= setting("AIW_AGENT_MAX_TURNS", 40):
            raise RuntimeError("HOST_ACTION_TURN_LIMIT")
        observe_context(request, "codex-app-server:host-session", {
            "instructions": HOST_ACTION_INSTRUCTIONS, "messages": [{"type": "text", "text": instruction}],
            "tools": [], "historyMode": "SERVER_CONTINUATION" if self.turns else "INITIAL",
        }, request_scope="BACKEND_TURN")
        params = {"threadId": self.thread_id, "input": [{"type": "text", "text": instruction}], "cwd": self.cwd,
                  "serviceTierForTurn": "default", "approvalPolicy": "never",
                  "sandboxPolicy": {"type": "readOnly", "networkAccess": False}}
        if request.reasoning_effort:
            params["effort"] = request.reasoning_effort
        self.turn_active = True
        turn_id = self.session.rpc("turn/start", params).get("turn", {}).get("id")
        if not isinstance(turn_id, str) or not turn_id or turn_id in self.turns:
            raise RuntimeError("CODEX_TURN_ID_INVALID_OR_REPLAYED")
        self.turns.add(turn_id)
        messages, output_bytes = {}, 0
        limit = setting("AIW_AGENT_MAX_OUTPUT_CHARS", 1024 * 1024)
        while True:
            event = self.session.next_event()
            values = event.get("params", {})
            if values.get("threadId") != self.thread_id or values.get("turnId") not in {None, turn_id}:
                continue
            method = event["method"]
            if method == "item/agentMessage/delta":
                output_bytes += len(str(values.get("delta", "")).encode("utf-8"))
                if output_bytes > limit:
                    raise RuntimeError("CODEX_OUTPUT_TOO_LARGE")
            if method == "item/completed":
                self._item(values.get("item", {}), messages, limit)
            if method == "turn/completed" and values.get("turn", {}).get("id") == turn_id:
                turn = values["turn"]
                if turn.get("status") != "completed":
                    raise RuntimeError("CODEX_TURN_NOT_COMPLETED")
                for item in turn.get("items", []) or []:
                    self._item(item, messages, limit)
                output = response_text(messages, True)
                if not output:
                    raise RuntimeError("CODEX_OUTPUT_EMPTY")
                request.attempt.evidence.record("model-terminal", status="completed", runtime="codex-app-server",
                                               usageKnown=False, unknown="backend-history-and-inner-request-usage")
                self.turn_active = False
                return output

    def _item(self, item, messages, limit):
        if not isinstance(item, dict) or item.get("type") not in {"agentMessage", "reasoning", "userMessage", "plan"}:
            self.request.attempt.resource_uncertain = True
            raise RuntimeError("CODEX_MODEL_ONLY_NATIVE_ITEM_DENIED")
        check_tool_result(item, self.request.attempt)
        if item.get("type") == "agentMessage":
            key, text = item.get("id"), item.get("text")
            if not isinstance(key, str) or not isinstance(text, str) or len(messages) >= 512:
                raise RuntimeError("CODEX_MESSAGE_INVALID")
            value = (text, item.get("phase"))
            if key in messages and messages[key] != value:
                raise RuntimeError("CODEX_MESSAGE_CONFLICT")
            messages[key] = value
            if sum(len(message[0].encode("utf-8")) for message in messages.values()) > limit:
                raise RuntimeError("CODEX_OUTPUT_TOO_LARGE")

    def close(self):
        if self.closed:
            return
        self.closed = True
        try:
            if self.session is not None:
                self.session.close()
        finally:
            if self.session is not None:
                self.original.attempt.remove_cancel_callback(self.session.interrupt)
            if self.cwd and not self.original.attempt.resource_uncertain:
                try:
                    Path(self.cwd).rmdir()  # Remove only an empty directory, never model-created contents.
                except OSError:
                    self.original.attempt.evidence.record("resource-retained", runtime="codex-app-server", status="retained",
                                                          reason="model-session-directory-not-empty")

    def __exit__(self, _type, _value, _traceback):
        self.close()
