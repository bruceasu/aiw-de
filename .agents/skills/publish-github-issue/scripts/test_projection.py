import tempfile
import unittest
from pathlib import Path

from projection import (
    END_MARKER,
    START_MARKER,
    load_mapping,
    render_projection,
    replace_managed_block,
    save_mapping,
)


class ProjectionTests(unittest.TestCase):
    def test_render_and_replace_preserve_human_content(self):
        generated = render_projection(
            "demo",
            "Build the thing.",
            "Only the backend.",
            ["A user can run it."],
            ["- [x] Implement", "- [ ] Verify"],
        )
        existing = "Human context.\n\n" + generated + "\n\nHuman follow-up."
        updated = render_projection("demo", "Updated goal.", "Only the backend.", [], ["- [x] Done"])
        merged = replace_managed_block(existing, updated)
        self.assertIn("Human context.", merged)
        self.assertIn("Human follow-up.", merged)
        self.assertIn("Updated goal.", merged)
        self.assertNotIn("Build the thing.", merged)
        self.assertEqual(merged.count(START_MARKER), 1)
        self.assertEqual(merged.count(END_MARKER), 1)

    def test_incomplete_markers_are_rejected(self):
        with self.assertRaises(ValueError):
            replace_managed_block(START_MARKER + "\npartial", "generated")

    def test_mapping_round_trip_and_validation(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "external" / "github.json"
            mapping = {
                "version": 1,
                "repository": "owner/repo",
                "issue_number": 12,
                "url": "https://github.com/owner/repo/issues/12",
            }
            save_mapping(path, mapping)
            self.assertEqual(load_mapping(path), mapping)

            path.write_text('{"version": 1}', encoding="utf-8")
            with self.assertRaises(ValueError):
                load_mapping(path)


if __name__ == "__main__":
    unittest.main()
