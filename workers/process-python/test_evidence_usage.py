"""Local SDK seam double with the real private Evidence writer; no network."""
import importlib.util
import json
import os
from pathlib import Path
import sys
import tempfile
from types import ModuleType, SimpleNamespace
import unittest
from unittest.mock import patch

from worker_runtime import Evidence


class EvidenceUsageTest(unittest.TestCase):
    def test_completed_model_turn_records_known_and_unknown_usage(self):
        agents = ModuleType("agents")
        agents.__path__ = []
        agents.RunContextWrapper = type("RunContextWrapper", (), {})
        agents.function_tool = lambda function: function
        models = ModuleType("agents.models")
        models.__path__ = []
        interface = ModuleType("agents.models.interface")
        interface.Model = type("Model", (), {})
        name = "fd085_guarded_model_test_double"
        spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name("openai_api_runtime.py"))
        module = importlib.util.module_from_spec(spec)
        with tempfile.TemporaryDirectory() as root, patch.dict(sys.modules, {
                "agents": agents, "agents.models": models, "agents.models.interface": interface, name: module}), \
                patch.dict(os.environ, {"AIW_WORKER_EVIDENCE_ROOT": str(Path(root) / "evidence")}, clear=False):
            spec.loader.exec_module(module)
            config = SimpleNamespace(fallback_workspace=str(Path(root) / "workspace"), repository_root=None,
                                     worktree_root=None, process_role="coder", runtime="openai-agents")
            evidence = Evidence(config, {"taskId": "task", "attemptId": "attempt"})
            guarded = module._GuardedModel.__new__(module._GuardedModel)
            guarded._request = SimpleNamespace(attempt=SimpleNamespace(evidence=evidence))
            guarded._model = SimpleNamespace(last_usage=None)
            guarded._tool_schemas = {}
            guarded.history = SimpleNamespace(response=lambda _items: None)
            for usage in (None, {"input_tokens": 12, "output_tokens": 5, "input_tokens_details": {"cached_tokens": 3}}):
                guarded._validate({"status": "completed", "output": [], "usage": usage})
            rows = [json.loads(line) for line in (evidence.directory / "events.jsonl").read_text(encoding="utf-8").splitlines()]
            terminal = [row for row in rows if row["event"] == "model-terminal"]
            self.assertEqual([(row["usageKnown"], row["usageSource"]) for row in terminal],
                             [(False, "unknown"), (True, "provider-response")])
            self.assertEqual(terminal[1]["cachedTokens"], 3)
            self.assertFalse(evidence.failed)
            with self.assertRaises(RuntimeError):
                evidence.record("model-terminal", usageSource="unbounded-private-content")
            self.assertTrue(evidence.failed)
