"""Offline file-tool source cases. No SDK, server, shell command or model request."""
import hashlib
from pathlib import Path
import tempfile
import threading
from types import SimpleNamespace
import unittest
from unittest.mock import Mock

from execution_guard import GuardBroker


class WorkspaceFilesTest(unittest.TestCase):
    def setUp(self):
        directory=tempfile.TemporaryDirectory();self.addCleanup(directory.cleanup)
        self.root=Path(directory.name).resolve()
        self.broker=GuardBroker.__new__(GuardBroker)
        self.broker.root=self.root;self.broker.source_root=self.root
        self.broker.context={"originalPolicy":{"riskStrategyVersion":1}}
        self.broker.file_changes={};self.broker.created_paths=set()
        self.broker.lock=threading.RLock();self.broker.parallel_reads=False;self.broker.writable=True
        self.broker.attempt=SimpleNamespace(resource_uncertain=False,evidence=Mock())
        self.broker.begin=Mock(return_value="operation");self.broker.finish=Mock()
        self.broker._request_approval=Mock(return_value={"waiting":True})

    def test_plain_directory_can_write_delete_and_restore_without_git(self):
        self.broker._invoke("write",{"path":"report.txt","content":"Report","expectedDigest":"ABSENT"})
        digest=hashlib.sha256(b"Report").hexdigest()
        deleted=self.broker._invoke("delete",{"path":"report.txt","expectedDigest":digest})
        self.assertFalse((self.root/"report.txt").exists())
        self.assertEqual(b"Report",(self.root/".ai"/"trash"/deleted["trashId"]/"content").read_bytes())
        self.broker._invoke("restore",{"path":"report.txt","trashId":deleted["trashId"]})
        self.assertEqual("Report",(self.root/"report.txt").read_text())
        self.broker._request_approval.assert_not_called()

    def test_sdk_write_schema_still_requests_approval_for_a_large_replacement(self):
        original=b"a"*70000;(self.root/"large.txt").write_bytes(original)
        result=self.broker._invoke("write",{"path":"large.txt","content":"new","expectedDigest":hashlib.sha256(original).hexdigest()})
        self.assertEqual({"waiting":True},result)
        self.broker._request_approval.assert_called_once()
        self.assertEqual(original,(self.root/"large.txt").read_bytes())

    def test_exact_small_edit_is_autonomous_and_stale_digest_is_rejected(self):
        (self.root/"note.txt").write_text("before")
        expected=hashlib.sha256(b"before").hexdigest()
        self.broker._invoke("write",{"path":"note.txt","content":"after","expectedDigest":expected})
        with self.assertRaisesRegex(RuntimeError,"GUARD_FILE_VERSION_CONFLICT"):
            self.broker._invoke("write",{"path":"note.txt","content":"stale","expectedDigest":expected})
        self.assertEqual("after",(self.root/"note.txt").read_text())


if __name__=="__main__":
    unittest.main()
