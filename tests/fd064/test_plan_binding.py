import io
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock
import urllib.error

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "personal-secretary"))
from authorization_requests import AuthorizationRequired
from personal_plans import PersonalPlans
from platform_client import PlatformClient, PlatformError
from professional_tasks import ProfessionalTasks
from store import SQLiteStore
from daily_assistant import DailyAssistant


class PlanBindingTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.store = SQLiteStore(Path(self.temp.name), "owner", "secretary")
        self.work = self.store.put("work", "work", {"title": "Robot avatar", "body": "Add a robot to sudoku2.htm", "state": "NEEDS_HUMAN"}, 0)
        self.job = self.store.put("decision", "job", {"kind": "DAILY_PLAN", "workId": "work", "state": "RUNNING"}, 0)
        self.plans = [{"id": "old", "state": "COMPLETED", "repositories": []}]
        self.calls = []
        self.draft_state = "DRAFT"
        identity = {"ownerId": "owner", "workerId": "secretary", "platformInstanceId": "instance"}

        def request(path, body=None):
            self.calls.append((path, body))
            if path == "/identity": return identity
            if path == "/professional-tasks/options":
                return {**identity, "plans": self.plans, "methods": [{"role": "coder", "method": {}}], "models": [{"id": "model", "profile": {}}]}
            if path.startswith("/personal-plans"):
                return {**identity, "requestId": "work", "planId": "FP-own", "state": self.draft_state}
            if path == "/professional-tasks":
                return {**identity, "requestId": body["requestId"], "planId": body["planId"], "preparation": {"errorCode": "WORKER_STARTING"}}
            raise AssertionError(path)

        platform = SimpleNamespace(session_started=True, request=request)
        daily = Mock()
        def report(identity, state, code, text, **extra):
            row = self.store.get("work", identity)
            return self.store.put("work", identity, {**{k: v for k, v in row.items() if k not in {"id", "revision"}},
                "state": state, "report": {"code": code, "message": text}, **extra}, row["revision"])
        daily._report.side_effect = report
        self.runtime = SimpleNamespace(store=self.store, platform=platform, daily=daily,
                                       personal_plans=PersonalPlans(self.store, platform))
        self.professional = ProfessionalTasks(self.runtime)

    def tearDown(self):
        self.store.close()
        self.temp.cleanup()

    def test_unlinked_work_never_uses_single_completed_plan(self):
        with self.assertRaises(AuthorizationRequired) as result:
            self.professional.submit(self.work, self.job, {"planId": "old"})
        self.assertEqual(result.exception.plan_id, "FP-own")
        self.assertEqual(self.store.get("work", "work")["professionalDraftPlanId"], "FP-own")
        self.assertFalse(any(p == "/professional-tasks" for p, _ in self.calls))
        self.assertEqual(sum(p == "/personal-plans" for p, _ in self.calls), 1)

    def test_even_unrelated_draft_is_not_automatically_selected(self):
        self.plans[0]["state"] = "DRAFT"
        self.test_unlinked_work_never_uses_single_completed_plan()

    def test_repeated_preparation_reads_original_draft_without_duplicate_post(self):
        for _ in range(2):
            with self.assertRaises(AuthorizationRequired):
                self.professional.prepare_own_plan(self.work, self.job)
        self.assertEqual(sum(p == "/personal-plans" for p, _ in self.calls), 1)
        self.assertIn(("/personal-plans/work", None), self.calls)

    def test_cached_receipt_is_refreshed_before_reusing_completed_plan(self):
        with self.assertRaises(AuthorizationRequired): self.professional.prepare_own_plan(self.work, self.job)
        self.draft_state = "COMPLETED"
        with self.assertRaises(ValueError): self.professional.prepare_own_plan(self.work, self.job)
        self.assertEqual(sum(p == "/personal-plans" for p, _ in self.calls), 1)

    def test_linked_plan_cannot_be_replaced_by_model(self):
        with self.assertRaises(ValueError):
            self.professional.submit({**self.work, "planId": "own"}, self.job, {"planId": "old"})
        self.assertFalse(any(p == "/professional-tasks" for p, _ in self.calls))

    def test_linked_completed_plan_is_rejected_locally(self):
        with self.assertRaises(ValueError):
            self.professional.submit({**self.work, "planId": "old"}, self.job, {"planId": "old"})
        self.assertFalse(any(p == "/professional-tasks" for p, _ in self.calls))

    def test_linked_draft_can_submit_exact_repository_and_baseline(self):
        self.plans = [{"id": "own", "state": "DRAFT", "repositories": [{"ref": "repo", "baselines": ["a" * 40]}]}]
        self.professional.submit({**self.work, "planId": "own"}, self.job,
                                 {"planId": "own", "role": "coder", "instruction": "Add avatar"})
        submitted = [body for path, body in self.calls if path == "/professional-tasks"]
        self.assertEqual(len(submitted), 1)
        self.assertEqual(submitted[0]["planId"], "own")
        self.assertEqual(submitted[0]["baseRevision"], "a" * 40)

    def test_cancelled_work_and_pending_requests_cannot_prepare_replacement(self):
        self.store.put("decision", "pending", {"kind": "PROFESSIONAL_TASK", "workId": "work", "state": "UNKNOWN"}, 0)
        with self.assertRaises(ValueError): self.professional.prepare_own_plan(self.work, self.job)
        self.assertEqual(self.calls, [])

    def test_conflict_response_retains_only_safe_diagnostic_fields(self):
        client = PlatformClient("http://127.0.0.1:8080", "secretary", "test", True)
        payload = json.dumps({"code": "PROFESSIONAL_PLAN_NOT_DRAFT", "planId": "old", "planState": "COMPLETED", "secret": "not exposed"}).encode()
        client.transport = Mock()
        client.transport.open.side_effect = urllib.error.HTTPError(client.base, 409, "Conflict", {}, io.BytesIO(payload))
        with self.assertRaises(PlatformError) as result: client.request("/professional-tasks", {})
        self.assertEqual(result.exception.code, "PROFESSIONAL_PLAN_NOT_DRAFT")
        self.assertEqual(result.exception.details, {"planId": "old", "planState": "COMPLETED"})
        self.assertNotIn("not exposed", str(result.exception))

    def test_recovery_prepares_own_plan_before_summary_or_model(self):
        self.store.put("decision", "rejected", {"kind": "PROFESSIONAL_TASK", "workId": "work", "state": "REJECTED"}, 0)
        runtime = self.runtime
        runtime.professional = self.professional
        runtime.conversations = Mock()
        runtime.model = Mock(side_effect=AssertionError("No model during plan recovery"))
        runtime.authorizations = Mock()
        daily = DailyAssistant.__new__(DailyAssistant)
        daily.runtime, daily.store = runtime, self.store
        daily.settings = SimpleNamespace(configured=lambda: True, value={})
        daily.run(self.work, self.work["body"])
        runtime.conversations.summarize.assert_not_called()
        runtime.model.assert_not_called()
        runtime.authorizations.wait.assert_called_once()
        self.assertEqual(sum(path == "/personal-plans" for path, _ in self.calls), 1)


if __name__ == "__main__": unittest.main()
