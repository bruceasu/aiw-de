# R8-S12 — Fleet-level Admission Budget

## Goal

Add a database-enforced global concurrency ceiling for the entire worker-pull fleet without introducing a push scheduler or a second global active-slot ledger.

## Configuration

```text
AIW_FLEET_MAX_PARALLELISM=0      # unlimited (default)
AIW_FLEET_MAX_PARALLELISM=N      # 1..65535 hard fleet ceiling
```

The deployment setting is persisted into the singleton `fleet_admission_budget` authority row when the Control Plane starts.

## Claim ordering

For an autonomous task, claim admission is ordered as:

```text
per-Run admission -> Fleet admission mutex -> Worker capacity -> Task ownership
```

`AutonomousRunConcurrencyBudget` is ordered before `FleetAdmissionConcurrencyBudget`.

## Why the Fleet budget does not have `active_slots`

The global ceiling uses `work_task.state IN ('CLAIMED','RUNNING')` as the only active-lease authority.

On claim:

1. lock `fleet_admission_budget(GLOBAL)` in the database;
2. count live claimed/running tasks;
3. compare the count to `max_parallelism`;
4. if admitted, continue the same transaction into worker-capacity and task ownership updates;
5. retain the singleton lock until transaction commit.

This makes concurrent PostgreSQL claims serialize at the admission boundary. The next claimant counts the lease committed by the previous claimant.

No global release counter is required on completion/failure/expiry/pause/cancel, so there is no second global ledger that can drift and no reverse claim/release lock dependency on a global counter.

SQLite keeps its existing single-connection `transaction_mode=IMMEDIATE` behavior and uses a no-op update to participate in the same write transaction.

## Persistence

R8-S12 adds one table:

```text
fleet_admission_budget
```

This table is justified by independent concurrency ownership: all Control Plane instances must coordinate claims through one database authority.

Migration:

```text
V13__r8_fleet_admission_budget.sql
```

Core business/authority tables become 8.

## Fleet dashboard

`GET /api/r8/fleet/dashboard` adds:

```text
admission.maxParallelism
admission.activeLeases
admission.availableSlots
admission.overcommittedSlots
admission.saturated
admission.updatedAt
```

Backlog additionally distinguishes:

```text
fleetAdmissionBlockedReady
```

from the existing per-Run:

```text
admissionBlockedReady
```

so an operator can tell whether a READY task is blocked by its own Run budget or by the fleet-wide ceiling.

## Non-goals

R8-S12 does not add:

- runtime API for changing the fleet ceiling;
- capability-specific quotas;
- worker assignment rows;
- scheduler queues;
- push scheduling.

Runtime fleet control can be introduced separately after the static hard-ceiling behavior is validated.
