import hashlib
import threading
import time
import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch

from run_channel import RunChannel, bind, encoded
from run_contract import RuntimeCapabilities


class RunChannelTest(unittest.TestCase):
    def view(self, phase="RUNNING", commands=None, ack=0):
        return {"protocol": "aiw.worker.loop.v1", "attemptId": "attempt", "revision": 1,
                "phase": phase, "acknowledgedSequence": ack, "commands": commands or []}

    def channel(self):
        client = SimpleNamespace(timeout=10, _post=Mock(return_value=self.view()))
        attempt = SimpleNamespace(check=Mock(), resource_uncertain=False, cancelled=threading.Event(),
                                  evidence=SimpleNamespace(observers=[]), cancel=Mock())
        broker = SimpleNamespace(client=client, attempt=attempt, base="/tasks/task", lease={"attemptId": "attempt"},
                                 task={"instructionRevision": 1})
        caps = RuntimeCapabilities("HOST", "HOST", "WORKER_PROJECTION", "TOOL", "NONE", "NONE", "TOOL",
                                   "BEST_EFFORT", "UNKNOWN", "BACKEND_TURN")
        with patch("run_channel.threading.Thread") as thread:
            thread.return_value.is_alive.return_value = False
            channel = RunChannel(broker, caps)
        self.addCleanup(channel.close)
        return channel

    def test_pause_is_acknowledged_only_after_the_admitted_unit_finishes(self):
        channel = self.channel()
        channel.enter()
        command = {"status": "ACCEPTED", "action": "PAUSE", "sequence_no": 1}
        channel._accept(self.view("PAUSING", [command]))
        self.assertTrue(channel.paused)
        self.assertEqual(channel.pending_receipts, [{"sequence": 1, "status": "RECEIVED"}])
        command["status"] = "RECEIVED"
        channel._accept(self.view("PAUSING", [command]))
        self.assertEqual(channel.pending_receipts, [])
        channel.leave()
        channel._accept(self.view("PAUSING", [command]))
        self.assertEqual(channel.pending_receipts, [{"sequence": 1, "status": "APPLIED"}])

    def test_unknown_resource_never_reports_pause_applied(self):
        channel = self.channel()
        channel.attempt.resource_uncertain = True
        channel._accept(self.view("PAUSING", [{"status": "RECEIVED", "action": "PAUSE", "sequence_no": 1}]))
        self.assertEqual(channel.pending_receipts, [])

    def test_stale_control_closes_new_dispatch_without_resetting_the_deadline(self):
        channel = self.channel()
        channel.last_contact = time.monotonic() - 6
        channel.attempt.check.side_effect = [None, RuntimeError("ATTEMPT_CANCELLED")]
        with self.assertRaisesRegex(RuntimeError, "ATTEMPT_CANCELLED"):
            channel.enter()
        self.assertEqual(channel.active, 0)

    def test_metadata_keeps_order_and_digests_without_raw_inputs(self):
        channel = self.channel()
        channel.observe("context-component", {"bytes": 12, "digest": "a" * 64, "sourceKind": "messages",
                                               "path": "secret", "instruction": "private"})
        channel.observe("guard-start", {"tool": "READ", "operation": "OP-" + "b" * 32})
        self.assertEqual([item["sequence"] for item in channel.queue], [1, 2])
        self.assertNotIn("secret", encoded(channel.queue).decode())
        self.assertNotIn("private", encoded(channel.queue).decode())
        self.assertEqual(channel.queue[0]["payloadDigest"], hashlib.sha256(encoded(channel.queue[0]["payload"])).hexdigest())
        channel._accept(self.view(ack=1))
        self.assertEqual([item["sequence"] for item in channel.queue], [2])

    def test_seal_consumes_the_atomic_workbench_response(self):
        channel = self.channel()
        batch = {"phase": "RUNNING", "batchId": "boundary-1", "messages": [{"text": "follow up"}]}
        channel.client._post.side_effect = [self.view(), {"run": self.view(), "batch": batch}]
        self.assertEqual(channel.seal("boundary-1"), batch)
        paths = [call.args[0] for call in channel.client._post.call_args_list]
        self.assertEqual(paths[-2:], ["/tasks/task/execution-run/control", "/tasks/task/execution-run/seal"])

    def test_cancel_uses_the_existing_stop_reason_and_never_claims_quiescence(self):
        channel = self.channel()
        channel._accept(self.view("STOPPING", [{"status": "ACCEPTED", "action": "CANCEL", "sequence_no": 1}]))
        channel.attempt.cancel.assert_called_with("stop-requested")
        self.assertNotIn({"sequence": 1, "status": "APPLIED"}, channel.pending_receipts)

    def test_plain_native_task_does_not_gain_or_require_a_control_channel(self):
        attempt = SimpleNamespace(required_capabilities=frozenset())
        request = SimpleNamespace(attempt=attempt, broker=None)
        with patch.dict("os.environ", {"AIW_WORKER_LOOP_CONTROL": "true"}):
            bind(request, "codex-native")
        self.assertFalse(hasattr(attempt, "run_channel"))

    def test_task_requiring_loop_control_cannot_fall_back_to_native_execution(self):
        request = SimpleNamespace(attempt=SimpleNamespace(required_capabilities={"worker-loop-v1"}), broker=None)
        with patch.dict("os.environ", {"AIW_WORKER_LOOP_CONTROL": "true"}):
            with self.assertRaisesRegex(RuntimeError, "REQUIRES_GUARDED_TASK"):
                bind(request, "codex-native")

    def test_native_pause_waits_for_an_acknowledged_quiescence_event(self):
        channel = self.channel()
        channel.capabilities = RuntimeCapabilities("BACKEND", "BACKEND", "WORKER_PROJECTION", "TURN", "NONE", "NONE", "TURN", "BEST_EFFORT", "UNKNOWN", "BACKEND_TURN")
        command = {"status": "RECEIVED", "action": "PAUSE", "sequence_no": 1}
        channel._accept(self.view("PAUSING", [command]))
        self.assertEqual(channel.pending_receipts, [])
        channel.observe("execution-quiescent", {"complete": True, "count": 0})
        channel._accept(self.view("PAUSING", [command]))
        self.assertEqual(channel.pending_receipts, [])
        channel._accept(self.view("PAUSING", [command], ack=1))
        self.assertEqual(channel.pending_receipts, [{"sequence": 1, "status": "APPLIED"}])
        channel.observe("native-turn-started", {"status": "running"})
        channel._accept(self.view("PAUSING", [command], ack=1))
        self.assertEqual(channel.pending_receipts, [])


if __name__ == "__main__":
    unittest.main()
