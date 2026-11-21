"""Bound repeated read results without interpreting task content or retrying effects."""
from __future__ import annotations

import hashlib
import json


READ_TOOLS = frozenset({"read", "ls", "grep", "search", "read_context_source", "search_context_sources", "read_skill_file", "read_skill_text", "git_status", "git_diff"})
REMINDER = (
    "Several identical read operations returned the same result. Reconsider the next step "
    "using the evidence already collected. If more investigation is needed, explain what "
    "new information it should provide. Otherwise work toward the requested result, "
    "or report the specific obstacle. Do not repeat a side effect."
)


READ_PHASE_REMINDER = (
    "Several turns collected information without taking a verification or delivery step. "
    "Use the results to choose the next check that could confirm or disprove your current explanation. "
    "If the evidence is already sufficient, proceed to the requested work. Otherwise name the specific missing evidence or blocker."
)

WORK_METHOD = (
    "\n\nExecution method: Identify the requested result and how to check it. "
    "For a small task, start with the most useful check. For a larger task, keep a short list of steps. "
    "For several changes in one file, read the relevant sections and group changes with edit_many when available. "
    "After editing, verify the affected behavior once and finish. Read again only for a specific missing fact or a changed file version. "
    "Use each tool result to decide what to do next. A failed check is evidence: read the failure, make the smallest relevant change, and run the check again when authorized. "
    "At delivery, give the actual result, checks or sources used, and any remaining gap in the required output format. "
    "If a permission, input, or environment is missing, state exactly what is needed. Do not claim a check was run without a tool result."
)

class ProgressMonitor:
    def __init__(self, evidence, threshold=3):
        self.evidence = evidence
        self.threshold = threshold
        self.seen = set()
        self.results = set()
        self.context = frozenset()
        self.repetitions = 0
        self.warned = False
        self.may_have_changed = False
        self.delivery_reminded = False
        self.read_batches = 0
        self.check_reminded = False
        self.read_phase_reminded = False
        self.read_phase_hint = None

    def review_read_batch(self, names):
        if not names:
            return None
        if any(name not in READ_TOOLS for name in names):
            self.read_batches = 0
            return None
        self.read_batches += 1
        if self.read_batches < 4 or self.read_phase_reminded:
            return None
        self.read_phase_reminded = True
        self.evidence.record("progress-stalled", status="possible", count=self.read_batches, code="READ_PHASE_CHECK")
        return READ_PHASE_REMINDER

    def _reset(self):
        self.results.clear()
        self.repetitions = 0
        self.warned = False

    def observe(self, items, context=(), *, writable=False):
        self.read_phase_hint = None
        if not isinstance(items, list):
            return False
        context = frozenset(context)
        if context != self.context:
            self._reset()
            self.context = context
            self.read_batches = 0
        calls = {item.get("call_id"): item for item in items if isinstance(item, dict)
                 and item.get("type") == "function_call" and item.get("call_id")}
        warned_before = self.warned
        observed, last_tool, names = False, None, []
        for item in items:
            if not isinstance(item, dict) or item.get("type") not in {"function_call_output", "function_call_result"}:
                continue
            call_id = item.get("call_id")
            call = calls.get(call_id)
            if not call_id or call_id in self.seen or not call:
                continue
            self.seen.add(call_id)
            name = call.get("name")
            if name == "update_work_checklist":
                continue
            observed = True
            names.append(name)
            output = item.get("output")
            if isinstance(output, str):
                try:
                    output = json.loads(output)
                except (ValueError, RecursionError):
                    pass
            if name not in READ_TOOLS:
                if isinstance(output, dict) and output.get("status") == "error" and output.get("error") and output.get("hint"):
                    # The broker confirmed failure. This does not establish a workspace change.
                    continue
                # Unknown tools may have side effects. Never tell the model that
                # work was not performed when the host cannot establish that.
                self.may_have_changed = True
                self._reset()
                warned_before = False
                continue
            arguments = call.get("arguments")
            if isinstance(arguments, str):
                try:
                    arguments = json.loads(arguments)
                except (ValueError, RecursionError):
                    pass
            raw = json.dumps([name, arguments, output],
                             ensure_ascii=False, sort_keys=True, default=str).encode("utf-8")
            signature = hashlib.sha256(raw).digest()
            last_tool = name
            if signature in self.results:
                self.repetitions += 1
            else:
                self.results.add(signature)
                self.repetitions = 0
                self.warned = False
                warned_before = False
        if writable:
            self.read_phase_hint = self.review_read_batch(names)
        # Decide after the whole batch: later reads or effects may provide progress.
        if not observed or self.repetitions < self.threshold:
            return False
        if warned_before and self.repetitions >= 2 * self.threshold:
            self.evidence.record("progress-stalled", status="failed", count=self.repetitions,
                                 tool=last_tool, code="MODEL_NO_PROGRESS")
            raise RuntimeError("MODEL_NO_PROGRESS")
        if not self.warned:
            self.evidence.record("progress-stalled", status="possible", count=self.repetitions,
                                 tool=last_tool, code="REPEATED_READ_RESULT")
            self.warned = True
            return True
        return False

    def check_feedback(self, commands, verdict):
        if verdict != "PASS" or not commands or self.check_reminded:
            return None
        last = commands[-1]
        if last.get("exitCode") == 0:
            return None
        self.check_reminded = True
        self.evidence.record("progress-review", status="possible", code="LAST_COMMAND_NOT_PASSED")
        return (
            "The last recorded command did not exit successfully. Check whether it was required for delivery. "
            "If it was required, correct the underlying problem and rerun the check when authorized, or report what blocks it. "
            "If the failure was expected or unrelated, explain that and cite the actual evidence. Do not repeat a side effect."
        )

    def delivery_feedback(self, writable, verdict):
        if not writable or verdict != "PASS" or self.may_have_changed or self.delivery_reminded:
            return None
        self.delivery_reminded = True
        self.evidence.record("progress-review", status="possible", code="NO_OBSERVED_CHANGE")
        return (
            "This task allows changes, but no change was observed through the tools in this run. "
            "Check the original task and its completion conditions. If work is still needed, "
            "continue using the available tools. If no change is needed, explain the evidence. "
            "If you cannot complete the task, report the obstacle and what remains. "
            "Do not invent evidence or make unnecessary changes."
        )
