import json
import unittest

from process_worker import _completion_report


class CompletionReportTest(unittest.TestCase):
    def test_report_preserves_explicit_unresolved_verdict(self):
        summary = json.dumps({
            "clarification": {"status": "UNRESOLVED"},
            "verdict": "REPAIR_REQUIRED",
        })
        self.assertEqual(_completion_report("REPORT", summary), {
            "verdict": "REPAIR_REQUIRED", "summary": summary,
        })

    def test_fenced_report_preserves_verdict_and_original_summary(self):
        for fence in ("json", "JSON", ""):
            summary = '```' + fence + '\r\n{"verdict":"REPAIR_REQUIRED"}\r\n```'
            with self.subTest(fence=fence):
                self.assertEqual(_completion_report("REPORT", summary), {
                    "verdict": "REPAIR_REQUIRED", "summary": summary,
                })

    def test_successful_report_and_plain_text_remain_supported(self):
        for summary in ('{"verdict":"PASS"}', 'The previous review said REPAIR_REQUIRED; the report is now complete.'):
            with self.subTest(summary=summary):
                self.assertEqual(_completion_report("REPORT", summary)["verdict"], "PASS")

    def test_invalid_explicit_report_verdict_is_not_success(self):
        for verdict in ("UNKNOWN", "", None, True, [], {}):
            with self.subTest(verdict=verdict):
                with self.assertRaisesRegex(RuntimeError, "COMPLETION_VERDICT_INVALID"):
                    _completion_report("REPORT", json.dumps({"verdict": verdict}))

    def test_quoted_example_inside_prose_is_not_the_report_verdict(self):
        summary = 'Example of a failed result:\n```json\n{"verdict":"REPAIR_REQUIRED"}\n```\nThis report is complete.'
        self.assertEqual(_completion_report("REPORT", summary)["verdict"], "PASS")

    def test_blocked_delivery_is_rejected_with_or_without_a_fence(self):
        summary = '{"outcome":"BLOCKED","completed":false}'
        for content in (summary, '```json\n' + summary + '\n```'):
            with self.subTest(content=content):
                self.assertEqual(_completion_report("DELIVERY", content)["verdict"], "REPAIR_REQUIRED")

    def test_review_marker_still_controls_verdict_reports(self):
        self.assertEqual(_completion_report("VERDICT", "Findings.\nAIW_VERDICT: REPAIR_REQUIRED")["verdict"],
                         "REPAIR_REQUIRED")

    def test_explicit_first_line_blocked_status_is_not_success(self):
        for kind in ("REPORT", "DELIVERY"):
            for summary in ("**结果：`BLOCKED_BEFORE_IMPLEMENTATION`**\nNo files changed.",
                            "Result: BLOCKED\nNo files changed.", "**Status:** `INCOMPLETE`"):
                with self.subTest(kind=kind, summary=summary):
                    self.assertEqual(_completion_report(kind, summary)["verdict"], "REPAIR_REQUIRED")

    def test_blocked_result_is_not_inferred_from_an_example_or_a_past_failure(self):
        for summary in ("The previous result was BLOCKED; this task is complete.",
                        "Example:\nResult: BLOCKED"):
            with self.subTest(summary=summary):
                self.assertEqual(_completion_report("DELIVERY", summary)["verdict"], "PASS")

    def test_report_with_explicit_incomplete_outcome_is_not_success(self):
        for kind in ("REPORT", "DELIVERY"):
            with self.subTest(kind=kind):
                self.assertEqual(_completion_report(kind, '{"completed":false}')["verdict"], "REPAIR_REQUIRED")

    def test_success_verdict_cannot_override_a_blocked_outcome(self):
        with self.assertRaisesRegex(RuntimeError, "COMPLETION_REPORT_CONFLICT"):
            _completion_report("REPORT", '{"verdict":"PASS","outcome":"BLOCKED"}')


if __name__ == "__main__":
    unittest.main()
