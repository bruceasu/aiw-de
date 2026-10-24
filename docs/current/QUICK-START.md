# Quick Start — Web Console + SQLite + Java Codex Worker

The fastest path is now browser-first: start the Control Plane and one Worker, then use the Web Console to create and operate a run. REST commands remain useful for automation and troubleshooting.


This is the shortest path to exercise the current system on one local Git repository.

## 1. Prerequisites

Install/prepare:

- JDK 21
- Maven 3.9+
- Git
- Codex CLI installed and authenticated
- a local Git repository to modify

The Control Plane defaults to `127.0.0.1:8080` and SQLite.

## 2. Verify the Control Plane

From the project root:

### PowerShell

```powershell
New-Item -ItemType Directory -Force data | Out-Null
mvn clean verify
mvn spring-boot:run
```

### Bash

```bash
mkdir -p data
mvn clean verify
mvn spring-boot:run
```

In another terminal:

```bash
curl http://127.0.0.1:8080/api/r1/fleet/dashboard
```

You should receive JSON rather than a connection error.

## 3. Prepare the target repository

The repository must have a committed base revision.

```bash
git -C /path/to/repo status
git -C /path/to/repo rev-parse HEAD
```

Save the full SHA returned by `rev-parse HEAD`.

For the first demo, use a disposable/test repository rather than an important working tree.

## 4. Start the Java reference worker

From:

```text
workers/codex-java
```

compile:

```bash
rm -rf out
javac --release 21 -d out src/main/java/me/asu/aidev/worker/example/CodexJavaWorker.java
```

Set environment variables. Example Bash:

```bash
export AIW_CONTROL_PLANE_URL=http://127.0.0.1:8080
export AIW_WORKER_ID=local-java-1
export AIW_WORKER_MAX_CONCURRENCY=2
export AIW_WORKER_CAPABILITIES=requirement-analysis,solution-architecture,implementation-planning,coding,integration,code-review,verification,integration-verification,repair,java,maven
export AIW_REPOSITORY_REF=core
export AIW_REPOSITORY_ROOT=/absolute/path/to/repo
export AIW_WORKTREE_ROOT=/absolute/path/to/.aidev-worktrees
export AIW_CODEX_PATH=codex

java -cp out me.asu.aidev.worker.example.CodexJavaWorker
```

PowerShell example:

```powershell
$env:AIW_CONTROL_PLANE_URL="http://127.0.0.1:8080"
$env:AIW_WORKER_ID="local-java-1"
$env:AIW_WORKER_MAX_CONCURRENCY="2"
$env:AIW_WORKER_CAPABILITIES="requirement-analysis,solution-architecture,implementation-planning,coding,integration,code-review,verification,integration-verification,repair,java,maven"
$env:AIW_REPOSITORY_REF="core"
$env:AIW_REPOSITORY_ROOT="C:\work\demo-repo"
$env:AIW_WORKTREE_ROOT="C:\work\.aidev-worktrees"
$env:AIW_CODEX_PATH="codex"

java -cp out me.asu.aidev.worker.example.CodexJavaWorker
```

For this single-machine demo, `AIW_GIT_PUBLISH_REMOTE` may remain empty because all attempts share the same local repository object database. For workers on different machines, configure a shared Git remote.

### Optional: use R10 process workers

The Java worker above is a single-process reference worker. To exercise the R2 role instructions with R9 pull/lease execution, use the [TypeScript process worker](../../workers/process-typescript/README.md) (Node.js 22+), [Go process worker](../../workers/process-go/README.md) (Go 1.23+), or [Python process worker](../../workers/process-python/README.md) (Python 3.11+) instead. Keep the Control Plane running and use the same repository ref, repository root, and technical capabilities in every worker process.

For Go, build from `workers/process-go` with `go test ./...` and `go build -o process-worker .` (use `process-worker.exe` on Windows). Start **one process per active profile**, setting `AIW_PROCESS_PROFILE` to that profile's path. A minimal full-lifecycle fleet needs `requirement-analyst.toml`, `solution-architect.toml`, `implementation-planner.toml`, `coding-executor.toml`, `code-reviewer.toml`, `verifier.toml`, and `repair-executor.toml`. Also start `integration-executor.toml` and `integration-verifier.toml` when integration tasks are expected. Give replicated processes unique `AIW_WORKER_ID` values. See the Go guide for the exact launch commands and all profile names.

For Python, install `workers/process-python/requirements.txt`, then use its `scripts/run-role.ps1` or `scripts/run-role.sh` in separate terminals for `requirements`, `architect`, `planner`, `coder`, `reviewer`, `verifier`, and `repairer`; add `integrator` when needed. The Python guide has the complete example.

For TypeScript, run `npm ci --omit=optional` and `npm run build` in `workers/process-typescript`, then launch each role with an explicit backend, for example `./scripts/run-role.ps1 coder codex-sdk` or `sh scripts/run-role.sh reviewer openai-agents`. Codex needs an installed authenticated CLI on `PATH` or `AIW_CODEX_PATH`; Agents needs `OPENAI_API_KEY`. The former API package and launcher have been removed. The [TypeScript guide](../../workers/process-typescript/README.md) lists the complete configuration.

The process workers add their role and `repo:<AIW_REPOSITORY_REF>` capabilities automatically. Set `AIW_WORKER_CAPABILITIES` only to the run's additional technical capabilities, such as `java,maven`. Do not launch the preserved release-readiness or orchestrator profiles: R9 has no matching task types. Read-only roles enforce a read-only Codex sandbox; coding, integration, and repair use an isolated writable worktree.

## 5. Submit a requirement

Replace `<BASE_SHA>` with the full commit from step 3.

```bash
curl -X POST http://127.0.0.1:8080/api/r1/requirements \
  -H 'Content-Type: application/json' \
  -d '{
    "requestId":"quickstart-001",
    "requirement":"Add a simple health endpoint and focused tests. Keep the change minimal and do not break existing behavior.",
    "repositoryRef":"core",
    "baseRevision":"<BASE_SHA>",
    "technicalCapabilities":["java","maven"],
    "priority":"NORMAL",
    "maxParallelism":2
  }'
```

Response contains `runId`, for example:

```json
{
  "runId": "AR-quickstart-001",
  "requirementTaskId": "AR-quickstart-001~requirement"
}
```

## 6. Watch progress

```bash
curl http://127.0.0.1:8080/api/r1/runs/AR-quickstart-001/dashboard
curl http://127.0.0.1:8080/api/r1/runs/AR-quickstart-001/timeline
```

Fleet view:

```bash
curl http://127.0.0.1:8080/api/r1/fleet/dashboard
```

The expected sequence is approximately:

```text
Requirement Analysis
-> Architecture
-> Planning
-> Implementation(s)
-> optional Integration
-> Review + Verification
-> possible Repair loop
-> Engineering Gate
-> Approval policy
```

## 7. Approval

Default policy is:

```text
AIW_APPROVAL_POLICY=MANUAL
```

Therefore a successfully engineered run stops at `ENGINEERING_PASSED` until an approval is requested. Query the run and use its exact `terminalSourceTaskId`:

```bash
curl http://127.0.0.1:8080/api/r1/runs/AR-quickstart-001
```

Create the approval request:

```bash
curl -X POST http://127.0.0.1:8080/api/r1/approvals \
  -H 'Content-Type: application/json' \
  -d '{
    "sourceTaskId":"<terminalSourceTaskId>",
    "requestedBy":"human:quickstart",
    "channel":"API"
  }'
```

The response contains `approvalRequestId`. Approve it:

```bash
curl -X POST http://127.0.0.1:8080/api/r1/approvals/<approvalRequestId>/decision \
  -H 'Content-Type: application/json' \
  -d '{
    "decision":"APPROVE",
    "decidedBy":"human:quickstart",
    "note":"Quick Start approval"
  }'
```

The Run should reach `SUCCEEDED` after approval.

If automatic approval-request materialization is desired, set `AIW_APPROVAL_POLICY=REQUIRED` **before starting the run**.

## 8. If something does not run

Use:

```text
GET /api/r1/fleet/dashboard
```

and inspect whether the task is:

- unmatched by capabilities;
- blocked by Worker capacity;
- blocked by Run admission;
- blocked by Fleet admission;
- blocked by Run policy.

See [Troubleshooting and Recovery](11-TROUBLESHOOTING-AND-RECOVERY.md) for details.


## Use the Web Console

Open:

```text
http://127.0.0.1:8080/ui
```

From the browser you can now:

1. choose **New Run** and submit the requirement, repository ref, immutable base SHA, capabilities, priority and run concurrency limit;
2. monitor requirement → architecture → planning → implementation/integration → review/verification → gate;
3. pause, resume, recover or cancel a run;
4. change Run/Fleet `maxParallelism`;
5. drain/offline Workers;
6. request and decide approvals.

When `AIW_APPROVAL_POLICY=MANUAL`, a PASSED Engineering Gate can be turned into an ApprovalRequest directly from Run Detail.

Required Web Console login:

```text
AIW_WEB_CONSOLE_USERNAME=admin
AIW_WEB_CONSOLE_PASSWORD=<strong secret>
AIW_WEB_CONSOLE_ROLE=ADMIN
```

All `/ui` and `/ui/**` business pages require sign-in. Login stays public, missing passwords cannot authenticate, and the former enable flag no longer permits anonymous access. Registered console APIs and human authority operations retain their session/CSRF/role checks; machine operations keep their separate credentials. Keep the Control Plane on localhost/protected internal networking because the management REST APIs retain their existing trust boundary.
