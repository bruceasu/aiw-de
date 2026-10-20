# aiw-de Control Plane API

All current control-plane APIs use `/api/r1/...`. Old release-number prefixes, unversioned API addresses and former UI API addresses have no compatibility aliases or redirects. Update external clients together with the server. Workers use `/api/r1/worker-protocol/v1`; console data APIs keep their human session, CSRF, role and ownership checks. HTML pages and redirecting forms stay under `/ui/...`.

This document describes the HTTP interfaces implemented by the current Spring MVC controllers. It is a standalone reference organized by endpoint and DTO, complementing the existing API user manual. The project does not currently include springdoc/Swagger or a generated OpenAPI document. Paths are relative to the application root; substitute the host and port for your deployment.

## Basics

- Default base URL: `http://localhost:8080`. Set `AIW_BIND_ADDRESS` and `AIW_PORT` to change the bind address and port.
- JSON endpoints use `Content-Type: application/json`. Timestamps are ISO-8601 UTC strings.
- The paths below are the paths registered by the controllers. POST requests without a business payload may be sent with an empty body or `{}`, depending on the client.
- IDs, tokens, timestamps, and response data in examples are illustrative. Nullable fields depend on the actual data.

## Authentication and errors

| API group | Authentication |
| --- | --- |
| Worker registration, heartbeat, task claim/start/report and output operations under `/api/r1/worker-protocol/v1/**` | When `AIW_WORKER_TOKEN` is nonempty, send `Authorization: Bearer <token>`. Enrolled delegate IDs additionally require `X-AIW-Delegate-Key`. Worker/lease checks remain in force. These machine operations do not require a browser session. |
| Worker Drain/Offline under `/api/r1/worker-protocol/v1/workers/{workerId}` | The Worker bearer and dedicated identity checks still apply. These are administrative writes, so the persistent human-auth rule below also applies after first delegate activation. |
| External Workflow `/api/r1/workflows/**` | Registered only when `aidev.external-workflow.enabled=true`. Send `X-Workflow-Token`; intake requests use a separate request token, and other calls use the submission token. Its writes also follow the general administrative-write rule below. |
| Console JSON/file APIs: `/api/r1/goals`, `/api/r1/secretary`, `/api/r1/delegation`, `/api/r1/task-outputs`, `/api/r1/workflow-plans`, `/api/r1/plan-editor`, and their children | Keep the Web Console session boundary, including the existing shared local mode when login is disabled. With login enabled, use an authenticated session cookie. Non-GET/HEAD requests require `_csrf`; current action roles and ownership checks remain in force. First delegate activation requires configured human login, and this persistent requirement cannot be removed by disabling delegates or restarting. |
| Other `/api/r1/` administrative writes, including Fleet, approval, engineering and knowledge operations | Before first delegate activation, retain their existing local trust policy. First activation permanently enables human session and CSRF checks for writes: `X-CSRF-Token` or `_csrf`, Approver for approval decisions, Admin for deletion, Operator for other writes. This does not add that write policy to ordinary legacy GET requests. |

Worker Protocol and Worker output errors use `{"code":"bad_request|state_conflict","message":"...","timestamp":"..."}` for 400 and 409 responses. An invalid token returns 401. Fleet APIs use the same structure. Workflow Plan management API errors are plain text; common statuses are 400, 403, 404, and 409. Actual status codes may also depend on Spring's underlying exception handling.

## Worker Protocol v1

Base path: `/api/r1/worker-protocol/v1`. Worker registration and task lease operations use POST.

| Method and path | Purpose | Parameters/request body | Successful response |
| --- | --- | --- | --- |
| `POST /workers/register` | Register or update a Worker | `workerId`, `runtimeType`, `capabilities` string array, `maxConcurrency` integer | Worker status object |
| `POST /workers/{workerId}/heartbeat` | Update Worker heartbeat | Path `workerId`; no business payload | Empty response |
| `POST /workers/{workerId}/drain` | Stop accepting new tasks | Path `workerId` | Empty response |
| `POST /workers/{workerId}/offline` | Mark Worker offline | Path `workerId` | Empty response |
| `POST /tasks/claim` | Claim one executable task | `workerId` | Claimed task object, or `204 No Content` when no task is available |
| `POST /tasks/{taskId}/start` | Start a claimed task | `workerId`, `leaseToken`; optional `externalSessionId`, `profileId`, `profileVersion`, `selectionReason` | Empty response |
| `POST /tasks/{taskId}/heartbeat` | Renew task lease and check for a stop request | `workerId`, `leaseToken`; optional `externalSessionId` | `stopRequested` object |
| `POST /tasks/{taskId}/stop-acknowledgement` | Acknowledge a stop request | `workerId`, `leaseToken` | Empty response |
| `POST /tasks/{taskId}/complete` | Submit task completion result | `workerId`, `leaseToken`; optional `externalSessionId`, `workspaceResult`, `report` | Completed task ID and lists of newly ready/generated task IDs |
| `POST /tasks/{taskId}/fail` | Report task failure | `workerId`, `leaseToken`; optional `externalSessionId`, `failureReason`, `failureCategory` | Empty response |

Registration example:

```http
POST /api/r1/worker-protocol/v1/workers/register
Authorization: Bearer <worker-token>
Content-Type: application/json

{"workerId":"worker-east-01","runtimeType":"codex","capabilities":["java","testing"],"maxConcurrency":2}
```

```json
{"workerId":"worker-east-01","status":"ONLINE","activeLeases":0,"maxConcurrency":2}
```

Claim example:

```http
POST /api/r1/worker-protocol/v1/tasks/claim
Authorization: Bearer <worker-token>
Content-Type: application/json

{"workerId":"worker-east-01"}
```

```json
{
  "taskId":"TASK-123",
  "taskType":"IMPLEMENTATION",
  "subjectType":"issue",
  "subjectId":"ISSUE-42",
  "requiredCapabilities":["java"],
  "skillRef":null,
  "instruction":"Implement the requested change.",
  "instructionRevision":1,
  "priority":50,
  "attemptId":"ATT-456",
  "attemptNo":1,
  "leaseToken":"<lease-token>",
  "leaseAcquiredAt":"2026-10-16T01:00:00Z",
  "leaseExpiresAt":"2026-10-16T01:05:00Z",
  "workspace":null,
  "inputs":[],
  "aiGuidance":null,
  "outputStore":null
}
```

The contents of `aiGuidance`, `outputStore`, and `workspace` depend on configuration and task data; the nulls above do not mean these fields are always empty. The nested completion request objects have these fields: `workspaceResult={resultRevision, changedFiles, publicationRef?}` and `report={verdict, summary}`. Failure reports use `failureReason` and `failureCategory`. Example completion body:

```json
{
  "workerId":"worker-east-01",
  "leaseToken":"<lease-token>",
  "externalSessionId":"session-789",
  "workspaceResult":{"resultRevision":"abc1234","changedFiles":["src/A.java"],"publicationRef":null},
  "report":{"verdict":"PASS","summary":"Implementation completed."}
}
```

Example completion response: `{"taskId":"TASK-123","newlyReadyTaskIds":["TASK-124"],"generatedTaskIds":[]}`. Enum fields must use names recognized by the service. Invalid states or mismatched leases are rejected.

## Worker task outputs

Base path: `/api/r1/worker-protocol/v1/tasks/{taskId}/outputs`. These endpoints require the Worker token and validate the associated lease.

| Method and path | Purpose | Request body | Successful response |
| --- | --- | --- | --- |
| `POST /api/r1/worker-protocol/v1/tasks/{taskId}/outputs` | Register an output bundle | `TaskOutputService.Registration`: supply bundle metadata and files according to the current output registration model | Stored `TaskOutputBundle` JSON |
| `POST /api/r1/worker-protocol/v1/tasks/{taskId}/outputs/read` | Read the file list from a previously registered bundle | `workerId`, `leaseToken`, `attemptId`, `bundleId` | File record array |

Read request example: `{"workerId":"worker-east-01","leaseToken":"<lease-token>","attemptId":"ATT-456","bundleId":"BUNDLE-9"}`. The file record structure is defined by `StoredFile`; the binary file is retrieved through the Web Console file download endpoint. See the current `TaskOutputService.Registration` DTO for the exact registration fields.

## External Workflow API

Base path: `/api/r1/workflows/{workflowId}`. These endpoints are enabled only when `aidev.external-workflow.enabled=true`. A `workflowId` starts with a lowercase letter, contains lowercase letters/digits/hyphens, and ends with `@v<digits>` (for example, `code-review@v1`).

| Method and path | Purpose | Parameters/request body | Successful response |
| --- | --- | --- | --- |
| `POST /requests` | Create an intake request | `X-Workflow-Token` uses the request token; JSON `capability`, `instruction` | 202 with `workflowId` and a new `runId` |
| `POST /runs` | Submit a set of steps directly | `X-Workflow-Token` uses the submission token; JSON `steps` | 202 with `workflowId` and `runId` |
| `POST /runs/{runId}/steps` | Materialize steps after intake | Submission token; JSON `steps` | 202 with the existing `workflowId` and `runId` |
| `GET /runs/{runId}` | Read run status | `X-Workflow-Token` submission token | Workflow status and step statuses |

The `steps` array must contain 1–32 items. Each step has `key`, `capability`, `instruction`, and optional `repositoryRef`, `baseRevision`, and `dependsOn`. Keys and capabilities use identifiers that start with a lowercase letter. Dependencies must reference steps defined earlier in the request. For steps added after intake, a step with no explicit dependencies depends on the intake step.

```http
POST /api/r1/workflows/code-review@v1/runs
X-Workflow-Token: <submission-token>
Content-Type: application/json

{"steps":[
  {"key":"inspect","capability":"java","instruction":"Inspect the change."},
  {"key":"report","capability":"writing","instruction":"Write findings.","dependsOn":["inspect"]}
]}
```

```json
{"workflowId":"code-review@v1","runId":"WF-5e96f8ed9f0e4b5ea7f3a4e8fa2c5e99"}
```

Example status response: `{"workflowId":"code-review@v1","runId":"WF-...","state":"RUNNING","steps":[{"taskId":"WF-...~inspect","state":"READY"}]}`. The aggregate state is `FAILED`, `COMPLETED`, or `RUNNING`.

## Approval API

Prefix: `/api/r1/approvals`.

| Method and path | Purpose | Parameters/request body | Successful response |
| --- | --- | --- | --- |
| `POST /api/r1/approvals` | Create an approval request for a source task | `sourceTaskId`, `requestedBy`; optional `channel`, `channelRef` | Approval record |
| `POST /{approvalRequestId}/decision` | Submit an approval decision | `decision`, `decidedBy`; optional `note` | Updated approval record |
| `GET /{approvalRequestId}` | Find approval by ID | Path `approvalRequestId` | Approval record or 404 |
| `GET /by-source/{sourceTaskId}` | Find approval by source task | Path `sourceTaskId` | Approval record or 404 |

`channel` defaults to `API`; decision and channel values are parsed as enum names. Create example: `{"sourceTaskId":"TASK-123","requestedBy":"operator@example.com","channel":"API","channelRef":"ticket-77"}`. Decision example: `{"decision":"APPROVE","decidedBy":"operator@example.com","note":"Reviewed."}`. The response includes `approvalRequestId`, source task and subject information, `status`, requester/decider and timestamps, and decision note. `decidedBy`, `decisionNote`, and `decidedAt` may be null before a decision.

```json
{
  "approvalRequestId":"APR-123",
  "sourceTaskId":"TASK-123",
  "subjectType":"issue",
  "subjectId":"ISSUE-42",
  "resultRevision":"abc1234",
  "engineeringGateClosedAt":"2026-10-16T01:00:00Z",
  "status":"PENDING",
  "requestedBy":"operator@example.com",
  "channel":"API",
  "channelRef":"ticket-77",
  "requestedAt":"2026-10-16T01:01:00Z",
  "decidedBy":null,
  "decisionNote":null,
  "decidedAt":null
}
```

## Query and Fleet APIs

### Fleet dashboard

`GET /api/r1/fleet/dashboard` returns the current Fleet projection. The optional integer query parameter `detailLimit` controls the number of detail items. The response type is `FleetDashboard`, which includes Worker, lease, ready task, and summary projections; its exact fields can change with that DTO. Example:

```http
GET /api/r1/fleet/dashboard?detailLimit=20
```

```json
{"workers":[],"activeLeases":[],"readyTasks":[]}
```

The example shows only the response shape; the actual `FleetDashboard` contains additional summary fields.

### Fleet scheduling

| Method and path | Purpose | Parameters/request body | Response |
| --- | --- | --- | --- |
| `GET /api/r1/fleet/scheduling` | Read current concurrency budget | None | `maxParallelism`, `activeLeases`, `availableSlots`, `overcommittedSlots`, `saturated`, `updatedAt` |
| `POST /api/r1/fleet/scheduling` | Update concurrency limit | `maxParallelism`, `controlledBy`, `reason` | Updated budget object |
| `GET /api/r1/fleet/scheduling/events?limit=100` | Read recent scheduling changes | Optional integer `limit`, default 100 | Scheduling event array |

Update example: `{"maxParallelism":8,"controlledBy":"operator@example.com","reason":"Increase capacity"}`. Example response: `{"maxParallelism":8,"activeLeases":3,"availableSlots":5,"overcommittedSlots":0,"saturated":false,"updatedAt":"2026-10-16T01:00:00Z"}`.

### Engineering Gate

- `GET /api/r1/engineering-gates/{sourceTaskId}`: find a gate by source task; returns 404 when no record exists.
- `GET /api/r1/engineering-gates/latest-passed?subjectType=issue&subjectId=ISSUE-42`: find the latest passed gate by subject; requires `subjectType` and `subjectId`; returns 404 when no record exists.

Successful responses include `sourceTaskId`, `sourceTaskType`, `subjectType`, `subjectId`, `status`, repository/revision/publication information, `changedFiles`, `review` and `verification` evidence (each with `taskId`, `verdict`, `summary`), and `closedAt`.

### Knowledge documents

- `GET /api/r1/knowledge/documents`: search documents. Optional query parameters: `repositoryRef`, `runId`, `q`; `limit` defaults to 50.
- `GET /api/r1/knowledge/documents/{id}`: retrieve one document; returns 404 when absent.

These return `KnowledgeDocument` values (an array or a single object). Properties are defined by the domain DTO. A schematic list response is `[ {"id":"DOC-1"} ]`; this does not mean the object contains only an `id` field.

## Web Console management APIs

The six console API roots listed above use the Web Console session/role boundary and are not Worker token decision APIs. With login enabled, use a logged-in session; their non-GET/HEAD requests require the `_csrf` parameter. HTML pages and redirecting forms stay under `/ui/**` and retain their session/CSRF checks. Moving console data to `/api/r1` does not remove its protection.

### Workflow plans

Base path: `/api/r1/workflow-plans`.

| Method and path | Purpose | Parameters/request body | Successful response |
| --- | --- | --- | --- |
| `GET /api/r1/workflow-plans?view=current` | List plans; use `view=archived` for archived plans | Optional `view`, default `current` | WorkflowPlan array |
| `GET /api/r1/workflow-plans/{id}` | Read plan details | Path `id` | `{plan,tasks,controls}` |
| `POST /api/r1/workflow-plans` | Create a draft | WorkflowPlanSpec JSON | WorkflowPlan |
| `POST /{id}/copy` | Copy a plan | No business payload | WorkflowPlan |
| `POST /{id}/confirm` | Confirm a plan | `{"version":<nonnegative integer>}` | WorkflowPlan |
| `POST /{id}/withdraw`, `/stop`, `/complete`, `/cancel` | Change plan control state | `{"version":<nonnegative integer>,"reason":"..."}` | WorkflowPlan |
| `POST /{id}/jump` | Jump to a step | `version`, nonnegative `targetStep`, optional `reason` | WorkflowPlan |
| `POST /{id}/resolve-stop` | Clear stale stop control | `{"version":<nonnegative integer>}` | WorkflowPlan |
| `POST /{id}/archive` | Archive a plan | No business payload | Empty response |
| `POST /{id}/schedule-delete`, `/undo-delete` | Schedule deletion or undo it | No business payload | Empty response |

The write operations above require the operator role; scheduling deletion and undoing it require the admin role. Versioned changes prevent updates based on stale state. See the `WorkflowPlanSpec` DTO for its fields.

### Source questions and confirmed answers

Base path: `/api/r1/plan-editor/plans/{planId}/questions`. Reads require an authenticated console session; answering requires the operator role and CSRF protection. These are console endpoints, not Worker-token decision endpoints. Examples below illustrate the contract and have not been executed.

| Method and path (relative to base) | Purpose | Parameters/body | Successful response |
| --- | --- | --- | --- |
| `GET` | Read questions, source documents, and answer history | Path `planId` | Board object |
| `GET /files/{fileId}` | Preview a current plan output | Path `planId`, `fileId` | `{path,content,truncated}`; content limited to 65,536 characters |
| `POST /answers` | Explicitly confirm an answer and advance the plan version | AnswerRequest below | Answer object |

AnswerRequest fields are all required:

| Field | Type | Meaning |
| --- | --- | --- |
| `version` | integer (Java long) | Current `planVersion` returned by the board. |
| `questionKey` | string | Board question `key`; copy it rather than recomputing it. |
| `sourceFileId` | string | Question's `source.file.id`. |
| `sourceHash` | string | Question's `source.file.sha256`. |
| `answer` | string | Nonblank, at most 2000 characters; reserved confirmed-decision delimiters are forbidden. |
| `confirmed` | boolean | Must be `true`; a draft answer has no authority. |

`actor` is taken from the session, not the request. The source must still match, and the plan must be open with no unfinished tasks, pending control, or current unexpired pending repair grant. See [database flows](08-DATA-MODEL-AND-MIGRATIONS.md#4-how-data-moves-through-the-database).

Board fields: `planVersion`, nullable `blockedReason`, `questions`, `documents`, `history`, `warnings`. A question has `key`, `id`, `question`, `impact`, `sourceState`, `affects` (string array), `context`, `source`, and nullable `decision`. A source/document has `taskId`, `attemptId`, `reviewState`, and the output `file` DTO. `history` and `decision` use the Answer response shape below. Extraction limits are 256 questions and 1 MiB per JSON file; extraction failures/limits appear in `warnings`.

Example reads (use an existing authenticated cookie jar):

```sh
curl -b cookies.txt 'http://localhost:8080/api/r1/plan-editor/plans/FP-1/questions'
curl -b cookies.txt 'http://localhost:8080/api/r1/plan-editor/plans/FP-1/questions/files/FILE-1'
```

Example board with no output questions yet, followed by a preview response for a plan that has an output file:

```json
{"planVersion":1,"blockedReason":null,"questions":[],"documents":[],"history":[],"warnings":[]}
```

```json
{"path":"requirements.json","content":"{\"openQuestions\":[]}","truncated":false}
```

For a board containing a question, copy its key, file ID/hash and current version into a request file. Replace the illustrative values below with that board's values; `CSRF_TOKEN` must be the current session token:

```json
{"version":7,"questionKey":"<board question key>","sourceFileId":"FILE-1","sourceHash":"<source SHA-256>","answer":"Use UTC for all report timestamps.","confirmed":true}
```

Save as `answer.json`, then submit:

```sh
curl -b cookies.txt -H 'Content-Type: application/json' \
  --data-binary @answer.json \
  "http://localhost:8080/api/r1/plan-editor/plans/FP-1/questions/answers?_csrf=${CSRF_TOKEN}"
```

Example response (generated IDs, hashes, and timestamp are illustrative):

```json
{"id":"QD-example","planId":"FP-1","questionKey":"<board question key>","sourceTaskId":"FP-1~requirements~work-1","sourceFileId":"FILE-1","sourceHash":"<source SHA-256>","questionId":"Q-1","questionText":"Which time zone should reports use?","answer":"Use UTC for all report timestamps.","actor":"operator@example.com","planVersion":8,"decidedAt":"2026-10-16T01:00:00Z"}
```

HTTP errors: `400` invalid/unconfirmed answer; `401` unauthenticated read; `403` insufficient authority; `404` plan not found; `409` stale version/source or blocked state/context limit. Controller-handled validation/conflict errors are text, for example `Plan changed. Refresh before answering.` Answers append history and do not rewrite old reports, start tasks, or grant operational permissions. Re-answering requires the new board version.

### One-round repair authorization

Base path: `/api/r1/plan-editor/plans/{id}/repair-authorizations`. Both operations require operator access and CSRF. This current implementation supports exactly one round and a one-hour lifetime; configurable policy templates remain a separate proposal.

| Method and path | Purpose | Request | Response |
| --- | --- | --- | --- |
| `POST` base path | Authorize one repair, directly or through a designated decision Worker | `planVersion` (long), `taskId` (source review ID), `taskVersion` (long), `reason` (1–2000 chars), `scope` (1–4000 chars), optional `delegateWorkerId`, `warningConfirmed=true` | Authorization object |
| `POST /{authorizationId}/revoke` | Revoke a pending grant | Path IDs; no business body | `{"status":"REVOKED"}` |

Example `repair.json`: `{"planVersion":8,"taskId":"FP-1~coder~review-3","taskVersion":2,"reason":"Fix review findings","scope":"Correct the timestamp formatting only.","delegateWorkerId":"decision-worker-1","warningConfirmed":true}`.

```sh
curl -b cookies.txt -H 'Content-Type: application/json' --data-binary @repair.json \
  "http://localhost:8080/api/r1/plan-editor/plans/FP-1/repair-authorizations?_csrf=${CSRF_TOKEN}"
curl -b cookies.txt -X POST \
  "http://localhost:8080/api/r1/plan-editor/plans/FP-1/repair-authorizations/RA-example/revoke?_csrf=${CSRF_TOKEN}"
```

Example delegated response: `{"id":"RA-example","planId":"FP-1","planVersion":9,"sourceReviewId":"FP-1~coder~review-3","sourceTaskVersion":2,"scope":"Correct the timestamp formatting only.","reason":"Fix review findings","grantedBy":"operator@example.com","delegateWorkerId":"decision-worker-1","decisionTaskId":"RA-example~decision","repairTaskId":null,"status":"PENDING","createdAt":"2026-10-16T01:00:00Z","expiresAt":"2026-10-16T02:00:00Z"}`. The response DTO omits the database's `round_budget` and `decided_at_epoch_ms` fields. Without a delegate, the service immediately attempts consumption, so the response need not be `PENDING`.

The delegate must be active, have a recent heartbeat and `workflow-recovery-decision`, and differ from the source executor/reviewer. Versions, eligibility, and grant status are checked before additional work; the human scope is passed into decision and repair instructions. The current text scope is not a structured, machine-enforced permission policy. Status and retention details are in the [database dictionary](08-DATA-MODEL-AND-MIGRATIONS.md).

### Task outputs (Web Console)

| Method and path | Purpose | Parameters | Successful response |
| --- | --- | --- | --- |
| `GET /api/r1/task-outputs/tasks/{taskId}` | List task output bundles | Path `taskId` | `TaskOutputBundle[]` |
| `GET /api/r1/task-outputs/files/{fileId}` | Download a file | Path `fileId` | Raw binary with `X-Content-SHA256` header |
| `POST /api/r1/task-outputs/files/{fileId}/publication-plans` | Create a publication plan | `reviewTaskId`, `targetPath`, `reason` | WorkflowPlan JSON |

Read endpoints require login; creating a publication plan requires the operator role. File responses use `application/octet-stream`; the download name is derived from the file path.

## Browser page routes

Web Console routes such as `/ui`, `/ui/runs`, `/ui/runs/{runId}`, `/ui/workers`, `/ui/approvals`, and `/ui/system` serve HTML pages and are not JSON APIs described above. The login page is `/ui/login`. Traditional form actions include Worker drain/offline and Fleet scheduling updates; they redirect back to the console after submission.

## Source and maintenance

This reference is based on the current controllers and DTOs under `src/main/java/me/asu/aidev/**/protocol/http`. Update it when request/response DTOs or routes change. Complete field definitions for dynamic domain DTOs (such as `FleetDashboard`, `WorkflowPlanSpec`, `KnowledgeDocument`, and the output registration model) remain in their Java types.
