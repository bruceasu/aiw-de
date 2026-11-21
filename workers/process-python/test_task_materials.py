import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace

from context_window import Sources, sha


def material(content, kind="knowledge", title="Policy"):
    return {"kind": kind, "title": title, "content": content, "digest": sha(content)}


class TaskMaterialsTest(unittest.TestCase):
    def test_initial_manifest_has_citations_but_not_the_body(self):
        sources = Sources()
        body = "private reference body"
        manifest = sources.materials([material(body)])
        source = "knowledge-" + sha(body)
        self.assertIn(source, manifest)
        self.assertNotIn(body, manifest)
        self.assertEqual(sources.page(source, 0, 8192)["text"], body)
        self.assertEqual(sources.page(source, 0, 8192)["sha256"], sha(body))

    def test_method_and_fact_sources_remain_distinct(self):
        sources = Sources()
        body = "same text"
        sources.materials([material(body, "skill", "Method"), material(body)])
        self.assertIn("skill-" + sha(body), sources.values)
        self.assertIn("knowledge-" + sha(body), sources.values)

    def test_changed_or_oversized_sources_are_rejected(self):
        source = material("original")
        source["content"] = "changed"
        with self.assertRaisesRegex(RuntimeError, "TASK_MATERIALS_INVALID"):
            Sources().materials([source])
        with self.assertRaisesRegex(RuntimeError, "TASK_MATERIALS_INVALID"):
            Sources().materials([material("x" * 131073)])
        with self.assertRaisesRegex(RuntimeError, "TASK_MATERIALS_INVALID"):
            Sources().materials([material("x")] * 9)

    def test_search_is_bounded_and_does_not_read_other_attempts(self):
        sources = Sources()
        for i in range(3):
            sources.add("Policy " + str(i) + "：东京天气晴。", "knowledge")
        other = Sources()
        other.add("private other task", "knowledge")
        result = sources.search("东京", 2)
        self.assertEqual(len(result["matches"]), 2)
        for match in result["matches"]:
            self.assertIn("东京", match["text"])
            self.assertIn(match["sourceId"], sources.values)
        self.assertEqual(sources.search("private other task")["matches"], [])

    def test_empty_materials_do_not_change_the_prompt(self):
        self.assertEqual(Sources().materials(()), "")

    def test_sdk_only_offers_tools_supported_by_frozen_and_current_permissions(self):
        from guarded_openai_tools import tools

        rule = lambda kind: {"tool": kind, "repositoryRef": "workspace", "pathPrefix": "."}
        broker = SimpleNamespace(writable=True, shell_enabled=lambda: False, attempt=SimpleNamespace(skill_files=None),
            allowed_tools={"READ", "WRITE", "QUESTION"}, context={
                "snapshot": {"task": {"repositoryRef": "workspace", "baseRevision": "rev"}},
                "originalPolicy": {"rules": list(map(rule, ["READ", "WRITE", "QUESTION", "COMMAND"])), "commands": [{"id": "test"}]},
                "currentPolicy": {"rules": list(map(rule, ["READ", "QUESTION", "COMMAND"])), "commands": [{"id": "test"}]},
            })
        self.assertEqual({tool.name for tool in tools(broker)}, {"read", "ls", "ask_question"})
        broker.attempt.skill_files = object()
        self.assertIn("read_skill_file", {tool.name for tool in tools(broker)})

    def test_sdk_checks_path_overlap_and_actual_git_workspace(self):
        from guarded_openai_tools import tools

        with TemporaryDirectory() as directory:
            scope = {"repositoryRef": "workspace", "pathPrefix": "."}
            read = {**scope, "tool": "READ", "pathPrefix": "src"}
            git = {**scope, "tool": "GIT"}
            shell = {**scope, "tool": "SHELL"}
            broker = SimpleNamespace(writable=True, shell_enabled=lambda: True,
                root=Path(directory), allowed_tools=None, attempt=SimpleNamespace(skill_files=None), context={
                    "snapshot": {"task": {"repositoryRef": "workspace", "baseRevision": "rev"}},
                    "originalPolicy": {"rules": [read, git, shell]},
                    "currentPolicy": {"rules": [{**read, "pathPrefix": "docs"}, git, shell]},
                })
            self.assertEqual({tool.name for tool in tools(broker)}, {"run_shell"})
            broker.context["currentPolicy"]["rules"][0] = {**read, "pathPrefix": "src/main", "baseRevision": "other"}
            self.assertEqual({tool.name for tool in tools(broker)}, {"run_shell"})
            broker.context["currentPolicy"]["rules"][0]["baseRevision"] = "rev"
            self.assertEqual({tool.name for tool in tools(broker)}, {"run_shell", "read", "ls"})
            (broker.root / ".git").write_text("gitdir: linked", encoding="utf-8")
            self.assertIn("git_status", {tool.name for tool in tools(broker)})

    def test_no_frozen_skill_bundle_needs_no_skill_tools(self):
        from skill_files import openai_tools

        self.assertEqual(openai_tools(SimpleNamespace(attempt=SimpleNamespace(skill_files=None))), [])


if __name__ == "__main__":
    unittest.main()
