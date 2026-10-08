# Worker Protocol and Reference Workers

## 1. Design goal

Workers are external AI employees. They do not access the Control Plane database. They communicate exclusively through Worker Protocol v1 and can be implemented in any language.

Base path:

```text
/api/worker-protocol/v1
```

## 2. Worker lifecycle

```text
register
   |
   +--> heartbeat
   |
   +--> claim
          |
          v
        start
          |
       heartbeat
          |
     execute task
          |
      +---+---+
      |       |
   complete  fail

shutdown: drain -> offline
```

## 3. Endpoints

### Register

```http
POST /api/worker-protocol/v1/workers/register
Content-Type: application/json
```

```json
{
  "workerId": "codex-java-1",
  "runtimeType": "codex-java",
  "capabilities": ["java", "maven", "coding", "repo:core"],
  "maxConcurrency": 2
}
```

### Worker heartbeat

```http
POST /api/worker-protocol/v1/workers/{workerId}/heartbeat
```

### Drain / offline

```http
POST /api/worker-protocol/v1/workers/{workerId}/drain
POST /api/worker-protocol/v1/workers/{workerId}/offline
```

`DRAINING` should not accept new work; `OFFLINE` represents a stopped worker.

### Claim

```http
POST /api/worker-protocol/v1/tasks/claim
```

```json
{ "workerId": "codex-java-1" }
```

A compatible task returns HTTP 200. No compatible/claimable task returns HTTP 204.

The claim response carries task authority needed by the worker: task type, instruction, priority, lease token/times, workspace assignment and authoritative upstream `inputs[]`.

### Start and heartbeat

```http
POST /api/worker-protocol/v1/tasks/{taskId}/start
POST /api/worker-protocol/v1/tasks/{taskId}/heartbeat
```

```json
{
  "workerId": "codex-java-1",
  "leaseToken": "...",
  "externalSessionId": "codex-thread-or-session-id"
}
```

### Complete

```http
POST /api/worker-protocol/v1/tasks/{taskId}/complete
```

Repository task example:

```json
{
  "workerId": "codex-java-1",
  "leaseToken": "...",
  "externalSessionId": "...",
  "workspaceResult": {
    "resultRevision": "<full git commit>",
    "changedFiles": ["src/main/java/..."],
    "publicationRef": "git:origin:aiw/..."
  },
  "report": {
    "verdict": "PASS",
    "summary": "..."
  }
}
```

`report` is required by semantic planning/review stages as appropriate; normal coding/integration tasks can complete without a semantic report.

### Fail

```http
POST /api/worker-protocol/v1/tasks/{taskId}/fail
```

```json
{
  "workerId": "codex-java-1",
  "leaseToken": "...",
  "externalSessionId": "...",
  "failureReason": "build failed after ..."
}
```

An explicit failure is different from a worker disappearing: it can move an autonomous run to `FAILED` and require recovery.

## 4. Authentication

Worker protocol protection is optional in local mode. Set:

```text
AIW_WORKER_TOKEN=<shared-secret>
```

When non-empty, every `/api/worker-protocol/...` request must include:

```http
Authorization: Bearer <shared-secret>
```

The implementation compares token bytes using `MessageDigest.isEqual`.

For remote/server operation, configure a token and place the service behind a trusted network/TLS boundary.

## 5. Capabilities

A worker may advertise arbitrary capabilities. Current autonomous roles use:

```text
requirement-analysis
solution-architecture
implementation-planning
coding
integration
code-review
verification
integration-verification
repair
```

Legacy YAML repository tasks automatically require:

```text
repo:<repositoryRef>
```

UI-managed Workspace targets are assigned by the Control Plane and do not require workers to advertise a separate `repo:` capability for every registered directory. Technical capabilities such as `java`, `maven`, `sql`, `go`, `python` may still be required.

## 6. Reference worker implementations

| Directory | Language | AI runtime |
|---|---|---|
| `workers/codex-python` | Python | Codex Python SDK |
| `workers/codex-typescript` | TypeScript | `@openai/codex-sdk` |
| `workers/codex-javascript` | JavaScript | `@openai/codex-sdk` |
| `workers/codex-go` | Go | installed `codex exec --json` |
| `workers/codex-java` | Java 21 | installed `codex exec --json` |

The Java and Go workers are useful when minimal runtime dependencies are desired. The Java reference worker uses only JDK facilities plus external `git` and `codex` executables.

## 7. Workspace contract

When the claim contains `workspace`, it includes `workspaceId`, `workspaceName`, `workspaceRoot`, `repositoryPath`, and `mode` alongside `repositoryRef`, `baseRevision`, `workspaceKey`, and `branchName`. UI-managed targets send an absolute local path. `mode=DIRECT` has null `baseRevision` and `branchName`; the worker executes in `repositoryPath` and reports null `workspaceResult`. Directory permissions decide whether writes succeed.

For `mode=GIT_WORKTREE`:

1. verify the assigned `repositoryRef`;
2. fetch compatible upstream publication refs when supplied;
3. verify the immutable `baseRevision` exists;
4. create an attempt-specific Git worktree/branch;
5. run the AI runtime inside that worktree;
6. commit remaining changes;
7. compute the complete `baseRevision..resultRevision` changed-file set;
8. optionally publish the result branch;
9. report the `WorkspaceResult`;
10. remove the local worktree.

The Control Plane stores paths for UI-managed local Workspaces because version 1 runs workers on the same machine. Legacy YAML targets remain supported.

## 8. Important environment variables for workers

Common variables:

```text
AIW_CONTROL_PLANE_URL
AIW_WORKER_ID
AIW_WORKER_TOKEN
AIW_WORKER_CAPABILITIES
AIW_WORKER_MAX_CONCURRENCY
AIW_REPOSITORY_REF
AIW_REPOSITORY_ROOT
AIW_WORKTREE_ROOT
AIW_GIT_PUBLISH_REMOTE
AIW_CODEX_MODEL
AIW_CODEX_PATH              # CLI-based workers
AIW_CODEX_SANDBOX
AIW_CODEX_APPROVAL_POLICY
AIW_CODEX_REASONING_EFFORT
AIW_CODEX_NETWORK_ACCESS
```

Polling/heartbeat variable names differ slightly by reference implementation (`*_MS` in Java/JS/TS, `*_SECONDS` in Python). Consult the worker source/README when deploying a specific implementation.
