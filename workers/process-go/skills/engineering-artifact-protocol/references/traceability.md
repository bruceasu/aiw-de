# Traceability Relationships

Use explicit links of the form:

```json
{
  "rel": "implements",
  "targetId": "REQ-000123",
  "targetVersion": 2
}
```

## Preferred relationships

- `derivedFrom`
- `dependsOn`
- `blocks`
- `blockedBy`
- `drivenBy`
- `satisfies`
- `satisfiedBy`
- `implements`
- `implementedBy`
- `conformsTo`
- `verifies`
- `verifiedBy`
- `violates`
- `foundIn`
- `affects`
- `supersedes`
- `supersededBy`
- `contains`
- `supportedBy`
- `approves`
- `rejects`

Store the authoritative relationship in one direction if the persistence model derives inverse links. Do not require both directions unless the orchestrator guarantees consistency.

## Traceability minimums

A Work Package should normally link to:

- requirements/acceptance criteria it implements
- architecture decisions/elements it must conform to
- dependencies

Verification evidence should link to exactly what it verifies.

A release candidate should link to exact code, verification, approval, migration, and rollback artifacts used for readiness assessment.
