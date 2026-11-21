"""Local SDK seam double with the real private Evidence writer; no network."""
import importlib.util
import asyncio
from contextlib import contextmanager
from dataclasses import dataclass
import json
import os
from pathlib import Path
import sys
import tempfile
from types import ModuleType, SimpleNamespace
import unittest
from unittest.mock import AsyncMock, Mock, patch

from worker_runtime import Evidence


@contextmanager
def load_runtime():
    agents = ModuleType("agents")
    agents.__path__ = []
    agents.RunContextWrapper = type("RunContextWrapper", (), {})
    agents.function_tool = lambda function: function
    models = ModuleType("agents.models")
    models.__path__ = []
    interface = ModuleType("agents.models.interface")
    interface.Model = type("Model", (), {})
    name = "guarded_model_test_double"
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name("openai_api_runtime.py"))
    module = importlib.util.module_from_spec(spec)
    with patch.dict(sys.modules, {
            "agents": agents, "agents.models": models, "agents.models.interface": interface, name: module}):
        spec.loader.exec_module(module)
        yield module


class EvidenceUsageTest(unittest.TestCase):
    def test_completed_model_turn_records_known_and_unknown_usage(self):
        with load_runtime() as module, tempfile.TemporaryDirectory() as root, \
                patch.dict(os.environ, {"AIW_WORKER_EVIDENCE_ROOT": str(Path(root) / "evidence")}, clear=False):
            config = SimpleNamespace(fallback_workspace=str(Path(root) / "workspace"), repository_root=None,
                                     worktree_root=None, process_role="coder", runtime="openai-agents")
            evidence = Evidence(config, {"taskId": "task", "attemptId": "attempt"})
            guarded = module._GuardedModel.__new__(module._GuardedModel)
            guarded._request = SimpleNamespace(attempt=SimpleNamespace(evidence=evidence), model="test", reasoning_effort=None)
            guarded._config = SimpleNamespace(max_input_bytes=1024, provider="openai", protocol="responses")
            guarded.input_limit = 200_000
            guarded._model = SimpleNamespace(last_usage=None)
            guarded._tool_schemas = {}
            guarded.history = SimpleNamespace(response=lambda _items: None)
            with patch("run_contract.observe_context"):
                guarded._request_check("Complete the task", "Example input", [])
            for usage in (None, {"input_tokens": 12, "output_tokens": 5, "input_tokens_details": {"cached_tokens": 3}}):
                guarded._validate({"status": "completed", "output": [], "usage": usage})
            rows = [json.loads(line) for line in (evidence.directory / "events.jsonl").read_text(encoding="utf-8").splitlines()]
            model_request = next(row for row in rows if row["event"] == "model-request")
            self.assertGreater(model_request["estimatedInputTokens"], 0)
            self.assertEqual(model_request["inputTokenLimit"], 200_000)
            self.assertFalse(model_request["usageKnown"])
            terminal = [row for row in rows if row["event"] == "model-terminal"]
            self.assertEqual([(row["usageKnown"], row["usageSource"]) for row in terminal],
                             [(False, "unknown"), (True, "provider-response")])
            self.assertEqual(terminal[1]["cachedTokens"], 3)
            self.assertFalse(evidence.failed)
            with self.assertRaises(RuntimeError):
                evidence.record("model-terminal", usageSource="unbounded-private-content")
            self.assertTrue(evidence.failed)

    def test_last_request_is_reserved_for_report_in_both_sdk_paths(self):
        @dataclass
        class Settings:
            tool_choice: str | None = "required"
            parallel_tool_calls: bool | None = True

        with load_runtime() as module:
            for streamed in (False, True):
                with self.subTest(streamed=streamed):
                    attempt = SimpleNamespace(resource_uncertain=False, check=Mock(), evidence=Mock())
                    guarded = module._GuardedModel.__new__(module._GuardedModel)
                    guarded._request = SimpleNamespace(attempt=attempt, broker=None)
                    guarded._config = SimpleNamespace(max_turns=4, max_input_bytes=1024)
                    guarded._context = SimpleNamespace(work_started=False)
                    guarded.calls = 3
                    guarded.inputs = None
                    guarded.history = SimpleNamespace(response=Mock())
                    guarded._prepare = AsyncMock(return_value=[])
                    response = {"status": "completed", "output": [{"type": "message"}], "usage": None}
                    call = Mock(return_value=response)

                    async def reply(*args, **kwargs):
                        return call(*args, **kwargs)

                    async def stream(*args, **kwargs):
                        yield {"type": "response.completed", "response": call(*args, **kwargs)}

                    guarded._model = SimpleNamespace(get_response=reply, stream_response=stream)

                    async def finish():
                        args = ("Fixed task", [], Settings(), [object()], None, [], None)
                        options = {"previous_response_id": None, "conversation_id": None, "prompt": None}
                        if streamed:
                            return [event async for event in guarded.stream_response(*args, **options)]
                        return await guarded.get_response(*args, **options)

                    asyncio.run(finish())
                    self.assertEqual(4, guarded.calls)
                    instructions, _history, settings, declared_tools = call.call_args.args[:4]
                    self.assertIn("Fixed task", instructions)
                    self.assertIn("final model call", instructions)
                    self.assertEqual([], declared_tools)
                    self.assertIsNone(settings.tool_choice)
                    self.assertIsNone(settings.parallel_tool_calls)
                    attempt.evidence.record.assert_any_call("runtime-event", status="final-report-only")

                    guarded.calls = 3
                    response["output"] = [{"type": "function_call"}]
                    with self.assertRaisesRegex(RuntimeError, "MODEL_FINAL_REPORT_TOOL_CALL"):
                        asyncio.run(finish())

    def test_provider_failure_settles_only_an_explicit_rejection_without_pending_input(self):
        with load_runtime() as module:
            for streamed, pending, status, partial, expected in (
                (False, False, 401, False, "FAILED"),
                (True, False, 429, False, "FAILED"),
                (False, False, 503, False, "UNKNOWN"),
                (False, True, 401, False, "UNKNOWN"),
                (True, False, 429, True, "UNKNOWN"),
            ):
                with self.subTest(streamed=streamed, pending=pending, status=status, partial=partial):
                    error = RuntimeError("Provider payload is private")
                    error.status_code = status
                    attempt = SimpleNamespace(resource_uncertain=False, check=Mock(), evidence=Mock())
                    broker = SimpleNamespace(finish=Mock())
                    guarded = module._GuardedModel.__new__(module._GuardedModel)
                    guarded._request = SimpleNamespace(attempt=attempt, broker=broker)
                    guarded._config = SimpleNamespace(max_input_bytes=1024)
                    guarded.inputs = SimpleNamespace(pending=pending)
                    guarded._prepare = AsyncMock(return_value=[])
                    guarded._count = Mock()
                    guarded._begin = Mock(return_value="model-operation")
                    guarded._model = SimpleNamespace(get_response=AsyncMock(side_effect=error))

                    async def stream(*args, **kwargs):
                        if partial:
                            yield {"type": "response.in_progress"}
                        raise error

                    guarded._model.stream_response = stream

                    async def run_request():
                        args = (None, [], None, [], None, [], None)
                        options = {"previous_response_id": None, "conversation_id": None, "prompt": None}
                        if streamed:
                            return [event async for event in guarded.stream_response(*args, **options)]
                        return await guarded.get_response(*args, **options)

                    with self.assertRaises(RuntimeError) as raised:
                        asyncio.run(run_request())
                    self.assertIs(error, raised.exception)
                    code = f"MODEL_API_HTTP_{status}" if expected == "FAILED" else "MODEL_OUTCOME_REQUIRES_RECONCILIATION"
                    broker.finish.assert_called_once_with("model-operation", expected, {"code": code})
                    self.assertEqual(expected == "UNKNOWN", attempt.resource_uncertain)
                    attempt.evidence.record.assert_called_once_with("model-terminal", status="error", code=f"MODEL_API_HTTP_{status}")
