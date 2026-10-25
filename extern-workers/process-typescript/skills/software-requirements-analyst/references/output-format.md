# Canonical Requirements Handoff Format

Use this format for formal output to another AI or engineering stage. Omit empty sections rather than filling them with boilerplate.

## 1. Requirement context

- **Problem:**
- **Desired outcome:**
- **Primary stakeholders:**
- **Scope:**
- **Out of scope:**

## 2. Source confidence

Summarize which parts are `CONFIRMED`, `INFERRED`, `ASSUMED`, `UNKNOWN`, or `CONFLICT`.

## 3. Goals

### GOAL-001 — [short name]
- State: CONFIRMED | INFERRED | ASSUMED
- Description:
- Success signal:

## 4. Actors

### ACTOR-001 — [actor]
- Role:
- Permissions/responsibilities, if known:

## 5. Scenarios

### SCN-001 — [scenario]
- State:
- Actor:
- Preconditions:
- Trigger:
- Main flow:
- Result:
- Alternatives/failures:

## 6. Functional requirements

### FR-001 — [short title]
- State:
- Requirement:
- Rationale:
- Source/derivation: GOAL/SCN/user statement
- Related: IDs

## 7. Business and data rules

### BR-001 / DR-001 — [short title]
- State:
- Rule:
- Notes:

## 8. Non-functional requirements

Use specific categories as needed: SEC, OBS, PERF, REL, INT, UX, OPS.

### SEC-001 — [short title]
- State:
- Requirement:
- Measurement or unresolved target:

## 9. Constraints and dependencies

### CON-001 — [constraint]
- State:
- Description:
- Consequence:

## 10. Edge and failure cases

### EDGE-001 — [case]
- Expected behavior:
- State:

## 11. Acceptance criteria

### AC-001 — [related requirement ID]
Given ...
When ...
Then ...

## 12. Open questions

### Q-001 — [question]
- Impact: BLOCKING | IMPORTANT | MINOR
- Affects: IDs/area
- Why it matters:
- Safe temporary assumption, if any:

## 13. Assumption and conflict log

### A-001
- State: ASSUMED | CONFLICT
- Statement:
- Evidence:
- Revision trigger:

## 14. Downstream handoff guardrails

- Architecture choices not explicitly stated above remain undecided.
- `INFERRED` and `ASSUMED` items are revisable and must not be treated as user approval.
- `BLOCKING` questions must be resolved before implementing affected behavior unless an authorized actor explicitly accepts the listed temporary assumption.
