# R8-S10 — Run Concurrency Budget / Admission Control

## 1. Goal

R8-S9 established priority, aging, capability matching, and best-effort fairness while preserving Worker Pull.
R8-S10 adds a hard per-Autonomous-Run concurrency ceiling so one large run cannot consume an arbitrary share of Fleet capacity.

The Control Plane still does **not** push tasks to workers. A worker asks for work; admission control decides whether a READY task may consume a lease at that instant.

## 2. Configuration contract

Each `autonomous_run` now owns:

```text
max_parallelism
active_slots
```

`max_parallelism` semantics:

```text
0       unlimited (backward-compatible default)
1..1024 hard maximum number of CLAIMED + RUNNING tasks for that Run
```

A requirement intake may override the value:

```json
{
  "requestId": "feature-123",
  "requirement": "...",
  "repositoryRef": "core",
  "baseRevision": "<immutable sha>",
  "technicalCapabilities": ["java", "maven"],
  "priority": "HIGH",
  "maxParallelism": 3
}
```

If omitted, the Control Plane uses:

```text
AIW_RUN_DEFAULT_MAX_PARALLELISM
```

Default is `0` (unlimited), so accepted R8-S1..S9 behavior does not change unless a budget is configured.

A deterministic request replay with an explicit `maxParallelism` must match the persisted budget. A replay that omitted the field reuses the already-persisted Run authority rather than silently rewriting it because a deployment default later changed.

## 3. Why `active_slots` is persisted

A naïve implementation would do:

```text
COUNT(active tasks) < maxParallelism
then claim
```

That is a check-then-act race. Two PostgreSQL worker transactions can both observe the same count and both claim, exceeding the configured limit.

R8-S10 therefore uses `active_slots` as a database semaphore, analogous to `worker.active_leases`:

```sql
UPDATE autonomous_run
   SET active_slots = active_slots + 1
 WHERE id = ?
   AND control_state = 'ACTIVE'
   AND status = 'RUNNING'
   AND (max_parallelism = 0 OR active_slots < max_parallelism)
```

Exactly one transaction can advance the same row through the limit boundary. The `READY -> CLAIMED` task update remains the task ownership boundary; the Run slot is admission capacity, not ownership.

## 4. Claim sequence

For each ranked candidate:

```text
Worker Pull
  |
  +-- capability filter
  +-- TaskClaimPolicy precheck
  +-- atomic Run slot reservation
  +-- atomic Worker capacity reservation
  +-- atomic READY -> CLAIMED
  +-- ExecutionAttempt insert
```

The lock order is intentionally:

```text
Run -> Worker -> Task
```

for autonomous claims. This avoids reintroducing a Worker->Run inversion against operator pause/cancel paths.

If the Run is at its limit, the candidate is skipped and the worker can consider another Run. If a Run slot was acquired but the task loses the final task-claim race, the slot and Worker capacity are released and the worker polls again.

## 5. Slot release paths

Every transition that destroys an active lease must release exactly one Run slot in the same transaction:

```text
Task COMPLETE
Task FAIL
natural lease expiry / recovery
operator PAUSE lease revocation
operator CANCEL lease revocation
```

The existing attempt history is unchanged. A slot is capacity accounting only; it does not replace `execution_attempt` or `work_task` authority.

Engineering Gate finalization and operator PAUSE/CANCEL require `active_slots = 0` after their lease-revocation/completion work, making slot leaks fail closed rather than silently advancing authority.

## 6. Upgrade behavior

Flyway `V11__r8_run_concurrency_budget.sql` adds two columns only. No table is added.

For an R8-S9 database stopped while tasks are still `CLAIMED`/`RUNNING`, V11 backfills `active_slots` from those persisted leases before the application resumes. Existing Runs get `max_parallelism=0`, preserving prior unlimited semantics.

## 7. Observability

### Run API / Run Dashboard

Run state now exposes:

```text
maxParallelism
activeSlots
availableSlots
```

The Run Dashboard adds:

```text
scheduling.maxParallelism
scheduling.activeSlots
scheduling.availableSlots
scheduling.accountingDelta
```

`accountingDelta` is:

```text
active_slots - count(CLAIMED/RUNNING tasks for the Run)
```

Expected value: `0`.

### Fleet Dashboard

READY backlog is now classified as:

```text
claimableReady
unmatchedReady
capacityBlockedReady      # matching Workers are full
admissionBlockedReady     # Run maxParallelism reached
policyBlockedReady        # Run paused/failed/etc.
```

Fleet execution also reports:

```text
runSlotAccountingDelta
```

Expected value: `0`.

The Dashboard remains read-only and never repairs accounting automatically.

## 8. Persistence budget

R8-S10 remains at seven business tables:

```text
worker
work_task
work_task_dependency
execution_attempt
approval_request
autonomous_run
autonomous_run_event
```

Schema delta:

```text
autonomous_run.max_parallelism
autonomous_run.active_slots
```

Flyway advances from V10 to V11.

No scheduler queue, assignment table, run-slot table, or central scheduler cursor is introduced.

## 9. Acceptance

New integration coverage: `R8S10RunConcurrencyBudgetIT`.

It verifies:

- maxParallelism=1 blocks a second claim even when the Worker has spare capacity;
- Fleet classifies the remaining READY task as `admissionBlockedReady`;
- Run/Fleet accounting deltas remain zero;
- explicit failure releases the slot;
- recover permits a new claim;
- pause revokes the lease and releases the slot;
- resume permits a new claim;
- cancel revokes the lease and returns active slots to zero;
- configured default budget is persisted when request intake omits an override.

Authoritative gate:

```bash
mvn clean verify
```
