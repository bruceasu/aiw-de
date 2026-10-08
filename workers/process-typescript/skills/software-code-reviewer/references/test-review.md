# Reviewing Tests and Verification Evidence

## Look for behavior coverage

Tests should demonstrate behavior required by the WP, not simply execute changed lines.

Check whether tests cover:
- main success scenario;
- material negative paths;
- boundaries and invalid inputs;
- failure/retry/idempotency/concurrency behavior when relevant;
- compatibility or migration behavior when relevant.

## Common weak-test patterns

- asserting only non-null or HTTP 200 when domain behavior matters;
- mocking the component whose behavior is supposed to be verified;
- reproducing implementation logic inside the assertion;
- no assertion on side effects/state;
- disabled/skipped/flaky tests presented as evidence;
- unit-only evidence for a cross-system acceptance criterion;
- snapshots updated without validating semantic correctness.

## Evidence consistency

Compare executor claims with actual tests/build output when available. If evidence is absent, mark the coverage as unverified rather than assuming success.
