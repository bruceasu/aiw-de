"""Candidate validation before sealing inputs. Business report semantics stay injected."""
from __future__ import annotations

import os


CORRECTABLE = frozenset({"COMPLETION_REPORT_EMPTY", "COMPLETION_VERDICT_INVALID", "COMPLETION_REPORT_CONFLICT"})


def correction_limit():
    value = os.environ.get("AIW_COMPLETION_CORRECTIONS", "0")
    if value not in {"0", "1", "2"}:
        raise RuntimeError("COMPLETION_CORRECTION_SETTING_INVALID")
    return int(value)


class CompletionValidator:
    def __init__(self, validate, evidence, maximum=2):
        if type(maximum) is not int or not 0 <= maximum <= 2:
            raise ValueError("Completion corrections must be between zero and two")
        self.validate = validate
        self.evidence = evidence
        self.maximum = maximum
        self.used = 0
        self.seen = set()
        self.last_verdict = None

    def feedback(self, summary, progress):
        self.last_verdict = None
        if self.validate is None:
            return None
        try:
            report = self.validate(summary)
            if not isinstance(report, dict) or report.get("verdict") not in {"PASS", "REPAIR_REQUIRED"}:
                raise RuntimeError("COMPLETION_VALIDATOR_INVALID")
        except RuntimeError as error:
            code = str(error)
            if code not in CORRECTABLE:
                raise
            if self.maximum == 0:
                raise
            key = (code, progress)
            if self.used >= self.maximum or key in self.seen:
                raise RuntimeError("COMPLETION_CORRECTION_LIMIT") from error
            self.used += 1
            self.seen.add(key)
            self.evidence.record("validation-failed", code=code, count=self.used, phase="candidate", status="correctable")
            return ("The candidate report failed validation: " + code + ". Correct the required output format and keep "
                    "the original task and authority. Preserve unresolved or blocked findings. Do not change "
                    "REPAIR_REQUIRED to PASS merely to satisfy validation. Do not repeat successful side effects.")
        self.last_verdict = report["verdict"]
        self.evidence.record("validation-passed", verdict=report["verdict"], count=self.used, phase="candidate", status="valid")
        return None


def no_change_evidence(result, observations):
    """Require a reason and references to actual successful reads, not invented tool claims."""
    value = result.get("noChange")
    if not isinstance(value, dict) or set(value) != {"reason", "evidence"}:
        return False
    reason, references = value["reason"], value["evidence"]
    if not isinstance(reason, str) or not reason.strip() or len(reason) > 2000:
        return False
    if not isinstance(references, list) or not 1 <= len(references) <= 16 or any(not isinstance(ref, str) for ref in references):
        return False
    actual = {item["callId"] for item in observations if item.get("callId")
              and item.get("action", {}).get("name") in {"read", "grep", "ls", "read_skill_file", "read_skill_text"}
              and isinstance(item.get("result"), dict) and "error" not in item["result"]}
    return len(set(references)) == len(references) and set(references).issubset(actual)
