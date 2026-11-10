"""Detect repeated identical read results without interpreting task content."""
from __future__ import annotations

import hashlib
import json


READ_TOOLS = frozenset({"read", "ls", "grep", "search", "read_context_source", "read_skill_file", "read_skill_text", "git_status", "git_diff"})
REMINDER = (
    "Several identical read operations returned the same result. Reconsider the next step "
    "using the evidence already collected. If more investigation is needed, explain what "
    "new information it should provide. Otherwise work toward the requested result, "
    "or report the specific obstacle. Do not repeat a side effect."
)


class ProgressMonitor:
    def __init__(self, evidence, threshold=3):
        self.evidence = evidence
        self.threshold = threshold
        self.seen = set()
        self.previous = None
        self.repetitions = 0
        self.may_have_changed = False
        self.delivery_reminded = False

    def observe(self, items):
        if not isinstance(items, list):
            return False
        calls = {item.get("call_id"): item for item in items if isinstance(item, dict)
                 and item.get("type") == "function_call" and item.get("call_id")}
        remind = False
        for item in items:
            if not isinstance(item, dict) or item.get("type") not in {"function_call_output", "function_call_result"}:
                continue
            call_id = item.get("call_id")
            call = calls.get(call_id)
            if not call_id or call_id in self.seen or not call:
                continue
            self.seen.add(call_id)
            name = call.get("name")
            if name not in READ_TOOLS:
                # Unknown tools may have side effects. Never tell the model that
                # work was not performed when the host cannot establish that.
                self.may_have_changed = True
                self.previous, self.repetitions = None, 0
                continue
            raw = json.dumps([name, call.get("arguments"), item.get("output")],
                             ensure_ascii=False, sort_keys=True, default=str).encode("utf-8")
            signature = hashlib.sha256(raw).digest()
            self.repetitions = self.repetitions + 1 if signature == self.previous else 1
            self.previous = signature
            if self.repetitions >= self.threshold:
                self.evidence.record("progress-stalled", status="possible", count=self.repetitions,
                                     code="REPEATED_READ_RESULT")
                self.repetitions = 0
                remind = True
        return remind

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
