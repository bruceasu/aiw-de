"""Offline coverage for secretary model-directory and credential selection."""
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "personal-secretary"))

from model_catalog import list_models
from assistant_settings import AssistantSettings


class Response:
    def __enter__(self):
        return self

    def __exit__(self, *_args):
        pass

    def read(self, _size):
        return json.dumps({"data": [{"id": "deepseek-flash"}, {"id": "deepseek-flash"}]}).encode()


class ModelCatalogTest(unittest.TestCase):
    def test_deepseek_environment_is_used_without_exposing_key(self):
        settings = SimpleNamespace(value={}, connections=None)
        requests = []
        opener = SimpleNamespace(open=lambda request, timeout: requests.append(request) or Response())
        with patch.dict("os.environ", {"DEEPSEEK_API_KEY": "deepseek-secret", "OPENAI_API_KEY": "wrong-secret"}):
            with patch("model_catalog.urllib.request.build_opener", return_value=opener):
                result = list_models(settings, {"service": "deepseek", "endpoint": "https://api.deepseek.com", "credentialSource": "environment"})
        self.assertEqual(result["models"], ["deepseek-flash"])
        self.assertEqual(requests[0].full_url, "https://api.deepseek.com/models")
        self.assertEqual(requests[0].get_header("Authorization"), "Bearer deepseek-secret")
        self.assertNotIn("deepseek-secret", json.dumps(result))

    def test_wrong_connection_endpoint_never_sends_key(self):
        binding = {"connectionId": "selected", "connectionVersion": 2, "endpoint": "https://elsewhere.example/v1"}
        connections = SimpleNamespace(binding=lambda *_args: binding,
            access=lambda *_args: {"token": "secret", "baseUrl": "https://elsewhere.example/v1"})
        with patch("model_catalog.urllib.request.build_opener") as opener:
            with self.assertRaises(ValueError):
                list_models(SimpleNamespace(value={}, connections=connections), {"service": "deepseek", "connectionId": "selected"})
        opener.assert_not_called()

    def test_deepseek_v1_base_uses_official_root_catalog(self):
        settings = SimpleNamespace(value={}, connections=None)
        requests = []
        opener = SimpleNamespace(open=lambda request, timeout: requests.append(request) or Response())
        with patch.dict("os.environ", {"DEEPSEEK_API_KEY": "private-key"}, clear=True):
            with patch("model_catalog.urllib.request.build_opener", return_value=opener):
                list_models(settings, {"service": "deepseek", "endpoint": "https://api.deepseek.com/v1", "credentialSource": "environment"})
        self.assertEqual(requests[0].full_url, "https://api.deepseek.com/models")

    def test_environment_and_selected_connection_are_not_mixed(self):
        with self.assertRaises(ValueError):
            list_models(SimpleNamespace(value={}, connections=None), {"service": "deepseek", "connectionId": "selected", "credentialSource": "environment"})

    def test_environment_is_not_used_when_current_source_is_connection(self):
        settings = SimpleNamespace(value={"endpoint": "https://api.deepseek.com", "credentialSource": "environment"}, connections=None)
        with patch.dict("os.environ", {"DEEPSEEK_API_KEY": "private-key"}, clear=True):
            with patch("model_catalog.urllib.request.build_opener") as opener:
                with self.assertRaises(ValueError):
                    list_models(settings, {"service": "deepseek", "endpoint": "https://api.deepseek.com", "credentialSource": ""})
        opener.assert_not_called()

    def test_catalog_accepts_new_model_ids_without_code_changes(self):
        response = Response()
        response.read = lambda _size: json.dumps({"data": [{"id": "future-model-2027"}]}).encode()
        opener = SimpleNamespace(open=lambda *_args, **_kwargs: response)
        with patch.dict("os.environ", {"DEEPSEEK_API_KEY": "private-key"}, clear=True):
            with patch("model_catalog.urllib.request.build_opener", return_value=opener):
                result = list_models(SimpleNamespace(value={}, connections=None), {"service": "deepseek",
                    "endpoint": "https://api.deepseek.com", "credentialSource": "environment"})
        self.assertEqual(result["models"], ["future-model-2027"])

    def test_save_reports_selected_missing_environment_variable(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict("os.environ", {}, clear=True):
            settings = AssistantSettings(directory)
            with self.assertRaisesRegex(ValueError, "DEEPSEEK_API_KEY"):
                settings.save({"provider": "openai-agents", "authMode": "api-key", "credentialSource": "environment",
                    "endpoint": "https://api.deepseek.com", "model": "deepseek-flash", "roots": []})
            self.assertFalse(settings.public()["environmentKeyAvailable"]["DEEPSEEK_API_KEY"])

    def test_saved_environment_availability_does_not_expose_its_value(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict("os.environ", {"DEEPSEEK_API_KEY": "private-key"}, clear=True):
            settings = AssistantSettings(directory)
            settings.save({"provider": "openai-agents", "authMode": "api-key", "credentialSource": "environment",
                "endpoint": "https://api.deepseek.com", "model": "deepseek-flash", "roots": []})
            state = settings.public()
            self.assertTrue(state["environmentKeyAvailable"]["DEEPSEEK_API_KEY"])
            self.assertTrue(state["configured"])
            self.assertNotIn("private-key", json.dumps(state))


if __name__ == "__main__":
    unittest.main()
