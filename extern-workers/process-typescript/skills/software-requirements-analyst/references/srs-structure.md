# Canonical Software Requirements Specification (SRS)

Use this structure for formal architecture handoff. Adapt depth to project size; omit irrelevant subsections rather than filling them with generic text.

## 0. Document Control

- Title
- Requirement baseline/version
- Status: Draft | Baseline | Superseded
- Source material
- Assumption policy
- Change summary when applicable

## 1. Introduction

### 1.1 Purpose
State why this SRS exists and what decision/design work it supports.

### 1.2 Business objectives
Use stable IDs such as `OBJ-001`.

For each objective:
- State
- Description
- Success signal / business outcome

### 1.3 Scope
Describe what capability is included.

### 1.4 Out of scope
Make exclusions explicit where useful.

### 1.5 Definitions and terminology
Define domain terms whose meaning affects requirements.

## 2. Stakeholders and Actors

Identify:
- business owners
- users
- operators/support roles
- administrators
- machine actors
- external organizations/systems

Do not invent organization ownership when unknown.

## 3. System Context and Boundaries

### 3.1 System under consideration
Define the provisional or confirmed system boundary.

### 3.2 External actors and systems
For each external party, state:
- role
- exchanged information/behavior
- ownership if known
- trust/authorization relationship if relevant

### 3.3 Responsibility boundaries
State which side is responsible for important decisions, validation, persistence, authoritative data, retries, reconciliation, and user communication when known.

### 3.4 Context flows
Represent major flows textually or with a simple context model. Do not prescribe protocols unless mandated.

## 4. Logical Capability Model

Group requirements into stable business/responsibility areas, e.g.:

### CAP-001 — [Capability name]
- State
- Purpose
- Responsibilities
- Related actors
- Related objectives
- Explicit exclusions

A capability is not automatically a service, process, repository, package, or database.

## 5. Operational and User Scenarios

### SCN-001 — [Scenario]
- State
- Actors
- Preconditions
- Trigger
- Inputs
- Main flow
- Postconditions/result
- Alternatives
- Failure paths
- Related capabilities/objectives

## 6. Domain and Lifecycle Rules

Describe important entities, states, transitions, invariants, and business rules.

### BR-001 — [Rule]
- State
- Rule
- Rationale/source
- Related scenarios/requirements

When an entity has a lifecycle, include:
- states
- permitted transitions
- prohibited transitions
- transition triggers
- terminal states if any
- concurrency/ordering implications when relevant

## 7. Functional Requirements

Group by capability, not technical module.

### FR-001 — [Short title]
- State: CONFIRMED | INFERRED | ASSUMED | UNKNOWN | CONFLICT
- Normative requirement
- Rationale
- Source/derivation
- Related: OBJ/CAP/SCN/BR/DR/INT IDs
- Priority, only if source defines or prioritization is required

A single requirement should normally express one principal obligation.

## 8. Data Requirements

### DR-001 — [Short title]
Capture as applicable:
- required data
- authoritative source
- ownership
- validation
- uniqueness/identity
- history/versioning
- retention/deletion
- confidentiality/classification
- consistency/reconciliation
- import/export obligations

Avoid physical schema design unless mandated.

## 9. External Interface and Interoperability Requirements

### INT-001 — [Boundary interaction]
- State
- Initiator/consumer
- Required information/behavior
- Trigger/frequency
- success semantics
- failure semantics
- ordering/idempotency/version compatibility where required
- mandated protocol/format only if confirmed

## 10. Non-Functional Requirements

Include only relevant categories.

### 10.1 Security and Privacy (`SEC`)
Authentication, authorization, least privilege, sensitive-data handling, separation of duties, privacy, secrets, abuse controls.

### 10.2 Audit and Observability (`OBS`)
Audit trail, traceability, logs, metrics, alerts, accountability, operational evidence.

### 10.3 Performance and Capacity (`PERF`)
Latency, throughput, data volume, concurrency, batch windows, resource constraints, growth assumptions.

### 10.4 Reliability and Recovery (`REL`)
Availability, durability, retries, idempotency, recovery, backup/restore, reconciliation, degraded mode, continuity.

### 10.5 Usability and Accessibility (`UX`)
Human interaction constraints that are truly required.

### 10.6 Operability and Supportability (`OPS`)
Deployment-independent operational needs: diagnostics, maintenance windows, configuration, support, safe manual intervention.

### 10.7 Compliance (`CMP`)
Applicable legal/regulatory/internal-policy obligations when known.

## 11. Constraints and Dependencies

### CON-001 — [Constraint]
- State
- Description
- Source
- Consequence for downstream design

Include mandated runtimes, environments, compatibility, organizational limits, migration constraints, contractual dependencies, etc.

## 12. Error, Edge, and Exceptional Behavior

Capture cases not adequately represented by main scenarios:
- invalid input
- duplicates
- partial failure
- timeout
- dependency unavailable
- retry/replay
- cancellation
- concurrent update
- stale data
- authorization change
- large/bulk request
- recovery after interruption

State expected behavior or mark unresolved.

## 13. Acceptance and Verification Criteria

### AC-001 — [Related requirement]
- Given ...
- When ...
- Then ...
- Verification: Test | Inspection | Analysis | Demonstration

Acceptance criteria must trace to requirements and must not add hidden requirements.

## 14. Requirements Traceability

Provide a compact mapping such as:

`OBJ -> CAP -> SCN -> FR/BR/DR/INT/NFR -> AC`

Highlight orphan requirements and uncovered objectives.

## 15. Assumptions, Conflicts, and Open Issues

### A-001
- State: ASSUMED | CONFLICT
- Statement
- Evidence
- Affected IDs
- Revision trigger

### Q-001
- Impact: BLOCKING | IMPORTANT | MINOR
- Question/decision
- Affected IDs/areas
- Why it matters
- Temporary assumption, if safe

## 16. Architecture Handoff Guardrails

State explicitly:

- Logical capabilities are requirement partitions, not prescribed software modules.
- Technical decomposition remains open unless recorded as a confirmed constraint.
- Architecture must preserve required responsibility, trust, data-ownership, and interface boundaries.
- `INFERRED` and `ASSUMED` items remain revisable.
- `BLOCKING` issues prevent architecture finalization for affected areas unless an authorized stakeholder accepts a documented temporary assumption.
