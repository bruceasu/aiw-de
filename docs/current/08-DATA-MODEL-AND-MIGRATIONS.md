# Data Model and Migrations

## 1. Persistence philosophy

The project intentionally avoids adding a table merely for normalization. A new table is justified when it owns an independent lifecycle, concurrency authority, independently indexed relationship or audit history that cannot be reconstructed from existing durable facts.

Current schema: **9 tables**, Flyway **V1–V14**.

## 2. Table summary

### `worker`

Current worker registration/capacity state.

Key fields:

- `id`
- `runtime_type`
- `capabilities`
- `max_concurrency`
- `active_leases`
- `status`
- heartbeat/registration timestamps

### `work_task`

Current task authority.

Key fields:

- task identity/type/state/subject;
- required capabilities and skill reference;
- priority/version/attempt number;
- current worker and lease token/timestamps;
- instruction;
- logical `repository_ref` and immutable `base_revision`.

Lease **current state** is stored here; there is no separate `task_lease` table.

### `work_task_dependency`

DAG edges:

```text
task_id -> depends_on_task_id
```

This relationship independently controls readiness and is therefore persisted/indexed separately.

### `execution_attempt`

Immutable-ish attempt history and result evidence.

Contains:

- attempt/worker/lease identity;
- state and external AI session ID;
- created/started/finished timestamps;
- workspace key/branch/base/result revision;
- changed files and publication ref;
- completion verdict/summary;
- engineering gate status and review/verification references;
- termination reason.

A retry creates another attempt rather than overwriting the previous one.

### `approval_request`

Independent human-approval authority:

- exact source task/result revision;
- gate-close time;
- PENDING/APPROVED/REJECTED;
- requester/channel;
- immutable decision actor/note/time.

### `autonomous_run`

Run-level lifecycle/current authority:

- lifecycle status;
- control state;
- terminal source/result;
- approval reference;
- failure/recovery counters;
- pause/resume/cancel metadata;
- event sequence;
- per-run `max_parallelism` and `active_slots`.

### `autonomous_run_event`

Retains run-level history that cannot be reconstructed from snapshots:

```text
FAILED
RECOVERED
PAUSED
RESUMED
CANCELLED
MAX_PARALLELISM_CHANGED
```

Strict `(run_id, sequence_no)` ordering avoids ambiguity when multiple actions have the same millisecond timestamp.

### `fleet_admission_budget`

Singleton global scheduling authority (`id='GLOBAL'`):

- `max_parallelism`
- update timestamp

It is also used as the database serialization point for fleet-wide admission. Live task state remains the active-work count authority; there is no duplicated global active-slot counter.

### `fleet_admission_budget_event`

Append-only audit of runtime fleet-ceiling changes:

- sequence;
- old/new limit;
- actor/reason;
- active leases observed at change time;
- timestamp.

## 3. Flyway history

| Migration | Purpose |
|---|---|
| V1 | R3 core Worker/Task/Dependency/Attempt schema |
| V2 | executable task instruction |
| V3 | isolated workspace/result revision fields |
| V4 | result publication reference |
| V5 | completion verdict/summary |
| V6 | engineering gate evidence on attempt |
| V7 | human approval |
| V8 | autonomous run authority |
| V9 | operator control + attempt termination reason |
| V10 | run audit events |
| V11 | per-run concurrency budget |
| V12 | runtime run scheduling event support |
| V13 | fleet admission budget |
| V14 | fleet scheduling change audit |

## 4. Database portability

Migrations use epoch milliseconds for timestamps so the same core migration set can operate with SQLite and PostgreSQL.

Database-specific concurrency SQL is isolated behind the work database dialect where necessary.

## 5. SQLite constraints

Local SQLite profile:

```text
maximum Hikari pool size = 1
transaction_mode = IMMEDIATE
foreign_keys = ON
busy_timeout = 5000 ms
```

This makes local behavior deterministic and keeps component count low. Repository code must respect the single-connection rule described in the Operations/Troubleshooting guides.
