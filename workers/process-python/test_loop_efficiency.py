import hashlib
import os
import threading
import unittest
from concurrent.futures import ThreadPoolExecutor
from types import SimpleNamespace
from unittest.mock import Mock, patch

from automation_loop import ToolCall
from completion_validation import no_change_evidence
from skill_files import Reader
from tool_coordination import ToolCoordinator, read_batch


class LoopEfficiencyTest(unittest.TestCase):
    def test_text_pages_preserve_utf8_byte_offsets_and_full_digest(self):
        raw = "a中b文".encode("utf-8")
        reader = Reader({("skill", "file.md"): raw}, {}, [])
        with patch.dict(os.environ, {"AIW_SKILL_TEXT_PROTOCOL": "v1"}):
            first = reader.read_text("skill", "file.md", 0, 3)
            second = reader.read_text("skill", "file.md", first["nextOffset"], 3)
            self.assertEqual(first["text"], "a")
            self.assertEqual(second["text"], "中")
            self.assertEqual(second["nextOffset"], 4)
            self.assertEqual(first["sha256"], hashlib.sha256(raw).hexdigest())
            with self.assertRaisesRegex(RuntimeError, "OFFSET_NOT_BOUNDARY"):
                reader.read_text("skill", "file.md", 2, 3)
            with self.assertRaisesRegex(RuntimeError, "PAGE_TOO_SMALL"):
                reader.read_text("skill", "file.md", 1, 2)

    def test_binary_contract_remains_available_when_text_is_invalid(self):
        reader = Reader({("skill", "raw.bin"): b"\xff\x00"}, {}, [])
        with patch.dict(os.environ, {"AIW_SKILL_TEXT_PROTOCOL": "v1"}):
            with self.assertRaisesRegex(RuntimeError, "NOT_UTF8"):
                reader.read_text("skill", "raw.bin", 0, 8192)
            self.assertEqual(reader.read("skill", "raw.bin", 0, 8192)["encoding"], "base64")

    def test_no_change_cannot_cite_invented_failed_or_write_actions(self):
        result = {"noChange": {"reason": "The required behavior already exists.", "evidence": ["real-read"]}}
        observations = [{"callId": "real-read", "action": {"name": "read"}, "result": {"sourceDigest": "digest"}}]
        self.assertTrue(no_change_evidence(result, observations))
        observations[0]["result"] = {"error": "NOT_FOUND"}
        self.assertFalse(no_change_evidence(result, observations))
        observations[0]["result"] = {}; observations[0]["action"]["name"] = "write"
        self.assertFalse(no_change_evidence(result, observations))
        self.assertFalse(no_change_evidence(result, []))

    def test_batch_runs_reads_concurrently_but_returns_original_call_order(self):
        barrier = threading.Barrier(4)
        calls = [ToolCall(str(i), "read", {}) for i in range(4)]
        def invoke(index, call):
            barrier.wait(timeout=2)
            return {"index": index}
        outcomes = read_batch(calls, invoke)
        self.assertEqual([value["index"] for value, error in outcomes], list(range(4)))
        self.assertTrue(all(error is None for value, error in outcomes))

    def test_effects_never_enter_the_parallel_batch(self):
        with self.assertRaisesRegex(ValueError, "READ_ONLY_BATCH_REQUIRED"):
            read_batch([ToolCall("write", "write", {})], Mock())

    def test_writes_wait_for_readers_and_readers_wait_for_writes(self):
        attempt = SimpleNamespace(check=Mock())
        gate = ToolCoordinator(attempt)
        entered, released, read_entered = threading.Event(), threading.Event(), threading.Event()
        def write():
            with gate.access(False):
                entered.set()
                released.wait(timeout=2)
        def read():
            with gate.access(True):
                read_entered.set()
        with ThreadPoolExecutor(max_workers=2) as executor:
            with gate.access(True):
                future = executor.submit(write)
                self.assertFalse(entered.wait(timeout=0.05))
            self.assertTrue(entered.wait(timeout=2))
            self.assertTrue(gate.writer)
            reader = executor.submit(read)
            self.assertFalse(read_entered.wait(timeout=0.05))
            released.set();future.result(timeout=2);reader.result(timeout=2)
            self.assertTrue(read_entered.is_set())
        self.assertEqual(gate.readers, 0)
        self.assertFalse(gate.writer)


if __name__ == "__main__":
    unittest.main()
