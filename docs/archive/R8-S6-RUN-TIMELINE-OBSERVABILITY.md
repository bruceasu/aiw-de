# R8-S6 — Autonomous Run Timeline / Observability Projection

## Goal

Provide a trustworthy, chronological view of one autonomous requirement run without converting the whole control plane into event sourcing.

## Why one new table is justified

The accepted R8-S5 schema can reconstruct most engineering history from existing authorities:

- `work_task`: task identity and creation time;
- `execution_attempt`: claim/start/final attempt state, workspace result, completion verdict, engineering gate;
- `approval_request`: approval request and human decision;
- `autonomous_run`: current run authority.

It cannot reconstruct every historical run-level control action. `autonomous_run` intentionally stores only counters and the latest control/recovery fields, so histories such as:

```text
pause -> resume -> pause -> resume
recover -> fail -> recover
```

lose the earlier actors, reasons, and timestamps.

R8-S6 therefore adds exactly one append-only audit table:

```text
autonomous_run_event
```

This is justified by audit retention, not normalization.

## What the event table stores

Only history that would otherwise be lost:

```text
FAILED
RECOVERED
PAUSED
RESUMED
CANCELLED
```

It does **not** duplicate task creation, attempt lifecycle, engineering gates, or approval decisions.

Every event receives a per-run strictly increasing `sequence_no`. `autonomous_run.event_sequence` allocates this sequence transactionally. This preserves exact operator order even when multiple actions share the same millisecond timestamp.

## Timeline projection

`GET /api/r8/runs/{runId}/timeline`

The response merges:

1. Run start/completion from `autonomous_run`.
2. Task creation from `work_task`.
3. Claim/start/completion/failure/expiry from `execution_attempt`.
4. Engineering Gate closure from the gate columns on `execution_attempt`.
5. Approval request/decision from `approval_request`.
6. Run failure/recovery/operator actions from `autonomous_run_event`.

The projection does not invent timestamps that were never persisted. For example, it does not fabricate a historical `TASK_READY` event from the task's current state.

## API response shape

```json
{
  "runId": "AR-example",
  "status": "RUNNING",
  "controlState": "ACTIVE",
  "effectiveStatus": "RUNNING",
  "totalEntries": 18,
  "returnedEntries": 18,
  "truncated": false,
  "entries": [
    {
      "occurredAt": "...",
      "category": "EXECUTION",
      "eventType": "ATTEMPT_STARTED",
      "runEventSequence": null,
      "taskId": "...",
      "taskType": "IMPLEMENTATION",
      "attemptNo": 2,
      "workerId": "worker-java-1",
      "state": "RUNNING"
    }
  ]
}
```

Optional query parameter:

```text
?limit=500
```

Default: 500. Maximum: 2000. If a run has more entries, the endpoint returns the newest `limit` entries and sets `truncated=true` while preserving chronological order inside the returned slice.

## Persistence budget

R8-S6 core business tables:

1. `worker`
2. `work_task`
3. `work_task_dependency`
4. `execution_attempt`
5. `approval_request`
6. `autonomous_run`
7. `autonomous_run_event`

Flyway: V1–V10.

No generic event store, task event table, artifact history table, or duplicated approval history is introduced.
