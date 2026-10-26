"""Offline protocol regression. No CLI, filesystem tools, network or model calls."""
import pathlib
import sys
import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[2] / "workers/process-python"))
import app_server_runtime as runtime
import json
from skill_files import Reader
from process_worker import _workspace_context, _validate_review_revision


class CodexCommandFailuresTest(unittest.TestCase):
    def run_turn(self, tool, *, terminal_only=False, model_only=False):
        attempt = Mock()
        attempt.remaining.return_value = 30
        request = SimpleNamespace(attempt=attempt, broker=None, profile=SimpleNamespace(writable=False),
            approval_mode="never", service_tier=None, sandbox="read-only", workspace="empty-workspace",
            model=None, model_only=model_only, reasoning_effort="medium", instruction="Review this output.")
        session = Mock()
        session.process.poll.return_value = 1
        def rpc(method, params):
            return {
                "initialize": {"userAgent": "offline-test"},
                "account/read": {"account": {"type": "chatgpt"}},
                "thread/start": {"thread": {"id": "thread", "ephemeral": True, "path": None},
                    "cwd": request.workspace, "approvalPolicy": "never",
                    "sandbox": {"type": "readOnly", "networkAccess": False}},
                "thread/read": {"thread": {"id": "thread", "ephemeral": True, "path": None}},
                "turn/start": {"turn": {"id": "turn"}},
            }[method]
        session.rpc.side_effect = rpc
        def event(method, **params):
            return {"method": method, "params": {"threadId": "thread", "turnId": "turn", **params}}
        answer = {"type": "agentMessage", "id": "answer", "text": "Review completed.\nAIW_VERDICT: PASS",
                  "phase": "final_answer"}
        session.next_event.side_effect = ([event("item/completed", item=tool)] if not terminal_only else []) + [
            event("turn/completed", turn={"id": "turn", "status": "completed", "items": [tool, answer]})]
        with patch.object(runtime, "StdioSession", return_value=session):
            try:
                return runtime.CodexAppServerRuntime().run(None, request, Mock()), attempt
            finally:
                session.close.assert_called_once()
                attempt.remove_cancel_callback.assert_called_once()

    def test_nonzero_command_result_can_be_followed_by_a_completed_model_turn(self):
        for terminal_only in (False, True):
            with self.subTest(terminal_only=terminal_only):
                text, attempt = self.run_turn({"type": "commandExecution", "status": "failed", "exitCode": 1},
                                              terminal_only=terminal_only)
                self.assertIn("AIW_VERDICT: PASS", text)
                self.assertTrue(any(call.args[0] == "tool-terminal" for call in attempt.evidence.record.call_args_list))

    def test_approval_denials_and_cancellations_still_fail(self):
        for status in ("declined", "cancelled"):
            for terminal_only in (False, True):
                with self.subTest(status=status, terminal_only=terminal_only):
                    with self.assertRaisesRegex(RuntimeError, "CODEX_TOOL_FAILED"):
                        self.run_turn({"type": "commandExecution", "status": status, "exitCode": 1},
                                      terminal_only=terminal_only)

    def test_unknown_failures_and_other_tool_failures_still_fail(self):
        for tool in ({"type": "commandExecution", "status": "failed"},
                     {"type": "fileChange", "status": "failed", "exitCode": 1}):
            with self.subTest(tool=tool):
                with self.assertRaisesRegex(RuntimeError, "CODEX_TOOL_FAILED"):
                    self.run_turn(tool)

    def test_undeclared_skill_lookup_returns_an_error_without_reading_outside_the_manifest(self):
        reader = Reader({("approved", "SKILL.md"): b"declared"}, {}, ())
        result = json.loads(runtime.skill_file_output(reader,
            {"ref": "approved", "path": "other.md", "offset": 0, "limit": 8192}))
        self.assertEqual("SKILL_FILE_NOT_DECLARED", result["error"])
        self.assertFalse(result["read"])
        valid = json.loads(runtime.skill_file_output(reader,
            {"ref": "approved", "path": "SKILL.md", "offset": 0, "limit": 8192}))
        self.assertEqual("base64", valid["encoding"])
        self.assertEqual(8, valid["size"])

    def test_skill_integrity_or_range_errors_remain_terminal(self):
        reader = Reader({("approved", "SKILL.md"): b"declared"}, {}, ())
        with self.assertRaisesRegex(RuntimeError, "SKILL_FILES_INVALID_OR_CHANGED"):
            runtime.skill_file_output(reader,
                {"ref": "approved", "path": "SKILL.md", "offset": -1, "limit": 8192})

    def test_context_names_the_materialized_attempt_directory_without_using_old_report_paths(self):
        cwd = "D:/assigned/worktrees/current-attempt"
        task = {"workspace": {"repositoryRef": "example/root", "baseRevision": "a" * 40,
                              "repositoryPath": "D:/original-checkout"},
                "inputs": [{"summary": "Old workspace: D:/deleted-old-worktree"}]}
        text = _workspace_context(task, cwd)
        self.assertIn(json.dumps({"cwd": cwd, "repositoryRef": "example/root", "baseRevision": "a" * 40}), text)
        self.assertNotIn("D:/original-checkout", text)
        self.assertNotIn("D:/deleted-old-worktree", text)
        self.assertIn("relative target file paths", text)

    def test_native_review_checks_current_work_output_and_allows_historical_context(self):
        task = {"taskId": "FP-test~code~review-5", "subjectType": "flexible-plan",
                "workspace": {"repositoryRef": "repo/root"}, "inputs": [
                    {"taskId": "FP-test~planning~work-1", "resultRevision": "a" * 40},
                    {"taskId": {"value": "FP-test~code~work-5"}, "repositoryRef": "repo/root", "resultRevision": "b" * 40}]}
        _validate_review_revision(task, "b" * 40)
        task["taskId"] += "-resume-1"
        _validate_review_revision(task, "b" * 40)
        for field, value in (("resultRevision", "a" * 40), ("repositoryRef", "wrong/root")):
            modified = {**task, "inputs": [task["inputs"][0], {**task["inputs"][1], field: value}]}
            with self.subTest(field=field), self.assertRaisesRegex(RuntimeError, "UPSTREAM_REVISION_MISMATCH"):
                _validate_review_revision(modified, "b" * 40)
        with self.assertRaisesRegex(RuntimeError, "UPSTREAM_REVISION_MISMATCH"):
            _validate_review_revision({**task, "inputs": task["inputs"][:1]}, "b" * 40)

    def test_other_verdict_revision_checks_remain_strict(self):
        with self.assertRaisesRegex(RuntimeError, "UPSTREAM_REVISION_MISMATCH"):
            _validate_review_revision({"subjectType": "other", "inputs": [{"resultRevision": "a" * 40}]}, "b" * 40)


if __name__ == "__main__":
    unittest.main()
