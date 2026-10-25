# Protocol Envelope

Every durable protocol object uses a common envelope.

```json
{
  "protocolVersion": "1.0",
  "id": "WP-000208",
  "type": "work_package",
  "projectId": "PRJ-000001",
  "version": 3,
  "status": "ready",
  "title": "Implement idempotent withdrawal submission",
  "createdAt": "2026-09-20T09:00:00+09:00",
  "updatedAt": "2026-09-20T09:10:00+09:00",
  "provenance": {
    "actorType": "agent",
    "actorId": "implementation-planner",
    "sourceArtifactIds": ["SRS-BL-000007", "ARCH-BL-000004"]
  },
  "links": [],
  "payload": {}
}
```

## Provenance

Recommended fields:

- `actorType`: `human`, `agent`, `system`, `policy`
- `actorId`: stable logical actor name/ID
- `sourceArtifactIds[]`
- `sessionId` optional
- `runId` optional

Do not store secrets, credentials, private chain-of-thought, or raw hidden model reasoning in provenance.

## Extensions

Use `extensions` for implementation-specific metadata.

Namespace keys, for example:

```json
"extensions": {
  "example.orchestrator": {
    "queue": "coding",
    "priority": 40
  }
}
```

Consumers should ignore unknown extension namespaces unless configured to require them.
