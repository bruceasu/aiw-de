# Implementation review methods

Apply to the exact assigned implementation and comparison base. Keep this review read-only
and retain the existing findings and disposition contract.

## Four dimensions
- Correctness: trace changed control/data flow and relevant callers, failure paths,
  ownership, compatibility, and data integrity. A report or checklist is not source review.
- Standards: cite actual repository requirements. Treat smells such as duplication,
  speculative abstraction, or excessive delegation as heuristics, not automatic blockers.
- Spec: compare with current accepted behavior, including boundary/failure scenarios,
  omitted scope, and unintended additions. Style compliance does not establish correctness.
- Evidence: check actual commands, revision coverage, skipped checks, and uncertainty.
  Green tests do not prove acceptance; static review does not prove runtime behavior.

## Findings
- State the exact location, current requirement, observed defect, consequence, and
  minimal correction. Label hypotheses and missing evidence instead of inventing defects.
- For docs/configuration, review the actual contract; do not demand irrelevant code tests.
- Use the current accepted waiver: a waived check is not passed, but its waiver does not
  waive required behavior. Do not repeat a superseded demand.
- For relevant test evidence, inspect oracle derivation and important assertions,
  not just pass counts. Do not restart an exhausted repair/testing budget.
- For conflict recovery, inspect the semantic resolution and exact resulting diff.
  A recovery commit alone is not evidence that both intended changes survived.
- Keep implementation advice local. A broad design preference is not a blocking finding.

## Check
Bind findings to supplied versions and distinguish confirmed defects from residual risk.
Return the existing review proposal; do not fix code, spawn reviewers, accept baselines,
or perform FD/Git delivery operations.
