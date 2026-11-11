"""Offline contract cases; the client and Windows protection are test doubles."""
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "workers" / "process-python"))
from model_usage_outbox import UsageOutbox


class FakeClient:
    def __init__(self):
        self.base = "http://example.invalid/api/r1/worker-protocol/v1"
        self.timeout = 1
        self.calls = []
        self.fail_finish = False

    def _post(self, path, data):
        self.calls.append((path, data))
        if path.endswith("/finish") and self.fail_finish:
            raise RuntimeError("outcome unknown")
        return {"state": "RECORDED"}


def fake_protection(value, decrypt=False):
    if decrypt:
        if not value.startswith(b"sealed:"):
            raise ValueError("invalid protected payload")
        return value[len(b"sealed:"):][::-1]
    return b"sealed:" + value[::-1]


class OutboxCases(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        environment = mock.patch.dict(os.environ, {"AIW_WORKER_USAGE_OUTBOX": str(root / "private-outbox")})
        environment.start()
        self.addCleanup(environment.stop)
        workspace = root / "workspace"
        workspace.mkdir()
        self.config = SimpleNamespace(fallback_workspace=str(workspace), repository_root="", worktree_root="", worker_id="worker-a")
        self.client = FakeClient()
        self.protection = mock.patch("model_usage_outbox._windows_protect", side_effect=fake_protection)
        self.protection.start()
        self.addCleanup(self.protection.stop)
        self.outbox = UsageOutbox(self.config, self.client)
        self.task = {"taskId": "task-a", "attemptId": "attempt-a", "leaseToken": "secret-lease"}

    def begin(self):
        return self.outbox.begin(self.task, "openai", "example-model", "responses", "task", "API_KEY", "unknown", "TEXT_ONLY")

    def test_completed_usage_is_retried_with_the_same_identity(self):
        ticket = self.begin()
        self.outbox.finish(ticket, "COMPLETED", {"inputTokens": 12, "cachedTokens": 2, "outputTokens": 3})
        self.outbox.client.fail_finish = True
        self.outbox.flush_once()
        self.assertTrue(ticket[0].exists())
        first = list(self.outbox.client.calls)
        self.outbox.client.fail_finish = False
        self.outbox.flush_once()
        self.assertFalse(ticket[0].exists())
        self.assertEqual(first, self.outbox.client.calls[2:])

    def test_started_request_is_retained_until_terminal(self):
        ticket = self.begin()
        self.outbox.flush_once()
        self.assertTrue(ticket[0].exists())
        self.assertEqual(1, len(self.outbox.client.calls))
        self.outbox.finish(ticket, "UNKNOWN", {})
        self.outbox.flush_once()
        self.assertFalse(ticket[0].exists())
        self.assertEqual("UNKNOWN", self.outbox.client.calls[-1][1]["state"])
        self.assertIsNone(self.outbox.client.calls[-1][1]["inputTokens"])

    def test_effective_tier_survives_retry_without_changing_start(self):
        ticket = self.begin()
        self.outbox.finish(ticket, "COMPLETED", {"inputTokens": 12, "cachedTokens": 2, "outputTokens": 3}, "priority")
        self.outbox.client.fail_finish = True
        self.outbox.flush_once()
        first = list(self.outbox.client.calls)
        self.assertEqual("priority", first[-1][1]["effectiveServiceTier"])
        self.assertEqual("unknown", first[0][1]["serviceTier"])
        self.outbox.client.fail_finish = False
        self.outbox.flush_once()
        self.assertEqual(first, self.outbox.client.calls[2:])

    def test_entries_are_private_and_workspace_location_is_rejected(self):
        ticket = self.begin()
        if os.name == "nt":
            self.assertNotIn(b"secret-lease", ticket[0].read_bytes())
        else:
            self.assertEqual(0, ticket[0].stat().st_mode & 0o077)
        with mock.patch.dict(os.environ, {"AIW_WORKER_USAGE_OUTBOX": str(Path(self.config.fallback_workspace) / "inside")}, clear=False):
            with self.assertRaises(RuntimeError):
                UsageOutbox(self.config, self.client)


if __name__ == "__main__":
    unittest.main()
