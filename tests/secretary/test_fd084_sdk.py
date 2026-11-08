"""SDK boundary doubles, not evidence of a real installed SDK or model."""
import json
from pathlib import Path
import sys
from types import ModuleType, SimpleNamespace
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "personal-secretary"))

import test_fd084_core as core
from secretary_credentials import ModelAuthRequired
from secretary_sdk import run
from secretary_runs import RunYield
from assistant_tools import ConfirmationRequired


class Item:
    def __init__(self, **values):
        self.values = values

    def model_dump(self, **_kwargs):
        def value(item):
            if isinstance(item, Item):
                return item.model_dump()
            if isinstance(item, list):
                return [value(v) for v in item]
            return item
        return {key: value(item) for key, item in self.values.items()}


class ModelResponse:
    def __init__(self, output, usage, response_id):
        self.output, self.usage, self.response_id = output, usage, response_id
        self.status, self.id = "completed", response_id


class FunctionTool:
    def __init__(self, name, description, params_json_schema, on_invoke_tool, strict_json_schema=False):
        self.name, self.on_invoke_tool = name, on_invoke_tool
        self.params_json_schema = params_json_schema


class SdkCase(unittest.TestCase):
    setUp = core.LocalCase.setUp
    start = core.LocalCase.start

    def sdk(self, response=None, error=None, swallow_errors=False):
        calls, clients = [], []
        class Client:
            def __init__(self, **kwargs):
                clients.append(kwargs)
                self.responses = self
            async def create(self, **kwargs):
                if error:
                    raise error
                return response
            async def close(self):
                pass
        class Provider:
            def __init__(self, **kwargs):
                pass
            def get_model(self, _name):
                return self
            async def get_response(self, *args, **kwargs):
                if error:
                    raise error
                return response
        class Agent:
            def __init__(self, **kwargs):
                self.__dict__.update(kwargs)
        class Runner:
            @staticmethod
            async def run(starting_agent, input, run_config=None, max_turns=None):
                result = await starting_agent.model.get_response(starting_agent.instructions, input,
                    starting_agent.model_settings, starting_agent.tools, None, [], None,
                    previous_response_id=None, conversation_id=None, prompt=None)
                for item in result.output:
                    data = item.model_dump()
                    if data["type"] == "function_call":
                        tool = next(t for t in starting_agent.tools if t.name == data["name"])
                        try:
                            await tool.on_invoke_tool(None, data["arguments"])
                        except Exception:
                            if not swallow_errors:
                                raise
                            # Simulate an SDK that turns a tool exception into
                            # feedback and attempts another model request.
                            await starting_agent.model.get_response(starting_agent.instructions, input,
                                starting_agent.model_settings, starting_agent.tools, None, [], None,
                                previous_response_id=None, conversation_id=None, prompt=None)
                return SimpleNamespace(final_output='{"action":"finish","completed":true,"text":"done"}')
        modules = {name: ModuleType(name) for name in ("agents", "agents.models", "agents.models.interface", "agents.items", "agents.usage", "openai", "openai.types", "openai.types.responses")}
        for module in modules.values():
            module.__path__ = []
        modules["agents"].__dict__.update(Agent=Agent, FunctionTool=FunctionTool, ModelSettings=lambda **kwargs: SimpleNamespace(**kwargs),
            OpenAIProvider=Provider, RunConfig=lambda **kwargs: SimpleNamespace(**kwargs), Runner=Runner, set_tracing_disabled=lambda _: None)
        modules["agents.models.interface"].Model = object
        modules["agents.items"].ModelResponse = ModelResponse
        modules["agents.usage"].Usage = lambda **kwargs: SimpleNamespace(**kwargs)
        modules["openai"].AsyncOpenAI = Client
        modules["openai"].DefaultAsyncHttpxClient = lambda **kwargs: SimpleNamespace(**kwargs)
        for name in ("ResponseFunctionToolCall", "ResponseOutputMessage", "ResponseOutputText"):
            setattr(modules["openai.types.responses"], name, Item)
        mocked = patch.dict(sys.modules, modules)
        mocked.start()
        self.addCleanup(mocked.stop)
        self.settings.value.update(key="legacy-test-key", endpoint="https://api.openai.com/v1")
        identity = self.start()
        execute = lambda action: calls.append(action) or {"text": "actual tool result"}
        return identity, execute, calls, clients

    @staticmethod
    def response(status="completed", arguments=None, call_id="call"):
        return ModelResponse(output=[Item(type="function_call", name="read_file", call_id=call_id, status=status,
            arguments=json.dumps(arguments or {"path": "C:/test/note.md"}))], usage=SimpleNamespace(input_tokens=10, output_tokens=5), response_id="response")

    def test_completed_response_is_dispatched_once_through_sdk(self):
        identity, execute, calls, _clients = self.sdk(self.response())
        result = run(self.settings, "instruction", {"request": "read"}, execute, self.journal, identity)
        self.assertEqual(json.loads(result)["text"], "done")
        self.assertEqual(len(calls), 1)
        self.assertEqual(self.journal.get(identity)["toolCalls"], 1)

    def test_incomplete_tool_item_never_executes(self):
        identity, execute, calls, _clients = self.sdk(self.response(status="incomplete"))
        with self.assertRaises(ValueError):
            run(self.settings, "instruction", {}, execute, self.journal, identity)
        self.assertEqual(calls, [])

    def test_incomplete_response_with_complete_tool_item_never_executes(self):
        response = self.response()
        response.status = "incomplete"
        identity, execute, calls, _clients = self.sdk(response)
        with self.assertRaises(ValueError):
            run(self.settings, "instruction", {}, execute, self.journal, identity)
        self.assertEqual(calls, [])

    def test_invalid_tool_arguments_never_execute(self):
        identity, execute, calls, _clients = self.sdk(self.response(arguments={"path": "target", "approved": True}))
        with self.assertRaises(ValueError):
            run(self.settings, "instruction", {}, execute, self.journal, identity)
        self.assertEqual(calls, [])

    def test_auth_error_does_not_invoke_tools_or_fallback(self):
        error = type("Rejected", (Exception,), {"status_code": 401})("rejected")
        identity, execute, calls, clients = self.sdk(error=error)
        with self.assertRaises(ModelAuthRequired):
            run(self.settings, "instruction", {}, execute, self.journal, identity)
        self.assertEqual(calls, [])
        self.assertEqual(len(clients), 1)

    def test_oauth_text_is_converted_to_sdk_tool_call_after_completion(self):
        identity, execute, calls, clients = self.sdk()
        self.settings.value.update(authMode="chatgpt", connectionId="local", connectionVersion=1)
        self.settings.connections = SimpleNamespace(access=lambda *args: {"token": "oauth-test-token"})
        self.journal.save(self.journal.get(identity), binding=self.journal.binding(self.settings.value))
        output = json.dumps({"action": "tool", "name": "read_file", "arguments": {"path": "C:/test/note.md"}})
        with patch("secretary_sdk.oauth_text", return_value=(output, {"input_tokens": 3, "output_tokens": 2}, "resp")) as transport:
            run(self.settings, "instruction", {}, execute, self.journal, identity)
        self.assertEqual(transport.call_count, 1)
        self.assertEqual(len(calls), 1)
        self.assertEqual(clients, [])

    def test_oauth_stream_failure_does_not_execute_tools(self):
        identity, execute, calls, clients = self.sdk()
        self.settings.value.update(authMode="chatgpt", connectionId="local", connectionVersion=1)
        self.settings.connections = SimpleNamespace(access=lambda *args: {"token": "oauth-test-token"})
        self.journal.save(self.journal.get(identity), binding=self.journal.binding(self.settings.value))
        with patch("secretary_sdk.oauth_text", side_effect=ValueError("incomplete")), self.assertRaises(ValueError):
            run(self.settings, "instruction", {}, execute, self.journal, identity)
        self.assertEqual(calls, [])
        self.assertEqual(clients, [])

    def test_swallowed_confirmation_still_blocks_next_model_call(self):
        identity, _execute, _calls, _clients = self.sdk(self.response(), swallow_errors=True)
        def waiting(action):
            raise ConfirmationRequired("confirm this exact action", action)
        with self.assertRaises(RunYield):
            run(self.settings, "instruction", {}, waiting, self.journal, identity)
        self.assertEqual(self.journal.get(identity)["modelCalls"], 1)
        self.assertTrue(self.journal.get(identity)["hasWaitingTool"])

    def test_swallowed_pause_does_not_start_another_model_request(self):
        identity, _execute, _calls, _clients = self.sdk(self.response(), swallow_errors=True)
        def pause(_action):
            self.journal.control({"runId": identity, "revision": self.journal.get(identity)["revision"], "control": "pause", "requestId": "pause-now"})
            self.journal.check(identity)
        with self.assertRaises(RunYield):
            run(self.settings, "instruction", {}, pause, self.journal, identity)
        self.assertEqual(self.journal.get(identity)["modelCalls"], 1)
        self.assertEqual(self.journal.get(identity)["state"], "PAUSED")


if __name__ == "__main__":
    unittest.main()
