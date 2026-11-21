"""Credential and profile boundaries for API providers; no model or network calls."""
import os
import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, Mock, patch

from api_connection import check_auth, runtime_type
from process_worker import choose_ai_profile


class ApiConnectionTest(unittest.TestCase):
    def test_openai_key_never_substitutes_for_missing_deepseek_key(self):
        with patch.dict(os.environ, {"AIW_API_PROVIDER": "deepseek", "OPENAI_API_KEY": "other-account"}, clear=True):
            with self.assertRaisesRegex(ValueError, "DEEPSEEK_API_KEY_REQUIRED"):
                check_auth()

    def test_chatgpt_connection_cannot_authenticate_deepseek_or_chat_completions(self):
        for settings in ({"AIW_API_PROVIDER": "deepseek"}, {"AIW_API_PROTOCOL": "chat-completions"}):
            with self.subTest(settings=settings), patch.dict(os.environ, {**settings, "AIW_MODEL_CREDENTIAL_REF": "MC-test"}, clear=True):
                with self.assertRaisesRegex(ValueError, "MODEL_CONNECTION_PROVIDER_MISMATCH"):
                    check_auth()

    def test_same_model_name_does_not_allow_cross_provider_profile_selection(self):
        guidance = {"allowedProfiles": [
            {"id": name, "profile": {"provider": name, "runtime": "openai-agents", "model": "same-model", "effort": "none", "fast": False}}
            for name in ("openai", "deepseek")
        ]}
        for name in ("openai", "deepseek"):
            with self.subTest(provider=name), patch.dict(os.environ, {"AIW_API_PROVIDER": name}, clear=True):
                selected, _ = choose_ai_profile(guidance, "openai-agents")
                self.assertEqual(selected["id"], name)

    def test_deepseek_identity_freezes_protocol_and_keeps_codex_legacy_identity(self):
        with patch.dict(os.environ, {"AIW_API_PROVIDER": "deepseek", "AIW_API_PROTOCOL": "chat-completions"}, clear=True):
            self.assertEqual(runtime_type(SimpleNamespace(runtime="openai-agents", process_role="goal-discussion")),
                             "openai-agents:deepseek:chat-completions:goal-discussion")
            self.assertEqual(runtime_type(SimpleNamespace(runtime="codex-app-server", process_role="coder")), "codex-app-server:coder")

    def test_unknown_protocol_fails_before_using_credentials(self):
        with patch.dict(os.environ, {"AIW_API_PROTOCOL": "auto", "DEEPSEEK_API_KEY": "unused"}, clear=True):
            with self.assertRaisesRegex(ValueError, "API_PROTOCOL_INVALID"):
                check_auth()

    def test_runtime_does_not_forward_deepseek_key_to_openai_custom_endpoint(self):
        from openai_api_runtime import OpenAIApiRuntime
        with patch.dict(os.environ, {"AIW_API_PROVIDER": "deepseek", "DEEPSEEK_API_KEY": "deepseek-key",
                "OPENAI_API_KEY": "other-key", "OPENAI_BASE_URL": "https://other-provider.invalid",
                "AIW_OPENAI_MODEL": "account-model"}, clear=True), patch("importlib.metadata.version", return_value="test"):
            config = OpenAIApiRuntime.from_env().config
            self.assertEqual(config.api_key, "deepseek-key")
            self.assertEqual(config.base_url, "https://api.deepseek.com")

    def test_chat_thinking_is_passed_to_the_native_message_adapter(self):
        from openai_api_runtime import _model_preferences
        config = SimpleNamespace(provider="deepseek", protocol="chat-completions", reasoning_effort="high")
        result = _model_preferences(SimpleNamespace(reasoning_effort=None), config)
        self.assertEqual(result["extra_args"]["reasoning_effort"], "high")

    def test_only_a_confirmed_preference_rejection_allows_fallback(self):
        from openai_api_runtime import ApiRuntimeConfig, OpenAIApiRuntime

        attempt = SimpleNamespace(check=Mock(), resource_uncertain=False)
        request = SimpleNamespace(attempt=attempt)
        runtime = OpenAIApiRuntime(ApiRuntimeConfig("", None, "test", "", 1000, 3, 1024, 1024, 1000))
        for status, uncertain, expected in ((400, False, "AI_PREFERENCES_REJECTED"),
                                            (404, True, "Model is not available"),
                                            (503, False, "Model is not available"),
                                            (None, False, "Model is not available")):
            with self.subTest(status=status, uncertain=uncertain):
                attempt.resource_uncertain = uncertain
                error = RuntimeError("Model is not available")
                error.status_code = status
                with patch.object(runtime, "_run", side_effect=error):
                    with self.assertRaisesRegex(RuntimeError, expected):
                        runtime.run(None, request, Mock())


class ConnectionExecutionTest(unittest.IsolatedAsyncioTestCase):
    async def test_oauth_adapter_preserves_a_json_report_and_rejects_invalid_actions(self):
        from automation_loop import ActionProtocolError
        from chatgpt_responses_runtime import sdk_model

        request = SimpleNamespace(attempt=SimpleNamespace(check=Mock()))
        read = SimpleNamespace(name="read", params_json_schema={"type": "object"})
        model = sdk_model(request, Mock())
        with patch("chatgpt_responses_runtime.ResponsesConversation") as transport:
            round_result = transport.return_value
            round_result.last_usage = None
            for text in ('{"choice":"accept","reason":"same record"}', '[{"title":"News"}]'):
                round_result.run_turn.return_value = text
                result = await model.get_response("", [], None, [read], None, [], None)
                self.assertEqual(result.output[0].content[0].text, text)
            round_result.run_turn.return_value = '{"action":"tool","name":"read","arguments":{}}'
            result = await model.get_response("", [], None, [read], None, [], None)
            self.assertEqual(result.output[0].name, "read")
            round_result.run_turn.return_value = '{"action":"tool","name":"unlisted","arguments":{}}'
            with self.assertRaisesRegex(ActionProtocolError, "HOST_ACTION_TOOL_INVALID"):
                await model.get_response("", [], None, [read], None, [], None)

    async def test_both_transports_mark_work_started_only_after_a_valid_response(self):
        from openai_api_runtime import ApiRuntimeConfig, ToolContext, _GuardedModel

        attempt = SimpleNamespace(check=Mock(), evidence=SimpleNamespace(record=Mock()))
        request = SimpleNamespace(attempt=attempt, broker=None)
        config = ApiRuntimeConfig("", None, "test", "", 1000, 3, 1024, 1024, 1000)
        valid = {"output": [{"type": "message", "status": "completed", "content": []}]}
        kwargs = dict(previous_response_id=None, conversation_id=None, prompt=None)
        for streamed in (False, True):
            with self.subTest(streamed=streamed):
                context = ToolContext(request, 1024, 1000)
                failed = True

                async def respond(*args, **kwargs):
                    self.assertFalse(context.work_started)
                    if failed:
                        raise RuntimeError("unsupported model")
                    return valid

                async def stream(*args, **kwargs):
                    yield {"type": "response.completed", "response": await respond()}

                model = _GuardedModel(SimpleNamespace(get_response=respond, stream_response=stream),
                                      request, config, context, {})
                model._prepare = AsyncMock(return_value=[])
                args = ("", [], None, [], None, [], None)
                with self.assertRaisesRegex(RuntimeError, "unsupported model"):
                    if streamed:
                        _ = [event async for event in model.stream_response(*args, **kwargs)]
                    else:
                        await model.get_response(*args, **kwargs)
                self.assertFalse(context.work_started)
                failed = False
                if streamed:
                    _ = [event async for event in model.stream_response(*args, **kwargs)]
                else:
                    await model.get_response(*args, **kwargs)
                self.assertTrue(context.work_started)


if __name__ == "__main__":
    unittest.main()
