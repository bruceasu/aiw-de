# Java and Go Integration

## Design principle

Treat the protocol as a versioned domain contract independent of any LLM provider.

## Persistence

A simple local-first implementation can use SQLite or PostgreSQL with:

- `artifact` table: current projection
- `artifact_revision` table: immutable revisions
- `artifact_link` table: normalized relationships when graph queries are needed
- `baseline` and `baseline_item` tables
- `orchestration_event` append-only table

For small teams, avoid introducing Kafka, graph databases, or event-sourcing infrastructure unless concrete scale/operational requirements justify them.

## Java

Recommended mapping:

- sealed interface or explicit type discriminator for artifact payloads
- Jackson with strict enum parsing
- records for immutable DTOs where JDK level permits
- Bean Validation or explicit validator layer
- optimistic locking on `(projectId, id, version)`

Keep lifecycle/state-transition logic in application/domain code rather than prompts.

## Go

Recommended mapping:

- common envelope struct plus typed payload structs
- custom unmarshalling or type switch on `type`
- explicit validation functions
- transactionally append revision + update current projection

## LLM boundary

LLM output should be treated as an untrusted proposal:

1. parse
2. schema validate
3. validate IDs and references
4. validate actor authorization
5. validate state transition
6. apply optimistic version check
7. persist atomically

Do not let natural-language text directly mutate orchestrator state.
