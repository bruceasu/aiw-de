"""Offline file-tool source cases. No SDK, server, shell command or model request."""
import hashlib
from pathlib import Path
import tempfile
import threading
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch
import json

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

    def test_plain_directory_question_retains_tree_and_rejects_changed_resume(self):
        from formal_questions import ask, verify_checkpoints
        from plan_workspace import PlanWorkspace, input_manifests
        broker=self.broker
        broker.task={"taskId":"task-1","attemptId":"attempt-1","planId":"plan-1","location":str(self.root)}
        broker.lease={"workerId":"worker-1","attemptId":"attempt-1","leaseToken":"token"}
        broker.base="/tasks/task-1";broker.pending_question=None;broker.repository="workspace"
        broker.context={"configurationDigest":"a"*64,"snapshot":{"task":{"baseRevision":None}}}
        broker.attempt.check=Mock()
        broker.workspace=PlanWorkspace(self.root,self.root,broker.attempt,Mock(),Mock())
        broker.checkpoint=lambda: broker.workspace.finalize(broker.task)
        receipt={"question":{"questionId":"Q-1"},"yield":{}}
        broker.client=Mock();broker.client._post.side_effect=[{}, {"kind":"LEGACY_PLAN_TASK"}, receipt]
        (self.root/"report.txt").write_text("Existing result",encoding="utf-8")
        ask(broker,{"question":"Which format?","context":"","options":[],"answerRequirements":""})
        payload=broker.client._post.call_args_list[-1].args[1]
        checkpoint=payload["checkpoint"]
        self.assertNotIn("resultRevision",checkpoint)
        raw=json.dumps(checkpoint,separators=(",",":"))
        ref={"kind":"checkpoint","id":"task-1","revision":checkpoint["handoff"]["sha256"],"sha256":hashlib.sha256(raw.encode()).hexdigest()}
        resumed=SimpleNamespace(task={},context={"snapshot":{"references":[ref]}},lease={},base="/tasks/next",client=Mock())
        resumed.client._post.return_value=[{"reference":ref,"checkpoint":checkpoint,"checkpointJson":raw,"attemptId":"attempt-1"}]
        verify_checkpoints(resumed)
        with patch("worker_runtime.current_attempt",return_value=broker.attempt):
            self.assertEqual(str(self.root),input_manifests(resumed.task)[0]["workingDirectory"])
            (self.root/"report.txt").write_text("Changed",encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError,"HANDOFF_CHECKPOINT_TREE_CHANGED"):
                input_manifests(resumed.task)

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
