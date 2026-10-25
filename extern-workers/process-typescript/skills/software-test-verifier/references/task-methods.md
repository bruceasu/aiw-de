# Test design and evidence methods

Use for assigned test design or verification. A cases-only task does not authorize
execution. Keep the existing verification result and runtime budgets unchanged.

## Design
- For important scenarios identify the accepted rule, preconditions/input, observable
  expected result and its derivation, and the assertions that establish that result.
- Derive expected values from confirmed contracts, independently worked examples, or
  justified invariants. Do not call production helpers or copy the current algorithm
  as the only oracle. Mark characterization as observed behavior, not acceptance proof.
- Select relevant normal, boundary, invalid, ownership, duplicate, state-transition,
  and dependency-failure scenarios by risk; do not manufacture a mandatory full suite.
- For each key assertion describe a plausible wrong implementation that it rejects.
  This is reasoning, not a claim that mutation testing or fault injection ran.
- Observe a suitable contract boundary. Do not mock away the rule being checked or
  change APIs, dependencies, requirements, or implementation merely to make a test pass.

## Execute only within authority
- Bind the proposed command to cwd, scope, implementation/test revisions, relevant
  dirty snapshot, side effects, and authorization before any allowed execution.
- Separate expected TDD assertion failure from import/build/fixture failure. Report
  only actually observed red/green phases; do not require TDD for an unselected task.
- Keep status (passed, failed, blocked, not run) separate from cause
  (product, test, environment, undetermined).
- Preserve raw failure evidence and consumed budget. When diagnosis-only applies,
  return a bounded cause analysis/candidate fix without execution or test-driven repair.
- Source inspection cannot demonstrate a performance gain or an actual runtime pass.

## Check
Report important assertions, actual version coverage, command results, consequential
omissions, and evidence limits. Never turn partial execution into a whole-scope pass.

## Close the assigned verification scope
- Relate each required acceptance outcome to a meaningful assertion or explicit evidence
  gap. A compiled fixture, returned success flag or large pass count is not enough
  when the rule requires a specific state, value or side effect.
- On changed code, tests or rules, identify which previous results remain relevant and
  why. Do not claim that old execution covered new content or a different environment.
- For a blocked check, preserve the observation, likely cause, raw output and versions.
  Name the smallest next check and its owner/needed authority without executing it.
- Keep unmet required behavior separate from an optional unrun check. An authorized
  waiver changes the required evidence, not the truth of a result or the business rule.
- On handoff, carry consumed retries and diagnosis-only state. New sessions, scenarios
  or reviewer requests do not restart an exhausted execution budget.
