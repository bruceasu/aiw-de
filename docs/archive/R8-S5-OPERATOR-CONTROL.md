# R8-S5 Operator Control: Pause / Resume / Cancel

## Purpose

R8-S4 made an autonomous requirement run authoritative. R8-S5 adds explicit operator control without turning the Control Plane into a push scheduler.

The autonomous lifecycle (`RUNNING`, `FAILED`, `ENGINEERING_PASSED`, ...) remains separate from operator control:

```text
control_state = ACTIVE | PAUSED | CANCELLED
```

This separation avoids rewriting the accepted engineering/approval lifecycle and makes operator intervention an orthogonal authority.

## Pause semantics

`POST /api/r8/runs/{runId}/pause`

Pause is a **hard execution pause**:

1. the run authority is serialized;
2. every current `CLAIMED` / `RUNNING` task lease is revoked;
3. the task returns to `READY`;
4. its execution attempt becomes `EXPIRED` with `termination_reason=OPERATOR_PAUSED`;
5. worker capacity is released;
6. `control_state` becomes `PAUSED`;
7. no new autonomous task from that run can be claimed.

The Worker Protocol already treats heartbeat loss as loss of ownership, so reference Workers stop/interrupt the active Codex execution when they observe the rejected heartbeat.

## Resume semantics

`POST /api/r8/runs/{runId}/resume`

Resume changes only `PAUSED -> ACTIVE`. Requeued tasks are not pushed to any Worker; they become eligible for the normal pull/claim loop. The next claim creates a new execution attempt.

`CANCELLED` is irreversible and cannot be resumed.

## Cancel semantics

`POST /api/r8/runs/{runId}/cancel`

Cancellation is allowed while the execution lifecycle is `RUNNING` or `FAILED`.

- completed tasks remain `COMPLETED`;
- `BLOCKED`, `READY`, and `FAILED` tasks become `CANCELLED`;
- `CLAIMED` / `RUNNING` leases are revoked and their attempts become `EXPIRED` with `termination_reason=OPERATOR_CANCELLED`;
- the run becomes `control_state=CANCELLED` and `terminal=true`.

Cancellation is intentionally rejected after engineering/approval authority has already been established (`ENGINEERING_PASSED`, `WAITING_APPROVAL`, `SUCCEEDED`, `REJECTED`). Those phases have separate authority semantics.

## Failure interaction

R8-S5 also closes an R8-S4 boundary: when an autonomous run is `FAILED`, other READY branches may no longer be newly claimed. An explicit `recover` returns the run to `RUNNING`, after which the normal pull loop resumes.

Natural lease expiry remains ordinary crash recovery and does not fail the run.

## Concurrency authority

Worker claim, worker execution, pause/resume/cancel all serialize on the `autonomous_run` authority row. This prevents an operator action from racing silently with a new claim or completion.

## Persistence

No new business table is introduced.

V9 adds current operator-control metadata to `autonomous_run` and `termination_reason` to `execution_attempt`.

Core business tables remain six:

1. `worker`
2. `work_task`
3. `work_task_dependency`
4. `execution_attempt`
5. `approval_request`
6. `autonomous_run`

## API status representation

The existing `status` field remains the engineering lifecycle status for compatibility. The run API additionally returns `controlState` and a derived `effectiveStatus`; while paused/cancelled, `effectiveStatus` is `PAUSED`/`CANCELLED` even though the preserved lifecycle `status` may still be `RUNNING`.
