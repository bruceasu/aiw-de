import unittest
from process_profiles import PROFILES, get_profile


class ProcessProfilesTest(unittest.TestCase):
    def test_r9_executable_roles_cover_all_current_task_types(self):
        active = (profile for name, profile in PROFILES.items() if name not in {"release-readiness", "orchestrator"})
        covered = {task for profile in active for task in profile.task_types}
        self.assertEqual(
            covered,
            {
                "REQUIREMENT_ANALYSIS", "SOLUTION_ARCHITECTURE", "IMPLEMENTATION_PLANNING",
                "IMPLEMENTATION", "INTEGRATION", "CODE_REVIEW", "VERIFICATION",
                "INTEGRATION_VERIFICATION", "REPAIR",
            },
        )

    def test_semantic_roles_are_read_only(self):
        for name in ("requirements", "architect", "planner", "reviewer", "verifier", "release-readiness", "orchestrator"):
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
        self.assertEqual(get_profile("release-readiness").primary_skill, "release-readiness-manager")
        self.assertEqual(get_profile("orchestrator").primary_skill, "software-development-orchestrator")

    def test_verifier_uses_integration_profile_for_integration_tasks(self):
        self.assertEqual(get_profile("verifier", "INTEGRATION_VERIFICATION"), get_profile("integration-verifier"))
        self.assertIn("integration scope", get_profile("verifier", "INTEGRATION_VERIFICATION").instruction)

    def test_python_profiles_include_instructions(self):
        self.assertEqual(len(PROFILES), 11)
        for profile in PROFILES.values():
            self.assertTrue(profile.instruction)
            self.assertEqual(profile.protocol_skills, ("engineering-artifact-protocol",))


if __name__ == "__main__":
    unittest.main()
