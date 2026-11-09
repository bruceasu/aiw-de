"""Focused regression contracts for independent review R1-R5; no live services."""
import json
import threading
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import test_fd084_core as core
from daily_assistant import DailyAssistant
from conversations import Conversations
from knowledge.client import KnowledgeError
from knowledge.settings import KnowledgeSettings
from knowledge.tools import KnowledgeNotExecuted
from platform_agent_actions import PlatformAgentActions
from professional_tasks import ProfessionalTasks
from runtime import Runtime, PersonalChatError
from secretary_host import SecretaryHost
from assistant_tools import ConfirmationRequired
from pet.desktop_client import PersonalClient, PersonalError


class ReviewCase(unittest.TestCase):
    setUp = core.LocalCase.setUp
    start = core.LocalCase.start

    def daily(self):
        daily = DailyAssistant.__new__(DailyAssistant)
        daily.runtime, daily.store, daily.settings = self.runtime, self.store, self.settings
        daily.busy, daily.lock = set(), threading.Lock()
        self.runtime.daily = daily
        self.settings.configured = lambda: True
        self.runtime.authorizations = SimpleNamespace(pending=lambda _id: False)
        self.runtime.professional = SimpleNamespace(pending=lambda _id: False)
        self.runtime.conversations = SimpleNamespace(summarize=lambda work, **_kw: work, context=lambda *_args: {})
        self.runtime.report_work = lambda *_args, **_kw: None
        self.runtime.platform_actions = SimpleNamespace(wait=lambda *_args: False)
        return daily

    def test_delete_purges_own_runtime_graph_and_future_backup(self):
        identity = self.start()
        self.journal.save(self.journal.get(identity), history=[{"content": "private-marker"}])
        for kind in ("SECRETARY_SOURCE", "SECRETARY_WIRE", "SECRETARY_TOOL", "SECRETARY_CONTROL"):
            self.store.put("runtime", identity + kind, {"kind": kind, "runId": identity, "state": "SUCCEEDED", "content": "private-marker"}, 0)
        self.journal.finish(identity, "SUCCEEDED")
        self.store.put("runtime", "submission", {"kind": "SECRETARY_SUBMISSION", "workId": "work", "payload": "private-marker"}, 0)
        self.store.put("runtime", "other", {"kind": "SECRETARY_RUN", "workId": "other", "history": ["other-private"]}, 0)
        self.store.put("runtime", "other-child", {"kind": "SECRETARY_SOURCE", "runId": "other", "content": "other-private"}, 0)
        work = self.store.get("work", "work")
        self.store.delete_conversation(work["id"], work["revision"], False)
        backup = json.dumps(self.store.backup())
        self.assertNotIn("private-marker", backup)
        self.assertIn("other-private", backup)
        self.assertEqual({row["id"] for row in self.store.list("runtime")}, {"other", "other-child"})

    def test_sdk_final_action_cannot_execute_legacy_tool(self):
        daily = self.daily()
        path = self.work / "unregistered.md"
        message = self.store.put("message", "msg", {"workId": "work", "text": "hello", "state": "PENDING"}, 0)
        action = {"action": "write_file", "path": str(path), "text": "must never be written"}
        with patch.object(SecretaryHost, "sdk", return_value=json.dumps(action)) as sdk:
            daily.run(self.store.get("work", "work"), "hello", message["id"])
        sdk.assert_called_once()
        self.assertFalse(path.exists())
        self.assertEqual(self.store.get("message", "msg")["state"], "FAILED")
        self.assertEqual(self.journal.records("SECRETARY_TOOL"), [])
        self.assertFalse(any(row.get("kind") == "DAILY_CONFIRMATION" for row in self.store.list("decision")))

    def test_file_permission_request_is_visible_and_approval_keeps_original_action(self):
        daily = self.daily()
        message = self.store.put("message", "msg-confirm", {"workId": "work", "text": "创建文件", "state": "PENDING"}, 0)
        job = daily._decision(self.job, messageId=message["id"], userText="请创建文件")
        host = SecretaryHost(daily, self.store.get("work", "work"), job, self.tools, SimpleNamespace())
        action = {"action": "create_file", "path": str(self.root / "outside" / "report.md"), "text": "draft"}
        def waiting(request):
            raise ConfirmationRequired("这项文件操作需要确认。", request)
        try:
            self.journal.invoke(host.identity, action, waiting, call_id="file-call")
        except ConfirmationRequired as error:
            host.waiting_confirmation(error, action)
        else:
            self.fail("confirmation was not requested")
        decisions = [row for row in self.store.list("decision") if row.get("kind") == "DAILY_CONFIRMATION"]
        self.assertEqual(len(decisions), 1)
        pending = decisions[0]
        self.assertEqual(pending["action"], action)
        self.assertEqual(self.store.get("runtime", pending["receiptKey"])["state"], "WAITING")
        self.assertIn(action["path"], self.store.get("message", message["id"])["reply"])
        with patch("daily_assistant.threading.Thread") as thread:
            result = daily.confirm(pending["id"], pending["revision"], True)
        self.assertEqual(result["state"], "ACCEPTED")
        self.assertEqual(self.store.get("decision", job["id"])["approvedAction"], action)
        thread.return_value.start.assert_called_once()
        self.assertFalse((self.root / "outside" / "report.md").exists())

    def runtime_chat(self):
        self.daily()
        runtime = Runtime.__new__(Runtime)
        runtime.__dict__.update(vars(self.runtime))
        runtime.chat_lock = threading.RLock()
        self.runtime = runtime
        runtime.daily.runtime = runtime
        return runtime

    def test_chat_response_loss_replays_original_new_conversation(self):
        runtime = self.runtime_chat()
        with patch("daily_assistant.threading.Thread") as thread:
            first = runtime.chat(None, "please save this note", "submission-1")
            # A new host has no in-memory dedup cache; the durable record wins.
            runtime.chat_lock = threading.RLock()
            repeated = runtime.chat(None, "please save this note", "submission-1")
        self.assertEqual(first["id"], repeated["id"])
        self.assertEqual(thread.call_count, 1)
        self.assertEqual(len(self.store.list("message")), 1)
        self.assertEqual(len([w for w in self.store.list("work") if w.get("conversationOnly")]), 1)
        with self.assertRaises(PersonalChatError):
            runtime.chat(None, "different content", "submission-1")
        with self.assertRaises(PersonalChatError):
            runtime.chat("work", "please save this note", "submission-1")

    def test_crash_after_acceptance_does_not_restart_submission(self):
        runtime = self.runtime_chat()
        with patch.object(runtime.daily, "chat", side_effect=OSError("interrupted before enqueue")):
            with self.assertRaises(OSError):
                runtime.chat(None, "request", "submission-2")
        with patch.object(runtime.daily, "chat") as enqueue:
            result = runtime.chat(None, "request", "submission-2")
        self.assertEqual(result["state"], "UNKNOWN")
        enqueue.assert_not_called()

    def test_platform_receipt_finishes_run_and_releases_followup(self):
        daily = self.daily()
        identity = self.start()
        self.journal.control({"runId": identity, "revision": self.journal.get(identity)["revision"],
                              "control": "follow-up", "requestId": "next", "text": "next request"})
        daily._decision(self.job, state="WAITING_PLATFORM", businessRequestId="operation")
        self.journal.finish(identity, "WAITING_USER")
        self.runtime.platform = SimpleNamespace(receipt=lambda _id: {"state": "APPLIED"})
        actions = PlatformAgentActions.__new__(PlatformAgentActions)
        actions.runtime, actions.store = self.runtime, self.store
        actions.poll()
        self.assertEqual(self.journal.get(identity)["state"], "SUCCEEDED")
        with patch.object(daily, "chat", return_value={"id": "follow-message"}) as chat:
            daily.followups()
        chat.assert_called_once()
        self.assertEqual(self.journal.records("SECRETARY_CONTROL", identity)[0]["state"], "APPLIED")

    def test_professional_receipt_cannot_revive_cancelled_run(self):
        self.daily()
        identity = self.start()
        self.journal.control({"runId": identity, "revision": self.journal.get(identity)["revision"],
                              "control": "follow-up", "requestId": "next", "text": "next request"})
        self.journal.finish(identity, "CANCELLED")
        professional = ProfessionalTasks.__new__(ProfessionalTasks)
        professional.runtime, professional.store = self.runtime, self.store
        row = {"jobId": "job", "platformInstanceId": "platform", "requestId": "request", "state": "ACTIVE"}
        result = {"platformInstanceId": "platform", "requestId": "request", "planId": "plan",
                  "preparation": {"state": "READY", "taskId": "task"}, "taskState": "COMPLETED",
                  "report": {"verdict": "PASS", "summary": "done"}}
        with patch.object(professional, "_identity"), patch.object(professional, "_save"), patch.object(professional, "_publish"):
            professional._receive(row, result)
        self.assertEqual(self.journal.get(identity)["state"], "CANCELLED")
        self.assertEqual(self.journal.records("SECRETARY_CONTROL", identity)[0]["state"], "NOT_APPLIED")

    def test_fast_completion_is_not_downgraded_when_tool_unwinds(self):
        daily = self.daily()
        host = SecretaryHost(daily, self.store.get("work", "work"), self.job, self.tools, SimpleNamespace())
        receipt = self.store.put("runtime", "inflight", {"kind": "SECRETARY_TOOL", "runId": host.identity, "state": "STARTED"}, 0)
        daily._decision(self.job, state="DONE", result="actual professional result")
        self.assertEqual(self.journal.get(host.identity)["state"], "RECONCILING")
        self.journal.save(receipt, state="SUCCEEDED")
        host.stopped("delegated")
        self.assertEqual(self.journal.get(host.identity)["state"], "SUCCEEDED")
        sequence = self.journal.get(host.identity)["sequence"]
        host.stopped("platform")
        self.assertEqual(self.journal.get(host.identity)["sequence"], sequence)

    def test_failure_with_unknown_effect_stays_reconciling(self):
        daily = self.daily()
        identity = self.start()
        self.store.put("runtime", "unknown", {"kind": "SECRETARY_TOOL", "runId": identity, "state": "UNKNOWN"}, 0)
        daily._decision(self.job, state="FAILED", message="lost result")
        self.assertEqual(self.journal.get(identity)["state"], "RECONCILING")

    def assert_both_delete_entries_blocked(self):
        conversations = Conversations(self.runtime)
        work = self.store.get("work", "work")
        with self.assertRaises(ValueError):
            conversations.delete("work", work["revision"], False)
        trashed = self.store.put("work", "work", {"title": "test", "state": "TRASHED"}, work["revision"])
        with self.assertRaises(ValueError):
            conversations.delete_work("work", trashed["revision"], False)
        self.assertIsNotNone(self.store.get("decision", "job"))
        self.assertTrue(self.store.list("runtime"))

    def test_paused_runtime_blocks_both_public_delete_entries(self):
        self.daily()
        self.journal.finish(self.start(), "PAUSED")
        self.assert_both_delete_entries_blocked()

    def test_reconciling_runtime_blocks_both_public_delete_entries(self):
        self.daily()
        self.journal.finish(self.start(), "RECONCILING")
        self.assert_both_delete_entries_blocked()

    def test_cancelled_unknown_effect_blocks_both_public_delete_entries(self):
        self.daily()
        identity = self.start()
        self.store.put("runtime", "unknown", {"kind": "SECRETARY_TOOL", "runId": identity, "state": "UNKNOWN"}, 0)
        self.journal.finish(identity, "CANCELLED")
        self.assert_both_delete_entries_blocked()

    def test_cancelled_pending_note_blocks_actual_delete_transaction(self):
        self.daily()
        identity = self.start()
        self.store.put("runtime", "pending-note", {"kind": "SECRETARY_TOOL", "runId": identity, "state": "SUCCEEDED",
            "action": {"action": "knowledge_save_note"}, "result": {"state": "pending"}}, 0)
        self.journal.finish(identity, "CANCELLED")
        work = self.store.get("work", "work")
        with self.assertRaises(ValueError):
            self.store.delete_conversation("work", work["revision"], False)
        self.assertIsNotNone(self.store.get("runtime", "pending-note"))

    def test_deletion_transaction_rechecks_after_entry_guard(self):
        self.daily()
        identity = self.start()
        self.journal.finish(identity, "SUCCEEDED")
        original = self.store.assert_runtime_deletable
        calls = []
        def check(work_id):
            calls.append(work_id)
            if len(calls) == 2:
                self.store.put("runtime", "late-run", {"kind": "SECRETARY_RUN", "workId": work_id, "state": "PAUSED"}, 0)
            original(work_id)
        work = self.store.get("work", "work")
        with patch.object(self.store, "assert_runtime_deletable", side_effect=check), self.assertRaises(ValueError):
            Conversations(self.runtime).delete("work", work["revision"], False)
        self.assertEqual(len(calls), 2)
        self.assertIsNotNone(self.store.get("runtime", identity))

    def test_cancelled_file_effect_can_be_reconciled_without_resuming(self):
        self.daily()
        identity = self.start()
        action = {"action": "create_file", "path": str(self.work / "result.md"), "text": "actual result"}
        key = self.journal.call_key(identity, action)
        def execute(value):
            self.tools.tool_receipt = key
            return self.tools.perform(value)
        self.journal.invoke(identity, action, execute)
        self.journal.save(self.store.get("runtime", key), state="UNKNOWN")
        self.journal.finish(identity, "CANCELLED")
        result = self.journal.reconcile({"runId": identity, "revision": self.journal.get(identity)["revision"]})
        self.assertEqual(result["state"], "CANCELLED")
        self.assertEqual(self.store.get("runtime", key)["state"], "SUCCEEDED")
        self.store.assert_runtime_deletable("work")

    def test_cancellation_closes_unadmitted_waiting_tool(self):
        self.daily()
        identity = self.start()
        self.store.put("runtime", "waiting", {"kind": "SECRETARY_TOOL", "runId": identity, "state": "WAITING"}, 0)
        self.journal.save(self.journal.get(identity), hasWaitingTool=True)
        self.journal.finish(identity, "CANCELLED")
        self.assertEqual(self.store.get("runtime", "waiting")["state"], "NOT_EXECUTED")
        self.store.assert_runtime_deletable("work")


class NullResultCase(unittest.TestCase):
    setUp = core.KnowledgeCase.setUp
    start = core.LocalCase.start

    def rejected_retry(self):
        identity = self.start()
        self.tools.user_message = "请解释如何保存到知识库"
        for _ in range(2):
            with self.assertRaises(KnowledgeNotExecuted):
                self.journal.invoke(identity, self.action, self.tools.perform)
        receipt = self.store.get("runtime", self.journal.call_key(identity, self.action))
        self.assertEqual(receipt["state"], "NOT_EXECUTED")
        self.assertIn("result", receipt)
        self.assertIsNone(receipt["result"])
        self.assertEqual(self.client.created, 0)
        daily = DailyAssistant.__new__(DailyAssistant)
        daily.runtime, daily.store = self.runtime, self.store
        self.runtime.daily = daily
        return identity

    def test_real_local_rejection_retry_remains_visible_and_cancellable(self):
        identity = self.rejected_retry()
        public = self.journal.public()
        self.assertEqual(public[0]["id"], identity)
        self.assertFalse(public[0]["hasUnknownEffect"])
        self.journal.finish(identity, "PAUSED")
        result = self.journal.control({"runId": identity, "revision": self.journal.get(identity)["revision"],
            "control": "cancel", "requestId": "cancel-rejected-retry"})
        self.assertEqual(result["state"], "CANCELLED")
        self.assertFalse(self.journal.public()[0]["hasUnknownEffect"])
        self.store.assert_runtime_deletable("work")

    def test_real_local_rejection_retry_reconciles_without_remote_calls(self):
        identity = self.rejected_retry()
        self.journal.finish(identity, "RECONCILING")
        with patch.object(self.client, "creation") as query, patch.object(self.client, "create_note") as create:
            result = self.journal.reconcile({"runId": identity, "revision": self.journal.get(identity)["revision"]})
            query.assert_not_called()
            create.assert_not_called()
        self.assertEqual(result["state"], "PAUSED")
        self.assertFalse(self.journal.public()[0]["hasUnknownEffect"])

    def test_result_shape_never_substitutes_for_actual_receipt_state(self):
        identity = self.start()
        receipt = self.store.put("runtime", "shape", {"kind": "SECRETARY_TOOL", "runId": identity,
            "action": {"action": "knowledge_save_note"}, "state": "SUCCEEDED"}, 0)
        cases = [("SUCCEEDED", None, False), ("SUCCEEDED", [], False), ("SUCCEEDED", "text", False),
                 ("SUCCEEDED", 7, False), ("SUCCEEDED", {"state": "pending"}, True),
                 ("SUCCEEDED", {"state": "unknown"}, True), ("UNKNOWN", None, True),
                 ("STARTED", None, True), ("NOT_EXECUTED", None, False)]
        for state, result, unresolved in cases:
            with self.subTest(state=state, result=result):
                receipt = self.journal.save(receipt, state=state, result=result)
                self.assertEqual(bool(self.journal.unresolved_effects(identity)), unresolved)
                self.assertEqual(self.journal.public()[0]["hasUnknownEffect"], unresolved)


class IdentityCase(unittest.TestCase):
    setUp = core.KnowledgeCase.setUp

    def test_replaced_credential_cannot_query_or_replay_original_note(self):
        self.client.lose_response = True
        with self.assertRaises(KnowledgeError):
            self.tools.perform(self.action)
        self.settings.value["testCredentialDigest"] = "replacement-account"
        for action in ({"action": "knowledge_note_status", "requestId": "one"}, self.action):
            with patch.object(self.client, "creation") as query, patch.object(self.client, "create_note") as create:
                with self.assertRaises(KnowledgeError):
                    self.tools.perform(action)
                query.assert_not_called()
                create.assert_not_called()

    def test_same_reference_with_replaced_secret_has_different_identity(self):
        settings = KnowledgeSettings.__new__(KnowledgeSettings)
        settings.value = {"knowledgeEnabled": True, "knowledgeBaseUrl": "https://kb.example",
                          "knowledgeServiceId": "library", "knowledgeCredentialRef": "same-ref"}
        settings.knowledge_secret = {"same-ref": "account-a-token"}
        _, first = settings.connection()
        settings.knowledge_secret["same-ref"] = "account-b-token"
        _, second = settings.connection()
        self.assertNotEqual(first, second)
        self.assertNotIn("account-a-token", json.dumps(first))

    def test_cancelled_note_status_never_posts_on_not_found(self):
        self.client.lose_response = True
        with self.assertRaises(KnowledgeError):
            self.tools.perform(self.action)
        self.client.records.clear()
        with patch.object(self.client, "create_note") as create:
            with self.assertRaises(KnowledgeError):
                self.tools.perform({"action": "knowledge_note_status", "requestId": "one", "queryOnly": True})
            create.assert_not_called()


class DesktopCase(unittest.TestCase):
    def setUp(self):
        self.client = PersonalClient("http://127.0.0.1:8766")

    def test_transport_loss_keeps_original_id_and_payload(self):
        first = self.client.chat_payload("work", "original text")
        with patch.object(self.client, "request", side_effect=PersonalError("lost response")):
            with self.assertRaises(PersonalError):
                self.client.operate("chat", first)
        self.assertEqual(first, self.client.chat_payload("work", "original text"))
        self.assertNotEqual(first["requestId"], self.client.chat_payload("other", "original text")["requestId"])
        with patch.object(self.client, "request", return_value={"state": "UNKNOWN", "reply": "not confirmed"}), patch.object(self.client, "state", return_value={}):
            result, _, warning = self.client.operate("chat", first)
        self.assertEqual(result["state"], "UNKNOWN")
        self.assertTrue(warning)
        self.assertEqual(first, self.client.chat_payload("work", "original text"))

    def test_explicit_new_submission_after_known_acceptance_gets_new_id(self):
        first = self.client.chat_payload("work", "text")
        with patch.object(self.client, "request", return_value={"state": "PENDING"}) as request, patch.object(self.client, "state", return_value={}):
            self.client.operate("chat", first)
        self.assertEqual(request.call_args.args[1]["requestId"], first["requestId"])
        self.assertNotEqual(first["requestId"], self.client.chat_payload("work", "text")["requestId"])

    def test_explicitly_ending_unknown_submission_starts_new_identity(self):
        original = self.client.chat_payload("", "text")
        self.client.forget_chat(original)
        self.assertNotEqual(original["requestId"], self.client.chat_payload("", "text")["requestId"])
