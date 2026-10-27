import os
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
from worker_runtime import failure_code, filesystem_path, snapshot


class WorkspacePathsTest(unittest.TestCase):
    def test_snapshot_is_stable_and_detects_changes(self):
        with tempfile.TemporaryDirectory() as root:
            file = Path(root) / "sample.txt"
            file.write_text("one", encoding="utf-8")
            attempt = SimpleNamespace(check=lambda: None)
            initial = snapshot(root, attempt)
            self.assertEqual(initial, snapshot(root, attempt))
            file.write_text("two", encoding="utf-8")
            self.assertNotEqual(initial, snapshot(root, attempt))

    def test_windows_extended_prefix_is_idempotent(self):
        if os.name != "nt": self.skipTest("Windows path contract")
        value = filesystem_path(r"C:\workspace")
        self.assertTrue(value.startswith("\\\\?\\"))
        self.assertEqual(value, filesystem_path(value))

    def test_snapshot_permission_error_is_specific(self):
        with patch("worker_runtime.os.walk", side_effect=PermissionError("denied")):
            with self.assertRaisesRegex(RuntimeError, "WORKSPACE_SNAPSHOT_ACCESS_DENIED"):
                snapshot(".", SimpleNamespace(check=lambda: None))

    def test_junction_reparse_tag_is_rejected_without_python_312_helpers(self):
        root = filesystem_path(".")
        metadata = SimpleNamespace(st_mode=0, st_reparse_tag=0xA0000003)
        with patch("worker_runtime.os.walk", return_value=iter([(root, ["junction"], [])])), patch("worker_runtime.Path.lstat", return_value=metadata):
            with self.assertRaisesRegex(RuntimeError, "WORKSPACE_SNAPSHOT_JUNCTION_UNSAFE"):
                snapshot(".", SimpleNamespace(check=lambda: None))

    def test_os_error_and_known_codes_are_preserved(self):
        self.assertEqual("FILE_NOT_FOUND", failure_code(FileNotFoundError("missing")))
        self.assertEqual("MODEL_UNAVAILABLE", failure_code(RuntimeError("MODEL_UNAVAILABLE")))
        self.assertEqual("OPERATION_FAILED", failure_code(ValueError("private details")))

    def test_long_missing_extended_path_is_not_misreported_as_length_limit(self):
        error = FileNotFoundError("missing")
        error.winerror = 3
        error.filename = "C:\\" + "a" * 270
        self.assertEqual("FILESYSTEM_PATH_TOO_LONG", failure_code(error))
        error.filename = "\\\\?\\" + error.filename
        self.assertEqual("FILE_NOT_FOUND", failure_code(error))


if __name__ == "__main__": unittest.main()
