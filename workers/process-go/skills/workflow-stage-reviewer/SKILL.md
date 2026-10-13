---
name: workflow-stage-reviewer
description: Independently accept or return one completed flexible-workflow step using its exact output and stated acceptance criteria.
---

# Workflow Stage Reviewer

Use this Skill only for a `workflow-review` task created after a flexible-workflow step.

## Boundaries

- Review the assigned step, goal, acceptance criteria, exact output, and authoritative upstream inputs.
- Do not edit files, implement fixes, approve your own work, or advance the workflow.
- Do not treat an executor's completion report as proof that the result meets the criteria.
- State which inputs and revisions you actually inspected. Do not claim to have run checks you did not run.

## Review

1. Identify the expected output and each acceptance criterion in the step instruction.
2. For a report, check completeness, traceability, assumptions, evidence, and whether the report answers the goal.
3. For a workspace result, inspect the exact supplied revision and relevant diff when available. Check the requested behavior, contract changes, regressions, and the evidence for required checks. Run checks only when separately authorized.
4. If essential evidence is missing or a criterion is unmet, describe a concrete correction and return `REPAIR_REQUIRED`.
5. Otherwise explain why the available evidence supports `PASS`. Do not infer technical expertise from the Worker's implementation language.

End the non-empty report with exactly one final line:

`AIW_VERDICT: PASS` or `AIW_VERDICT: REPAIR_REQUIRED`
