# R8-S7 - Run Dashboard / Progress Projection

## Goal

Expose an operational, read-only view of an autonomous requirement run without introducing a new source of authority or a dashboard persistence table.

The dashboard is derived from existing authoritative state:

- `autonomous_run`: lifecycle/control status and run counters
- `work_task`: task graph progress and active ownership
- `execution_attempt`: attempts, retries, failures, expirations and observed parallelism
- `approval_request`: current approval status

`autonomous_run_event` remains the audit source for non-reconstructable run-level history, but the dashboard does not duplicate that history.

## API

```text
GET /api/r8/runs/{runId}/dashboard
```

The response contains:

- lifecycle status, control state and effective status
- projected phase
- elapsed time
- task totals and counts by state/type
- active task/worker/attempt information
- total attempts and retries
- failed and expired attempts
- current and peak observed execution parallelism
- current approval projection when present
- run failure/recovery/pause/resume counters

## Projected phases

```text
REQUIREMENT_ANALYSIS
SOLUTION_ARCHITECTURE
IMPLEMENTATION_PLANNING
IMPLEMENTATION
INTEGRATION
REVIEW_VERIFICATION
REPAIR
ENGINEERING_GATE
APPROVAL
FAILED
PAUSED
CANCELLED
COMPLETED
```

Operator control and terminal lifecycle states take precedence over task-derived phase calculation.

## Authority rule

The dashboard is not authoritative state. It must never be used as a write model and it must not drive worker ownership decisions. Existing task/run/approval services remain the authority.

## Persistence

R8-S7 adds no table, no column, and no Flyway migration. The schema remains V1-V10 with seven core business tables.
