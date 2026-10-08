# R8-S4 — Autonomous Run Authority

R8-S4 gives one autonomous requirement execution its own durable lifecycle. Before this stage, callers had to inspect the task graph, engineering gate, and approval request separately to infer whether a run was still active, had failed, or had completed. That inference is no longer authoritative.

## 1. Lifecycle

```text
start
  |
  v
RUNNING
  | explicit worker-reported task failure
  v
FAILED
  | POST /api/r8/runs/{runId}/recover
  v
RUNNING
  |
  | EngineeringGate = PASSED
  v
ENGINEERING_PASSED
  | approval request materialized
  v
WAITING_APPROVAL
  +-------------------+
  |                   |
APPROVED            REJECTED
  |                   |
  v                   v
SUCCEEDED          REJECTED
```

`ENGINEERING_PASSED` is intentionally not terminal. Under `AIW_APPROVAL_POLICY=MANUAL`, it means the exact engineering result is known but no durable human approval request has been created yet. Under `REQUIRED`, the policy listener normally moves the run to `WAITING_APPROVAL` in the same transaction that finalizes the gate.

## 2. Failure versus lease expiry

Two cases remain deliberately different:

- lease expiry / crashed worker: existing lease recovery returns the task to `READY`; the run remains `RUNNING`;
- explicit worker failure: the task becomes `FAILED` and the run becomes `FAILED`.

Reference workers now send an optional `failureReason` in the existing `/fail` protocol call. The run authority stores the latest reason for operator visibility.

## 3. Recovery

Recovery is explicit:

```text
POST /api/r8/runs/{runId}/recover
{
  "recoveredBy": "operator:alice"
}
```

The transaction finds every currently `FAILED` task belonging to that run, locks them in deterministic task-id order, changes them back to `READY`, and changes the run back to `RUNNING`.

Previous `execution_attempt` rows are never rewritten or deleted. The next claim creates a new attempt number, preserving failure history.

This also handles parallel implementation safely: if more than one branch failed, one run-recovery operation requeues all currently failed branches.

## 4. Final authority

When a PASSED Engineering Gate is observed, the run records the exact:

```text
terminalSourceTaskId
resultRevision
```

When an approval request is materialized, it also records the exact `approvalRequestId`. Approval decisions then transition the run to `SUCCEEDED` or `REJECTED` transactionally with the approval authority.

The run never recomputes its approved revision from `latest-passed`.

## 5. Persistence

R8-S4 adds one table:

```text
autonomous_run
```

This is justified by the project's table-admission rule: an autonomous run now has an independent lifecycle, explicit recovery ownership, human-approval linkage, and durable authority distinct from any single WorkTask or ExecutionAttempt.

Detailed task and attempt history remains in the existing tables; no run-transition history table is added.

Core business tables are now:

1. `worker`
2. `work_task`
3. `work_task_dependency`
4. `execution_attempt`
5. `approval_request`
6. `autonomous_run`

Flyway latest migration: `V8__r8_autonomous_run_authority.sql`.

## 6. HTTP query

```text
GET /api/r8/runs/{runId}
```

Returns current authoritative status, exact terminal result, approval id, last failed task/reason, failure/recovery counters, recovery actor, and timestamps.

## 7. Acceptance

R8-S4 acceptance requires:

- run created atomically with autonomous requirement intake;
- explicit task failure moves run to `FAILED`;
- recovery requeues failed task(s) without deleting attempt history;
- next claim creates attempt 2+;
- PASSED gate binds exact result revision to the run;
- required approval moves run to `WAITING_APPROVAL`;
- approval moves run to `SUCCEEDED`;
- rejection moves run to `REJECTED`;
- non-autonomous tasks remain unaffected;
- lease-expiry recovery does not become run failure.
