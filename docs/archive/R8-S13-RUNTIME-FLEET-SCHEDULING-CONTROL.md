# R8-S13 — Runtime Fleet Scheduling Control

## Purpose

R8-S13 makes the global Fleet admission ceiling mutable at runtime while preserving the Worker Pull model and R8-S12 database-coordinated admission authority.

The control API is:

```text
GET  /api/r8/fleet/scheduling
POST /api/r8/fleet/scheduling
GET  /api/r8/fleet/scheduling/events?limit=100
```

A control request is:

```json
{
  "maxParallelism": 8,
  "controlledBy": "operator:alice",
  "reason": "increase capacity for the release window"
}
```

`0` means unlimited. Valid bounded values are `1..65535`.

## Contraction semantics

Changing the Fleet ceiling never revokes a healthy lease.

Example:

```text
before: maxParallelism=10, activeLeases=8
change: maxParallelism=4

after:
  maxParallelism=4
  activeLeases=8
  availableSlots=0
  overcommittedSlots=4
  saturated=true
```

New claims are blocked while `activeLeases >= maxParallelism`. Existing leases continue until ordinary completion/failure/operator-control paths release them.

This deliberately keeps scheduling control separate from cancellation/pause authority.

## Expansion semantics

Increasing the ceiling becomes available to the next Worker pull immediately. Setting the limit to `0` removes the global ceiling.

## Concurrency authority

The update transaction locks the same singleton `fleet_admission_budget('GLOBAL')` row used by R8-S12 claim admission.

This gives a single lock order boundary between runtime reconfiguration and concurrent claims. The authoritative active count remains:

```sql
SELECT COUNT(*)
FROM work_task
WHERE state IN ('CLAIMED','RUNNING')
```

No duplicate Fleet `active_slots` counter is introduced.

## Restart semantics

`AIW_FLEET_MAX_PARALLELISM` is now explicitly a **bootstrap default**.

On a new database, the startup initializer applies it while `fleet_admission_budget.updated_at_epoch_ms=0`. After that first initialization, a runtime API update is persisted and survives Control Plane restarts. Startup does not silently overwrite operator authority.

## Audit retention

Runtime Fleet scheduling changes have an independent audit-retention requirement, so R8-S13 adds one append-only table:

```text
fleet_admission_budget_event
```

Each effective change stores:

- strict per-Fleet sequence number;
- previous limit;
- new limit;
- live active lease count observed while holding the Fleet lock;
- controlling actor;
- optional reason;
- timestamp.

Same-value replays are idempotent and do not append audit noise.

## Persistence budget

R8-S13 has 9 justified tables:

1. `worker`
2. `work_task`
3. `work_task_dependency`
4. `execution_attempt`
5. `approval_request`
6. `autonomous_run`
7. `autonomous_run_event`
8. `fleet_admission_budget`
9. `fleet_admission_budget_event`

Flyway advances to V14.

The ninth table exists only because historical operator changes cannot be reconstructed from the current Fleet budget row and audit retention is an explicit independent lifecycle requirement.
