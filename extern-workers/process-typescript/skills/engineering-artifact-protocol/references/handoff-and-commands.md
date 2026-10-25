# Handoff Batches and Commands

## Handoff batch

When an agent emits multiple related objects, wrap them in a batch rather than letting the orchestrator persist objects one by one without context.

Recommended shape:

```json
{
  "protocolVersion": "1.0",
  "batchId": "BATCH-000041",
  "projectId": "PRJ-000001",
  "producer": {"actorType": "agent", "actorId": "solution-architect"},
  "mode": "atomic",
  "expectedBaselines": [
    {"id": "SRS-BL-000007", "version": 1}
  ],
  "artifacts": [],
  "commands": []
}
```

`atomic` means validate the whole batch and persist none of it if a required object, reference, authorization, or version check fails.

Use `best_effort` only for explicitly independent telemetry/import workloads, not engineering baseline transitions.

## Commands are proposals

Agents may propose commands such as:

- `register_baseline_candidate`
- `request_state_transition`
- `route_work`
- `request_approval`
- `mark_blocked`

A command is not an event and does not prove that the action happened.

The orchestrator validates a command and, if accepted, emits an immutable `orchestration_event` recording the committed result.

## State-transition request

Recommended fields:

```json
{
  "commandId": "CMD-000018",
  "commandType": "request_state_transition",
  "subjectId": "WP-000021",
  "expectedVersion": 3,
  "from": "in_progress",
  "to": "code_review",
  "reasonArtifactIds": ["IMPL-EVID-000033"]
}
```

Reject when:

- subject version is stale
- `from` does not match current state
- transition is illegal
- required gate evidence is missing
- actor is not authorized
- referenced artifacts are not authoritative/visible

## Idempotency

Every externally submitted batch or command should have a stable request/batch/command ID so retried transport does not duplicate committed engineering actions.
