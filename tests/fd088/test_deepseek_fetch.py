"""Offline, mocked tests for the source-owned public page fetch."""
from types import SimpleNamespace
import unittest
from unittest import mock
from test_deepseek_collector import PAGE, deepseek


class Response:
    status = 200
    headers = SimpleNamespace(get_content_type=lambda: "text/html", get=lambda name: str(len(PAGE)))

    def __enter__(self): return self
    def __exit__(self, *args): return False
    def geturl(self): return "https://api-docs.deepseek.com/new-pricing"
    def read(self, limit): return PAGE[:limit]


class DeepSeekFetchCases(unittest.TestCase):
    def test_fetch_uses_source_module_and_returns_actual_official_path(self):
        opener = SimpleNamespace(open=lambda request, timeout: Response())
        with mock.patch.object(deepseek, "build_opener", return_value=opener):
            content, observed, source = deepseek.fetch()
        self.assertEqual(PAGE, content)
        self.assertGreater(observed, 0)
        self.assertEqual("https://api-docs.deepseek.com/new-pricing", source)

    def test_rejects_nonofficial_redirect_without_network(self):
        handler = deepseek._OfficialRedirect()
        with self.assertRaises(deepseek.SourceFetchError):
            handler.redirect_request(None, None, 302, "", {}, "https://example.invalid/pricing")

    def test_rejects_invalid_source_without_network(self):
        with mock.patch.object(deepseek, "SOURCE", "http://api-docs.deepseek.com/pricing"):
            with self.assertRaises(deepseek.SourceFetchError):
                deepseek.fetch()


if __name__ == "__main__":
    unittest.main()
