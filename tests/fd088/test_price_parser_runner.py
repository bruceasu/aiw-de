"""Offline checks for the parser runner's bounded provider contract."""
import base64
import hashlib
import importlib.util
from pathlib import Path
import unittest
from types import SimpleNamespace
from unittest import mock
from test_deepseek_collector import PAGE

MODULE = Path(__file__).resolve().parents[2] / "workers" / "price-collectors" / "runner.py"
spec = importlib.util.spec_from_file_location("price_parser_runner", MODULE)
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class PriceParserRunnerCases(unittest.TestCase):
    def request(self):
        return {"protocol": "aiw.price-parser-request.v1", "provider": "deepseek", "observedMs": 123456,
                "contentBase64": base64.b64encode(PAGE).decode("ascii")}

    def test_known_provider_returns_observation_only(self):
        result = runner.execute(self.request())
        self.assertEqual("deepseek", result["provider"])
        self.assertFalse(result["accountingReady"])
        source = MODULE.parent / "deepseek.py"
        self.assertEqual(hashlib.sha256(source.read_bytes()).hexdigest(), result["collectorSha256"])

    def test_invalid_request_never_selects_arbitrary_file(self):
        request = self.request()
        request["provider"] = "../../other"
        with self.assertRaises(ValueError):
            runner.execute(request)
        request = self.request()
        request["unexpected"] = "value"
        with self.assertRaises(ValueError):
            runner.execute(request)

    def test_invalid_encoding_and_duplicate_fields_are_rejected(self):
        request = self.request()
        request["contentBase64"] = "not base64!"
        with self.assertRaises(ValueError):
            runner.execute(request)
        with self.assertRaises(ValueError):
            runner._unique([("provider", "deepseek"), ("provider", "other")])

    def test_collect_uses_installed_module_for_fetch_and_parse(self):
        module = SimpleNamespace(SourceFetchError=RuntimeError, _aiw_collector_sha256="a" * 64,
                                 fetch=lambda: (PAGE, 123456, "https://api-docs.deepseek.com/new-pricing"),
                                 parse=lambda content, timestamp, source: {"source": source, "observed": timestamp})
        with mock.patch.object(runner, "_load", return_value=module):
            result, encoded = runner.collect({"protocol": "aiw.price-collector-request.v1", "provider": "deepseek"})
        self.assertEqual("https://api-docs.deepseek.com/new-pricing", result["source"])
        self.assertEqual(PAGE, base64.b64decode(encoded))

    def test_fetch_failure_is_not_a_parse_failure(self):
        def fail():
            raise RuntimeError("source unavailable")
        module = SimpleNamespace(SourceFetchError=RuntimeError, fetch=fail)
        with mock.patch.object(runner, "_load", return_value=module):
            with self.assertRaises(runner.FetchFailed):
                runner.collect({"protocol": "aiw.price-collector-request.v1", "provider": "deepseek"})

    def test_structure_failure_retains_only_bounded_public_source_sample(self):
        class Changed(ValueError):
            pass
        def fail(content, timestamp, source):
            raise Changed("PRICE_ROWS_CHANGED")
        module = SimpleNamespace(SourceFetchError=RuntimeError, PriceStructureError=Changed, VERSION="v1", _aiw_collector_sha256="a" * 64,
                                 fetch=lambda: (PAGE, 123456, "https://api-docs.deepseek.com/pricing"), parse=fail)
        with mock.patch.object(runner, "_load", return_value=module):
            with self.assertRaises(runner.StructureChanged) as caught:
                runner.collect({"protocol": "aiw.price-collector-request.v1", "provider": "deepseek"})
        self.assertEqual(PAGE, caught.exception.content)
        self.assertEqual("PRICE_ROWS_CHANGED", caught.exception.reason)
        self.assertEqual("a" * 64, caught.exception.collector_sha)


if __name__ == "__main__":
    unittest.main()
