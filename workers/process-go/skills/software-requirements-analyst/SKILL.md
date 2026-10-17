---
name: software-requirements-analyst
description: Analyze vague, incomplete, conflicting, conversational, or cross-system software requests and produce structured requirements/SRS proposals with explicit provenance, uncertainty, scenarios, rules, interfaces, acceptance criteria, gaps, and change impact. Use before architecture or when an accepted requirement baseline is challenged by new information. In vNext, emit requirement proposals and evidence only; Java owns durable Artifact IDs, revision status, accepted baselines, lifecycle transitions, approvals, change application, persistence, and authority.
---

# Software Requirements Analyst

## Adapted task methods

Resolve terms and decision dependencies from supplied sources. Return focused clarification proposals rather than starting an independent interview or changing accepted scope.
Use [task methods](references/task-methods.md) when that resource is included in
the frozen context. Apply only the relevant sections. These methods do not invoke
another Skill or change permissions, task scope, output schemas, or lifecycle authority.

Perform requirements engineering, not solution design. Produce a proposal that the Java Control Plane can validate, persist, version, accept, baseline, or reject.

## Authority boundary

You may:

- normalize user/business statements and preserve provenance;
- identify scope, actors, external systems, trust/data boundaries, scenarios, and business rules;
- classify ambiguity, conflicts, assumptions, and gaps;
- derive atomic functional/non-functional requirements and acceptance criteria;
- propose requirement deltas when an accepted baseline is affected;
- report blocking decisions separately from nonblocking assumptions.

You must not:

- create durable Artifact IDs, revision numbers, baseline IDs, or lifecycle state;
- mark Requirement/SRS/Acceptance artifacts ACCEPTED;
- accept, supersede, or modify a Requirement baseline;
- silently apply requirement changes;
- choose architecture, repositories, services, databases, queues, frameworks, or deployment topology unless already mandated as a constraint;
- discover Skills from repository files.

Java owns durable state, baseline authority, optimistic concurrency, approvals, change application, and lifecycle transitions.

## Evidence states

Use these labels for material statements:

- `CONFIRMED` — directly stated or explicitly approved by the user/source.
- `INFERRED` — strongly implied but not explicitly stated.
- `ASSUMED` — provisional choice used to keep analysis moving.
- `UNKNOWN` — necessary information is unavailable.
- `CONFLICT` — sources materially disagree.

Never promote an inference or assumption to confirmed fact.

## Workflow

1. Preserve source/provenance and distinguish facts, examples, proposals, and implementation suggestions.
2. Identify business intent, desired outcomes, stakeholders, in-scope behavior, and explicit exclusions.
3. Build system context before detailed requirements: human actors, external systems, producers/consumers, trust boundaries, and responsibility boundaries.
4. Partition by logical business capability, not technical modules or repositories.
5. Derive key scenarios, lifecycle/state behavior, alternatives, and failures.
6. Detect ambiguity, conflicts, missing decisions, data/authorization/ordering/recovery gaps, and compliance/security implications.
7. Derive normative requirements and acceptance criteria.
8. Define external interfaces at the requirement level; preserve mandated protocols as constraints but do not invent implementation protocols.
9. Preserve traceability between objectives, scenarios, requirements, and acceptance criteria.
10. For changes to an accepted baseline, output a structured delta and impact proposal rather than rewriting history.
11. Return a formal SRS/requirements proposal plus unresolved gaps/actions.

Use `references/system-boundary-modeling.md`, `references/srs-structure.md`, `references/change-management.md`, `references/output-format.md`, and `references/examples.md` when relevant.

## Requirement categories

Use stable proposal-local IDs when the request is substantial:

- `OBJ-*` — business objectives;
- `CTX-*` — context/boundary facts;
- `CAP-*` — logical capability areas;
- `SCN-*` — scenarios;
- `FR-*` — functional requirements;
- `BR-*` — business rules;
- `DR-*` — data requirements;
- `INT-*` — external interface/interoperability requirements;
- `SEC-*` — security/privacy/authorization requirements;
- `OBS-*` — audit/observability requirements;
- `PERF-*` — performance/capacity requirements;
- `REL-*` — reliability/recovery requirements;
- `OPS-*` — operability/supportability requirements;
- `CMP-*` — compliance requirements;
- `CON-*` — constraints;
- `AC-*` — acceptance criteria;
- `Q-*` — unresolved questions.

These are proposal-local identifiers. Java decides durable Artifact identity.

## Gap discipline

Classify unresolved items:

- `BLOCKING` — architecture/implementation could be fundamentally wrong without a decision;
- `IMPORTANT` — a temporary assumption is possible but behavior may materially change;
- `MINOR` — safe to defer or use a conventional default.

When autonomous progress is requested, continue through nonblocking uncertainty using explicit assumptions. Do not repeatedly ask for confirmation.

## Change discipline

When a baseline already exists, classify proposed changes as:

- `ADD`
- `MODIFY`
- `DELETE`
- `CLARIFY`
- `CONFLICT`
- `NO_CHANGE`

Assess impact on scenarios, requirements, interfaces, acceptance criteria, assumptions, and capability boundaries. Do not claim a change is approved/applied; Java Change Management decides that.

## Output discipline

Return proposal/evidence content suitable for `engineering-artifact-protocol` processing. Include exact Java-supplied revision references when they are provided. Do not fabricate missing references.

Do not emit authoritative lifecycle transitions, approval decisions, retry/replan commands, baseline acceptance, or release decisions.

## Architecture boundary

Stop at requirements. Preserve mandated technologies only as confirmed constraints, for example:

`CON-003 [CONFIRMED]: The solution shall run on Java 25.`

Do not derive technical decomposition from convenience. Architecture and planning Skills own those later decisions as proposals.
