"""Local text callers of the same AgentRuntime implementations as Workers.

Callers retain private context and tool authorization. No platform client,
registration, lease, shared credentials or task workspace is required.
"""
from __future__ import annotations

import os
import tempfile
import threading
import uuid

from agent_runtime import AgentRunRequest
from process_profiles import ProcessProfile
from worker_runtime import Attempt


class ModelEvidence:
    """Bounded metadata only. Never retain private instructions or responses."""
    def __init__(self):
        self.identity = {"attemptId": str(uuid.uuid4())}
        self.terminal = None

    def record(self, event, **values):
        if event == "model-terminal":
            self.terminal = {key: value for key, value in values.items() if key in {
                "status", "runtime", "usageKnown", "usageSource", "inputTokens", "outputTokens", "cachedTokens"}}


def complete_text(settings, instruction, cancelled, on_evidence=None):
    if len(instruction.encode("utf-8")) > 240000:
        raise RuntimeError("MODEL_INPUT_BUDGET_EXCEEDED")
    cancelled = cancelled if cancelled is not None else threading.Event()
    evidence = ModelEvidence()
    attempt = Attempt(None, None, cancelled, evidence=evidence, timeout_ms=120000)
    profile = ProcessProfile("personal-secretary", "", (), (), (), "", "", False, "text")
    try:
        with tempfile.TemporaryDirectory(prefix="aiw-model-") as workspace:
            request = AgentRunRequest(attempt, workspace, profile, instruction, settings.get("model") or None,
                settings.get("effort", ""), False, None, "read-only", "never", model_only=True,
                executable=settings.get("executable") or None)
            if settings["provider"] == "codex-app-server":
                from app_server_runtime import CodexAppServerRuntime
                runtime = CodexAppServerRuntime()
            elif settings["provider"] == "openai-agents":
                from openai_api_runtime import ApiRuntimeConfig, OpenAIApiRuntime
                # Explicit settings avoid changing process-global environment or
                # leaking a secretary's key into another Agent's configuration.
                key = settings.get("key") or os.environ.get("OPENAI_API_KEY", "")
                if not key:
                    raise RuntimeError("OPENAI_API_KEY_REQUIRED")
                runtime = OpenAIApiRuntime(ApiRuntimeConfig(key, settings.get("endpoint") or None,
                    settings["model"], settings.get("effort", ""), 120000, 1, 256000, 32000, 60000))
            else:
                raise RuntimeError("AGENT_RUNTIME_UNSUPPORTED")
            result = runtime.run(None, request, lambda *_args: None)
            attempt.check()
            if attempt.resource_uncertain:
                raise RuntimeError("PROCESS_TREE_UNCERTAIN")
            if not isinstance(result, str) or not result.strip() or len(result) > 32000:
                raise RuntimeError("AGENT_OUTPUT_INVALID")
            return result.strip()
    finally:
        attempt.close()
        if on_evidence is not None and evidence.terminal:
            on_evidence(evidence.terminal)

