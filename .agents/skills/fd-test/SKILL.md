---
name: fd-test
description: Generate and run optional black-box tests for a numbered FD when explicitly invoked.
disable-model-invocation: true
---

# FD Test

Read `skills/reviewed-skill-contract.md`, `skills/work-management.md`, the
repository instructions, the target FD, and relevant stable specs. Use this
Skill only when the user explicitly invokes `fd-test` or asks for a standalone
FD test round. The normal `fd-workflow` does not invoke this Skill.

## Inputs and outputs

Inputs are an FD ID or path, its acceptance conditions and public contracts,
the requested test scope, and whether the user wants cases, test code, or
execution. When black-box independence matters, work in a session separate
from the implementation session. If that separation cannot be established,
state the limitation before claiming independent evidence.

Write assigned repository test code under the root `tests/` directory. Write
a factual report under `docs/features/reports/` using
`docs/templates/FD_TEST_REPORT_TEMPLATE.md` and
`docs/templates/FD_TEST_REPORT_DATA_TEMPLATE.json`. Keep the same FD, revision,
scenario IDs, commands, and results in both files.

## Process

1. Derive observable scenarios from the FD acceptance and public contracts.
   Split broad conditions into distinct behaviors. Do not inspect
   implementation source while deriving black-box cases.
2. Create or modify only the assigned test files. Preserve existing test
   ownership and use repository conventions.
3. If execution was requested, inspect the exact command and its invoked code
   and side effects. State the command, working directory, scope, expected
   duration, and side effects before running it. Follow the repository runtime
   authorization rules; a request to generate cases alone does not authorize
   execution. Run one focused command first and do not widen its scope without
   user authorization.
4. Record actual outcomes, unrun scenarios and reasons, commands, evidence,
   optional coverage measurements and their raw source, and residual risks.
   Report counts and coverage only as test facts; do not turn them into FD
   acceptance thresholds or a delivery recommendation.

## Boundaries

- This Skill does not claim or emit FD workflow events, change FD status, or
  update Work Item acceptance.
- Do not send this report to a Reviewer, PM, or risk assessor for evaluation.
  It is optional test evidence and is not an input to normal FD acceptance.
- Never describe a prepared, partial, blocked, or unrun scenario as passed.
- Do not run tests, measure coverage, access external services, download
  dependencies, or make unrelated changes without the authorization required
  by repository instructions. Treat a coverage command as a separate exact
  command that needs the same authorization as a test command.

## Completion

Complete when the requested cases and test code are recorded, the optional
report matches the observed evidence, skipped execution is explicit, and no
FD workflow state has changed. If a required input or authorization is
missing, report `INCOMPLETE` or `BLOCKED` with the precise missing item.
