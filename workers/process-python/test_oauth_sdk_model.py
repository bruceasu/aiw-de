"""SDK interface doubles; no model, credentials, service, or network."""
import asyncio
import io
import json
import sys
from types import ModuleType, SimpleNamespace
import unittest
from unittest.mock import patch

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
