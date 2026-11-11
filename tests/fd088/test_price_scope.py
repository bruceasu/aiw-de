"""Offline endpoint classification; no client or network is created."""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "workers" / "process-python"))
from api_connection import pricing_scope, effective_service_tier


class PriceScopeCases(unittest.TestCase):
    def test_official_standard_text_endpoints_can_match_prices(self):
        self.assertEqual("TEXT_ONLY", pricing_scope("openai", None, False))
        self.assertEqual("TEXT_ONLY", pricing_scope("openai", "https://api.openai.com/v1/", False))
        self.assertEqual("TEXT_ONLY", pricing_scope("deepseek", "https://api.deepseek.com", False))

    def test_regional_proxies_images_and_ambiguous_urls_are_not_priced(self):
        for provider, url, images in [
            ("openai", "https://eu.api.openai.com/v1", False),
            ("openai", "https://example.invalid/v1", False),
            ("openai", "https://api.openai.com/v1?region=eu", False),
            ("openai", "https://api.openai.com.evil.invalid/v1", False),
            ("openai", "http://api.openai.com/v1", False),
            ("deepseek", "https://api.deepseek.com/custom", False),
            ("openai", None, True),
        ]:
            with self.subTest(provider=provider, url=url, images=images):
                self.assertEqual("UNSUPPORTED", pricing_scope(provider, url, images))

    def test_openai_requested_tier_is_not_assumed_to_be_effective_tier(self):
        self.assertIsNone(effective_service_tier(None))
        self.assertIsNone(effective_service_tier("auto"))
        self.assertIsNone(effective_service_tier("unknown"))
        self.assertEqual("priority", effective_service_tier("priority"))
