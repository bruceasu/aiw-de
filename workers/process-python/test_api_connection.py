"""Credential and profile boundaries for API providers; no model or network calls."""
import os
import unittest
from types import SimpleNamespace
from unittest.mock import patch

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


if __name__ == "__main__":
    unittest.main()
