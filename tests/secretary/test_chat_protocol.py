"""SDK protocol conversion with simulated HTTP results; never calls a model."""
from pathlib import Path
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import AsyncMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "personal-secretary"))
from secretary_sdk import chat_completion
from assistant_settings import AssistantSettings


class ChatProtocolTest(unittest.IsolatedAsyncioTestCase):
    def test_old_chat_entry_moves_to_sdk_without_silently_selecting_environment_credentials(self):
        value = {"provider": "chat", "endpoint": "https://custom.example/v1", "model": "future-model"}
        with patch("assistant_settings.read_private", return_value=value), patch("assistant_settings.ModelConnections"), patch("assistant_settings.KnowledgeSettings"):
            settings = AssistantSettings(Path("unused-settings-directory"))
        self.assertEqual("openai-agents", settings.value["provider"])
        self.assertEqual("chat-completions", settings.value["apiProtocol"])
        self.assertEqual("api-key", settings.value["authMode"])
        self.assertNotIn("credentialSource", settings.value)
        self.assertFalse(settings.configured())

    def response(self, finish="tool_calls", usage=True):
        from openai.types.chat import ChatCompletion
        value = {"id": "chat-test", "object": "chat.completion", "created": 1, "model": "future-model",
            "choices": [{"index": 0, "finish_reason": finish, "message": {"role": "assistant", "content": None,
                "reasoning_content": "Private reasoning", "tool_calls": [{"id": "call-test", "type": "function",
                    "function": {"name": "search", "arguments": '{"query":"weather"}'}}]}}]}
        if usage:
            value["usage"] = {"prompt_tokens": 20, "completion_tokens": 10, "total_tokens": 30}
        return ChatCompletion.model_validate(value)

    async def test_chat_uses_sdk_history_conversion_and_retains_deepseek_reasoning(self):
        create = AsyncMock(return_value=self.response())
        client = SimpleNamespace(base_url="https://api.deepseek.com/v1", chat=SimpleNamespace(completions=SimpleNamespace(create=create)))
        config = {"model": "future-model", "effort": "none"}
        response, known = await chat_completion(client, config, "Use declared tools", [{"role": "user", "content": "Weather?"}], {"search": {"type": "object"}})
        self.assertTrue(known)
        params = create.call_args.kwargs
        self.assertNotIn("parallel_tool_calls", params)
        self.assertEqual({"thinking": {"type": "disabled"}}, params["extra_body"])
        self.assertEqual("search", params["tools"][0]["function"]["name"])
        history = [item.model_dump(exclude_none=True) for item in response.output]
        history.append({"type": "function_call_output", "call_id": "call-test", "output": "Sunny"})
        await chat_completion(client, config, "Use declared tools", history, {})
        assistant = next(message for message in create.call_args.kwargs["messages"] if message["role"] == "assistant")
        self.assertEqual("Private reasoning", assistant["reasoning_content"])

    async def test_incomplete_tool_batch_is_rejected_before_conversion(self):
        create = AsyncMock(return_value=self.response("length"))
        client = SimpleNamespace(base_url="https://api.example/v1", chat=SimpleNamespace(completions=SimpleNamespace(create=create)))
        with patch("agents.models.chatcmpl_converter.Converter.message_to_output_items") as convert:
            with self.assertRaises(ValueError):
                await chat_completion(client, {"model": "future-model"}, "", [], {})
        convert.assert_not_called()

    async def test_missing_usage_stays_unknown_and_default_omits_effort(self):
        create = AsyncMock(return_value=self.response(usage=False))
        client = SimpleNamespace(base_url="https://api.example/v1", chat=SimpleNamespace(completions=SimpleNamespace(create=create)))
        _, known = await chat_completion(client, {"model": "future-model"}, "", [], {})
        self.assertFalse(known)
        self.assertNotIn("reasoning_effort", create.call_args.kwargs)
        self.assertNotIn("extra_body", create.call_args.kwargs)
