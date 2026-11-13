import unittest

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


if __name__ == "__main__":
    unittest.main()
