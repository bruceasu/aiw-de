import os
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from openai_api_runtime import _boolean_setting, _model_preferences


class ParallelToolCallSettingTest(unittest.TestCase):
    def test_defaults_to_parallel(self):
        with patch.dict(os.environ, {}, clear=True):
            self.assertTrue(_boolean_setting("AIW_OPENAI_PARALLEL_TOOL_CALLS", True))

    def test_accepts_explicit_boolean_values_case_insensitively(self):
        for value, expected in (("true", True), (" TRUE ", True), ("false", False), ("False", False)):
            with self.subTest(value=value), patch.dict(os.environ, {"AIW_OPENAI_PARALLEL_TOOL_CALLS": value}):
                self.assertEqual(_boolean_setting("AIW_OPENAI_PARALLEL_TOOL_CALLS", True), expected)

    def test_rejects_invalid_values(self):
        for value in ("", "yes", "1"):
            with self.subTest(value=value), patch.dict(os.environ, {"AIW_OPENAI_PARALLEL_TOOL_CALLS": value}):
                with self.assertRaisesRegex(RuntimeError, "OPENAI_PARALLEL_TOOL_CALLS_INVALID"):
                    _boolean_setting("AIW_OPENAI_PARALLEL_TOOL_CALLS", True)

    def test_setting_flows_to_responses_preferences(self):
        config = SimpleNamespace(
            provider="openai", protocol="responses", reasoning_effort="",
            parallel_tool_calls=False,
        )
        result = _model_preferences(SimpleNamespace(reasoning_effort=None), config)
        self.assertFalse(result["parallel_tool_calls"])


if __name__ == "__main__":
    unittest.main()
