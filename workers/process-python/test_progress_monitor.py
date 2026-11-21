import unittest
from unittest.mock import Mock

from progress_monitor import ProgressMonitor
from worker_runtime import failure_code


def read(call, value="same", arguments=None, name="read"):
    return [{"type": "function_call", "call_id": str(call), "name": name,
             "arguments": {"path": "file.txt"} if arguments is None else arguments},
            {"type": "function_call_output", "call_id": str(call), "output": value}]


class ProgressMonitorTest(unittest.TestCase):
    def setUp(self):
        self.evidence = Mock()
        self.monitor = ProgressMonitor(self.evidence)

    def warn(self):
        for call in range(3):
            self.assertFalse(self.monitor.observe(read(call)))
        self.assertTrue(self.monitor.observe(read(3)))

    def test_warns_then_stops_after_further_repeated_results(self):
        self.warn()
        self.assertFalse(self.monitor.observe(read(4)))
        self.assertFalse(self.monitor.observe(read(5)))
        with self.assertRaisesRegex(RuntimeError, "^MODEL_NO_PROGRESS$"):
            self.monitor.observe(read(6))
        self.assertEqual(self.evidence.record.call_args.kwargs,
                         {"status": "failed", "count": 6, "tool": "read", "code": "MODEL_NO_PROGRESS"})

    def test_alternating_known_results_do_not_evade_the_limit(self):
        self.assertFalse(self.monitor.observe(read(0, "a")))
        self.assertFalse(self.monitor.observe(read(1, "b")))
        for call in (2, 3):
            self.assertFalse(self.monitor.observe(read(call, "a" if call % 2 == 0 else "b")))
        self.assertTrue(self.monitor.observe(read(4, "a")))
        for call in (5, 6):
            self.assertFalse(self.monitor.observe(read(call, "a" if call % 2 == 0 else "b")))
        with self.assertRaisesRegex(RuntimeError, "MODEL_NO_PROGRESS"):
            self.monitor.observe(read(7, "b"))

    def test_replaying_history_does_not_count_tools_twice(self):
        history = []
        for call in range(4):
            history += read(call)
            self.monitor.observe(history)
        for _ in range(4):
            self.assertFalse(self.monitor.observe(history))
        self.assertEqual(self.monitor.repetitions, 3)
        self.assertEqual(self.evidence.record.call_count, 1)

    def test_a_large_batch_must_give_the_model_a_reminder_first(self):
        self.monitor.observe(read(0))
        batch = [item for call in range(1, 17) for item in read(call)]
        self.assertTrue(self.monitor.observe(batch))
        with self.assertRaisesRegex(RuntimeError, "MODEL_NO_PROGRESS"):
            self.monitor.observe(read(17))

    def test_new_result_at_batch_end_prevents_a_false_stop(self):
        self.warn()
        batch = read(4) + read(5) + read(6) + read(7, "new information")
        self.assertFalse(self.monitor.observe(batch))
        self.assertEqual(self.monitor.repetitions, 0)
        self.assertFalse(self.monitor.warned)

    def test_new_arguments_are_progress_but_json_formatting_is_not(self):
        self.monitor.observe(read(0, '{"answer":1}', '{"b":2,"a":1}'))
        for call in range(1, 3):
            self.assertFalse(self.monitor.observe(read(call, '{ "answer": 1 }', {"a": 1, "b": 2})))
        self.assertTrue(self.monitor.observe(read(3, {"answer": 1}, {"b": 2, "a": 1})))
        self.assertFalse(self.monitor.observe(read(4, {"answer": 1}, {"page": 2})))
        self.assertEqual(self.monitor.repetitions, 0)

    def test_possible_effects_reset_reads_and_prevent_no_change_claims(self):
        self.warn()
        self.assertFalse(self.monitor.observe(read(4, "completed", name="unknown_tool")))
        self.assertTrue(self.monitor.may_have_changed)
        self.assertFalse(self.monitor.observe(read(5)))
        self.assertEqual(self.monitor.repetitions, 0)
        self.assertIsNone(self.monitor.delivery_feedback(True, "PASS"))

    def test_confirmed_write_failure_does_not_count_as_a_change(self):
        self.monitor.observe(read(0, '{"status":"error","error":"GUARD_FILE_VERSION_CONFLICT","hint":"Read again."}', name="write"))
        self.assertFalse(self.monitor.may_have_changed)
        self.assertIsNotNone(self.monitor.delivery_feedback(True, "PASS"))

    def test_new_reads_can_continue_without_a_fixed_read_phase_reminder(self):
        for call in range(12):
            self.assertFalse(self.monitor.observe(read(call, f"new evidence {call}")))
        self.evidence.record.assert_not_called()

    def test_repeated_confirmed_write_rejection_warns_then_stops(self):
        def reject(call):
            return read(call, {"status": "error", "error": "GUARD_FILE_VERSION_CONFLICT", "hint": f"Read again {call}."}, name="edit")
        for call in range(3):
            self.assertFalse(self.monitor.observe(reject(call)))
        self.assertTrue(self.monitor.observe(reject(3)))
        self.assertIn("same failure", self.monitor.reminder)
        self.assertEqual(self.evidence.record.call_args.kwargs["code"], "REPEATED_TOOL_FAILURE")
        self.assertFalse(self.monitor.may_have_changed)
        for call in (4, 5):
            self.assertFalse(self.monitor.observe(reject(call)))
        with self.assertRaisesRegex(RuntimeError, "^MODEL_NO_PROGRESS$"):
            self.monitor.observe(reject(6))

    def test_failed_command_with_changed_diagnostics_is_new_evidence(self):
        def command(call, digest):
            return read(call, {"facts": {"exitCode": 1}, "sha256": digest, "modelPreviewComplete": False},
                        {"commandId": "check-json"}, name="run_command")
        for call in range(3):
            self.assertFalse(self.monitor.observe(command(call, "a" * 64)))
        self.assertTrue(self.monitor.observe(command(3, "a" * 64)))
        self.assertTrue(self.monitor.may_have_changed)
        self.assertFalse(self.monitor.observe(command(4, "b" * 64)))
        self.assertEqual(self.monitor.repetitions, 0)

    def test_repeated_confirmed_shell_timeout_also_stops_for_large_output(self):
        from context_window import Sources

        result = {"status": "timedOut", "exitCode": None, "output": "waiting\n" * 400, "outputTruncated": False}
        for output in (result, Sources().model_result(result, 512)):
            with self.subTest(output_type=type(output).__name__):
                monitor = ProgressMonitor(self.evidence)
                def timed_out(call):
                    return read(call, output, {"command": "check"}, name="run_shell")

                for call in range(3):
                    self.assertFalse(monitor.observe(timed_out(call)))
                self.assertTrue(monitor.observe(timed_out(3)))
                self.assertTrue(monitor.may_have_changed)
                for call in (4, 5):
                    self.assertFalse(monitor.observe(timed_out(call)))
                with self.assertRaisesRegex(RuntimeError, "^MODEL_NO_PROGRESS$"):
                    monitor.observe(timed_out(6))

    def test_user_supplement_resets_but_does_not_replay_old_tools(self):
        self.warn()
        history = [item for call in range(4) for item in read(call)]
        self.assertFalse(self.monitor.observe(history, {"new-supplement-digest"}))
        self.assertFalse(self.monitor.observe(history + read(4), {"new-supplement-digest"}))
        self.assertEqual(self.monitor.repetitions, 0)
        self.assertFalse(self.monitor.warned)

    def test_diagnostics_do_not_include_read_content(self):
        for call in range(4):
            self.monitor.observe(read(call, "private body", {"path": "private path"}))
        recorded = str(self.evidence.record.call_args)
        self.assertNotIn("private body", recorded)
        self.assertNotIn("private path", recorded)

    def test_sdk_limit_has_an_explicit_safe_failure_code(self):
        error = type("MaxTurnsExceeded", (Exception,), {})("private model output")
        self.assertEqual(failure_code(error, "MODEL_ERROR"), "MODEL_TOTAL_CALL_LIMIT")
        self.assertEqual(failure_code(RuntimeError("MODEL_NO_PROGRESS"), "MODEL_ERROR"), "MODEL_NO_PROGRESS")


if __name__ == "__main__":
    unittest.main()
