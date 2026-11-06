import unittest
import json
import os
from types import SimpleNamespace
from unittest.mock import Mock, patch

from automation_loop import ActionProtocolError, CorrectionBudget, parse_action


SCHEMAS = [{"name": "read", "inputSchema": {"type": "object", "properties": {
    "path": {"type": "string"}, "offset": {"type": "integer", "minimum": 1}},
    "required": ["path", "offset"], "additionalProperties": False}}]


class ActionProtocolTest(unittest.TestCase):
    def test_batch_is_parsed_without_executing_any_tool(self):
        batch = parse_action('{"action":"tools","calls":[{"id":"a","name":"read","arguments":{"path":"a","offset":1}},'
                             '{"id":"b","name":"read","arguments":{"path":"b","offset":2}}]}', SCHEMAS)
        self.assertEqual([call.alias for call in batch.calls], ["a", "b"])
        self.assertIsNone(batch.result)

    def test_later_invalid_arguments_reject_the_entire_batch(self):
        with self.assertRaisesRegex(ActionProtocolError, "HOST_ACTION_ARGUMENTS_INVALID"):
            parse_action('{"action":"tools","calls":[{"id":"a","name":"read","arguments":{"path":"a","offset":1}},'
                         '{"id":"b","name":"read","arguments":{"path":"b","offset":false}}]}', SCHEMAS)

    def test_duplicate_json_fields_and_unknown_tools_are_not_silently_accepted(self):
        with self.assertRaisesRegex(ActionProtocolError, "DUPLICATE_FIELD"):
            parse_action('{"action":"finish","action":"tool","result":{}}', SCHEMAS)
        with self.assertRaisesRegex(ActionProtocolError, "TOOL_INVALID"):
            parse_action('{"action":"tool","name":"shell","arguments":{}}', SCHEMAS)

    def test_fenced_finish_is_a_candidate_not_tool_execution(self):
        batch = parse_action('```json\n{"action":"finish","result":{"verdict":"REPAIR_REQUIRED"}}\n```', SCHEMAS)
        self.assertEqual(batch.result["verdict"], "REPAIR_REQUIRED")
        self.assertFalse(batch.calls)

    def test_nonfinite_result_is_rejected_before_returning_a_candidate(self):
        with self.assertRaisesRegex(ActionProtocolError, "NONFINITE"):
            parse_action('{"action":"finish","result":{"value":1e999}}', SCHEMAS)

    def test_same_failure_without_progress_stops_before_another_correction(self):
        budget = CorrectionBudget()
        error = ActionProtocolError("HOST_ACTION_JSON_INVALID")
        self.assertTrue(budget.feedback(error, "unchanged")["retryable"])
        with self.assertRaisesRegex(RuntimeError, "CORRECTION_LIMIT"):
            budget.feedback(error, "unchanged")

    def test_changed_observations_do_not_reset_the_total_correction_limit(self):
        budget = CorrectionBudget()
        error = ActionProtocolError("HOST_ACTION_JSON_INVALID")
        budget.feedback(error, "first")
        budget.feedback(error, "second")
        with self.assertRaisesRegex(RuntimeError, "CORRECTION_LIMIT"):
            budget.feedback(error, "third")


class HostLoopContractTest(unittest.TestCase):
    def request(self):
        from agent_runtime import AgentRunRequest
        attempt = SimpleNamespace(check=Mock(), evidence=SimpleNamespace(record=Mock()), resource_uncertain=False)
        broker = SimpleNamespace(attempt=attempt, context={}, pending_question=None, _active=False,
                                 model_started=Mock(return_value="model-operation"), model_finished=Mock(),
                                 invoke=Mock(return_value={"text": "read result"}))
        return AgentRunRequest(attempt, ".", SimpleNamespace(writable=False), "Analyze the assigned files.",
                               "fixed-model", "medium", False, None, "read-only", "never", broker=broker)

    def test_bad_protocol_can_be_corrected_in_the_original_run(self):
        from guarded_text_runtime import run
        request = self.request()
        with patch.dict(os.environ, {"AIW_HOST_ACTION_PROTOCOL": "v2"}), \
                patch("app_server_runtime.CodexAppServerRuntime.run", side_effect=["invalid JSON", '{"action":"finish","result":{"verdict":"PASS"}}']) as model:
            result = run(request, Mock())
        self.assertEqual(json.loads(result)["verdict"], "PASS")
        self.assertEqual(model.call_count, 2)
        request.broker.invoke.assert_not_called()

    def test_read_failure_leaves_later_batch_calls_undispatched(self):
        from guarded_text_runtime import run
        request = self.request()
        request.broker.invoke.return_value = {"error": "GUARD_READ_NOT_FOUND"}
        batch = {"action": "tools", "calls": [
            {"id": "a", "name": "read", "arguments": {"path": "a", "offset": 1, "limit": 10}},
            {"id": "b", "name": "read", "arguments": {"path": "b", "offset": 1, "limit": 10}}]}
        with patch.dict(os.environ, {"AIW_HOST_ACTION_PROTOCOL": "v2"}), \
                patch("app_server_runtime.CodexAppServerRuntime.run", side_effect=[json.dumps(batch), '{"action":"finish","result":{"verdict":"REPAIR_REQUIRED"}}']):
            run(request, Mock())
        self.assertEqual(request.broker.invoke.call_count, 1)
        self.assertEqual(request.broker.invoke.call_args.args[1]["path"], "a")

    def test_denied_read_stops_the_run_instead_of_correcting_authority(self):
        from guarded_text_runtime import run
        request = self.request()
        request.broker.invoke.side_effect = RuntimeError("GUARD_PATH_DENIED")
        output = '{"action":"tool","name":"read","arguments":{"path":"private","offset":1,"limit":10}}'
        with patch.dict(os.environ, {"AIW_HOST_ACTION_PROTOCOL": "v2"}), \
                patch("app_server_runtime.CodexAppServerRuntime.run", return_value=output) as model:
            with self.assertRaisesRegex(RuntimeError, "GUARD_PATH_DENIED"):
                run(request, Mock())
        self.assertEqual(model.call_count, 1)


if __name__ == "__main__":
    unittest.main()
