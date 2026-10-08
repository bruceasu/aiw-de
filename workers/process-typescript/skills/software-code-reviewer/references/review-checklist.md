# Risk-Based Review Checklist

Use only relevant sections; do not mechanically produce comments for every item.

## Contract fidelity
- Accepted FR/NFR/AC satisfied?
- Out-of-scope behavior unintentionally changed?
- Public API/event/schema/config semantics preserved or intentionally migrated?

## State and data
- Correct source of truth?
- Transaction boundaries safe?
- Partial writes/retries handled?
- Null/empty/missing/duplicate/stale states considered?
- Precision/timezone/ordering semantics correct where relevant?

## Concurrency
- Shared state synchronized appropriately?
- Check-then-act races?
- Duplicate requests?
- Lost updates?
- Lock ordering/deadlocks?
- Async completion/cancellation races?

## Failure handling
- Timeout behavior?
- Retry ownership and limits?
- Idempotency?
- Compensation/reconciliation?
- Resource cleanup?
- Error translation hides useful cause or leaks internals?

## Security
- Authorization at correct boundary?
- Trusted/untrusted input distinction?
- Secret/token handling?
- Path/command/SQL/template injection?
- Unsafe deserialization or SSRF-like behavior?

## Compatibility/rollout
- Old/new versions coexist safely?
- DB/event/API changes backward compatible as required?
- Migration sequencing valid?
- Feature flag/default behavior safe?

## Operations
- Failures observable?
- Logs actionable and safe?
- Metrics/alerts affected?
- Resource consumption bounded?
