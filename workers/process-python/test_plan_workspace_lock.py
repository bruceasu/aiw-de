from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, call, patch

from plan_workspace import DirectoryLock, PlanWorkspace, input_manifests


class WorkspaceLockTest(unittest.TestCase):
    def test_text_task_does_not_wait_for_shared_git_workspace_and_carries_its_location(self):
        with TemporaryDirectory() as location:
            root = Path(location).resolve()
            (root / ".git").mkdir()
            source = root / "previous-worktree"
            source.mkdir()
            attempt = SimpleNamespace(check=Mock(), evidence=Mock(), resource_uncertain=False)
            shared = DirectoryLock(root, attempt, "active-file-task")
            old_task = {"planId": "plan", "taskId": "files", "attemptId": "file-output"}
            ref = PlanWorkspace(root, source, attempt, Mock(), None).handoff(old_task, {"summary": "Report", "verdict": "PASS"}, None)
            task = {"location": location, "planId": "plan", "taskId": "text", "attemptId": "new-text",
                    "subjectType": "flexible-plan", "requiredCapabilities": ["general-work"],
                    "inputs": [{**old_task, "handoff": {**ref, "location": location}}]}
            config = SimpleNamespace(runtime="openai-agents", process_role="general")
            try:
                with patch("worker_runtime.current_attempt", return_value=attempt), patch("worker_runtime.git") as git:
                    workspace = PlanWorkspace.materialize(config, task)
                self.assertNotEqual(Path(workspace.cwd), root)
                self.assertEqual(Path(workspace.cwd).parent, root / ".ai" / "sandboxes")
                self.assertTrue(shared.owner.is_file())
                self.assertEqual(attempt.handoff_reports, [("files", "Report")])
                self.assertIsNone(workspace.finalize(task, read_only=True))
                next_ref = workspace.handoff(task, {"summary": "Text outcome", "verdict": "PASS"}, None)
                next_input = {"inputs": [{**task, "handoff": {**next_ref, "location": location}}]}
                self.assertEqual(input_manifests(next_input)[0]["workingDirectory"], str(source))
                git.assert_not_called()
                workspace.cleanup()
            finally:
                shared.release()

    def test_text_question_checkpoint_reuses_private_workspace(self):
        from worker_runtime import snapshot

        with TemporaryDirectory() as location:
            root = Path(location).resolve()
            (root / ".git").mkdir()
            (root / "project.txt").write_text("Project content outside the text sandbox", encoding="utf-8")
            attempt = SimpleNamespace(check=Mock(), evidence=Mock(), resource_uncertain=False)
            config = SimpleNamespace(runtime="openai-agents", process_role="general")
            task = {"location": location, "planId": "plan", "taskId": "text", "attemptId": "initial",
                    "subjectType": "flexible-plan", "requiredCapabilities": ["general-work"]}
            with patch("worker_runtime.current_attempt", return_value=attempt), patch("worker_runtime.git") as git:
                first = PlanWorkspace.materialize(config, task)
                ref = first.handoff(task, {"summary": "Question", "verdict": "REPAIR_REQUIRED"}, None,
                                    checkpoint_tree_digest=snapshot(first.cwd, attempt))
                first.cleanup()
                resume = {**task, "attemptId": "continuation", "taskId": "next",
                          "checkpointInputs": [{**task, "handoff": {**ref, "location": location}}]}
                workspace = PlanWorkspace.materialize(config, resume)
                self.assertEqual(workspace.cwd, first.cwd)
                self.assertEqual(workspace.handoff_cwd, str(root))
                git.assert_not_called()
                workspace.cleanup()

    def test_paused_waiter_does_not_take_a_released_lock(self):
        with TemporaryDirectory() as location:
            root = Path(location)
            lock = root / ".ai" / "locks" / "execution.lock"
            lock.mkdir(parents=True)
            channel = Mock()
            channel.enter.side_effect = [None, RuntimeError("STOP_WAITING_WHILE_PAUSED")]
            attempt = SimpleNamespace(check=Mock(), evidence=Mock(), run_channel=channel)
            with patch("plan_workspace.time.sleep", side_effect=lambda _: lock.rmdir()):
                with self.assertRaisesRegex(RuntimeError, "STOP_WAITING_WHILE_PAUSED"):
                    DirectoryLock(root, attempt, "paused-attempt")
            self.assertFalse(lock.exists())
            channel.leave.assert_called_once_with()

    def test_waits_until_markerless_lock_is_released(self):
        with TemporaryDirectory() as location:
            root = Path(location)
            lock = root / ".ai" / "locks" / "execution.lock"
            lock.mkdir(parents=True)
            attempt = SimpleNamespace(check=Mock(), evidence=Mock())
            sleeps = 0

            def wait(_):
                nonlocal sleeps
                self.assertTrue(lock.is_dir())
                sleeps += 1
                if sleeps == 2:
                    lock.rmdir()

            with patch("plan_workspace.time.monotonic", side_effect=[0, 60, 60]), patch("plan_workspace.time.sleep", side_effect=wait):
                current = DirectoryLock(root, attempt, "new-attempt", ["stopped-attempt"])
            self.assertTrue(current.owner.is_file())
            attempt.evidence.record.assert_has_calls([
                call("workspace-lock", status="waiting", code="WORKSPACE_LOCK_BUSY"),
                call("workspace-lock", status="acquired", code="WORKSPACE_LOCK_ACQUIRED"),
            ])
            current.release()

    def test_retry_reclaims_only_the_confirmed_attempt_lock(self):
        with TemporaryDirectory() as location:
            attempt = SimpleNamespace(check=Mock(), evidence=Mock())
            previous = DirectoryLock(Path(location), attempt, "stopped-attempt")

            current = DirectoryLock(Path(location), attempt, "new-attempt", ["stopped-attempt"])
            self.assertFalse(previous.owner.exists())
            self.assertTrue(current.owner.is_file())
            attempt.evidence.record.assert_called_once_with(
                "workspace-lock", status="acquired", code="WORKSPACE_LOCK_RECOVERED")
            current.release()
            self.assertFalse(previous.path.exists())

    def test_retry_keeps_a_lock_without_exact_stop_evidence(self):
        with TemporaryDirectory() as location:
            attempt = SimpleNamespace(check=Mock(), evidence=Mock())
            original = DirectoryLock(Path(location), attempt, "active-attempt")
            attempt.check.side_effect = [None, RuntimeError("TEST_STOP_WAITING")]
            with self.assertRaisesRegex(RuntimeError, "TEST_STOP_WAITING"):
                DirectoryLock(Path(location), attempt, "new-attempt", ["different-attempt"])
            self.assertTrue(original.owner.is_file())
            attempt.evidence.record.assert_called_once_with(
                "workspace-lock", status="waiting", code="WORKSPACE_LOCK_BUSY")
            original.release()
