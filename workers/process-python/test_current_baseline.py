import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch
from process_worker import _pin_current_revision


class CurrentBaselineTest(unittest.TestCase):
    def test_empty_revision_resolves_head_and_uses_server_workspace_identity(self):
        revision = "a" * 40
        task = {"workspace": {"repositoryRef": "repo", "repositoryPath": "/repo", "baseRevision": None}}
        client = Mock()
        client.pin_baseline.return_value = {"repositoryRef": "repo", "baseRevision": revision, "workspaceKey": "key", "branchName": "branch"}
        attempt = Mock()
        with patch("process_worker._git", return_value=revision) as git:
            result = _pin_current_revision(SimpleNamespace(repository_root=None, worker_id="worker"), task, client, attempt)
        git.assert_called_once_with("/repo", "rev-parse", "--verify", "HEAD^{commit}")
        self.assertEqual(revision, result["baseRevision"])
        self.assertEqual("/repo", result["repositoryPath"])
        attempt.evidence.record.assert_called_once()

    def test_explicit_revision_never_reads_head_or_rebinds(self):
        task = {"workspace": {"baseRevision": "b" * 40}}
        client = Mock()
        with patch("process_worker._git") as git:
            self.assertEqual(task["workspace"], _pin_current_revision(None, task, client, None))
        git.assert_not_called(); client.pin_baseline.assert_not_called()

    def test_non_git_directory_reports_specific_failure(self):
        task = {"workspace": {"repositoryPath": "/repo"}}
        with patch("process_worker._git", side_effect=RuntimeError("GIT_COMMAND_FAILED")):
            with self.assertRaisesRegex(RuntimeError, "GIT_HEAD_UNAVAILABLE"):
                _pin_current_revision(SimpleNamespace(worker_id="worker"), task, Mock(), Mock())


if __name__ == "__main__": unittest.main()
