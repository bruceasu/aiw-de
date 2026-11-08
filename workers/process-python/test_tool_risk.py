"""Risk decisions only; this does not grant authority or run tools."""
import unittest
from tool_risk import Decision, Facts, classify


class ToolRiskTest(unittest.TestCase):
    def test_workspace_routine_operations(self):
        for facts in (Facts("read", inside_workspace=True),
                      Facts("create", inside_workspace=True, new_file=True),
                      Facts("edit", inside_workspace=True, task_scope_verified=True, version_matches=True),
                      Facts("delete", inside_workspace=True, multiple_or_recursive=False, version_matches=True,
                            task_created_or_generated=True),
                      Facts("delete", inside_workspace=True, multiple_or_recursive=False, version_matches=True,
                            task_scope_verified=True, tracked_and_clean=True)):
            with self.subTest(facts=facts):
                self.assertEqual(classify(facts), Decision.ALLOW)

    def test_unknown_and_irreversible_operations_need_confirmation(self):
        for facts in (Facts("read"), Facts("read", inside_workspace=False),
                      Facts("edit", inside_workspace=True, task_scope_verified=True, version_matches=False),
                      Facts("delete", inside_workspace=True, multiple_or_recursive=True, version_matches=True,
                            task_created_or_generated=True),
                      Facts("delete", inside_workspace=True, multiple_or_recursive=False, version_matches=True,
                            task_scope_verified=True, tracked_and_clean=False),
                      Facts("replace", inside_workspace=True), Facts("command", inside_workspace=True),
                      Facts("publish", inside_workspace=True)):
            with self.subTest(facts=facts):
                self.assertEqual(classify(facts), Decision.CONFIRM)
