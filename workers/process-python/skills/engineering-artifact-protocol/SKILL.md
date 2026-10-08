---
name: engineering-artifact-protocol
description: Define how software-engineering agents exchange structured artifact proposals, exact revision references, cross-repository contracts, handoffs, evidence, findings, and lifecycle action proposals with the Java vNext Control Plane. Use when an agent must produce machine-consumable engineering results for requirements, architecture, planning, coding, review, verification, release, change management, or multi-repository coordination. The Java Control Plane owns durable IDs, revisions, baselines, hashes, approvals, commands, and state transitions; agents only propose semantic payloads and evidence.
---

# Engineering Artifact Protocol

## Purpose

Produce structured engineering results that the Java vNext Control Plane can validate, persist, trace, freeze, and replay.

Treat the Java domain model as runtime authority. Do not create a second artifact registry or workflow state machine inside agent output.

## Hard authority rules

1. Never invent durable Artifact IDs, Baseline IDs, Revision numbers, Command IDs, content hashes, acceptance metadata, or committed orchestration events.
2. For new objects, use batch-local `clientRef` values. Java allocates durable IDs after validation.
3. For existing objects, reference exact Java-owned revisions.
4. Treat lifecycle actions as proposals only. Java decides whether to commit transition, retry, replan, approval, block, baseline acceptance, or release actions.
5. Preserve exact upstream artifact and repository revision references used to reach a result.
6. Never use conversation/session history as handoff authority. Persist structured artifacts and handoffs.
7. Requirement scope is business scope. Repository scope belongs to executable engineering work.
8. One implementation WorkItem has one writable primary repository in R2 v1. Reference repositories are read-only unless Java policy explicitly says otherwise.
9. Skills never grant permissions. Permission is owned by Java policy, ExecutionContract, and runtime sandbox.
10. Do not rely on the agent being able to read `.agent/skills` or another Skill directory. Java delivers resolved Skill context.

Read `references/java-authority-model.md` before designing a new artifact or proposal shape.

## Workflow

### 1. Determine the semantic output

Classify the result as one or more of:

- engineering artifact proposal
- artifact revision proposal
- finding/gap/conflict proposal
- review result
- verification evidence/result
- engineering contract
- execution handoff
- lifecycle action proposal

Use `references/object-model.md` for the Java-aligned object model.

### 2. Pin exact inputs

Record exact references used by the result:

- Artifact `RevisionRef`
- accepted baseline ID/hash when supplied by Java
- primary repository ID and exact Git revision
- reference repository IDs and exact Git revisions when actually read
- WorkItem / WorkPackage / ExecutionSnapshot identifiers supplied by Java

Never replace an exact ref with `latest`, a filename, a branch name, or conversational recency.

For multi-repository context, read `references/repository-context.md`.

### 3. Build ArtifactProposal objects

Use the bundled `assets/schemas/artifact-proposal.schema.json`.

For a new artifact, use:

- `clientRef`
- `operation: CREATE`
- `artifactType`
- `displayName`
- semantic `payload`
- proposed artifact links

For an existing artifact revision, additionally provide:

- `targetArtifactId`
- `expectedArtifactVersion`
- `operation: REVISE`

Do not provide authoritative revision numbers or hashes.

### 4. Use ActionProposal for lifecycle requests

Use `assets/schemas/action-proposal.schema.json`.

Allowed proposal categories include:

- `REQUEST_TRANSITION`
- `REQUEST_RETRY`
- `REQUEST_REPLAN`
- `REQUEST_APPROVAL`
- `MARK_BLOCKED`
- `RAISE_CLARIFICATION`

These are requests, not committed Java `CommandEnvelope` objects.

Read `references/handoff-and-proposals.md`.

### 5. Package related output in AgentHandoffBatch

Use `assets/schemas/agent-handoff-batch.schema.json` when multiple related proposals/results must move atomically.

Use batch-local `clientRef` values for cross-reference among newly proposed objects.

For baseline-affecting results, prefer atomic validation semantics.

### 6. Use durable Engineering Contract artifacts for shared boundaries

When multiple repositories can implement in parallel against a frozen shared boundary, propose an `ENGINEERING_CONTRACT` artifact.

Read `references/repository-context.md` for contract semantics and `assets/schemas/engineering-contract-payload.schema.json` for the payload shape.

A contract may describe API, event, data, database, protocol, file-format, CLI, or internal-interface semantics.

### 7. Use Execution Handoff artifacts instead of session messages

When one execution result must be consumed by later tasks/agents, propose an `EXECUTION_HANDOFF` artifact.

Read `assets/schemas/execution-handoff-payload.schema.json`.

A handoff records exact work/repository/artifact/evidence refs. Do not include hidden reasoning or raw conversation history.

### 8. Preserve role ownership

Read `references/agent-contracts.md`.

Agents may propose only artifacts appropriate to their engineering role. Any agent may raise a gap/finding/conflict when an upstream problem blocks safe work.

### 9. Preserve traceability

Use exact artifact-to-artifact links. Read `references/traceability.md`.

Java validates and persists links. The Agent only proposes them.

### 10. Preserve failed evidence

Never rewrite failed review or verification evidence to make it pass.

A retest/re-review creates new evidence/result data according to Java policy.

## Canonical output rule

When Java or another machine consumer will use the result, emit structured JSON conforming to the relevant proposal/payload schema.

Do not wrap Java-owned persistence metadata around the payload.

If required information is missing, emit a structured gap/finding or `RAISE_CLARIFICATION` proposal. Do not fabricate data.

## Java integration rule

The Java Control Plane performs:

```text
validate proposal
allocate durable IDs
canonicalize JSON
compute content hash
append ArtifactRevision
validate/create ArtifactLink
validate baseline policy
validate lifecycle action
create real CommandEnvelope when authorized
commit state/event
```

The Agent does not perform these authoritative operations.

Read `references/java-authority-model.md` and `references/persistence-alignment.md` for the exact boundary.
