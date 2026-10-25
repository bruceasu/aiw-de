"""Secretary-owned, text-only adapters for the installed Codex and Agents SDK."""
from __future__ import annotations

import asyncio
import os
import tempfile
import threading
import time
from types import SimpleNamespace

from assistant_codex_runtime import CodexAppServerRuntime, HOST_ACTION_INSTRUCTIONS


MAX_INPUT_BYTES = 240_000
MAX_OUTPUT_BYTES = 32_000
ATTEMPT_TIMEOUT_SECONDS = 120


class _Evidence:
    """Retain only bounded status metadata, never private prompts or replies."""
    def __init__(self):
        self.terminal = None

    def record(self, event, **values):
        if event == "model-terminal":
            allowed = {"status", "runtime", "usageKnown", "usageSource", "inputTokens", "outputTokens", "cachedTokens", "unknown"}
            self.terminal = {key: value for key, value in values.items() if key in allowed}


class _Attempt:
    """Bound one request and deliver shutdown/timeout cancellation to its backend."""
    def __init__(self, force_event):
        self.deadline = time.monotonic() + ATTEMPT_TIMEOUT_SECONDS
        self.force_event = force_event or threading.Event()
        self.cancelled = threading.Event()
        self.closed = threading.Event()
        self.evidence = _Evidence()
        self.resource_uncertain = False
        self._lock = threading.Lock()
        self._callbacks = []
        self._notified = False
        self._watcher = threading.Thread(target=self._watch, daemon=True)
        self._watcher.start()

    def _watch(self):
        while not self.closed.wait(0.05):
            if self.force_event.is_set() or time.monotonic() >= self.deadline:
                self.cancel()
                return

    def cancel(self):
        already_cancelled = self.cancelled.is_set()
        self.cancelled.set()
        with self._lock:
            callbacks = tuple(self._callbacks) if not self._notified else ()
            self._notified = True
        if not already_cancelled or callbacks:
            for callback in callbacks:
                try:
                    callback()
                except Exception:
                    pass

    def add_cancel_callback(self, callback):
        with self._lock:
            self._callbacks.append(callback)
            cancelled = self.cancelled.is_set() or self.force_event.is_set()
        if cancelled:
            try:
                callback()
            except Exception:
                pass

    def remove_cancel_callback(self, callback):
        with self._lock:
            self._callbacks = [item for item in self._callbacks if item is not callback]

    def check(self):
        if self.force_event.is_set() or time.monotonic() >= self.deadline:
            self.cancel()
        if self.cancelled.is_set():
            raise RuntimeError("ATTEMPT_CANCELLED")

    def remaining(self):
        self.check()
        return max(0.001, self.deadline - time.monotonic())

    def close(self):
        self.closed.set()
        self._watcher.join(timeout=1)


def complete_text(settings, instruction, cancelled=None, on_evidence=None):
    if len(instruction.encode("utf-8")) > MAX_INPUT_BYTES:
        raise RuntimeError("MODEL_INPUT_BUDGET_EXCEEDED")
    attempt = _Attempt(cancelled)
    try:
        with tempfile.TemporaryDirectory(prefix="aiw-personal-model-") as workspace:
            request = SimpleNamespace(
                attempt=attempt,
                workspace=workspace,
                profile=SimpleNamespace(writable=False),
                instruction=instruction,
                model=settings.get("model") or None,
                reasoning_effort=settings.get("effort", ""),
                service_tier=None,
                sandbox="read-only",
                approval_mode="never",
                broker=None,
                model_only=True,
                executable=settings.get("executable") or None,
            )
            attempt.check()
            provider = settings.get("provider")
            if provider == "codex-app-server":
                result = CodexAppServerRuntime().run(None, request, lambda *_args: None)
            elif provider == "openai-agents":
                result = asyncio.run(_complete_openai(settings, request))
            else:
                raise RuntimeError("SECRETARY_MODEL_PROVIDER_UNSUPPORTED")
            attempt.check()
            if attempt.resource_uncertain:
                raise RuntimeError("PROCESS_TREE_UNCERTAIN")
            if not isinstance(result, str) or not result.strip() or len(result.encode("utf-8")) > MAX_OUTPUT_BYTES:
                raise RuntimeError("AGENT_OUTPUT_INVALID")
            return result.strip()
    finally:
        attempt.close()
        if on_evidence is not None and attempt.evidence.terminal:
            on_evidence(attempt.evidence.terminal)


async def _complete_openai(settings, request):
    os.environ.setdefault("OPENAI_AGENTS_DONT_LOG_MODEL_DATA", "1")
    os.environ.setdefault("OPENAI_AGENTS_DONT_LOG_TOOL_DATA", "1")
    try:
        from agents import Agent, ModelSettings, OpenAIProvider, RunConfig, Runner, set_tracing_disabled
        from openai import AsyncOpenAI
    except ImportError:
        raise RuntimeError("OPENAI_AGENTS_SDK_MISSING") from None

    request.attempt.check()
    api_key = settings.get("key") or os.environ.get("OPENAI_API_KEY", "").strip()
    if not api_key:
        raise RuntimeError("OPENAI_API_KEY_REQUIRED")
    set_tracing_disabled(True)
    client = AsyncOpenAI(api_key=api_key, base_url=settings.get("endpoint") or "https://api.openai.com/v1",
                         timeout=ATTEMPT_TIMEOUT_SECONDS, max_retries=0)
    provider = OpenAIProvider(openai_client=client, use_responses=True)
    model = provider.get_model(request.model)
    preferences = {"parallel_tool_calls": True, "truncation": "disabled", "store": False,
                   "preserve_raw_usage": True}
    if request.reasoning_effort and request.reasoning_effort != "none":
        preferences["reasoning"] = {"effort": request.reasoning_effort}
    agent = Agent(name="Personal Secretary", instructions=HOST_ACTION_INSTRUCTIONS + "\n" + request.instruction,
                  model=model, tools=[], model_settings=ModelSettings(**preferences))
    run_config = RunConfig(tracing_disabled=True, model_provider=provider)
    loop = asyncio.get_running_loop()
    stream_box = []
    execution_task = asyncio.current_task()

    def cancel_run():
        def cancel_on_loop():
            if stream_box:
                try:
                    stream_box[0].cancel(mode="immediate")
                except Exception:
                    request.attempt.cancel()
            elif execution_task is not None:
                execution_task.cancel()
        loop.call_soon_threadsafe(cancel_on_loop)

    request.attempt.add_cancel_callback(cancel_run)
    try:
        result = Runner.run_streamed(agent, "Execute the request and return its final response.",
                                     run_config=run_config, max_turns=1)
        stream_box.append(result)
        if request.attempt.cancelled.is_set() or request.attempt.force_event.is_set():
            result.cancel(mode="immediate")
        async for _event in result.stream_events():
            request.attempt.check()
        request.attempt.check()
        output = result.final_output
        if not isinstance(output, str) or not output.strip() or len(output.encode("utf-8")) > MAX_OUTPUT_BYTES:
            raise RuntimeError("AGENT_OUTPUT_INVALID")
        request.attempt.evidence.record("model-terminal", status="completed", runtime="openai-agents",
                                        usageKnown=False, unknown="token-usage")
        return output.strip()
    except asyncio.CancelledError:
        request.attempt.check()
        raise RuntimeError("ATTEMPT_CANCELLED") from None
    finally:
        request.attempt.remove_cancel_callback(cancel_run)
        await client.close()
