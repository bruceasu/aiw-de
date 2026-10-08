# R3-S2 — Pull Worker + Lease Ownership

Status: IMPLEMENTED

## Scope

R3-S2 introduces the minimum runtime ownership model required for decoupled AI workers.

Included:

- dynamic Worker registration;
- capability matching;
- worker-side pull claim;
- max-concurrency reservation;
- atomic task ownership CAS;
- CLAIMED -> RUNNING transition;
- task lease heartbeat;
- lease expiry recovery;
- durable execution-attempt history;
- completion-driven dependency release.

Not included:

- Codex SDK;
- HTTP Worker Protocol;
- isolated coding workspaces;
- scheduler/event bus;
- retry policy scoring;
- artifact production protocol.

Those remain later stages.

## Ownership rule

A Worker never directly updates lifecycle state.

It must use Control Plane application services:

```text
claimNext
start
heartbeat
complete
fail
```

Task ownership is valid only when all of these still match:

```text
task id
worker id
lease token
lease not expired
expected task state
```

## Atomic claim

Workers may observe the same READY candidate, but ownership is established only by the conditional database update:

```text
READY + lease_token IS NULL
    ->
CLAIMED + worker + lease token + expiry
```

Only one concurrent updater can succeed.

This keeps claim semantics portable across SQLite and PostgreSQL without requiring a dedicated lease table.

## Capability matching

R3-S2 keeps capabilities as canonical labels in one column to preserve the small-table model.

A worker may claim a task when:

```text
worker.capabilities containsAll task.requiredCapabilities
```

A worker with `*` is universal.

The current implementation scans the READY pool in priority order and performs matching in Java. This deliberately favors schema simplicity at the current project scale. If READY-pool size later becomes large enough to make this expensive, capability indexing may be redesigned based on measured workload rather than preemptive normalization.

## Capacity

`worker.active_leases` is updated transactionally.

A claim first reserves one slot using:

```text
status = ACTIVE
active_leases < max_concurrency
```

If no compatible task can be claimed, the slot is released in the same transaction.

Completion, failure and expiry recovery also release the slot transactionally.

## Crash recovery

Lease expiry is the recovery authority.

For an expired CLAIMED/RUNNING task:

```text
execution_attempt -> EXPIRED
work_task          -> READY
lease fields       -> NULL
worker slot        -> released
```

Another compatible worker can then claim the task as a new attempt.

## Database budget

R3-S2 domain tables remain limited to four:

```text
worker
work_task
work_task_dependency
execution_attempt
```

## Recovery representation

`TaskLease` represents a valid active lease and therefore keeps the invariant `expiresAt > acquiredAt`.
The expiry-recovery query does **not** reconstruct `TaskLease`; it returns `ExpiredLease`, a minimal ownership identity containing only task id, worker id, and lease token. Recovery then performs a database compare-and-set requeue. This keeps normal lease invariants strict while allowing stale persisted rows to be recovered safely.
