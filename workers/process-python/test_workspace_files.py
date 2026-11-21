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
from tool_coordination import ToolCoordinator


class WorkspaceFilesTest(unittest.TestCase):
    def setUp(self):
        directory=tempfile.TemporaryDirectory();self.addCleanup(directory.cleanup)
        self.root=Path(directory.name).resolve()
        self.broker=GuardBroker.__new__(GuardBroker)
        self.broker.root=self.root;self.broker.source_root=self.root
        self.broker.context={"originalPolicy":{"riskStrategyVersion":1}}
        self.broker.file_changes={};self.broker.created_paths=set()
        self.broker.lock=threading.RLock();self.broker.parallel_reads=False;self.broker.writable=True
        self.broker.attempt=SimpleNamespace(resource_uncertain=False,evidence=Mock(),check=Mock())
        self.broker.tool_coordinator=ToolCoordinator(self.broker.attempt);self.broker.accepting_tools=True
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

    def test_git_status_in_plain_directory_leaves_file_tools_available(self):
        self.broker.attempt.check = Mock()
        self.broker.git = Mock()
        result = self.broker._invoke("git_status", {})
        self.assertEqual("GUARD_GIT_REPOSITORY_REQUIRED", result["error"])
        self.broker.git.run.assert_not_called()
        self.broker.begin.assert_not_called()
        self.broker._invoke("write", {"path": "result.txt", "content": "Done", "expectedDigest": "ABSENT"})
        self.assertEqual("Done", (self.root / "result.txt").read_text())

    def test_plain_directory_advertises_file_tools_without_git(self):
        from guarded_app_server import definitions
        self.broker.shell_enabled = Mock(return_value=False)
        tools = {definition["name"] for definition in definitions(self.broker)}
        self.assertIn("read", tools)
        self.assertIn("write", tools)
        self.assertIn("edit", tools)
        self.assertIn("edit_many", tools)
        self.assertFalse(any(name.startswith("git_") for name in tools))
        (self.root / ".git").write_text("gitdir: retained-worktree", encoding="utf-8")
        self.assertIn("git_status", {definition["name"] for definition in definitions(self.broker)})

    def test_git_inspection_distinguishes_failure_from_unknown_process_outcome(self):
        (self.root / ".git").mkdir()
        self.broker.attempt.check = Mock()
        self.broker.git = Mock()
        self.broker.git.run.side_effect = RuntimeError("GUARD_GIT_OPERATION_FAILED")
        result = self.broker._invoke("git_diff", {})
        self.assertEqual("GUARD_GIT_INSPECTION_FAILED", result["error"])
        self.assertFalse(self.broker.attempt.resource_uncertain)
        self.broker.finish.assert_called_once_with("operation", "FAILED", {"code": "OPERATION_FAILED"})

        self.broker.finish.reset_mock()
        self.broker.attempt.resource_uncertain = True
        self.broker.git.run.side_effect = RuntimeError("GUARD_GIT_RESOURCE_UNKNOWN")
        with self.assertRaisesRegex(RuntimeError, "GUARD_GIT_RESOURCE_UNKNOWN"):
            self.broker._invoke("git_status", {})
        self.broker.finish.assert_called_once_with(
            "operation", "UNKNOWN", {"code": "OPERATION_OUTCOME_REQUIRES_RECONCILIATION"})

    def test_exact_edit_can_replace_the_entire_file_with_much_shorter_code(self):
        path = self.root / "report.py"
        original = "def total():\n" + "    # Repeated explanation\n" * 40 + "    return 100\n"
        shorter = "def total():\n    return 100\n"
        path.write_bytes(original.encode("utf-8"))
        self.broker.invoke("edit", {"path": "report.py", "oldText": original, "newText": shorter,
                                     "expectedDigest": hashlib.sha256(original.encode("utf-8")).hexdigest()})
        self.assertEqual(shorter, path.read_text(encoding="utf-8"))
        self.broker._request_approval.assert_not_called()

    def test_fragment_replacement_is_rejected_then_exact_edit_preserves_the_rest(self):
        path = self.root / "report.py"
        original = b'def run():\r\n    label = "old"\r\n    return label\r\n' + b"# retained\r\n" * 2500
        path.write_bytes(original)
        expected = hashlib.sha256(original).hexdigest()
        result = self.broker.invoke("write", {"path": "report.py", "content": '    label = "new"', "expectedDigest": expected})
        self.assertEqual("GUARD_EXISTING_FILE_REQUIRES_EDIT", result["error"])
        self.assertEqual(original, path.read_bytes())
        self.broker.begin.assert_not_called()
        self.broker._request_approval.assert_not_called()

        edits = {"path": "report.py", "oldText": '    label = "old"\n    return label',
                 "newText": '    label = "new"\n    return label', "expectedDigest": expected}
        result = self.broker.invoke("edit", edits)
        updated = original.replace(b'"old"', b'"new"')
        self.assertEqual(updated, path.read_bytes())
        self.assertEqual(hashlib.sha256(updated).hexdigest(), result["resultDigest"])
        self.assertEqual("GUARD_FILE_VERSION_CONFLICT", self.broker.invoke("edit", edits)["error"])
        self.broker._request_approval.assert_not_called()

    def test_edit_requires_a_unique_match(self):
        path = self.root / "note.txt"
        original = b"aaaa"
        path.write_bytes(original)
        result = self.broker.invoke("edit", {"path": "note.txt", "oldText": "aaa", "newText": "x",
                                            "expectedDigest": hashlib.sha256(original).hexdigest()})
        self.assertEqual("GUARD_EDIT_MATCH_REQUIRED", result["error"])
        self.assertEqual(original, path.read_bytes())

    def test_edit_many_applies_all_fragments_in_one_operation(self):
        path = self.root / "report.py"
        original = b'LANG = "ja"\r\n\r\ndef label():\r\n    return "old"\r\n'
        path.write_bytes(original)
        result = self.broker.invoke("edit_many", {
            "path": "report.py", "expectedDigest": hashlib.sha256(original).hexdigest(),
            "edits": [{"oldText": 'def label():\n    return "old"', "newText": 'def label():\n    return "new"'},
                      {"oldText": 'LANG = "ja"', "newText": 'LANG = "zh"'}]})
        expected = original.replace(b'"ja"', b'"zh"').replace(b'"old"', b'"new"')
        self.assertEqual(expected, path.read_bytes())
        self.assertEqual(hashlib.sha256(expected).hexdigest(), result["resultDigest"])
        self.broker.begin.assert_called_once()
        self.broker.finish.assert_called_once()
        self.broker._request_approval.assert_not_called()

    def test_edit_many_leaves_file_unchanged_if_any_fragment_is_invalid(self):
        path = self.root / "note.txt"
        original = b"unique text"
        path.write_bytes(original)
        for fragment, code in [("absent", "GUARD_EDIT_MATCH_REQUIRED"), ("unique text", "GUARD_EDIT_OVERLAP")]:
            with self.subTest(fragment=fragment):
                result = self.broker.invoke("edit_many", {
                    "path": "note.txt", "expectedDigest": hashlib.sha256(original).hexdigest(),
                    "edits": [{"oldText": "unique", "newText": "first change"},
                              {"oldText": fragment, "newText": "second change"}]})
                self.assertEqual(code, result["error"])
                self.assertEqual(original, path.read_bytes())

    def test_exact_small_edit_is_autonomous_and_stale_digest_is_rejected(self):
        (self.root/"note.txt").write_text("before")
        expected=hashlib.sha256(b"before").hexdigest()
        self.broker._invoke("edit",{"path":"note.txt","oldText":"before","newText":"after","expectedDigest":expected})
        with self.assertRaisesRegex(RuntimeError,"GUARD_FILE_VERSION_CONFLICT"):
            self.broker._invoke("edit",{"path":"note.txt","oldText":"before","newText":"stale","expectedDigest":expected})
        self.assertEqual("after",(self.root/"note.txt").read_text())

    def test_model_can_recover_from_missing_file_and_stale_digest(self):
        broker = self.broker
        args = {"path": "note.txt", "offset": 1, "limit": 10}
        self.assertEqual("GUARD_PARENT_OR_FILE_MISSING", broker.invoke("read", args)["error"])
        (self.root / "note.txt").write_text("before", encoding="utf-8")
        result = broker.invoke("write", {"path": "note.txt", "content": "after", "expectedDigest": "ABSENT"})
        self.assertEqual("GUARD_EXISTING_FILE_REQUIRES_EDIT", result["error"])
        current = broker.invoke("read", args)
        broker.invoke("edit", {"path": "note.txt", "oldText": "before", "newText": "after", "expectedDigest": current["sourceDigest"]})
        self.assertEqual("after", (self.root / "note.txt").read_text(encoding="utf-8"))

    def test_create_never_overwrites_a_file_created_during_publication(self):
        def another_writer(_source, target, **_options):
            target.write_bytes(b"another writer")
            raise FileExistsError()

        with patch("execution_guard.os.link", side_effect=another_writer):
            result = self.broker.invoke("write", {"path": "note.txt", "content": "new", "expectedDigest": "ABSENT"})
        self.assertEqual("GUARD_EXISTING_FILE_REQUIRES_EDIT", result["error"])
        self.assertEqual(b"another writer", (self.root / "note.txt").read_bytes())
        self.assertFalse(self.broker.attempt.resource_uncertain)

    def test_unconfirmed_write_blocks_the_next_tool(self):
        broker = self.broker
        with patch.object(broker, "_execute", side_effect=OSError("private detail")):
            with self.assertRaises(OSError):
                broker.invoke("write", {"path": "note.txt", "content": "after", "expectedDigest": "ABSENT"})
        self.assertTrue(broker.attempt.resource_uncertain)
        broker.finish.assert_called_once_with(
            "operation", "UNKNOWN", {"code": "OPERATION_OUTCOME_REQUIRES_RECONCILIATION"})
        with self.assertRaisesRegex(RuntimeError, "GUARD_RESOURCE_OUTCOME_UNCONFIRMED"):
            broker.invoke("ls", {"path": "."})
        broker.begin.assert_called_once()


if __name__=="__main__":
    unittest.main()
