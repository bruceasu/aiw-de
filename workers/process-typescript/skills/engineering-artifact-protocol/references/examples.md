# Examples

## Requirement

```json
{
  "protocolVersion": "1.0",
  "id": "REQ-000018",
  "type": "requirement",
  "projectId": "PRJ-000001",
  "version": 2,
  "status": "accepted",
  "title": "Prevent duplicate withdrawal execution",
  "createdAt": "2026-09-20T08:00:00+09:00",
  "updatedAt": "2026-09-20T08:30:00+09:00",
  "provenance": {"actorType": "agent", "actorId": "software-requirements-analyst", "sourceArtifactIds": []},
  "links": [
    {"rel": "satisfiedBy", "targetId": "ADR-000009", "targetVersion": 1}
  ],
  "payload": {
    "category": "functional",
    "statement": "The system shall prevent duplicate business execution when the same accepted withdrawal request is submitted more than once.",
    "sourceConfidence": "confirmed"
  }
}
```

## Work Package

```json
{
  "protocolVersion": "1.0",
  "id": "WP-000021",
  "type": "work_package",
  "projectId": "PRJ-000001",
  "version": 1,
  "status": "ready",
  "title": "Implement withdrawal idempotency",
  "createdAt": "2026-09-20T09:00:00+09:00",
  "updatedAt": "2026-09-20T09:00:00+09:00",
  "provenance": {"actorType": "agent", "actorId": "implementation-planner", "sourceArtifactIds": ["SRS-BL-000007", "ARCH-BL-000004"]},
  "links": [
    {"rel": "implements", "targetId": "REQ-000018", "targetVersion": 2},
    {"rel": "conformsTo", "targetId": "ADR-000009", "targetVersion": 1}
  ],
  "payload": {
    "scope": ["withdrawal application service"],
    "outOfScope": ["payment provider protocol changes"],
    "verification": ["unit", "integration", "acceptance"]
  }
}
```

## Verification evidence

```json
{
  "protocolVersion": "1.0",
  "id": "TEST-EVID-000044",
  "type": "verification_evidence",
  "projectId": "PRJ-000001",
  "version": 1,
  "status": "completed",
  "title": "Duplicate withdrawal acceptance verification",
  "createdAt": "2026-09-20T11:00:00+09:00",
  "updatedAt": "2026-09-20T11:00:00+09:00",
  "provenance": {"actorType": "agent", "actorId": "software-test-verifier", "sourceArtifactIds": ["CODE-BL-000014"]},
  "links": [
    {"rel": "verifies", "targetId": "REQ-000018", "targetVersion": 2}
  ],
  "payload": {
    "result": "pass",
    "environment": "integration-local",
    "observations": ["Two submissions with the same request key produced one business execution."],
    "evidenceRefs": ["test-run:withdrawal-idempotency-44"]
  }
}
```
