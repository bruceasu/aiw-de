# Operations Guide

## 1. Deployment modes

### Local development / demonstration

Use the default `sqlite` profile. This is the preferred low-operations mode for local development and functional testing.

### Shared/server

Use PostgreSQL and a non-SQLite active profile. External workers may run on other machines as long as they can reach the Control Plane and the required Git repository/result publication mechanism.

## 2. Local startup

Prerequisites:

- JDK 21;
- Maven 3.9+;
- Git;
- for a real Codex worker: authenticated Codex CLI or the SDK required by the selected worker.

Create the SQLite parent directory before startup:

```bash
mkdir -p data
mvn spring-boot:run
```

The default service binds to:

```text
127.0.0.1:8080
```

The default SQLite file is:

```text
./data/aidev-r3.db
```

## 3. Build and verification

Always validate from a clean tree before deploying:

```bash
mvn clean verify
```

Current R9 documentation baseline expectation: **33 Failsafe integration-test methods**.

Build an executable Spring Boot jar:

```bash
mvn clean package
java -jar target/ai-dev-manager-r9-9.0.0-SNAPSHOT.jar
```

## 4. PostgreSQL startup

Example environment:

```bash
export SPRING_PROFILES_ACTIVE=postgresql
export AIW_DB_URL='jdbc:postgresql://db-host:5432/aidev'
export AIW_DB_USER='aidev'
export AIW_DB_PASSWORD='change-me'
export AIW_BIND_ADDRESS='0.0.0.0'
export AIW_WORKER_TOKEN='long-random-secret'
java -jar target/ai-dev-manager-r9-9.0.0-SNAPSHOT.jar
```

There is no `application-postgresql.yaml`; activating a non-SQLite profile prevents the default `sqlite` profile from loading, leaving the base PostgreSQL datasource/dialect configuration active.

## 5. Database migrations

Flyway runs automatically at startup from:

```text
classpath:db/migration
```

Current latest migration: **V14**.

Operational upgrade sequence:

1. stop or drain application instances as required by your maintenance policy;
2. back up the database;
3. deploy the new application;
4. allow Flyway to migrate;
5. verify startup and dashboards;
6. start/resume workers.

Do not manually edit Flyway history or change an already-applied migration file.

## 6. Backup and restore

### SQLite

For the safest consistent copy, stop the Control Plane before copying the database file:

```text
./data/aidev-r3.db
```

Keep the application version/migration level together with the backup metadata.

### PostgreSQL

Use the organization’s normal PostgreSQL backup mechanism (`pg_dump`, snapshots, managed-service backup, etc.). Test restore procedures before relying on them operationally.

## 7. Worker operations

Reference workers normally:

1. register;
2. periodically heartbeat;
3. run one loop per configured concurrency slot;
4. poll claim;
5. execute leased work;
6. drain/offline on graceful shutdown.

Manual worker state endpoints are available through Worker Protocol:

```text
POST /api/r1/worker-protocol/v1/workers/{workerId}/drain
POST /api/r1/worker-protocol/v1/workers/{workerId}/offline
```

Use `DRAINING` before planned maintenance if you want a worker to stop receiving new work while current work finishes naturally.

### R10 process-worker fleet

The [TypeScript](../../workers/process-typescript/README.md), [Go](../../workers/process-go/README.md), and [Python](../../workers/process-python/README.md) process workers run as independent processes, one role profile per process. Keep an active worker for each stage that a run may reach: requirement analysis, architecture, planning, coding, review, verification, and repair. Add integration execution and integration verification where runs can create integration tasks. Missing role coverage leaves compatible READY work in the unmatched backlog; inspect `GET /api/r1/fleet/dashboard` after startup and after changing the fleet.

The single [TypeScript process worker](../../workers/process-typescript/README.md) selects `codex-sdk` or `openai-agents` explicitly at startup. The former API package and launcher have been removed. For Agents, configure `OPENAI_API_KEY` and approve `openai-agents` runtime profiles in relevant task policies. Read-only roles have file inspection tools; writable roles can explicitly enable a host shell on an isolated host/container because it is not an OS sandbox. See the worker guide for configuration and validation limits.

Configure every process with the same `AIW_CONTROL_PLANE_URL`, `AIW_REPOSITORY_REF`, and reachable `AIW_REPOSITORY_ROOT`. Set `AIW_WORKER_CAPABILITIES` to technical capabilities required by submitted runs; the process runtime adds its role capability and `repo:<repositoryRef>`. Give each concurrent or replicated process a unique `AIW_WORKER_ID`. If workers run on different machines, configure a shared Git publication remote (`AIW_GIT_PUBLISH_REMOTE`) so later stages can obtain code results.

For a rolling worker update, drain the old process, wait for its active lease count to reach zero, stop it, then start the replacement with the same role and a unique worker ID. Keep enough role coverage during the transition to avoid a stalled READY backlog. If a process dies during a lease, the Control Plane's lease recovery loop requeues the task after expiry; inspect the run timeline before intervening. Read-only role profiles enforce a read-only Codex sandbox; coding, integration, and repair are the writable roles.

## 8. Lease recovery

A scheduled recovery loop is enabled by default:

```text
AIW_LEASE_RECOVERY_ENABLED=true
AIW_LEASE_RECOVERY_INTERVAL_MS=5000
AIW_LEASE_RECOVERY_BATCH_SIZE=100
```

If a worker disappears and its task lease expires, the Control Plane requeues the task and expires the execution attempt. This is different from an explicit `/fail`, which is treated as engineering/run failure.

## 9. Run incident operations

### Explicit task failure

Symptom: Run becomes `FAILED`.

Action:

1. inspect `/dashboard` and `/timeline`;
2. identify `lastFailedTaskId` and `lastFailureReason`;
3. resolve environment/repository issue;
4. call `/recover`.

### Suspicious active execution

Use `pause`, inspect repository/workers, then `resume`.

### Requirement withdrawn / unsafe continuation

Use `cancel`. Cancellation is irreversible.

## 10. Scheduling operations

### Per-run

Use `/api/r1/runs/{runId}/scheduling`.

- lower values: block new claims as current work drains;
- higher values: make capacity available to subsequent pulls;
- 0: unlimited.

### Fleet-wide

Use `/api/r1/fleet/scheduling` with the same contraction semantics.

A positive `overcommittedSlots` after contraction is expected and not an accounting error.

## 11. Health/observability checks

There is no Spring Actuator health endpoint in the current baseline. Operational checks should use:

```text
GET /api/r1/fleet/dashboard
GET /api/r1/fleet/scheduling
```

Important consistency indicators:

- worker lease accounting delta should be 0;
- run slot accounting delta should be 0;
- unexpected growth of FAILED/PAUSED runs needs operator attention;
- unmatched READY backlog means no active worker has all required capabilities;
- capacity/admission blocked backlog identifies the relevant scheduling ceiling.

## 12. SQLite operational rule

The SQLite profile intentionally sets Hikari `maximum-pool-size: 1`. Do not “fix” single-connection behavior by increasing the pool to hide nested JDBC queries. Repository/projection code must not execute another `JdbcTemplate` query from inside a JDBC `RowMapper`/callback that already holds the only connection.

This rule exists because R8-S7 previously exposed exactly that self-deadlock pattern.

## 13. Logging

The application currently uses Spring/JUL defaults rather than a dedicated structured logging subsystem. Capture stdout/stderr through your process manager/container platform. For server deployment, add environment-specific logging/retention without changing lifecycle authority.

## 14. Production hardening checklist

Before exposing beyond a trusted internal environment:

- run PostgreSQL multi-instance concurrency tests;
- put management APIs behind authentication/authorization;
- terminate TLS at a trusted reverse proxy/load balancer or application boundary;
- rotate and protect `AIW_WORKER_TOKEN` and Telegram token;
- define backup/restore/retention policy;
- define secrets management;
- run long-duration worker crash/recovery tests;
- run real AI-runtime end-to-end scenarios, including all deployed process-worker roles;
- define release/PR integration policy if automatic delivery is required.

## Web Console operations

For day-to-day operation, prefer `http://127.0.0.1:8080/ui` over manual REST calls. Run Detail exposes Pause/Resume/Recover/Cancel and per-run scheduling. Workers exposes Drain/Offline. System exposes Fleet scheduling and its audit history. Approvals exposes human decisions. All browser mutations call the same application services as the REST adapters.

When reducing Run/Fleet limits, expect a temporary overcommitted state if current healthy leases exceed the new limit; this is normal natural-drain behavior.
