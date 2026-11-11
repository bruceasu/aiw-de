"""Offline samples for OpenAI Markdown price observation, without a live API."""
import importlib.util
from pathlib import Path
import unittest

MODULE = Path(__file__).resolve().parents[2] / "workers" / "price-collectors" / "openai.py"
spec = importlib.util.spec_from_file_location("openai_price_collector", MODULE)
openai = importlib.util.module_from_spec(spec)
spec.loader.exec_module(openai)

PAGE = b"""# Pricing
Prices per 1M tokens.
## Standard
| Model | Input | Output | Cached input |
| --- | --- | --- | --- |
| sample-model | $2.00 | $10.00 | $0.20 |
| sample-pro | $30.00 | $180.00 | - |

## Long context
| Model | Short context input | Short context output | Short context cached input | Short context 30m write | Short context 12h write | Long context input | Long context output | Long context cached input | Long context 30m write | Long context 12h write |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sample-long | $2.00 | $10.00 | $0.20 | $2.50 | $5.00 | $4.00 | $15.00 | $0.40 | $5.00 | $10.00 |
"""


class OpenAiCollectorCases(unittest.TestCase):
    def test_observes_columns_without_guessing_tiers_or_effective_dates(self):
        result = openai.parse(PAGE, 123456)
        self.assertFalse(result["accountingReady"])
        self.assertIn("SERVICE_TIER_AND_PRICING_CONDITIONS", result["missing"])
        self.assertEqual(2_000_000, result["models"][0]["rows"][0]["rates"][0])
        self.assertIsNone(result["models"][0]["rows"][1]["rates"][2])
        self.assertEqual("Short context input", result["models"][1]["columns"][1])

    def test_rejects_changed_unit_rows_and_nonofficial_source(self):
        for content in (PAGE.replace(b"per 1M tokens", b"per 1K tokens"), PAGE.replace(b"$2.00", b"unknown")):
            with self.assertRaises(openai.PriceStructureError):
                openai.parse(content, 123456)
        with self.assertRaises(openai.PriceStructureError):
            openai.parse(PAGE, 123456, "https://example.invalid/pricing")


if __name__ == "__main__":
    unittest.main()
