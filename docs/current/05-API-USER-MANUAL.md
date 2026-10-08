# API and User Manual

This manual is for three practical roles:

- **Requester:** submits autonomous engineering requirements.
- **Operator:** monitors and controls runs/workers/scheduling.
- **Approver:** accepts or rejects an engineering-gated result.

Examples assume the local default address `http://127.0.0.1:8080`.

## 1. Submit a requirement

```http
POST /api/r8/requirements
Content-Type: application/json
```

```json
{
  "requestId": "health-endpoint-001",
  "requirement": "Add a /health endpoint and focused tests without breaking existing behavior.",
  "repositoryRef": "core",
  "baseRevision": "maven",
  "technicalCapabilities": ["java", "maven"],
  "priority": "HIGH",
  "maxParallelism": 2
}
```

Response:

```json
{
  "runId": "AR-health-endpoint-001",
  "requirementTaskId": "AR-health-endpoint-001~requirement"
}
```

Rules:

- `requestId` is optional. If present it must match `[A-Za-z0-9._-]{1,80}`.
- Operators can register multiple Workspace roots at `/ui/workspaces`. A root may already exist or be a new directory inside an existing parent. The list and detail pages allow editing and disabling registrations. Removing a Workspace or explicit child registration keeps its directory, link, clone, and files on disk; an implicit child has no registration to remove. After the last explicit child is removed, remaining first-level directories can appear as implicit targets again. A child can be a local directory link, a Git clone, or a new empty directory. With no explicitly registered children, first-level directories are implicit targets; an empty root falls back to `workspaceId/root`. Operators can also explicitly select the root as the target.
- Git initialization is best effort when registering a root or child. A failed or unavailable Git command appears as a warning; the directory remains registered and can still be used in direct directory mode.
- Linking an outside local directory does not initialize Git in the source by default. Select the explicit Git initialization option if adding Git metadata to that source is intended.
- `baseRevision` is optional. Leave it empty to run directly in the registered directory, with no Git revision or result. The directory's permissions determine whether a worker can write; changes remain in that directory without a Git rollback. Direct runs use one task at a time per target and a Run max parallelism of 1.
- When present, `baseRevision` accepts a branch, tag, short SHA, or full SHA. The Control Plane verifies that it resolves to a commit in the selected repository and stores the full commit before starting a Git worktree Run. An invalid nonempty revision is rejected.
- Existing `aidev.workspaces.<workspace>.repositories.<repositoryRef>` YAML entries remain usable as legacy targets and can be entered manually in the Run form. They are not imported into the managed registry. If a managed and YAML target use the same ref with different paths, lookup fails rather than silently changing directories. UI-managed targets do not require `AIW_REPOSITORY_REF` or `AIW_REPOSITORY_ROOT` on the worker; the claim response includes their path.
- `priority` accepts `LOW`, `NORMAL`, `HIGH`, `CRITICAL`, or a numeric value 0–100.
- `maxParallelism` accepts 0–1024; 0 means unlimited. Omit it to use `AIW_RUN_DEFAULT_MAX_PARALLELISM`.

## 2. Inspect run state

```http
GET /api/r8/runs/{runId}
```

Important fields:

- `status`: autonomous lifecycle state;
- `controlState`: operator state (`ACTIVE/PAUSED/CANCELLED`);
- `effectiveStatus`: convenient effective status;
- `terminalSourceTaskId` and `resultRevision`: exact final engineering result when known;
- failure/recovery counters;
- pause/resume metadata;
- scheduling budget (`maxParallelism`, `activeSlots`, `availableSlots`, `overcommittedSlots`).

## 3. Dashboard

```http
GET /api/r8/runs/{runId}/dashboard
```

Use it for a human-oriented view of:

- current phase;
- task counts by state/type;
- active workers/attempts;
- retries, failed and expired attempts;
- current/peak parallelism;
- approval status;
- elapsed time;
- run admission accounting.

## 4. Timeline

```http
GET /api/r8/runs/{runId}/timeline
GET /api/r8/runs/{runId}/timeline?limit=200
```

Default limit is 500; maximum is 2000. If truncated, the API returns the newest N entries in chronological order.

Timeline entries combine task/attempt/gate/approval projections with retained run-level audit events.

## 5. Recover an explicitly failed run

```http
POST /api/r8/runs/{runId}/recover
Content-Type: application/json

{ "recoveredBy": "operator:alice" }
```

Recovery is valid when lifecycle status is `FAILED` and operator control is `ACTIVE`. Failed tasks are requeued; the next claim creates a new attempt.

## 6. Pause / resume

Pause:

```http
POST /api/r8/runs/{runId}/pause

{
  "controlledBy": "operator:alice",
  "reason": "inspect repository state"
}
```

Resume:

```http
POST /api/r8/runs/{runId}/resume

{
  "controlledBy": "operator:alice",
  "reason": "inspection complete"
}
```

Pause is a hard execution pause: active leases are revoked and the tasks are requeued. It is not equivalent to lowering maxParallelism.

## 7. Cancel

```http
POST /api/r8/runs/{runId}/cancel

{
  "controlledBy": "operator:alice",
  "reason": "requirement withdrawn"
}
```

Cancellation is irreversible. Use it only when the run should not resume.

## 8. Change per-run scheduling limit

```http
POST /api/r8/runs/{runId}/scheduling

{
  "maxParallelism": 1,
  "controlledBy": "operator:alice",
  "reason": "reduce load"
}
```

Contraction does not terminate healthy leases. New claims are blocked until active slots naturally drain below the new limit.

## 9. Fleet dashboard

```http
GET /api/r8/fleet/dashboard
GET /api/r8/fleet/dashboard?detailLimit=100
```

The fleet dashboard explains READY backlog using categories such as:

- `claimableReady`
- `unmatchedReady`
- `capacityBlockedReady`
- `admissionBlockedReady`
- `fleetAdmissionBlockedReady`
- `policyBlockedReady`

It also reports worker capacity, run distribution, capability demand and accounting deltas.

## 10. Fleet scheduling ceiling

Current state:

```http
GET /api/r8/fleet/scheduling
```

Update:

```http
POST /api/r8/fleet/scheduling

{
  "maxParallelism": 8,
  "controlledBy": "operator:alice",
  "reason": "business-hours limit"
}
```

Audit history:

```http
GET /api/r8/fleet/scheduling/events?limit=100
```

`0` means unlimited. A runtime update is persistent; `AIW_FLEET_MAX_PARALLELISM` is a bootstrap default for a new database, not a value that overwrites runtime authority on every restart.

## 11. Engineering Gate

Exact code result:

```http
GET /api/r6/engineering-gates/{sourceTaskId}
```

Convenience lookup:

```http
GET /api/r6/engineering-gates/latest-passed?subjectType=AUTONOMOUS_REQUIREMENT&subjectId={runId}
```

For long-lived approval/release authority, persist the exact source task/result identity; do not treat `latest-passed` as a historical authorization reference.

## 12. Human approval

Create manually:

```http
POST /api/r7/approvals

{
  "sourceTaskId": "...",
  "requestedBy": "operator:alice",
  "channel": "API",
  "channelRef": null
}
```

Decide:

```http
POST /api/r7/approvals/{approvalRequestId}/decision

{
  "decision": "APPROVE",
  "decidedBy": "human:alice",
  "note": "Reviewed and approved"
}
```

Other valid decision: `REJECT`.

Query:

```text
GET /api/r7/approvals/{approvalRequestId}
GET /api/r7/approvals/by-source/{sourceTaskId}
```

## 13. Common HTTP behavior

Current controllers use these patterns:

- 200: successful operation/query;
- 204: worker claim has no compatible task;
- 400: invalid request/argument;
- 404: run/approval/gate not found where applicable;
- 409: lifecycle/ownership/state conflict;
- 401: invalid Worker Protocol bearer token when worker token protection is enabled.

## Browser user interface

The same operational actions are available through `/ui`. The Web Console is the preferred human interface; REST endpoints remain appropriate for automation and integrations. See [Web Console](15-WEB-CONSOLE.md).
