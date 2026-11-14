"""Offline delegation contracts. Tests are written but require separate execution approval."""
import sys
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock, MagicMock
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "personal-secretary"))
import test_fd084_core as core
from professional_tasks import ProfessionalTasks
from platform_client import PlatformError, PlatformClient


class DelegationCase(unittest.TestCase):
    setUp = core.LocalCase.setUp

    def professional(self):
        identity = {"ownerId": "owner", "agentId": "secretary", "platformInstanceId": "platform"}
        self.runtime.platform = SimpleNamespace(session_started=True, request=Mock(), boot="boot")
        self.runtime.daily = SimpleNamespace(_decision=Mock(), _report=Mock(), _message=Mock())
        service = ProfessionalTasks(self.runtime)
        service.options = Mock(return_value={**identity, "available": True})
        return service, identity

    def test_only_selected_task_is_submitted_without_repository_or_history(self):
        service, identity = self.professional()
        self.runtime.platform.request.side_effect = lambda path, body: {**identity, "requestId": body["requestId"],
            "planId": "FP-task", "executionId": "FP-task", "state": "RUNNING", "taskId": "task", "report": ""}
        service.submit(self.store.get("work", "work"), self.job, {"instruction": "Summarize the selected text", "history": "private", "body": "private"})
        path, body = self.runtime.platform.request.call_args.args
        self.assertEqual(path, "/task-delegations")
        self.assertEqual(set(body), {"requestId", "instruction"})
        self.assertNotIn("private", str(body))
        self.assertTrue(service.pending("work"))

    def test_uncertain_post_is_queried_without_resubmission(self):
        service, identity = self.professional()
        self.runtime.platform.request.side_effect = PlatformError(503, "unconfirmed")
        service.submit(self.store.get("work", "work"), self.job, {"instruction": "Analyze the selected text"})
        row = next(item for item in self.store.list("decision") if item.get("kind") == "PROFESSIONAL_TASK")
        self.assertEqual(row["state"], "UNKNOWN")
        self.runtime.platform.request.reset_mock()
        self.runtime.platform.request.side_effect = None
        self.runtime.platform.request.return_value = {**identity, "requestId": row["requestId"], "planId": "FP-task",
            "executionId": "FP-task", "state": "RUNNING", "taskId": "task", "report": ""}
        service.recheck(row["id"], row["revision"])
        self.runtime.platform.request.assert_called_once_with("/task-delegations/" + row["requestId"])

    def test_finished_status_without_report_is_not_success(self):
        service, identity = self.professional()
        row = {"requestId": "request", "platformInstanceId": "platform"}
        with self.assertRaises(ValueError):
            service._receive(row, {**identity, "requestId": "request", "planId": "FP-task", "executionId": "FP-task", "state": "COMPLETED", "report": ""})
        self.runtime.daily._report.assert_not_called()

    def test_failed_run_with_retained_lease_still_blocks_duplicate_submission(self):
        service, identity = self.professional()
        self.runtime.platform.request.side_effect = lambda path, body: {**identity, "requestId": body["requestId"],
            "planId": "FP-task", "executionId": "FP-task", "state": "FAILED", "taskId": "task", "activeLease": True, "report": ""}
        service.submit(self.store.get("work", "work"), self.job, {"instruction": "Analyze selected text"})
        self.assertTrue(service.pending("work"))
        with self.assertRaises(ValueError):
            service.submit(self.store.get("work", "work"), self.job, {"instruction": "Repeat the same work"})
        self.assertEqual(self.runtime.platform.request.call_count, 1)

    def test_real_client_allows_exact_delegation_routes_without_opening_other_paths(self):
        client = PlatformClient("http://127.0.0.1", "secretary", "mock-credential", allow_loopback_http=True)
        response = MagicMock()
        response.__enter__.return_value = response
        response.read.return_value = b'{"state":"RUNNING"}'
        client.transport.open = Mock(return_value=response)
        client.request("/task-delegations/options")
        client.request("/task-delegations", {"instruction": "Selected task"})
        client.request("/task-delegations/00000000-0000-0000-0000-000000000001")
        requests = [call.args[0] for call in client.transport.open.call_args_list]
        self.assertEqual([request.get_method() for request in requests], ["GET", "POST", "GET"])
        self.assertTrue(all(request.full_url.startswith(client.base + "/task-delegations") for request in requests))
        with self.assertRaises(ValueError):
            client.request("/task-delegations/../identity")
        with self.assertRaises(ValueError):
            client.request("/unregistered-operation")
        self.assertEqual(client.transport.open.call_count, 3)
