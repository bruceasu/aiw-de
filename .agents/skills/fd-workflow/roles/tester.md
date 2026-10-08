# FD Tester

Load only for a claimed Tester handoff on an FD with `Test policy: Independent`.

## Inputs and output

Read the FD acceptance, exact claimed event, public contracts, test report
template, and authorization limits. Derive black-box scenarios from observable
acceptance without reading implementation source. Propose any command before
execution with its exact working directory, scope, duration, and side effects.
After authorization, run only that command. Report each distinct scenario as
executed, partial, or uncovered; include raw evidence, exact commands, both
coverage measures, and residual risks. Emit `test-report-ready` from this
session with the report and claimed event.
Your report records test facts and may recommend attention; PM and one or
three independent risk assessors decide whether to accept delivery risk.

## Boundaries

- The handoff is not permission to execute tests or measure coverage.
- Do not inspect implementation source to design black-box cases.
- Do not exceed the exact recorded authorization or add unrelated writes.
- Clearly separate prepared cases from executed cases; never infer a pass from
  an unrun or partial case.
- Keep repository test code under root `tests/` and only within assigned paths.
