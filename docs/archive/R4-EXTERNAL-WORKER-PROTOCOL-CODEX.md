# R4 — External Worker Protocol + Multi-language Codex Workers

Status: IMPLEMENTED / PENDING USER MAVEN GATE

## 1. Goal

R3 proved task graphs, atomic claim/lease ownership, worker loops, concurrency, and crash recovery semantics inside one Java process.

R4 removes both process and implementation-language coupling:

```text
Control Plane
     |
Worker Protocol v1
     |
external worker process
     |
agent runtime
```

Reference workers now exist in Python, TypeScript, JavaScript, Go and Java.

## 2. Authority boundary

The external worker can request lifecycle operations but cannot authoritatively change lifecycle state.

```text
worker                         Control Plane
------                         -------------
register        -------------> validates/persists worker
claim           -------------> atomic ownership CAS
start           -------------> validates lease
heartbeat       -------------> renews lease
complete/fail   -------------> validates lease + transitions attempt/task
```

Workers have no database credentials and never issue SQL.

## 3. Protocol v1

Base path:

```text
/api/worker-protocol/v1
```

Endpoints:

```text
POST /workers/register
POST /workers/{workerId}/heartbeat
POST /workers/{workerId}/drain
POST /workers/{workerId}/offline
POST /tasks/claim
POST /tasks/{taskId}/start
POST /tasks/{taskId}/heartbeat
POST /tasks/{taskId}/complete
POST /tasks/{taskId}/fail
```

Claim returns `204 No Content` when there is no compatible READY task.

State conflicts such as stale/expired ownership return `409`.

## 4. Protocol security

Default server bind address:

```text
127.0.0.1
```

Remote-worker deployments should set:

```text
AIW_BIND_ADDRESS=0.0.0.0
AIW_WORKER_TOKEN=<secret>
```

When a token is configured, Worker Protocol requests require:

```text
Authorization: Bearer <secret>
```

This is intentionally a simple R4 trust mechanism, not the final worker identity/PKI model.

## 5. Executable instruction

R4 adds `work_task.instruction` through Flyway V2.

This is deliberately a column rather than another table because instruction text has no independent lifecycle or concurrency semantics.

The claimed-task response contains:

```text
taskId
taskType
subjectType
subjectId
requiredCapabilities
skillRef
instruction
priority
attemptId
attemptNo
leaseToken
leaseAcquiredAt
leaseExpiresAt
```

## 6. Codex session traceability

`execution_attempt.external_session_id` already existed in R3-S2.

Every reference worker captures a Codex thread/session identity when the runtime exposes one and reports it through task heartbeat/terminal reporting. No separate Codex session table is introduced.

## 7. Reference worker matrix

```text
workers/codex-python       Python      Codex Python SDK
workers/codex-typescript   TypeScript  @openai/codex-sdk
workers/codex-javascript   JavaScript  @openai/codex-sdk
workers/codex-go           Go          codex exec --json
workers/codex-java         Java 21     codex exec --json
```

The worker protocol is the stable boundary. Runtime choice is deliberately replaceable.

## 8. SDK workers

TypeScript and JavaScript use the official Codex SDK package. The SDK wraps the Codex CLI and emits structured thread/turn/item events.

The workers use `runStreamed()` so that `thread.started` can be persisted immediately as the attempt's external session id.

If lease heartbeat fails, the active turn is aborted and stale completion/failure is suppressed.

The Python reference follows the same ownership rule with the Python SDK.

## 9. CLI workers

Java and Go use:

```text
codex exec --json
```

They parse the JSONL stream:

```text
thread.started
turn.started
item.completed
turn.completed / turn.failed
```

`thread.started.thread_id` becomes `external_session_id`.

The worker language process remains responsible for:

```text
Worker Protocol HTTP
claim loop
slot concurrency
lease heartbeat
process cancellation
terminal reporting
```

Codex CLI is only the task execution engine.

## 10. Common execution policy

Reference defaults target unattended but bounded execution:

```text
sandbox          workspace-write
approval         never / SDK equivalent
network          disabled
reasoning effort high
max concurrency  1
```

The workspace should be the narrowest repository/worktree root intended to be writable. R5 will make that isolation explicit per task.

## 11. Automatic lease recovery

R4 includes `LeaseRecoveryScheduler`:

```text
@Scheduled
  -> recoverExpiredLeases(batchSize)
```

Default interval is 5 seconds. It adds no persistent state.

Integration tests disable the scheduler to keep timing deterministic; production/local runtime enables it by default.

## 12. Persistence budget

Domain tables remain exactly:

```text
worker
work_task
work_task_dependency
execution_attempt
```

R4 schema delta:

```text
+ work_task.instruction
```

No additional table.

## 13. Acceptance

`R4WorkerProtocolIT` verifies:

- token authentication when configured;
- external worker registration;
- capability-aware pull claim;
- executable instruction materialization;
- start/heartbeat/complete over real HTTP;
- external session id persistence;
- completed attempt state;
- `204` when a worker has no compatible task.

The test does not require OpenAI/Codex network access or authentication.

Reference-worker source gates additionally check Python syntax, JavaScript syntax, Go build, Java 21 compilation, and TypeScript type/syntax compatibility.

## 14. Deliberate non-goals

Not yet included:

- isolated per-task Git worktrees/workspaces;
- Artifact Registry/materialized code-change artifacts;
- task-specific repository routing;
- resume policy across failed attempts;
- TLS/mTLS worker identity;
- structured agent event streaming into the Control Plane;
- review/verification worker specialization.

Those should build on Worker Protocol v1 rather than re-entering the Java execution core.
