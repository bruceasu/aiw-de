"""FD-084 local contracts. Execution requires the repository's test authorization."""
import codecs
import io
import json
from pathlib import Path
import sys
import tempfile
import threading
import time
from types import SimpleNamespace
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "personal-secretary"))

from assistant_tools import DailyTools
from secretary_completion import validate_completion
from secretary_credentials import ModelAuthRequired, ModelConnections
from secretary_files import FileNotExecuted, operation_diff
from secretary_oauth import SCOPES, validate, verify
from secretary_runs import RunJournal, RunYield
from secretary_sdk import object_json, paired_history, project, response_events
from store import SQLiteStore
from private_config import PrivateProcessLock
from knowledge.client import KnowledgeError
from secretary_host import SecretaryHost


class LocalCase(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.work = self.root / "work"
        self.work.mkdir()
        self.store = SQLiteStore(self.root / "private", "owner", "secretary")
        self.addCleanup(self.store.close)
        self.settings = SimpleNamespace(directory=self.store.path.parent, value={"roots": [str(self.work)],
            "provider": "openai-agents", "authMode": "api-key", "model": "test"}, knowledge_available=lambda: False)
        self.tools = DailyTools(self.settings, "请创建文件", store=self.store)
        self.runtime = SimpleNamespace(store=self.store, settings=self.settings, cancelled=threading.Event())
        work = self.store.put("work", "work", {"title": "test", "state": "OPEN"}, 0)
        self.job = self.store.put("decision", "job", {"kind": "DAILY_PLAN", "workId": work["id"], "userText": "request", "steps": []}, 0)
        self.journal = RunJournal(self.runtime)
        self.runtime.runs = self.journal
        self.runtime.daily = SimpleNamespace(resume_run=lambda _id: None)

    def start(self):
        return self.journal.start(self.job, self.settings.value)["id"]

    def file(self, name="note.md", text="one\r\ntwo\r\n"):
        path = self.work / name
        path.write_bytes(text.encode())
        return path

    def read(self, path, **extra):
        return self.tools.perform({"action": "read_file", "path": str(path), **extra})

    def edit(self, path, version, old="two", new="three"):
        return self.tools.perform({"action": "prepare_edit", "path": str(path), "expectedVersion": version,
                                  "edits": [{"old": old, "new": new}]})

    def test_create_does_not_overwrite(self):
        path = self.file()
        with self.assertRaises(FileNotExecuted):
            self.tools.perform({"action": "create_file", "path": str(path), "text": "replacement"})
        self.assertEqual(path.read_bytes(), b"one\r\ntwo\r\n")

    def test_new_file_is_written_with_actual_receipt(self):
        path = self.work / "new.md"
        result = self.tools.perform({"action": "create_file", "path": str(path), "text": "hello"})
        self.assertEqual(result["status"], "CREATED")
        self.assertEqual(path.read_bytes(), b"hello")
        self.assertTrue(result["saved"])

    def test_weather_question_does_not_create_a_file(self):
        tools = DailyTools(self.settings, "明天东京的天气如何？是否晴天。", store=self.store)
        path = self.work / "weather.md"
        result = tools.perform({"action": "create_file", "path": str(path), "text": "weather"})
        self.assertEqual(result["status"], "NOT_EXECUTED")
        self.assertFalse(path.exists())

    def test_requested_output_path_allows_file_creation(self):
        path = self.work / "requested.md"
        tools = DailyTools(self.settings, "整理天气", output_path=str(path), store=self.store)
        result = tools.perform({"action": "create_file", "path": str(path), "text": "weather"})
        self.assertEqual(result["status"], "CREATED")

    def test_old_file_request_does_not_authorize_new_weather_question(self):
        tools = DailyTools(self.settings, "之前请创建文件\n明天东京的天气如何？", store=self.store,
                           user_message="明天东京的天气如何？", file_request_text="明天东京的天气如何？")
        path = self.work / "weather.md"
        result = tools.perform({"action": "create_file", "path": str(path), "text": "weather"})
        self.assertEqual(result["status"], "NOT_EXECUTED")
        self.assertFalse(path.exists())

    def test_negative_instruction_overrides_output_path(self):
        path = self.work / "cancelled.md"
        tools = DailyTools(self.settings, "不要写文件", output_path=str(path), store=self.store)
        result = tools.perform({"action": "create_file", "path": str(path), "text": "weather"})
        self.assertEqual(result["status"], "NOT_EXECUTED")
        self.assertFalse(path.exists())

    def test_original_file_intent_is_available_without_reading_target(self):
        identity = self.start()
        action = {"action": "create_file", "path": "C:/example/weather.md", "text": "original proposed content"}
        self.store.put("runtime", "receipt-test", {"kind": "SECRETARY_TOOL", "runId": identity,
            "state": "UNKNOWN", "action": action}, 0)
        result = self.journal.file_intents(identity)
        self.assertEqual(result["files"][0]["path"], action["path"])
        self.assertEqual(result["files"][0]["text"], action["text"])
        self.assertEqual(result["files"][0]["state"], "UNKNOWN")

    def test_saved_model_interactions_are_bounded_and_exclude_binding(self):
        identity = self.start()
        run = self.journal.get(identity)
        self.journal.save(run, history=[{"role": "user", "content": "weather?"},
            {"type": "function_call", "name": "search", "call_id": "call-1", "arguments": '{"query":"weather"}'},
            {"type": "function_call_output", "call_id": "call-1", "output": "x" * 7000}])
        self.store.put("runtime", "receipt-interaction", {"kind": "SECRETARY_TOOL", "runId": identity,
            "callId": "call-1", "state": "SUCCEEDED", "action": {"action": "search"}}, 0)
        result = self.journal.interaction_history(identity)
        self.assertEqual([item["kind"] for item in result["items"]], ["message", "function_call", "function_call_output"])
        self.assertEqual(result["items"][1]["receiptState"], "SUCCEEDED")
        self.assertEqual(len(result["items"][2]["text"]), 6000)
        self.assertTrue(result["truncated"])
        self.assertNotIn("binding", json.dumps(result))

    def test_draft_preserves_original_bom_and_newlines(self):
        path = self.work / "note.md"
        original = codecs.BOM_UTF8 + b"one\r\ntwo\r\n"
        path.write_bytes(original)
        result = self.edit(path, self.read(path)["version"])
        self.assertEqual(path.read_bytes(), original)
        self.assertEqual(Path(result["path"]).read_bytes(), codecs.BOM_UTF8 + b"one\r\nthree\r\n")
        self.assertEqual(result["status"], "DRAFT_READY")
        self.assertFalse(result["saved"])
        diff = operation_diff(self.store, self.settings.directory, "file-op:" + result["operationId"])
        self.assertIn("-two", diff["diff"])
        self.assertIn("+three", diff["diff"])

    def test_changed_source_rejects_edit(self):
        path = self.file()
        version = self.read(path)["version"]
        path.write_text("human update", encoding="utf-8")
        with self.assertRaises(FileNotExecuted):
            self.edit(path, version)
        self.assertEqual(path.read_text(), "human update")

    def test_partial_read_cannot_replace_whole_document(self):
        path = self.file(text="a" * 31000)
        read = self.read(path)
        self.assertTrue(read["truncated"])
        with self.assertRaises(FileNotExecuted):
            self.tools.perform({"action": "write_file", "path": str(path), "text": "lost remainder", "expectedVersion": read["version"]})
        self.assertEqual(len(path.read_bytes()), 31000)

    def test_ambiguous_replacement_is_rejected(self):
        path = self.file(text="two two")
        with self.assertRaises(FileNotExecuted):
            self.edit(path, self.read(path)["version"])

    def test_overlapping_replacements_are_rejected(self):
        path = self.file(text="abcd")
        read = self.read(path)
        with self.assertRaises(FileNotExecuted):
            self.tools.perform({"action": "prepare_edit", "path": str(path), "expectedVersion": read["version"],
                "edits": [{"old": "ab", "new": "x"}, {"old": "bc", "new": "y"}]})

    def test_empty_file_can_have_a_revision_copy(self):
        path = self.file(text="")
        result = self.edit(path, self.read(path)["version"], "", "new")
        self.assertEqual(path.read_bytes(), b"")
        self.assertEqual(Path(result["path"]).read_bytes(), b"new")

    def test_writeback_is_explicitly_unavailable(self):
        with self.assertRaises(FileNotExecuted):
            self.tools.perform({"action": "apply_edit", "editId": "unknown", "expectedVersion": "v"})

    def test_restore_will_not_cover_later_human_change(self):
        path = self.file()
        read = self.read(path)
        result = self.edit(path, read["version"])
        path.write_text("later human work", encoding="utf-8")
        with self.assertRaises(ValueError):
            self.tools.perform({"action": "restore_file", "operationId": result["operationId"], "expectedCurrentVersion": read["version"]})
        self.assertEqual(path.read_text(), "later human work")

    def test_exclusive_publish_race_preserves_other_writer(self):
        path = self.work / "new.md"
        import os
        link = os.link
        def competing_link(source, target):
            Path(target).write_bytes(b"other writer")
            return link(source, target)
        with patch("secretary_files.os.link", competing_link), self.assertRaises(FileNotExecuted):
            self.tools.perform({"action": "create_file", "path": str(path), "text": "ours"})
        self.assertEqual(path.read_bytes(), b"other writer")
        self.assertEqual([r for r in self.store.list("decision") if r.get("kind") == "FILE_OPERATION"][0]["state"], "NOT_EXECUTED")

    def test_same_effect_is_not_executed_twice(self):
        identity, calls = self.start(), []
        action = {"action": "create_file", "path": "target", "text": "value"}
        execute = lambda request: calls.append(request) or {"status": "CREATED"}
        first = self.journal.invoke(identity, action, execute)
        self.assertEqual(self.journal.invoke(identity, action, execute), first)
        self.assertEqual(len(calls), 1)

    def test_unknown_effect_blocks_further_work(self):
        identity, calls = self.start(), []
        def unknown(_action):
            calls.append(1)
            raise OSError("lost receipt")
        action = {"action": "create_file", "path": "target", "text": "value"}
        with self.assertRaises(OSError):
            self.journal.invoke(identity, action, unknown)
        with self.assertRaises(ValueError):
            self.journal.invoke(identity, action, unknown)
        self.assertEqual(len(calls), 1)

    def test_pause_is_queued_then_applied_at_boundary(self):
        identity = self.start()
        self.journal.count(identity, "modelCalls", 12)
        result = self.journal.control({"runId": identity, "revision": self.journal.get(identity)["revision"], "control": "pause", "requestId": "pause"})
        self.assertEqual(result["state"], "QUEUED")
        self.assertEqual(self.journal.get(identity)["state"], "PAUSE_REQUESTED")
        with self.assertRaises(RunYield) as caught:
            self.journal.check(identity)
        self.assertEqual(caught.exception.reason, "pause")
        self.journal.finish(identity, "PAUSED")
        self.journal.start(self.job, self.settings.value)
        self.assertEqual(self.journal.get(identity)["modelCalls"], 1)

    def test_control_replay_is_idempotent(self):
        identity = self.start()
        data = {"runId": identity, "revision": self.journal.get(identity)["revision"], "control": "follow-up", "text": "later", "requestId": "same"}
        self.journal.control(data)
        sequence = self.journal.get(identity)["sequence"]
        self.journal.control(data)
        self.assertEqual(sequence, self.journal.get(identity)["sequence"])
        with self.assertRaises(ValueError):
            self.journal.control({**data, "text": "different"})

    def test_steering_is_not_a_follow_up(self):
        identity = self.start()
        self.journal.control({"runId": identity, "revision": self.journal.get(identity)["revision"], "control": "follow-up", "text": "later", "requestId": "later"})
        self.journal.check(identity)
        self.assertEqual(self.journal.get(identity)["notes"], [])
        self.journal.control({"runId": identity, "revision": self.journal.get(identity)["revision"], "control": "steer", "text": "change current", "requestId": "now"})
        with self.assertRaises(RunYield) as caught:
            self.journal.check(identity)
        self.assertEqual(caught.exception.reason, "steer")
        self.assertEqual(self.journal.get(identity)["notes"], ["change current"])

    def test_settings_change_does_not_switch_live_account(self):
        identity = self.start()
        self.settings.value["model"] = "another"
        with self.assertRaises(ValueError):
            self.journal.check(identity)

    def test_restart_marks_active_run_for_reconciliation(self):
        identity = self.start()
        restarted = RunJournal(self.runtime)
        self.assertEqual(restarted.get(identity)["state"], "RECONCILING")
        with self.assertRaises(ValueError):
            restarted.start(self.job, self.settings.value)

    def test_events_drop_private_payloads(self):
        identity = self.start()
        self.journal.event(identity, "model-start", prompt="private", token="secret", status="started")
        event = self.journal.events(identity)[-1]
        self.assertNotIn("prompt", event)
        self.assertNotIn("token", event)

    def test_v1_upgrade_retains_data_and_creates_private_backup(self):
        self.store.db.execute("PRAGMA user_version=1")
        self.store.db.commit()
        root = self.root / "private"
        self.store.close()
        reopened = SQLiteStore(root, "owner", "secretary")
        self.addCleanup(reopened.close)
        self.assertEqual(reopened.get("work", "work")["title"], "test")
        self.assertEqual(reopened.db.execute("PRAGMA user_version").fetchone()[0], 2)
        self.assertEqual(len(list(reopened.path.parent.glob("personal-v1-before-fd084-*.sqlite"))), 1)

    def test_live_old_service_is_not_upgraded_before_lock(self):
        self.store.db.execute("PRAGMA user_version=1")
        self.store.db.commit()
        process = PrivateProcessLock(self.store.path.parent / "service.lock")
        process.acquire()
        try:
            with self.assertRaises(ValueError):
                SQLiteStore(self.root / "private", "owner", "secretary")
            self.assertEqual(self.store.db.execute("PRAGMA user_version").fetchone()[0], 1)
            self.assertEqual(list(self.store.path.parent.glob("personal-v1-before-fd084-*.sqlite")), [])
        finally:
            process.release()

    def test_waiting_read_receipt_is_the_actual_original_call(self):
        from assistant_tools import ConfirmationRequired
        identity = self.start()
        action = {"action": "read_file", "path": "unapproved"}
        def waiting(_action):
            raise ConfirmationRequired("confirm", action)
        with self.assertRaises(ConfirmationRequired):
            self.journal.invoke(identity, action, waiting)
        key = self.journal.waiting_receipt(identity, action)
        self.assertEqual(self.store.get("runtime", key)["state"], "WAITING")
        with self.assertRaises(RunYield):
            self.journal.check(identity)
        self.journal.admitted(key, lambda: {"text": "approved result"})
        self.assertEqual(self.store.get("runtime", key)["result"]["text"], "approved result")

    def test_approved_file_result_replaces_waiting_model_output(self):
        from assistant_tools import ConfirmationRequired
        identity = self.start()
        action = {"action": "create_file", "path": str(self.work / "approved.md"), "text": "draft"}
        self.journal.save(self.journal.get(identity), history=[
            {"type": "function_call", "name": "create_file", "call_id": "file-call", "arguments": json.dumps(action)},
            {"type": "function_call_output", "call_id": "file-call", "output": '{"status":"WAITING_USER"}'},
        ])
        def waiting(_action):
            raise ConfirmationRequired("confirm", action)
        with self.assertRaises(ConfirmationRequired):
            self.journal.invoke(identity, action, waiting, call_id="file-call")
        key = self.journal.waiting_receipt(identity, action)
        result = self.journal.admitted(key, lambda: {"status": "CREATED", "path": action["path"]})
        self.journal.record_approved_output(identity, key, result)
        outputs = [item for item in self.journal.get(identity)["history"] if item.get("type") == "function_call_output"]
        self.assertEqual(len(outputs), 1)
        self.assertEqual(json.loads(outputs[0]["output"]), result)
        self.assertFalse(self.journal.get(identity)["hasWaitingTool"])

    def test_waiting_receipt_matches_exact_file_request(self):
        identity = self.start()
        first = {"action": "create_file", "path": "first.md", "text": "one"}
        second = {"action": "create_file", "path": "second.md", "text": "two"}
        for ordinal, action in enumerate((first, second), 1):
            self.store.put("runtime", "waiting-" + str(ordinal), {"kind": "SECRETARY_TOOL", "runId": identity,
                "state": "WAITING", "action": action, "ordinal": ordinal}, 0)
        self.assertEqual(self.journal.waiting_receipt(identity, first), "waiting-1")


class ConnectionCase(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.path = Path(self.temporary.name)
        def read(path, default=None):
            return json.loads(Path(path).read_text()) if Path(path).exists() else default
        def write(path, value):
            Path(path).write_text(json.dumps(value))
        replacement = patch.multiple("secretary_credentials", read_private=read, write_private=write)
        replacement.start()
        self.addCleanup(replacement.stop)
        self.connections = ModelConnections(self.path)

    def oauth_record(self):
        with self.connections.locked() as value:
            value["connections"] = [{"id": "test", "version": 1, "generation": 1, "kind": "chatgpt", "status": "READY", "account": "account",
                "subject": "sub", "clientId": "issued", "accessToken": "old", "refreshToken": "old-refresh", "scope": SCOPES, "expiresAt": time.time() + 10}]
            import secretary_credentials
            secretary_credentials.write_private(self.connections.path, value)

    def test_connection_public_state_has_no_key(self):
        self.connections.create_api_key("secret", "https://api.openai.com/v1")
        self.assertNotIn("secret", json.dumps(self.connections.public()))

    def test_disconnect_invalidates_exact_reference(self):
        connection = self.connections.create_api_key("secret", "https://api.openai.com/v1")
        self.connections.disconnect(connection["id"], 1)
        with self.assertRaises(ModelAuthRequired):
            self.connections.access(connection["id"], 1, "api-key")

    def test_refresh_replacement_is_reused_by_another_instance(self):
        self.oauth_record()
        def refreshed(record):
            return {**record, "accessToken": "next", "refreshToken": "next-refresh", "expiresAt": time.time() + 3600}
        with patch("secretary_oauth.refresh", side_effect=refreshed) as refresh:
            self.assertEqual(self.connections.access("test", 1, "chatgpt")["token"], "next")
            self.assertEqual(ModelConnections(self.path).access("test", 1, "chatgpt")["token"], "next")
        self.assertEqual(refresh.call_count, 1)

    def test_unknown_refresh_is_not_repeated(self):
        self.oauth_record()
        with patch("secretary_oauth.refresh", side_effect=OSError("lost response")) as refresh:
            with self.assertRaises(ModelAuthRequired):
                self.connections.access("test", 1, "chatgpt")
            with self.assertRaises(ModelAuthRequired):
                ModelConnections(self.path).access("test", 1, "chatgpt")
        self.assertEqual(refresh.call_count, 1)

    def test_other_coordinator_cannot_refresh_same_rotating_token(self):
        self.oauth_record()
        entered, release, outcome = threading.Event(), threading.Event(), []
        def refreshed(record):
            entered.set()
            if not release.wait(1):
                raise OSError("test deadline")
            return {**record, "accessToken": "next", "refreshToken": "next-refresh", "expiresAt": time.time() + 3600}
        def first():
            try:
                outcome.append(self.connections.access("test", 1, "chatgpt"))
            except Exception as error:
                outcome.append(error)
        with patch("secretary_oauth.refresh", side_effect=refreshed) as refresh:
            thread = threading.Thread(target=first, daemon=True)
            thread.start()
            try:
                self.assertTrue(entered.wait(1))
                with self.assertRaises(ValueError):
                    ModelConnections(self.path).access("test", 1, "chatgpt")
            finally:
                release.set()
                thread.join(2)
        self.assertFalse(thread.is_alive())
        self.assertEqual(refresh.call_count, 1)
        self.assertEqual(outcome[0]["token"], "next")

    def test_account_change_during_refresh_is_rejected(self):
        self.oauth_record()
        with patch("secretary_oauth.refresh", side_effect=lambda record: {**record, "account": "other"}):
            with self.assertRaises(ModelAuthRequired):
                self.connections.access("test", 1, "chatgpt")

    def test_import_does_not_accept_foreign_tokens(self):
        with self.assertRaises(ValueError):
            self.connections.import_metadata({"registration": {"issuer": "https://auth.openai.com", "client_id": "issued", "access_token": "do not import"}})
        result = self.connections.import_metadata({"registration": {"issuer": "https://auth.openai.com", "client_id": "issued"}})
        self.assertTrue(result["requiresLogin"])
        self.assertEqual(self.connections.public()["connections"], [])


class KnowledgeCase(unittest.TestCase):
    def setUp(self):
        LocalCase.setUp(self)
        class Client:
            def __init__(self):
                self.created, self.searched, self.lose_response, self.records = 0, 0, False, {}
            def creation(self, key):
                if key not in self.records:
                    raise KnowledgeError("not found", "CREATION_NOT_FOUND")
                return self.records[key]
            def create_note(self, key, body):
                self.created += 1
                self.records[key] = {"state": "succeeded", "indexState": "pending", "documentId": "note", "url": "https://kb.example/notes/one"}
                if self.lose_response:
                    raise KnowledgeError("response lost", "UNAVAILABLE")
                return self.records[key]
            def search(self, query, limit):
                self.searched += 1
                return {"items": [], "complete": True, "hasMore": False, "diagnostics": []}
        self.client = Client()
        self.settings.value.update(knowledgeEnabled=True, knowledgeServiceId="kb", knowledgeCapabilities=["search", "read", "create_note", "creation_status"])
        self.settings.knowledge_available = lambda: True
        self.settings.knowledge_client = lambda: self.client
        self.settings.knowledge_connection = lambda: (self.client, {"knowledgeServiceId": self.settings.value.get("knowledgeServiceId"),
            "knowledgeBaseUrl": "https://kb.example", "knowledgeCredentialRef": self.settings.value.get("knowledgeCredentialRef", "original"),
            "credentialDigest": self.settings.value.get("testCredentialDigest", "original-digest")})
        self.settings.trusted_knowledge_url = lambda url: url if url.startswith("https://kb.example/") else None
        self.tools.user_message = "请保存到知识库"
        self.action = {"action": "knowledge_save_note", "requestId": "one", "title": "note", "content": "actual note"}

    def test_same_note_identity_does_not_create_twice(self):
        first = self.tools.perform(self.action)
        second = self.tools.perform(self.action)
        self.assertEqual(first["state"], "succeeded")
        self.assertEqual(second["documentId"], "note")
        self.assertEqual(self.client.created, 1)

    def test_lost_note_response_is_reconciled_without_second_creation(self):
        self.client.lose_response = True
        with self.assertRaises(KnowledgeError):
            self.tools.perform(self.action)
        result = self.tools.perform({"action": "knowledge_note_status", "requestId": "one"})
        self.assertEqual(result["state"], "succeeded")
        self.assertEqual(self.client.created, 1)

    def test_service_change_does_not_replay_note(self):
        self.client.lose_response = True
        with self.assertRaises(KnowledgeError):
            self.tools.perform(self.action)
        self.settings.value["knowledgeServiceId"] = "other-kb"
        with self.assertRaises(KnowledgeError):
            self.tools.perform({"action": "knowledge_note_status", "requestId": "one"})
        self.assertEqual(self.client.created, 1)

    def test_explaining_save_or_saving_a_file_is_not_note_authority(self):
        for text in ("请解释如何保存到知识库", "请保存到文件 note.md", "请保存到知识库\n不要保存了"):
            self.tools.user_message = text
            with self.subTest(text=text), self.assertRaises(KnowledgeError):
                self.tools.perform(self.action)
        self.assertEqual(self.client.created, 0)

    def test_knowledge_budget_survives_host_pause_and_resume(self):
        def decision(row, **changes):
            return self.store.put("decision", row["id"], {**{key: value for key, value in row.items() if key not in {"id", "revision"}}, **changes}, row["revision"])
        daily = SimpleNamespace(runtime=self.runtime, store=self.store, _decision=decision)
        work = self.store.get("work", "work")
        host = SecretaryHost(daily, work, self.job, self.tools, SimpleNamespace())
        host.perform({"action": "knowledge_search", "query": "one"})
        self.journal.finish(host.identity, "PAUSED")
        new_tools = DailyTools(self.settings, "request", store=self.store)
        resumed = SecretaryHost(daily, work, host.job, new_tools, SimpleNamespace())
        with self.assertRaises(KnowledgeError):
            resumed.perform({"action": "knowledge_search", "query": "two"})
        self.assertEqual(self.client.searched, 1)


class ProtocolCase(unittest.TestCase):
    def test_duplicate_and_nonfinite_arguments_are_rejected(self):
        for text in ('{"x":1,"x":2}', '{"x":NaN}', '{"x":1e999}'):
            with self.subTest(text=text), self.assertRaises(ValueError):
                object_json(text)

    def test_tool_calls_are_paired_on_interruption(self):
        history = [{"type": "function_call", "call_id": "one", "name": "create_file"}]
        result = paired_history(history)
        self.assertEqual(result[1]["call_id"], "one")
        self.assertEqual(json.loads(result[1]["output"])["status"], "NOT_EXECUTED")
        self.assertEqual(paired_history(result), result)

    def test_unknown_effect_is_not_labelled_unexecuted(self):
        history = [{"type": "function_call", "call_id": "one", "name": "create_file"}]
        result = paired_history(history, {"one": {"status": "UNKNOWN"}})
        self.assertEqual(json.loads(result[-1]["output"])["status"], "UNKNOWN")

    def test_large_result_projection_retains_a_source_reference(self):
        history = [{"type": "function_call_output", "call_id": "one", "output": "x" * 20000}]
        value = json.loads(project(history, "run")[0]["output"])
        self.assertTrue(value["truncated"])
        self.assertEqual(value["sourceId"], "run:source:one")
        self.assertEqual(len(history[0]["output"]), 20000)

    def test_incomplete_stream_has_no_success_result(self):
        stream = io.BytesIO(b'data: {"type":"response.output_text.delta","delta":"proposed tool"}\n\n')
        with self.assertRaises(ValueError):
            list(response_events(stream, lambda: None))

    def test_stream_deadline_includes_partial_lines(self):
        with patch("secretary_sdk.time.monotonic", side_effect=[0, 1, 121]):
            with self.assertRaises(ValueError):
                list(response_events(io.BytesIO(b"data: partial"), lambda: None))

    def test_pending_note_cannot_be_completed(self):
        with self.assertRaises(ValueError):
            validate_completion("save note", "done", [{"action": {"action": "knowledge_save_note"}, "result": {"state": "pending"}}])

    def test_saved_but_not_indexed_note_is_not_searchable(self):
        with self.assertRaises(ValueError):
            validate_completion("save note", "已可搜索", [{"action": {"action": "knowledge_save_note"}, "result": {"state": "succeeded", "indexState": "pending"}}])

    def test_draft_does_not_prove_original_was_updated(self):
        with self.assertRaises(ValueError):
            validate_completion("edit", "原文件已修改", [{"action": {"action": "prepare_edit"}, "result": {"status": "DRAFT_READY"}}])

    def test_oidc_uses_rs256_and_required_identity_claims(self):
        decode_calls = []
        fake = SimpleNamespace(get_unverified_header=lambda token: {"alg": "RS256", "kid": "key"},
            PyJWK=SimpleNamespace(from_dict=lambda key: SimpleNamespace(key=SimpleNamespace(key_size=2048))),
            decode=lambda *args, **kwargs: decode_calls.append(kwargs) or {"sub": "subject"})
        with patch.dict(sys.modules, {"jwt": fake}):
            verify("token", "client", [{"kid": "key", "kty": "RSA"}])
        self.assertEqual(decode_calls[0]["algorithms"], ["RS256"])
        self.assertEqual(decode_calls[0]["issuer"], "https://auth.openai.com")
        self.assertIn("exp", decode_calls[0]["options"]["require"])

    def test_token_without_plan_scope_is_rejected(self):
        identity = {"sub": "sub", "nonce": "nonce"}
        access = {"sub": "sub", "client_id": "client", "scope": SCOPES, "exp": time.time() + 3600}
        with patch("secretary_oauth.request_json", side_effect=[{"issuer": "https://auth.openai.com", "jwks_uri": "https://auth.openai.com/keys"}, {"keys": []}]), \
             patch("secretary_oauth.verify", side_effect=[identity, access]):
            with self.assertRaises(ValueError):
                validate({"client_id": "client", "token_type": "Bearer", "id_token": "id", "access_token": "access", "refresh_token": "refresh", "scope": "openid"}, nonce="nonce")


if __name__ == "__main__":
    unittest.main()
