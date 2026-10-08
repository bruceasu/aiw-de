# R8-S9 — Worker-Pull Scheduling Policy / Fairness / Priority

## Goal

R8-S9 improves `claimNext` without introducing a central push scheduler. Workers still register capacity and independently pull READY work from the Control Plane.

The scheduling policy must balance four concerns:

1. explicit business priority;
2. starvation prevention for long-waiting READY work;
3. fairness across independent runs/subjects;
4. exact worker capability compatibility.

Correctness remains owned by the atomic `READY -> CLAIMED` update. Candidate ordering is advisory and may race under concurrent pulls; a failed claim simply moves the worker to the next candidate.

## Candidate Model

`WorkTaskRepository.findReadySchedulingCandidates()` returns each READY task together with the number of currently active (`CLAIMED` or `RUNNING`) tasks for the same `(subject_type, subject_id)`.

This is computed in one SQL statement. No nested JDBC query is used, preserving the SQLite single-connection rule established in R8-S7 Fix1.

No scheduler cursor, assignment table, queue table, or run-slot table is introduced.

## Ordering

For a worker, candidates that do not satisfy its declared capabilities are removed first.

Remaining candidates are ranked by:

1. **effective priority descending**;
2. **active subject leases ascending**;
3. **READY waiting timestamp ascending** (`updated_at_epoch_ms`);
4. creation timestamp ascending;
5. task id ascending for deterministic tie-breaking.

### Effective priority and aging

```text
effectivePriority = basePriority + ageBoost
ageBoost = min(100, floor(waitingSeconds / 600))
```

Thus every 10 minutes of READY waiting adds one priority point, up to +100.

Examples:

```text
CRITICAL = 100
HIGH     = 75
NORMAL   = 50
LOW      = 25
```

A newly READY HIGH task normally precedes a newly READY LOW task. A LOW task that has waited long enough can eventually overtake newer work, preventing permanent starvation.

The policy intentionally does not rewrite the persisted `priority`; aging is a read-time scheduling projection only.

## Run fairness

When candidates have the same effective priority, the scheduler prefers the subject with fewer active leases.

Example:

```text
Run A: READY A1, A2, A3; active leases = 1
Run B: READY B1;         active leases = 0

next claim -> B1
```

After a first worker claims Run A, another worker pulling equal-priority work naturally prefers Run B. No persistent round-robin cursor is required.

This is deliberately **best-effort fairness**, not a global serialization point. Two workers may read the same snapshot concurrently, but only one can atomically claim a specific task. This keeps the system decentralized and avoids turning fairness into a central bottleneck.

## Capability matching

Worker capability compatibility remains a hard eligibility rule before ranking. Scheduling never assigns a task to a worker lacking required capabilities.

Repository capabilities such as:

```text
repo:core
```

remain ordinary required capabilities and continue to protect isolated Git/worktree execution.

## Run authority

Existing `TaskClaimPolicy` checks still run before the lease is granted. A READY task belonging to a `PAUSED`, `FAILED`, or otherwise non-executable Autonomous Run remains ineligible even if its scheduling score is highest.

Therefore scheduling cannot bypass R8-S4/R8-S5 run authority.

## Persistence

R8-S9 adds:

```text
new tables      0
new columns     0
new migrations  0
```

The core remains seven business tables and Flyway remains V10.

## Acceptance

`R8S9SchedulingPolicyIT` verifies in one integration flow that:

1. CRITICAL work is claimed ahead of older NORMAL work;
2. equal-priority tasks are spread across independent runs based on active lease count;
3. a sufficiently old LOW-priority READY task eventually outranks a recent HIGH task through aging.

The existing R3-S3 concurrency test remains the duplicate-ownership safety gate.
