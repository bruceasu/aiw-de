# Requirements Change Management

Use this workflow when a requirement baseline already exists.

## 1. Preserve the baseline

Treat existing requirement IDs as stable. Do not renumber unaffected requirements merely for presentation.

## 2. Classify each incoming statement

- `ADD` — introduces a new obligation, rule, scenario, interface, or constraint.
- `MODIFY` — changes the semantics of an existing item.
- `DELETE` — explicitly removes a prior obligation.
- `CLARIFY` — reduces ambiguity without changing intended behavior.
- `CONFLICT` — cannot coexist with the current baseline without a decision.
- `NO-CHANGE` — restates existing behavior.

Do not treat omission in a new conversation as deletion.

## 3. Perform impact analysis

For changed items inspect affected:

- objectives
- scope
- actors
- system boundaries
- logical capabilities
- scenarios
- lifecycle/business rules
- functional requirements
- data ownership/retention
- interfaces
- non-functional requirements
- acceptance criteria
- assumptions/open issues

## 4. Produce a delta record

For each material change capture:

- change ID
- classification
- old statement
- new statement
- source/evidence state
- rationale if available
- affected requirement IDs
- compatibility/migration concern if visible at requirement level
- unresolved decisions

## 5. Update the SRS

Merge approved/confirmed changes while preserving traceability.

For inferred changes, keep the baseline semantics and list the proposed delta unless autonomous assumptions are explicitly allowed.

## 6. Architecture impact flag

Flag changes that likely require architecture reassessment, especially changes to:

- trust boundary
- authoritative data owner
- external interface obligations
- consistency/transaction semantics
- scale or latency class
- availability/recovery requirements
- compliance/security constraints
- major capability scope

Do not redesign the architecture inside the requirements change workflow.
