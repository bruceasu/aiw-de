import unittest
from unittest.mock import Mock
from html.parser import HTMLParser
from pathlib import Path

from model_catalog import read_catalog, CatalogCredential


class ModelCatalogTest(unittest.TestCase):
    def test_only_model_list_rpc_is_used_for_pagination(self):
        session = Mock()
        session.rpc.side_effect = [
            {"data": [{"model": "model-b", "displayName": "B", "private": "do-not-copy"}], "nextCursor": "next"},
            {"data": [{"model": "model-a", "hidden": True}, {"model": "model-c", "supportedReasoningEfforts": [{"reasoningEffort": "high"}]}], "nextCursor": None},
        ]
        result = read_catalog(session)
        self.assertEqual([row["model"] for row in result["data"]], ["model-b", "model-c"])
        self.assertNotIn("private", result["data"][0])
        self.assertTrue(all(call.args[0] == "model/list" for call in session.rpc.call_args_list))
        self.assertEqual(session.rpc.call_args_list[1].args[1]["cursor"], "next")

    def test_cursor_cycle_is_rejected_without_an_unbounded_probe(self):
        session = Mock()
        session.rpc.return_value = {"data": [], "nextCursor": "same"}
        with self.assertRaisesRegex(RuntimeError, "CURSOR_INVALID"):
            read_catalog(session)
        self.assertEqual(session.rpc.call_count, 2)

    def test_probe_does_not_refresh_externally_managed_tokens(self):
        source = CatalogCredential({"accessToken": "access", "accountId": "account"})
        with self.assertRaisesRegex(RuntimeError, "LOGIN_REQUIRED"):
            source.codex_refresh({"id": 1})

    def test_login_choices_exist_in_the_template_before_api_initialization(self):
        class Options(HTMLParser):
            def __init__(self):
                super().__init__()
                self.active = False
                self.values = set()

            def handle_starttag(self, tag, attrs):
                attrs = dict(attrs)
                if tag == "select":
                    self.active = attrs.get("id") == "factory-auth-mode"
                elif tag == "option" and self.active:
                    self.values.add(attrs.get("value"))

            def handle_endtag(self, tag):
                if tag == "select":
                    self.active = False

        parser = Options()
        template = Path(__file__).resolve().parents[2] / "src/main/resources/templates/ui/agent-factory.html"
        parser.feed(template.read_text(encoding="utf-8"))
        self.assertTrue({"codex-local", "codex-plane", "chatgpt", "api-key"}.issubset(parser.values))


if __name__ == "__main__":
    unittest.main()
