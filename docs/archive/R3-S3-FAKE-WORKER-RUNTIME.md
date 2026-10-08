# R3-S3 — FakeWorker Runtime + Worker Loop

Status: ACCEPTED / MAVEN VERIFY PASS (user-confirmed 2026-09-26)

## 1. Goal

R3-S3 turns the S2 claim/lease protocol into a continuously running worker-side execution loop without introducing Codex or transport concerns.

The stage proves that the Control Plane can support a dynamic AI workforce where workers independently pull compatible work, execute concurrently, heartbeat their leases, and recover from process loss.

## 2. New runtime seam

```text
WorkerRuntime
    |
    +-- WorkerService
    +-- TaskClaimService
    +-- TaskExecutionService
    |
    +-- TaskExecutor
            |
            +-- FakeTaskExecutor      (R3-S3)
            +-- CodexTaskExecutor     (later)
```

`TaskExecutor` contains execution behavior only. It does not own lifecycle transitions.

## 3. Worker loop

Each runtime starts `maxConcurrency` independent slots.

Each slot performs:

```text
while RUNNING:
    heartbeat worker registration when due
    claimNext(worker)
    if none:
        backoff
        continue

    start(task, lease)
    schedule lease heartbeat
    TaskExecutor.execute(task)

    success -> complete(task, lease)
    error   -> fail(task, lease)
```

The worker never updates database lifecycle state directly.

## 4. Concurrency ownership

The number of local execution slots equals the registered `maxConcurrency`, but database capacity remains authoritative.

Therefore both constraints must agree before work can run:

```text
local free slot
AND
worker.active_leases < worker.max_concurrency
AND
atomic READY -> CLAIMED CAS succeeds
```

Local concurrency is an optimization/execution resource. Database lease ownership remains the correctness boundary.

## 5. Heartbeat

A running task receives periodic lease heartbeats through `TaskExecutionService.heartbeat()`.

The heartbeat extends the persisted lease and also updates worker heartbeat state.

If heartbeat fails, the runtime treats ownership as lost and does not later attempt to complete/fail the task from stale local state. The Control Plane remains authoritative.

Idle worker heartbeats are rate-limited using the same configured heartbeat interval so the pull loop does not write to the database on every poll.

## 6. Graceful drain

Graceful shutdown is:

```text
RUNNING
  -> DRAINING
  -> stop claiming new work
  -> let in-flight tasks finish
  -> OFFLINE
```

`WorkerStatus.DRAINING` prevents new capacity reservations in the database.

## 7. Crash semantics

`WorkerRuntime.crash()` deliberately simulates abrupt process loss:

- heartbeat stops;
- local threads are interrupted;
- worker is not marked OFFLINE;
- active lease is not released;
- task is not locally failed or completed.

The persisted lease therefore remains the recovery authority:

```text
RUNNING + expired lease
        |
        v
recoverExpiredLeases()
        |
        +-- task -> READY
        +-- attempt -> EXPIRED
        +-- worker capacity released
        |
        v
another worker claims attempt N+1
```

This is intentionally different from graceful shutdown.

## 8. FakeTaskExecutor

S3 includes a deterministic fake adapter with:

- configurable execution duration;
- optional configured task failures;
- observed task IDs;
- active/max-active concurrency metrics.

It exists only to validate the runtime protocol before real agent integration.

## 9. Acceptance tests

`R3S3FakeWorkerRuntimeIT` adds two integration scenarios.

### Fleet

```text
100 READY tasks
10 workers
maxConcurrency = 1 each
```

Expected:

- all 100 tasks complete;
- exactly 100 execution attempts;
- exactly 100 distinct attempted tasks;
- no duplicate ownership;
- observable parallel execution;
- all worker lease counters return to zero;
- graceful close leaves all workers OFFLINE.

### Crash recovery

```text
worker-A claims + starts task
worker-A crashes
lease is made expired
Control Plane recovers task
worker-B claims same task
```

Expected:

- first attempt becomes EXPIRED;
- task returns to READY;
- worker-B receives attempt 2;
- second attempt becomes COMPLETED.

## 10. Persistence budget

R3-S3 adds no persistent entities.

Domain tables remain:

```text
worker
work_task
work_task_dependency
execution_attempt
```

Thread pools, runtime state, heartbeat schedules and in-flight slot counts are process-local implementation state and do not justify new tables.

## 11. Deliberate non-goals

Not included in S3:

- Codex SDK/CLI integration;
- HTTP worker transport;
- artifact materialization;
- isolated workspaces;
- automatic periodic server-side lease sweeper;
- retry policy/backoff classification;
- human approval.

The next stage can attach a real executor to the proven worker protocol rather than embedding Codex concerns into task ownership.
