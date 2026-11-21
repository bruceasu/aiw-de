from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, call, patch

from plan_workspace import DirectoryLock


class WorkspaceLockTest(unittest.TestCase):
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
