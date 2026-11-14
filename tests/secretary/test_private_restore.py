"""Offline regression cases; no services, credentials or models are used."""
import copy
from pathlib import Path
import sys
import tempfile
import threading
import unittest
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "personal-secretary"))
from store import SQLiteStore
from conversations import Conversations


class PrivateRestoreTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.source = SQLiteStore(Path(self.directory.name), "owner-a", "worker-a")
        self.target = SQLiteStore(Path(self.directory.name), "owner-b", "worker-b")
        self.addCleanup(self.directory.cleanup)
        self.addCleanup(self.source.close)
        self.addCleanup(self.target.close)
        self.source.put("work", "original", {"title": "Trip", "body": "Check weather", "state": "SCHEDULED",
            "dueMs": 1, "planId": "old-plan", "outputPath": "old-file"}, 0)
        self.source.put("message", "message", {"workId": "original", "text": "Tomorrow?", "reply": "Checking",
            "state": "PENDING", "acceptedBinding": {"connectionId": "old-connection"}, "createdAt": 1}, 0)
        self.source.put("memory", "memory", {"text": "Prefers concise replies", "sourceWorkId": "original",
            "sourceMessageIds": ["message"], "sourceTitle": "Trip"}, 0)
        self.source.put("policy", "policy", {"enabled": True}, 0)
        self.source.queue("old-request", "/actions", {"requestId": "old-request"})

    def test_restore_copies_text_and_sources_without_replaying_work(self):
        self.target.put("memory", "existing", {"text": "Keep me"}, 0)
        package = self.source.backup()
        result = self.target.restore_backup(package)
        self.assertEqual(3, result["restored"])
        work = self.target.list("work")[0]
        self.assertEqual("TRASHED", work["state"])
        self.assertEqual("Check weather", work["body"])
        for field in ("dueMs", "planId", "outputPath"):
            self.assertNotIn(field, work)
        message = self.target.list("message")[0]
        self.assertEqual("RESTORED", message["state"])
        self.assertNotIn("acceptedBinding", message)
        imported = next(row for row in self.target.list("memory") if row["id"] != "existing")
        self.assertEqual(work["id"], imported["sourceWorkId"])
        self.assertEqual([message["id"]], imported["sourceMessageIds"])
        self.assertEqual([], self.target.list("policy"))
        self.assertEqual([], self.target.pending())
        self.assertEqual("owner-b", self.target.backup()["ownerId"])
        self.assertTrue(self.target.restore_backup(package)["duplicate"])
        self.assertEqual(2, len(self.target.list("memory")))
        self.assertEqual(1, len(self.target.backup()["archivedBackups"]))
        # A later backup remains portable with flat, inactive original copies.
        self.source.restore_backup(self.target.backup())
        self.assertTrue(all("archivedBackups" not in item for item in self.source.backup()["archivedBackups"]))

    def test_invalid_backup_leaves_no_partial_records_or_archive(self):
        package = copy.deepcopy(self.source.backup())
        package["records"]["memory"][0]["text"] = None
        with self.assertRaises(ValueError):
            self.target.restore_backup(package)
        self.assertEqual([], self.target.list("work"))
        self.assertEqual([], self.target.backup()["archivedBackups"])

    def test_archived_copy_does_not_claim_to_have_restored_its_records(self):
        original = self.source.backup()
        archived = {key: value for key, value in original.items() if key != "archivedBackups"}
        carrier = {"schema": "aiw.personal.backup.v1", "records": {}, "archivedBackups": [archived]}
        self.target.restore_backup(carrier)
        self.assertEqual([], self.target.list("work"))
        restored = self.target.restore_backup(original)
        self.assertFalse(restored["duplicate"])
        self.assertEqual(3, restored["restored"])
        self.assertTrue(self.target.restore_backup(original)["duplicate"])
        self.assertEqual(1, len(self.target.list("work")))

    def test_removed_memory_is_not_recalled_and_purge_requires_confirmation(self):
        daily = SimpleNamespace(lock=threading.RLock(), busy=set())
        conversations = Conversations(SimpleNamespace(store=self.source, daily=daily))
        self.assertTrue(conversations.context({"title": "replies"}, "concise")["relevantMemories"])
        removed = conversations.delete_memory("memory", 1)
        self.assertEqual([], conversations.context({"title": "replies"}, "concise")["relevantMemories"])
        restored = conversations.change_memory("memory", removed["revision"], "restore")
        edited = conversations.change_memory("memory", restored["revision"], "edit", "Prefers detailed replies")
        self.assertEqual("original", edited["sourceWorkId"])
        with self.assertRaises(ValueError):
            conversations.change_memory("memory", restored["revision"], "edit", "Stale")
        removed = conversations.delete_memory("memory", edited["revision"])
        with self.assertRaises(ValueError):
            conversations.change_memory("memory", removed["revision"], "purge")
        conversations.change_memory("memory", removed["revision"], "purge", confirmed=True)
        self.assertIsNone(self.source.get("memory", "memory"))
        self.assertTrue(all(row["data"] == '{"purged":true}' for row in self.source.history("memory", "memory")))
        fresh = self.source.put("memory", "memory", {"text": "New fact"}, 0)
        self.assertGreater(fresh["revision"], removed["revision"])

    def test_deleted_draft_can_be_restored_or_erased_without_revision_reuse(self):
        self.target.put("draft", "draft", {"title": "Private note", "text": "Keep the original"}, 0)
        self.target.delete_draft("draft", 1)
        tombstone = self.target.deleted_drafts()[0]
        self.assertEqual("Private note", tombstone["title"])
        restored = self.target.change_deleted_draft("draft", tombstone["revision"], "restore")
        self.assertEqual("Keep the original", restored["text"])
        self.target.delete_draft("draft", restored["revision"])
        tombstone = self.target.deleted_drafts()[0]
        self.target.change_deleted_draft("draft", tombstone["revision"], "purge", True)
        self.assertEqual([], self.target.deleted_drafts())
        self.assertNotIn("Keep the original", str(self.target.history("draft", "draft")))
        fresh = self.target.put("draft", "draft", {"text": "New"}, 0)
        self.assertGreater(fresh["revision"], restored["revision"])
