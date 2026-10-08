# R8-S8 - Fleet / Backlog Dashboard

## Goal

Project the operational state of the whole AI workforce without adding another authority model or persistence table.

R8-S7 answers "what is happening in this run?". R8-S8 answers fleet-level questions such as:

- how much Worker capacity exists and how much is currently available;
- how many tasks are READY/BLOCKED/RUNNING/FAILED;
- which READY tasks cannot be matched to any ACTIVE Worker capability;
- which READY tasks have matching Workers but all matching Workers are currently at capacity;
- which READY tasks are intentionally blocked by Autonomous Run authority such as PAUSED or FAILED;
- how many Runs are active, paused, failed, waiting for approval, or terminal;
- which leases are active and whether Worker lease accounting agrees with task ownership.

The Fleet Dashboard is a read-only projection. It does not participate in task claiming, run control, scheduling, recovery, or approval decisions.

## API

```text
GET /api/r8/fleet/dashboard
GET /api/r8/fleet/dashboard?detailLimit=100
```

`detailLimit` controls only detail arrays. Aggregate counts always cover the full current dataset.

- default: `100`
- minimum: `1`
- maximum: `500`

## Worker projection

The `workers` section includes:

```text
total
active
draining
offline
registeredCapacity
claimableCapacity
activeLeases
availableClaimCapacity
byRuntimeType
workers[]
```

Definitions:

- `registeredCapacity`: sum of `max_concurrency` for every registered Worker.
- `claimableCapacity`: sum of `max_concurrency` for ACTIVE Workers only.
- `activeLeases`: Worker-side lease accounting from `worker.active_leases`.
- `availableClaimCapacity`: free capacity on ACTIVE Workers only.

DRAINING and OFFLINE Workers never contribute new claim capacity.

## Backlog classification

Every READY task is assigned to exactly one current operational category:

```text
claimableReady
unmatchedReady
capacityBlockedReady
policyBlockedReady
```

The categories mean:

### claimableReady

The task is allowed by run authority and at least one ACTIVE compatible Worker currently has free capacity.

### unmatchedReady

The task is allowed by run authority but no ACTIVE Worker supports all required capabilities.

This includes repository capabilities such as:

```text
repo:core
```

so the dashboard can expose missing repository-local Worker coverage as well as missing language/role capabilities.

### capacityBlockedReady

At least one ACTIVE Worker supports the task, but every compatible Worker has reached `max_concurrency`.

### policyBlockedReady

The task is physically READY in the task graph but current Autonomous Run authority does not permit claiming it.

For autonomous tasks, claim policy remains:

```text
control_state = ACTIVE
AND
status = RUNNING
```

This means a task returned to READY by `pause` is correctly shown as `policyBlockedReady`, not falsely reported as missing Worker capacity.

## Capability demand

`readyCapabilityDemand` counts capability demand across READY tasks. It is an observability signal, not a scheduler input.

Example:

```json
{
  "java": 7,
  "maven": 6,
  "coding": 3,
  "repo:core": 4
}
```

This can later support capacity planning without changing TaskClaimService.

## Run fleet

The `runs` section keeps lifecycle and operator-control counts separate because they are orthogonal authorities.

For example, a PAUSED run may still have:

```text
lifecycleStatus = RUNNING
controlState    = PAUSED
```

The projection includes:

- lifecycle counts;
- PAUSED/CANCELLED control counts;
- current phase distribution;
- recent non-terminal runs;
- attention runs.

`attentionRuns` currently includes:

```text
PAUSED
FAILED
WAITING_APPROVAL
ENGINEERING_PASSED
```

It is only an operational shortlist. It does not change authority.

## Execution fleet

The execution section exposes current lease ownership:

```text
leasedTasks
claimed
running
currentParallelism
workerLeaseAccountingDelta
activeLeases[]
```

`workerLeaseAccountingDelta` is:

```text
SUM(worker.active_leases) - COUNT(work_task state CLAIMED/RUNNING)
```

Normally it should be zero. A non-zero value is diagnostic evidence of accounting drift; the Fleet Dashboard never repairs it automatically.

## SQLite single-connection rule

R8-S8 follows the R8-S7 Fix1 rule strictly:

> Never issue another JdbcTemplate query from inside a RowMapper/callback while using the SQLite single-connection profile.

All Fleet Dashboard queries execute sequentially at the top level. Java performs the cross-query projection after each JDBC call has returned its connection.

## Persistence

R8-S8 adds:

```text
new business tables: 0
new columns:         0
new migrations:      0
```

The schema remains:

```text
worker
work_task
work_task_dependency
execution_attempt
approval_request
autonomous_run
autonomous_run_event
```

Flyway remains V10.
