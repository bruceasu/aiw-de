---
name: diagnosing-bugs
description: Diagnose a reported fault or unexplained failure using preserved evidence, bounded hypotheses and authorized experiments.
---

# Diagnosing Bugs

Use for a reported defect, regression or unclear failed check. Passing work does
not need a diagnosis stage. Keep the primary task, role, result format, tools and
remaining execution budget. Diagnosis alone does not authorize a fix or a run.

## Preserve the starting evidence

Record the expected and observed behavior, exact input or action, revision,
environment and available raw output. Cite an existing diagnosis before repeating
it. Keep the original failure and any diagnosis-only restriction visible. Ask for
a missing decisive fact through the existing question/gap path; do not fill it
with an invented reproduction.

## Follow the failure

1. Locate the first useful failing boundary and trace the relevant caller, data
   and error path. Inspect nearby contracts, code and supplied logs first.
2. Separate symptom from cause. Consider product behavior, test expectation,
   fixture/input and environment as distinct cause classes. A compile failure
   may block reproduction but is not proof of the reported runtime defect.
3. Keep only useful hypotheses. For each, name the supporting evidence,
   contradictory evidence and the observation that would distinguish it from
   another explanation. Do not force a fixed hypothesis count.
4. Use static evidence where sufficient. If a runtime observation is essential,
   propose the smallest discriminating experiment with exact input, expected
   outcomes, cost and stop condition. Run it only through a permitted adapter
   within the task's current authorization and remaining retry budget.
5. Compare actual output with those outcomes. Update the cause and confidence;
   preserve an inconclusive result as inconclusive. A check not run is a proposed
   next step. Do not install tools, bisect history, add instrumentation or start
   a broad check loop just because they might help.
6. When the cause is supported, identify the smallest candidate correction and
   the observable case that would verify it. In diagnosis-only mode stop at that
   recommendation. If repair is separately assigned, change only the affected
   scope and keep the original failure case; do not weaken an assertion or hide
   an error merely to obtain a passing result.

## Deliver the diagnosis

Use the primary output fields for symptom, expected behavior, evidence locations,
cause class, evidence chain, confidence, checks actually run, candidate correction
and remaining gaps. If unknown, name the next decisive observation and who or
which authorized tool can obtain it. Completion is a supported cause or an honest,
actionable limit, not a guessed root cause.

Keep exhausted retries and unknown operations in the handoff. This Skill does
not reset budgets, bypass read-only mode, expand task scope, run tests by default
or mark verification passed. Report a missing tool or authority through the
normal gap mechanism and continue only independent work that is already allowed.
