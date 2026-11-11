"""Offline checks for Worker-local repair material handling."""
import base64
import hashlib
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "workers" / "process-python"))
from price_repair_materials import materialize, submission


def item(name, content):
    return {"name": name, "sha256": hashlib.sha256(content).hexdigest(),
            "contentBase64": base64.b64encode(content).decode("ascii")}


def payload():
    return {"protocol": "aiw.price-repair-material.v1", "repairId": "price-repair-11111111-1111-1111-1111-111111111111",
            "provider": "deepseek", "failureFingerprint": "a" * 64,
            "files": [item("collector.py", b"module"), item("last-success.page", b"old"), item("changed.page", b"new")]}


class PriceRepairMaterialCases(unittest.TestCase):
    def test_writes_fixed_files_in_worker_local_directory(self):
        with tempfile.TemporaryDirectory() as parent:
            root = materialize(payload(), parent)
            self.assertEqual(Path(parent).resolve(), root.parent)
            self.assertEqual(b"module", (root / "collector.py").read_bytes())
            self.assertEqual(b"old", (root / "last-success.page").read_bytes())
            self.assertEqual(b"new", (root / "changed.page").read_bytes())

    def test_rejects_tampering_and_extra_paths_before_creating_directory(self):
        with tempfile.TemporaryDirectory() as parent:
            data = payload()
            data["files"][0]["contentBase64"] = base64.b64encode(b"different").decode("ascii")
            with self.assertRaises(ValueError): materialize(data, parent)
            self.assertFalse(list(Path(parent).iterdir()))
            data = payload()
            data["files"][0]["name"] = "../collector.py"
            with self.assertRaises(ValueError): materialize(data, parent)
            self.assertFalse(list(Path(parent).iterdir()))

    def test_submission_contains_only_two_explicit_files(self):
        with tempfile.TemporaryDirectory() as parent:
            root = materialize(payload(), parent)
            (root / "test_collector.py").write_text("assert True", encoding="utf-8")
            (root / "latest-page.url").write_text("https://api-docs.deepseek.com/quick_start/pricing/", encoding="utf-8")
            (root / "other.txt").write_text("not delivered", encoding="utf-8")
            result = submission(root, {"attemptId": "attempt", "leaseToken": "lease"}, "worker", payload()["repairId"], "fixed")
            self.assertEqual(b"module", base64.b64decode(result["moduleBase64"]))
            self.assertEqual(b"assert True", base64.b64decode(result["checksBase64"]))
            self.assertNotIn("other.txt", str(result))

    def test_submission_rejects_binary_checks(self):
        with tempfile.TemporaryDirectory() as parent:
            root = materialize(payload(), parent)
            (root / "test_collector.py").write_bytes(b"bad\0data")
            (root / "latest-page.url").write_text("https://api-docs.deepseek.com/quick_start/pricing/", encoding="utf-8")
            with self.assertRaises(ValueError):
                submission(root, {"attemptId": "attempt", "leaseToken": "lease"}, "worker", payload()["repairId"], "fixed")
