import unittest
from process_profiles import PROFILES, get_profile


class ProcessProfilesTest(unittest.TestCase):
    def test_r9_executable_roles_cover_all_current_task_types(self):
        active = PROFILES.values()
        covered = {task for profile in active for task in profile.task_types}
        self.assertEqual(covered, {"EXTERNAL_WORK"})

    def test_semantic_roles_are_read_only(self):
        for name in ("requirements", "architect", "planner", "reviewer", "stage-reviewer", "verifier"):
            self.assertFalse(PROFILES[name].writable, name)

    def test_code_mutating_roles_are_writable(self):
        for name in ("coder", "integrator", "repairer"):
            self.assertTrue(PROFILES[name].writable, name)

    def test_r2_primary_skills_are_preserved(self):
        self.assertEqual(get_profile("requirements").primary_skill, "software-requirements-analyst")
        self.assertEqual(get_profile("architect").primary_skill, "solution-architect")
        self.assertEqual(get_profile("planner").primary_skill, "implementation-planner")
        self.assertEqual(get_profile("reviewer").primary_skill, "software-code-reviewer")
        self.assertEqual(get_profile("verifier").primary_skill, "software-test-verifier")
        self.assertEqual(get_profile("stage-reviewer").primary_skill, "workflow-stage-reviewer")
        with self.assertRaises(ValueError):
            get_profile("release-readiness")

    def test_verifier_does_not_switch_roles_for_old_task_types(self):
        self.assertEqual(get_profile("verifier", "INTEGRATION_VERIFICATION"), get_profile("verifier"))
        self.assertIn("integration scope", get_profile("integration-verifier").instruction)

    def test_python_profiles_include_instructions(self):
        self.assertEqual(len(PROFILES), 10)
        for profile in PROFILES.values():
            self.assertTrue(profile.instruction)
            self.assertEqual(profile.protocol_skills, ("engineering-artifact-protocol",))


if __name__ == "__main__":
    unittest.main()
