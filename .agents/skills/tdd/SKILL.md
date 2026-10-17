---
name: tdd
description: Implement or fix behavior through test-first red-green-refactor when requested, including the suitable core rules of an FD with test support.
---

# Test-Driven Development

Follow `skills/reviewed-skill-contract.md`, `skills/work-management.md`, and
the needed sections of `skills/testing-methods.md`. Use on an explicit
test-first request or an FD test-support selection, in any language. A generic
request for integration cases alone does not select TDD. More specific user
limits prevail; repository runtime authorization is still required.

Inputs: confirmed rules/examples, implementation scope, chosen boundary and
execution authority. Output: scoped implementation and regression tests plus
actual Red/Green evidence and limits in the existing Worker report. Standalone
use reports the same facts without inventing an FD. Do not add a second report
for every slice. For missing rules, block only that behavior; do not invent it.

Before writing the first test, identify the repo's test stack, naming conventions, and package layout. If `CONTEXT.md` exists, read it. For Java, Go, and Python projects, follow the existing framework instead of forcing a new style.

Language cues:

- Java: JUnit or TestNG assertions, fixtures or builders, and `Mockito` only at boundaries.
- Go: `testing`, table-driven tests, `t.Run`, `httptest`, and small hand-written fakes.
- Python: `pytest` or `unittest`, fixtures, `assert`, `pytest.raises`, and `unittest.mock` or `monkeypatch` at boundaries.

## What a good test is

Use the shared Design rules for independent oracles, risk selection and
meaningful assertions. Test stable behavior at the chosen boundary and preserve
it across internal refactors. See [tests.md](tests.md) for concrete checks and
[mocking.md](mocking.md) only when choosing a replacement dependency.

## One behavior cycle

1. Identify the rule, input, independently derived expected result, key
   assertion and the error it should distinguish. Use the approved boundary.
2. Write the smallest test and, when authorized, observe target Red. Classify
   fixture/import/environment failures separately; they are not valid Red.
3. Make the minimal scoped implementation and observe Green using the same
   assertion. For a bug, retain the original reproducer as regression protection.
   Explain changed tests and invalidate old Red evidence when assertions change.
4. Refactor only when useful after Green, within scope and remaining authority.
   Keep behavioral assertions; any unexecuted post-refactor check is explicit.

Follow shared Execution for planned phases, unexpected-failure retry limits and
diagnosis-only fallback. Do not start unlimited new cycles. In this repository,
assigned test code belongs under root tests/ and Go test prohibitions still
apply. Framework examples never override local rules.

Complete when the requested behavior and regression artifacts exist and the
report accurately distinguishes observed Red/Green, not-run and diagnosis-only.
Without execution, report prepared tests/implementation, not completed TDD.
Do not claim independent acceptance or change FD status from this method.

For unexplained Red/Green failures, diagnosing-bugs may analyze existing output
and caller paths within the same authority and attempt ledger. It does not turn
a fixture/import error into target Red or permit oracle changes to fit code.
In diagnosis-only, one bounded static analysis (or its existing report) yields
a candidate correction only, with no test edits, production repair or execution.
Use codebase-design for an in-scope interface question; propose broader
structural changes to Planner rather than expanding the behavior cycle. Missing
supporting Skills are an explicit limitation, not a new budget or phase.
