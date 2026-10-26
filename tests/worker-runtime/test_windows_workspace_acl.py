"""Offline policy tests; no Windows permissions or model calls are changed."""
import pathlib
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[2] / "workers/process-python"))
import windows_workspace_acl as policy


class WindowsWorkspaceAclTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        base = pathlib.Path(self.directory.name)
        self.source, self.root = base / "source", base / "worktrees"
        self.workspace = self.root / "assigned"
        self.source.mkdir()
        self.workspace.mkdir(parents=True)
        (self.source / "page.htm").write_text("source")
        (self.workspace / "page.htm").write_text("checkout")
        self.acl = Mock()
        self.acl.permits.return_value = True
        self.acl.read.side_effect = lambda path: (str(path), str(path) + ":original")

    def run_policy(self):
        with patch.object(policy, "_Acl", return_value=self.acl):
            policy.permit_workspace_read(self.root, self.workspace, self.source)

    def test_only_container_traverse_and_workspace_read_are_granted(self):
        self.run_policy()
        grants = self.acl.grant.call_args_list
        self.assertEqual(2, len(grants))
        self.assertEqual((self.root, (str(self.root), str(self.root) + ":original"), policy.TRAVERSE, False), grants[0].args)
        self.assertEqual((self.workspace, (str(self.workspace), str(self.workspace) + ":original"), policy.READ_EXECUTE, True), grants[1].args)
        self.acl.permits.assert_any_call(self.source / "page.htm", policy.READ)
        self.acl.close.assert_called_once()

    def test_private_source_blocks_grants(self):
        self.acl.permits.return_value = False
        with self.assertRaisesRegex(RuntimeError, "SOURCE_NOT_USERS_READABLE"):
            self.run_policy()
        self.acl.grant.assert_not_called()

    def test_private_file_inside_readable_repository_blocks_grants(self):
        self.acl.permits.side_effect = lambda path, rights: path != self.source / "page.htm"
        with self.assertRaisesRegex(RuntimeError, "SOURCE_NOT_USERS_READABLE"):
            self.run_policy()
        self.acl.grant.assert_not_called()

    def test_failed_grant_restores_both_original_dacls(self):
        self.acl.grant.side_effect = [None, OSError("Failed target grant")]
        with self.assertRaises(OSError):
            self.run_policy()
        self.assertEqual([(self.workspace, str(self.workspace) + ":original"),
                          (self.root, str(self.root) + ":original")],
                         [call.args for call in self.acl.write.call_args_list])

    def test_outside_workspace_cannot_change_permissions(self):
        with self.assertRaisesRegex(RuntimeError, "OUTSIDE_ROOT"):
            policy.permit_workspace_read(self.workspace, self.root, self.source)
        self.acl.grant.assert_not_called()

    def test_coding_write_scope_requires_source_modify_permission(self):
        writer = Mock()
        writer.permits.return_value = False
        with patch.object(policy, "_Acl", side_effect=[self.acl, writer]):
            with self.assertRaisesRegex(RuntimeError, "SOURCE_MODIFY_SCOPE_UNAVAILABLE"):
                policy.permit_workspace_read(self.root, self.workspace, self.source, writable=True)
        self.acl.grant.assert_not_called()
        writer.grant.assert_not_called()
        writer.close.assert_called_once()

    def test_coding_worktree_retains_only_verified_source_modify_scope(self):
        writer = Mock()
        writer.permits.return_value = True
        writer.read.return_value = ("after-read-grant", "existing-acl")
        with patch.object(policy, "_Acl", side_effect=[self.acl, writer]):
            policy.permit_workspace_read(self.root, self.workspace, self.source, writable=True)
        writer.permits.assert_any_call(self.source / "page.htm", policy.MODIFY)
        writer.grant.assert_called_once_with(self.workspace, ("after-read-grant", "existing-acl"), policy.MODIFY, True)
        writer.close.assert_called_once()


if __name__ == "__main__":
    unittest.main()
