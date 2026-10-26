---
name: fd-test
description: Design optional black-box acceptance tests for a numbered FD and run them within authorization when requested, directly or through FD test support.
disable-model-invocation: true
---

# FD Test

Read `skills/reviewed-skill-contract.md`, `skills/work-management.md`, the
needed sections of `skills/testing-methods.md`, repository rules, target FD
and relevant stable specs. Use when explicitly requested, including a workflow
request for test support in any wording/language. No default test round for
ordinary FD implementation. Cases-only, execution-only and other narrower
limits prevail; the selection does not itself approve arbitrary commands.

## Inputs and outputs

Resolve the FD, acceptance/public contracts, test scope, requested cases/code/
execution and command authorization. Write assigned test code under root
`tests/`, respecting ownership and repository conventions. Write Chinese
Markdown and same-name JSON under `docs/features/<FD-ID>/reports/` using
`docs/templates/FD_TEST_REPORT_TEMPLATE.md` and
`docs/templates/FD_TEST_REPORT_DATA_TEMPLATE.json`. Standalone missing
material inputs produce INCOMPLETE/BLOCKED, not guessed policies.

For orchestrated FD testing use a session separate from Worker. If unavailable,
record the limitation; never claim independent evidence. Derive expectations
independently even in a separate session. Report session identity and access
scope. Return the report to the host, not a fabricated Tester event.

## Process

1. Fix FD revision and acceptance source. Before inspecting implementation or
   existing tests, record scenario IDs, rule references, inputs, expected
   results/derivations and key assertions using shared Design. Block only
   ambiguous scenarios and continue independent ones.
2. Then inspect only necessary adapters, signatures and test facilities. Record
   implementation access and reused tests; adapt or add only missing checks.
   Do not treat reuse as independently generated evidence, or count duplicate
   executions as independent assurance. Existing code must not redefine oracles.
3. Statically check whether assertions detect the intended errors, replacements
   leave the rule real, and fixtures/cleanup are confined. This does not count
   as observed test success or an executed mutation check.
4. For requested execution, follow shared Execution and exact repository
   authorization. State the command/cwd/scope/duration/side effects first;
   record actual revisions, dirty diff identities, phase and raw output. No
   suite widening, coverage run, network or downloads without authority.
5. Classify observed outcomes separately from causes. Return confirmed defects
   and material unknowns with rule references; do not silently fix production
   code. Host Worker handles authorized repair before budget exhaustion.
6. On exhausted budget, no safe repair or unclear cause, stop test execution
   and test-related automatic repair; produce bounded static diagnosis-only
   evidence. Preserve original failures, candidate fix, remaining scenarios,
   budget ledger and dependencies. Continue independent assigned work only.

## Boundaries and completion

For unclear failures after the independent scenarios are fixed, diagnosing-bugs
may supply bounded cause analysis within the existing evidence/access scope.
At diagnosis-only use preserved evidence once, or cite the existing diagnosis;
never repair production/tests, instrument or run again through that method.
Report structural test-seam gaps to Worker/Planner as proposals, not permission
for an architecture refactor. Missing diagnostic Skill does not restart or
block the existing bounded shared method; record which method was actually used.

This Skill does not claim/emit FD events, change FD status or acceptance, merge,
archive, or own a delivery recommendation. Relevant facts may be read by Worker,
PM and Reviewer. Confirmed acceptance defects are not erased by optionality;
counts/coverage add no automatic thresholds or risk voting stage.

Complete the requested deliverables when the report matches actual evidence,
including not-run/blocked/diagnosis-only and missing-input reasons. Completion
of a report is not acceptance of the FD. Never describe prepared, partial,
stale or unrun evidence as passed. Preserve historical reports; changed code
requires new version-bound evidence or an explicit unverified limitation.
