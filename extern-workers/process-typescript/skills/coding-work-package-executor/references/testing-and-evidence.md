# Testing and Evidence Standard

## Principle

Evidence must show that intended behavior works and that important existing behavior was not unintentionally broken.

## Test selection

Select tests based on risk and architecture, not habit.

- Pure deterministic logic: unit tests may provide most evidence.
- Database behavior: include repository/component/integration tests as appropriate.
- Cross-service/API behavior: include contract and integration evidence.
- Concurrency/idempotency: test duplicate, ordering, retry, and race-sensitive paths where applicable.
- Security behavior: test authorization/authentication boundaries and negative paths where required.
- Migration/schema changes: test forward migration, compatibility window, and rollback/recovery strategy when required.

## Evidence quality

Good evidence identifies:

- what was executed;
- against what scope/environment;
- the observed result;
- which AC/requirement it supports.

Weak evidence includes phrases such as "should work", "looks correct", or "tests are expected to pass".

## Failure handling

A failed verification step does not automatically mean `CODE_DEFECT`.

Classify whether the cause is:

- implementation defect;
- stale/incorrect test;
- environment/toolchain failure;
- unavailable external dependency;
- requirement/design conflict;
- baseline mismatch.

Do not modify tests or production code until the failure is understood well enough to assign the correct class.
