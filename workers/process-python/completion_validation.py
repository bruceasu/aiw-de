"""Candidate validation before sealing inputs. Business report semantics stay injected."""
from __future__ import annotations


CORRECTABLE = frozenset({"COMPLETION_REPORT_EMPTY", "COMPLETION_VERDICT_INVALID", "COMPLETION_REPORT_CONFLICT"})


class CompletionValidator:
    def __init__(self, validate, evidence, maximum=2):
        if type(maximum) is not int or not 0 <= maximum <= 2:
            raise ValueError("Completion corrections must be between zero and two")
        self.validate = validate
        self.evidence = evidence
        self.maximum = maximum
        self.used = 0
        self.seen = set()

    def feedback(self, summary, progress):
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
            key = (code, progress)
            if self.used >= self.maximum or key in self.seen:
                raise RuntimeError("COMPLETION_CORRECTION_LIMIT") from error
            self.used += 1
            self.seen.add(key)
            self.evidence.record("validation-failed", code=code, count=self.used, phase="candidate", status="correctable")
            return ("The candidate report failed validation: " + code + ". Correct the required output format and keep "
                    "the original task and authority. Preserve unresolved or blocked findings. Do not change "
                    "REPAIR_REQUIRED to PASS merely to satisfy validation. Do not repeat successful side effects.")
        self.evidence.record("validation-passed", verdict=report["verdict"], count=self.used, phase="candidate", status="valid")
        return None
