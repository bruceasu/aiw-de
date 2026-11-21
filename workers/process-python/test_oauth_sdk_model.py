"""SDK interface doubles; no model, credentials, service, or network."""
import asyncio
import http.client
import io
import json
import sys
import urllib.error
from types import ModuleType, SimpleNamespace
import unittest
from unittest.mock import Mock, patch

import chatgpt_responses_runtime as oauth
from automation_loop import ActionProtocolError


class Item:
    def __init__(self, **fields):
        self.__dict__.update(fields)


def modules():
    names = ("agents", "agents.models", "agents.models.interface", "agents.items", "agents.usage",
             "openai", "openai.types", "openai.types.responses", "openai_api_runtime")
    result = {name: ModuleType(name) for name in names}
    for module in result.values():
        module.__path__ = []
    result["agents.models.interface"].Model = type("Model", (), {})
    result["agents.items"].ModelResponse = Item
    result["agents.usage"].Usage = Item
    for name in ("ResponseFunctionToolCall", "ResponseOutputMessage", "ResponseOutputText"):
        setattr(result["openai.types.responses"], name, Item)
    result["openai_api_runtime"]._json_value = lambda value: value
    return result


class OAuthSdkModelTest(unittest.TestCase):
    def test_stream_requires_completed_output_to_match_all_deltas(self):
        request = SimpleNamespace(model_images=[], credential_provider=object(), attempt=SimpleNamespace(check=lambda: None))
        conversation = oauth.ResponsesConversation(request, lambda *_args: None)
        def stream(delta, final):
            events = [dict(type='response.output_text.delta', delta=delta),
                      dict(type='response.completed', response={'status': 'completed', 'output': [
                          {'type': 'message', 'status': 'completed', 'content': [{'type': 'output_text', 'text': final}]}]})]
            return io.BytesIO(''.join('data: ' + json.dumps(event) + '\n\n' for event in events).encode())
        self.assertEqual(conversation._stream(stream('answer', 'answer'))[0], 'answer')
        with self.assertRaisesRegex(RuntimeError, 'MODEL_STREAM_TERMINAL_MISMATCH'):
            conversation._stream(stream('answer', 'different'))

    def test_work_report_can_fill_the_shared_report_file_budget(self):
        from plan_workspace import REPORT_FILE_MAX_BYTES

        request = SimpleNamespace(model_images=[], model_only=False, credential_provider=object(),
                                  attempt=SimpleNamespace(check=lambda: None))
        conversation = oauth.ResponsesConversation(request, Mock())
        report = "a" * REPORT_FILE_MAX_BYTES
        events = ({"type": "response.output_text.delta", "delta": report},
                  {"type": "response.completed", "response": {"status": "completed", "output": [{
                      "type": "message", "status": "completed", "content": [{"type": "output_text", "text": report}]
                  }]}})
        stream = io.BytesIO("".join("data: " + json.dumps(event) + "\n\n" for event in events).encode())
        self.assertEqual(conversation._stream(stream)[0], report)
        request.model_only = True
        stream.seek(0)
        with patch.dict("os.environ", {"AIW_AGENT_MAX_OUTPUT_CHARS": "24000"}):
            with self.assertRaisesRegex(RuntimeError, "MODEL_(OUTPUT|STREAM)_LIMIT"):
                conversation._stream(stream)

    def test_oauth_preserves_known_rejection_and_only_safe_preference_detail(self):
        from ai_preferences import preference_rejected
        from worker_runtime import failure_code

        attempt = SimpleNamespace(check=Mock(), evidence=SimpleNamespace(record=Mock()), remaining=lambda: 20)
        credential = SimpleNamespace(access=lambda _scope: {"accessToken": "dummy"})
        request = SimpleNamespace(model_images=[], credential_provider=credential, attempt=attempt,
                                  model="example", reasoning_effort="default")
        conversation = oauth.ResponsesConversation(request, Mock())
        for status, message, preference in ((400, "Model unsupported (private body)", True),
                                             (422, "Invalid request (private body)", False),
                                             (503, "Model unavailable (private body)", False)):
            with self.subTest(status=status):
                body = io.BytesIO(json.dumps({"error": {"message": message}}).encode())
                rejection = urllib.error.HTTPError("https://example.invalid", status, "private error", {}, body)
                with patch.object(oauth.urllib.request, "build_opener") as opener:
                    opener.return_value.open.side_effect = rejection
                    with self.assertRaises(RuntimeError) as result:
                        conversation.run_turn("Task")
                self.assertEqual(failure_code(result.exception, "MODEL_ERROR"), f"MODEL_API_HTTP_{status}")
                self.assertEqual(preference_rejected(result.exception), preference)
                self.assertNotIn("private", str(result.exception))
                self.assertTrue(body.closed)

        class TruncatedBody(io.BytesIO):
            def read(self, *_args):
                raise http.client.IncompleteRead(b"", 2)

        with patch.object(oauth.urllib.request, "build_opener") as opener:
            opener.return_value.open.side_effect = urllib.error.HTTPError(
                "https://example.invalid", 404, "private error", {}, TruncatedBody())
            with self.assertRaisesRegex(RuntimeError, "^MODEL_API_HTTP_404$"):
                conversation.run_turn("Task")

    def invoke(self, output, tools):
        calls = []
        request = SimpleNamespace(model_images=[], credential_provider=object(), attempt=SimpleNamespace(check=lambda: None))
        class Conversation:
            last_usage = None
            def __init__(self, request, on_session):
                pass
            def run_turn(self, prompt, parts, system_instructions, record_terminal):
                calls.append((prompt, system_instructions, record_terminal))
                return output
        with patch.dict(sys.modules, modules()), patch.object(oauth, "ResponsesConversation", Conversation):
            model = oauth.sdk_model(request, lambda *_args: None)
            result = asyncio.run(model.get_response("trusted policy", [{"role": "user", "content": "task"}],
                None, tools, None, [], None))
        return result, calls

    def test_completed_text_action_becomes_sdk_tool_call(self):
        tool = SimpleNamespace(name="read", params_json_schema={"type": "object", "properties": {"path": {"type": "string"}}})
        result, calls = self.invoke('{"action":"tool","name":"read","arguments":{"path":"a"}}', [tool])
        self.assertEqual(result.output[0].type, "function_call")
        self.assertEqual(result.output[0].name, "read")
        self.assertEqual(json.loads(result.output[0].arguments), {"path": "a"})
        self.assertTrue(calls[0][1].startswith("trusted policy"))
        self.assertIn('"content": "task"', calls[0][0])
        self.assertFalse(calls[0][2])

    def test_undeclared_action_is_rejected_before_sdk_dispatch(self):
        tool = SimpleNamespace(name="read", params_json_schema={"type": "object"})
        with self.assertRaises(ActionProtocolError):
            self.invoke('{"action":"tool","name":"write","arguments":{}}', [tool])

    def test_model_only_plain_text_does_not_create_tools(self):
        result, _ = self.invoke("answer", [])
        self.assertEqual(result.output[0].type, "message")
        self.assertEqual(result.output[0].content[0].text, "answer")

    def test_plain_final_answer_with_tools_is_not_dispatched_as_a_tool(self):
        tool = SimpleNamespace(name="read", params_json_schema={"type": "object"})
        result, _ = self.invoke("No changes are required.", [tool])
        self.assertEqual(result.output[0].type, "message")
        self.assertEqual(result.output[0].content[0].text, "No changes are required.")

    def test_invalid_structured_action_is_not_treated_as_final_text(self):
        tool = SimpleNamespace(name="read", params_json_schema={"type": "object"})
        with self.assertRaises(ActionProtocolError):
            self.invoke('{"action":"tool"', [tool])
