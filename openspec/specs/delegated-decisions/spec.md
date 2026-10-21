# Personal decisions and platform action authority

## Purpose

FD-024 moves private decision business into a user-owned Worker. The platform retains minimal effective capabilities, prohibitions, cumulative consumption, fixed attempts, receipts and execution audit. It does not retain new private policy originals or reasoning.

## Requirements

### Requirement: Closed minimal capabilities
Human-confirmed capabilities SHALL bind one owner, enrolled Worker, exact target/action/step, fixed business parameters, states, conditions, total ceiling, version and expiry. Every execution SHALL recheck current human role and identity generation. A11 requires approval authority, A17 administration authority, and other actions operation authority. Model text and private database edits SHALL NOT issue or expand capabilities.

### Requirement: Effective prohibition before private activation
Private prohibitions SHALL be projected only as necessary target/action/condition fields and a salted private revision digest. Machine updates may only narrow their explicit target scope, pause it and advance its restriction generation. Applicable prohibitions SHALL defeat ALLOW regardless of grant replacement. Removing an exact prohibition or expanding a ceiling SHALL require a current authorized human. A private write failure after narrowing SHALL leave the platform paused; a private backup SHALL NOT roll back platform restrictions.

### Requirement: Durable request identity and receipts
The deduplication identity SHALL be persistent platform instance plus stable owner plus requestId, independent of Worker, grant, credential or boot. The canonical digest SHALL cover business input, not replaceable authority identity. The same key and digest returns its original receipt with no new execution or debit; conflicting content is rejected. Read permission SHALL be checked separately from execution, including after revocation and for a replacement Worker. Stable target references, request tombstones, budgets and execution facts SHALL remain durable; restoration with missing deduplication history requires a separate human recovery decision.

### Requirement: Complete fixed action vocabulary
The platform SHALL preserve all twenty existing subactions: A01, A02, A03, A04, A05, A06, A07, A08, A09, A10, A11_APPROVE, A11_REJECT, A12_CREATE, A12_EXECUTE, A13, A14, A15, A16, A17_SCHEDULE and A17_UNDO. Each adapter SHALL retain the original business service's state/version Gates and its independent cumulative ledger. Execution rounds, technical retries and review-only recovery SHALL not share consumption counters. Templates, new grants and replacement Workers SHALL not reset history.

A01 includes execution and independent review. A02 SHALL enforce its approved profile catalog and scope at actual task start. A03 SHALL bind an exact failed task, confirmed failure category, explicit side-effect consent and interval; unconfirmed effects cannot be retried. A04 SHALL not repeat execution. Approval SHALL preserve historical specialist/delegate separation. A12 creation SHALL NOT imply execution authority. Publication execution SHALL require a separate current capability and one exact fixed attempt, including when reached through confirmation, repair or retry. Fleet changes SHALL respect explicit interval, bounds and the special meaning of zero. Drain/Offline SHALL affect only ordinary Workers and retain lease constraints. Archive and deletion scheduling/undo SHALL retain original result and retention Gates.

### Requirement: Start is a new authority boundary
Database mutation, cumulative debit and receipt SHALL commit atomically behind the platform authority first lock. A generated task SHALL carry a durable single-attempt permit before any external effect. Actual start SHALL lock and recheck current capability/role/expiry, identity and target generation, restriction revision, applicable DENY, pause/takeover, captured post-action business version, models and historical independence. A normal DRAFT-to-CONFIRMED postcondition does not require the old DRAFT precondition to remain true. A stale permit SHALL not be renewed by claim, heartbeat, lease reclaim or automatic repair. Real running outcomes remain recordable.

### Requirement: Unknown effects are not failed effects
The platform SHALL persist intent and occupied budget before external work starts and SHALL not hold a database transaction across external IO. Accepted/admitted, running and completed are different facts. An unconfirmed external effect SHALL remain UNKNOWN, keep consumption and never replay, refund or create a replacement request automatically. Human reconciliation SHALL bind an exact attempt and explicit evidence, preserve history and create no effect. Any later retry still needs a new explicit capability/candidate, fixed attempt and independent debit.

### Requirement: No private schema or old mode
The current engine SHALL use only platform authority tables and public business services. Private SQLite records are not permission evidence. Old in-platform secretary execution is retired by explicit user decision. V8/V9 records are sealed, not automatically copied, deleted, activated or assigned to a current login. Ordinary specialist lease protocols remain separate.

## Validation limit

Compile-only and static review are source evidence, not a runtime concurrency, authentication, database migration or release result. Actual migration/cutover and runtime validation require separate authorization.
