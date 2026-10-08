# IDs and Versioning

## Identifier grammar

Recommended pattern:

`<PREFIX><number>`

Examples:

- `REQ-000123`
- `ADR-000041`
- `WP-000208`
- `FINDING-000019`
- `SRS-BL-000007`

Project-scoped uniqueness is acceptable if `projectId` is always part of the persistence key. Global uniqueness is also acceptable. Pick one policy and keep it stable.

## Stable identity

Use the same ID when the semantic identity remains the same but details are revised.

Example:

`REQ-000123 v1` -> ambiguous wording

`REQ-000123 v2` -> clarified wording with the same underlying requirement

Use a new ID when the requirement or engineering object becomes a distinct semantic object.

## Version

`version` is a positive integer starting at 1.

Use optimistic update semantics:

- request proposes `expectedVersion: 3`
- committed revision becomes `version: 4`
- reject stale updates instead of overwriting silently

## Status is not version

Do not encode state in IDs such as `REQ-123-FINAL`.

Typical entity lifecycle:

`draft -> proposed -> accepted -> superseded | rejected | withdrawn`

Execution-oriented objects can use additional states such as:

`ready -> in_progress -> blocked -> completed -> verified -> closed`

Use only statuses allowed for that type.

## Immutability rules

Immutable after acceptance unless a new revision is explicitly created:

- accepted baseline manifest
- approval decision
- verification evidence result
- orchestration event

Never modify evidence to make a failed result look passed. Add new evidence instead.
