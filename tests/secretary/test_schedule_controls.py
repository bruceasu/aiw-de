"""Pause automatic starts, while manual chat and real execution controls remain independent."""
import json
import unittest
from unittest.mock import Mock, patch

import test_fd084_core as core
import test_fd084_review as review
from secretary_host import SecretaryHost
from secretary_runs import RunYield
from professional_tasks import ProfessionalTasks
from platform_agent_actions import PlatformAgentActions
from authorization_requests import AuthorizationRequests
from types import SimpleNamespace


class ScheduleControlsCase(unittest.TestCase):
    setUp = core.LocalCase.setUp
    start = core.LocalCase.start
    daily = review.ReviewCase.daily

    def test_automatic_pause_allows_manual_conversation(self):
        daily = self.daily()
        self.settings.value["paused"] = True
        message = self.store.put("message", "paused-chat", {"workId": "work", "text": "Hello", "state": "PENDING"}, 0)
        with patch.object(SecretaryHost, "sdk", return_value=json.dumps({"action": "finish", "completed": True, "text": "Hello"})):
            daily.run(self.store.get("work", "work"), "Hello", message["id"])
        self.assertEqual(self.store.get("message", message["id"])["state"], "DONE")

    def test_automatic_pause_does_not_interrupt_an_active_run(self):
        identity = self.start()
        self.settings.value["paused"] = True
        self.journal.check(identity)
        self.assertEqual(self.journal.get(identity)["state"], "RUNNING")
        self.journal.save(self.journal.get(identity), state="PAUSED")
        with self.assertRaises(RunYield) as stopped:
            self.journal.check(identity)
        self.assertEqual(stopped.exception.reason, "pause")

    def test_automatic_pause_keeps_due_local_work_from_starting(self):
        daily = self.daily();daily._start = Mock()
        self.settings.value["paused"] = True
        work = self.store.get("work", "work")
        self.store.put("work", "work", {"title": "Scheduled", "state": "SCHEDULED", "assistantMode": "daily",
            "dueMs": 100000, "executionMode": "TIMED"}, work["revision"])
        with patch("daily_assistant.now_ms", return_value=100000):
            daily.tick()
        daily._start.assert_not_called()

    def test_automatic_pause_keeps_reading_existing_delegation_receipts(self):
        self.settings.value["paused"] = True
        service = ProfessionalTasks(self.runtime);service.last_poll = -100
        service.recheck = Mock()
        row = self.store.put("decision", "remote", {"kind": "PROFESSIONAL_TASK", "state": "ACTIVE", "workId": "work"}, 0)
        service.tick()
        service.recheck.assert_called_once_with(row["id"], row["revision"])

    def test_paused_authorization_tick_reads_existing_receipt_without_resuming_or_answering(self):
        self.daily();self.settings.value["paused"] = True
        self.store.put("message", "business-message", {"workId": "work", "text": "Original action", "state": "WAITING_PLATFORM"}, 0)
        self.store.put("decision", "business-job", {"kind": "DAILY_PLAN", "workId": "work", "messageId": "business-message",
            "state": "WAITING_PLATFORM", "businessRequestId": "original-receipt"}, 0)
        self.runtime.platform = SimpleNamespace(backend=SimpleNamespace(profile="LOCAL"), receipt=Mock(return_value={"state": "SUCCEEDED"}))
        self.runtime.platform_setup = SimpleNamespace(public=lambda: {"connected": True}, check=Mock())
        actions = PlatformAgentActions(self.runtime);actions.follow_questions = Mock()
        self.runtime.platform_actions = actions
        authorization = AuthorizationRequests(self.runtime);authorization.resume = Mock();authorization.last_poll = -100
        self.runtime.authorizations = authorization
        with patch("authorization_requests.threading.Thread") as thread:
            thread.return_value.start.side_effect = lambda: thread.call_args.kwargs["target"]()
            authorization.tick()
        self.runtime.platform.receipt.assert_called_once_with("original-receipt")
        self.assertEqual(self.store.get("message", "business-message")["state"], "DONE")
        authorization.resume.assert_not_called();actions.follow_questions.assert_not_called()
        self.runtime.platform_setup.check.assert_not_called()

    def test_question_tracking_uses_plan_and_task_from_the_shared_executor_receipt(self):
        self.daily();self.runtime.enabled = True
        self.store.put("decision", "delegated-question", {"kind": "PROFESSIONAL_TASK", "state": "ACTIVE", "workId": "work",
            "requestId": "request", "jobId": "job", "body": {"instruction": "Selected text"},
            "receipt": {"planId": "plan", "taskId": "task", "taskState": "WAITING_INPUT"}}, 0)
        actions = PlatformAgentActions(self.runtime)
        with patch("platform_agent_actions.BusinessActions") as business:
            business.return_value.permissions.return_value = {"permissions": [{"id": "permission", "action": "A19_ANSWER", "effectiveState": "ENABLED", "remaining": 1, "scopeKind": "ALL"}]}
            business.return_value.questions.return_value = {"questions": []}
            actions.follow_questions()
        business.return_value.questions.assert_called_once_with("permission", "plan")
        self.assertEqual(self.store.get("decision", "platform-question:request")["taskId"], "task")
