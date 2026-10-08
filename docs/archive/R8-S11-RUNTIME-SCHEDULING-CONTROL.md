# R8-S11 - Runtime Scheduling Control

## Goal

Allow an operator to change one Autonomous Run's `maxParallelism` while the run is live without changing the Worker Pull model or revoking healthy leases merely because the limit was reduced.

## API

```http
POST /api/r8/runs/{runId}/scheduling
Content-Type: application/json

{
  "maxParallelism": 2,
  "controlledBy": "operator:alice",
  "reason": "reduce fleet pressure"
}
```

`maxParallelism=0` means unlimited. Valid range: `0..1024`.

Scheduling may be changed while execution authority can still matter: RUNNING or FAILED runs, with ACTIVE or PAUSED control. CANCELLED and engineering/approval-terminal runs reject the change.

## Contraction semantics

A runtime contraction is non-destructive.

Example:

```text
old maxParallelism = 4
activeSlots        = 3
new maxParallelism = 1
```

After the update:

```text
maxParallelism    = 1
activeSlots       = 3
overcommittedSlots= 2
availableSlots    = 0
```

The three existing leases remain valid. New claims are blocked by the existing atomic admission SQL until `activeSlots < maxParallelism`. Slots drain through the normal complete/fail/lease-expiry/pause/cancel paths.

If immediate revocation is desired, the operator uses the existing `pause` authority explicitly; changing a scheduling limit is not a hidden pause/retry operation.

## Expansion semantics

Increasing the limit takes effect immediately for subsequent Worker pulls. Setting it to `0` removes the per-Run limit without altering existing attempts.

## Audit

Every effective value change appends:

```text
MAX_PARALLELISM_CHANGED
```

to `autonomous_run_event`, including actor, old/new limit, active slots at the time of the change, and optional reason. Replaying the same value is idempotent and does not append another event.

Timeline projection exposes it as:

```text
category  = SCHEDULING
eventType = RUN_MAX_PARALLELISM_CHANGED
```

## Persistence

No new business table is introduced. Flyway V12 rebuilds the existing `autonomous_run_event` table only to extend its event-type CHECK constraint while preserving historical rows and sequence numbers.

Core tables remain seven.

## Observability

Run response, Run Dashboard, and Fleet RunSummary expose `overcommittedSlots` so a controlled contraction is visible rather than looking like an accounting error.

The existing accounting delta remains independent:

```text
accountingDelta = active_slots - actual CLAIMED/RUNNING task count
```

A correct contracted run can therefore have:

```text
overcommittedSlots > 0
accountingDelta    = 0
```
