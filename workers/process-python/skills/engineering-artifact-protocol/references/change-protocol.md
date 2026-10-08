# Change Protocol

## Delta operations

Allowed operations:

- `ADD`
- `MODIFY`
- `REMOVE`
- `SUPERSEDE`
- `INVALIDATE`
- `REORDER`
- `SPLIT`
- `MERGE`
- `CLARIFY`

## Change request payload

Recommended structure:

```json
{
  "reason": "Business now requires retry after transient provider failure",
  "requestedBy": "business-owner",
  "operations": [
    {
      "op": "MODIFY",
      "targetId": "REQ-000018",
      "expectedVersion": 2,
      "summary": "Add bounded retry semantics"
    }
  ],
  "impact": {
    "requirements": "required",
    "architecture": "review_required",
    "implementation": "review_required",
    "verification": "review_required",
    "release": "review_required"
  }
}
```

## Propagation

Use this conceptual propagation chain:

`Requirement Delta -> Architecture Impact -> Implementation Impact -> Verification Impact -> Release Impact`

Do not skip a layer merely because an agent believes the code change is small.

## Conflict handling

Create a `gap` or finding rather than silently reconciling:

- `BASELINE_CONFLICT`
- `PROTOCOL_CONFLICT`
- `REQUIREMENT_CONFLICT`
- `DESIGN_GAP`
- `PLAN_GAP`
