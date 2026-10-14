# System Architecture

## 1. Architecture style

AI Worker R8 is a **modular monolith Control Plane** with external AI workers.

```text
                       +--------------------------+
                       |      User / Operator     |
                       | API / Telegram / future  |
                       +------------+-------------+
                                    |
                                    v
+--------------------------------------------------------------------+
|                    Java Control Plane                              |
|                                                                    |
|  autonomous   work   execution   development   approval   fleet    |
|      |          |        |             |           |        |      |
|      +----------+--------+-------------+-----------+--------+      |
|                              |                                     |
|                         JDBC + Flyway                              |
+------------------------------+-------------------------------------+
                               |
                     SQLite / PostgreSQL

                               ^
                               |
                    Worker Protocol v1
                               |
        +----------------------+----------------------+
        |          |           |          |           |
      Python       TS          JS         Go         Java
        |          |           |          |           |
        +----------+-----------+----------+-----------+
                               |
                      Codex SDK / Codex CLI
                               |
                         Git repositories
                      + isolated worktrees
```

The Control Plane does not invoke a “next agent” directly. Completion handlers materialize new tasks and dependencies; available workers subsequently pull those tasks.

## 2. Main Java modules

All production code is rooted at `me.asu.aidev`.

### `work`

Owns durable `WorkTask` and dependency graph semantics:

- task type and state;
- dependency readiness;
- repository/base revision metadata;
- atomic task ownership;
- lease fields on the current task row.

### `worker`

Owns worker registration and capacity:

- runtime type;
- capabilities;
- `ACTIVE / DRAINING / OFFLINE`;
- `maxConcurrency` and current active lease accounting;
- Worker Protocol HTTP adapter.

### `execution`

Owns task claim and execution-attempt semantics:

- pull scheduling;
- per-attempt lease tokens;
- start/heartbeat/complete/fail;
- expired lease recovery;
- result handoff and completion reports;
- completion-handler dispatch.

### `autonomous`

Owns autonomous requirement execution:

- requirement intake;
- requirement → architecture → planning task materialization;
- parallel implementation work-item materialization;
- integration coordination;
- run lifecycle authority;
- pause/resume/cancel/recover;
- per-run concurrency budget;
- run timeline and dashboard projections.

### `development`

Owns engineering result semantics:

- review/verification fan-out;
- repair loop;
- engineering gate;
- immutable association between the exact code result and review/verification evidence.

### `approval`

Owns human approval:

- approval request lifecycle;
- approval policy;
- immutable final decision;
- Telegram notification/decision adapter.

### `fleet`

Owns whole-workforce scheduling/observability:

- fleet/backlog dashboard;
- fleet-wide concurrency ceiling;
- runtime adjustment of the global ceiling;
- fleet scheduling audit history.

## 3. Authority model

The architecture deliberately separates **execution** from **authority**.

| Concern | Authority |
|---|---|
| Task state/dependencies | Java Control Plane + database |
| Task ownership | atomic DB `READY -> CLAIMED` transition + lease token |
| Worker capacity | worker row + transactional reservation |
| Run lifecycle | `autonomous_run` |
| Run operator control | `autonomous_run.control_state` |
| Code result | immutable Git `resultRevision` |
| Review/verification result | completion report persisted on execution attempt |
| Engineering acceptance | Engineering Gate |
| Human authorization | `approval_request` |
| Fleet ceiling | `fleet_admission_budget` |
| AI procedure | worker instruction / AI runtime |

A worker never becomes lifecycle authority merely because its model says “done”. It must complete the leased task through the protocol.

## 4. Worker-pull scheduling path

For a claim request, the Control Plane performs these logical checks:

```text
READY tasks
   |
   +--> worker capability match
   |
   +--> priority + aging + run fairness ranking
   |
   +--> run execution policy
   |
   +--> per-run admission budget
   |
   +--> fleet-wide admission budget
   |
   +--> worker maxConcurrency reservation
   |
   +--> atomic READY -> CLAIMED
```

The important correctness boundary is the final atomic ownership update. Ranking is advisory; ownership is not.

## 5. Concurrency lock/admission order

For autonomous tasks, concurrency is intentionally acquired in this order:

```text
Run budget -> Fleet budget -> Worker capacity -> Task ownership
```

The per-run budget is an atomic slot ledger. The fleet-wide budget does not maintain a second live counter: it serializes admission on the singleton fleet row and counts current `CLAIMED/RUNNING` tasks while the mutex is held.

## 6. Persistence

Current schema: nine tables, Flyway V1–V14.

- `worker`
- `work_task`
- `work_task_dependency`
- `execution_attempt`
- `approval_request`
- `autonomous_run`
- `autonomous_run_event`
- `fleet_admission_budget`
- `fleet_admission_budget_event`

See [Data Model and Migrations](08-DATA-MODEL-AND-MIGRATIONS.md).

## 7. Local vs shared mode

### Local mode

Default profile is `sqlite`:

- SQLite file: `./data/aidev-r3.db` unless `AIW_DB_URL` overrides it;
- Hikari pool size is intentionally one;
- SQLite transaction mode is `IMMEDIATE`;
- suitable for local development, demonstration and single-Control-Plane use.

### Shared/server mode

Activate a non-SQLite profile (for example `postgresql`) and configure `AIW_DB_URL`, `AIW_DB_USER`, `AIW_DB_PASSWORD`. The base configuration and PostgreSQL dialect support shared database operation.

Before treating multi-Control-Plane PostgreSQL deployment as production hardened, run the planned concurrency/chaos stress suite described in [Testing and Limitations](12-TESTING-VALIDATION-AND-LIMITATIONS.md).
