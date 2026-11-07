import unittest
from unittest.mock import Mock

from completion_validation import CompletionValidator


class CompletionValidationTest(unittest.TestCase):
    def test_repair_required_remains_a_valid_report_without_rewriting_it(self):
        validate = Mock(return_value={"verdict": "REPAIR_REQUIRED", "summary": "Blocked on an unanswered question."})
        evidence = Mock()
        policy = CompletionValidator(validate, evidence)
        self.assertIsNone(policy.feedback("original report", "unchanged"))
        validate.assert_called_once_with("original report")
        self.assertEqual(evidence.record.call_args.kwargs["verdict"], "REPAIR_REQUIRED")

    def test_candidate_format_is_corrected_before_a_caller_can_seal(self):
        validate = Mock(side_effect=[RuntimeError("COMPLETION_VERDICT_INVALID"), {"verdict": "PASS"}])
        policy = CompletionValidator(validate, Mock())
        self.assertIn("COMPLETION_VERDICT_INVALID", policy.feedback("bad", "unchanged"))
        self.assertIsNone(policy.feedback("fixed", "unchanged"))

    def test_same_error_without_new_observations_stops(self):
        policy = CompletionValidator(Mock(side_effect=RuntimeError("COMPLETION_REPORT_CONFLICT")), Mock())
        policy.feedback("first", "unchanged")
        with self.assertRaisesRegex(RuntimeError, "COMPLETION_CORRECTION_LIMIT"):
            policy.feedback("second", "unchanged")

    def test_authority_or_effect_failures_are_never_report_format_corrections(self):
        for code in ("AUTHORIZATION_REQUIRED", "LEASE_LOST", "UNKNOWN_EFFECT"):
            with self.subTest(code=code):
                policy = CompletionValidator(Mock(side_effect=RuntimeError(code)), Mock())
                with self.assertRaisesRegex(RuntimeError, code):
                    policy.feedback("report", "unchanged")

    def test_absent_callback_keeps_plain_model_transport_behavior(self):
        evidence = Mock()
        self.assertIsNone(CompletionValidator(None, evidence).feedback("text", "unchanged"))
        evidence.record.assert_not_called()

    def test_zero_corrections_still_validates_and_preserves_the_original_failure(self):
        policy = CompletionValidator(Mock(side_effect=RuntimeError('COMPLETION_REPORT_CONFLICT')), Mock(), 0)
        with self.assertRaisesRegex(RuntimeError, '^COMPLETION_REPORT_CONFLICT$'):
            policy.feedback('invalid report', 'unchanged')
        self.assertEqual(policy.used, 0)


if __name__ == "__main__":
    unittest.main()
