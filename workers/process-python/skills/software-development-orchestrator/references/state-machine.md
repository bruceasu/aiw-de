# Lifecycle State Machine

## Canonical States

| State | Purpose | Typical Owner |
|---|---|---|
| INTAKE | Capture initial intent and project context | Orchestrator |
| REQUIREMENTS | Elicit/analyze software requirements | Requirements Analyst |
| REQUIREMENTS_REVIEW | Check SRS completeness, conflicts, testability | Reviewer / stakeholders |
| ARCHITECTURE | Produce architecture baseline and ADRs | Solution Architect |
| ARCHITECTURE_REVIEW | Validate architecture against drivers and constraints | Architecture reviewer |
| IMPLEMENTATION_PLANNING | Build increments, WPs, dependency graph, test obligations | Implementation Planner |
| READY_FOR_IMPLEMENTATION | Authorized WPs exist and gates pass | Orchestrator |
| IMPLEMENTING | Coding is active | Coding Agent |
| CODE_REVIEW | Implementation is independently reviewed | Code Reviewer |
| VERIFYING | Required automated/manual verification is executed | Test Agent |
| ACCEPTANCE | Requirement/acceptance evidence is evaluated | Acceptance owner |
| RELEASE_READY | Release, rollback, migration and operational evidence are complete | Release Agent |
| RELEASED | Approved release has been performed and recorded | Release owner |
| CHANGE_ASSESSMENT | Determine impact of a new change on baselines | Orchestrator + specialists |
| BLOCKED | Required dependency/decision/approval unavailable | Owning party |
| SUSPENDED | Work intentionally paused | Human/project authority |

## Forward Transitions

- INTAKE -> REQUIREMENTS
- REQUIREMENTS -> REQUIREMENTS_REVIEW
- REQUIREMENTS_REVIEW -> ARCHITECTURE
- ARCHITECTURE -> ARCHITECTURE_REVIEW
- ARCHITECTURE_REVIEW -> IMPLEMENTATION_PLANNING
- IMPLEMENTATION_PLANNING -> READY_FOR_IMPLEMENTATION
- READY_FOR_IMPLEMENTATION -> IMPLEMENTING
- IMPLEMENTING -> CODE_REVIEW
- CODE_REVIEW -> VERIFYING
- VERIFYING -> ACCEPTANCE
- ACCEPTANCE -> RELEASE_READY
- RELEASE_READY -> RELEASED

Transitions may be scoped per increment or WP. Do not wait for unrelated WPs if an increment is independently releasable.

## Backward / Exception Transitions

- Any accepted baseline affected by new scope -> CHANGE_ASSESSMENT.
- REQUIREMENTS_REVIEW failure -> REQUIREMENTS.
- ARCHITECTURE or later finding `REQUIREMENT_GAP` -> REQUIREMENTS.
- ARCHITECTURE_REVIEW failure -> ARCHITECTURE.
- Planning finding `DESIGN_GAP` -> ARCHITECTURE.
- CODE_REVIEW finding local implementation defect -> IMPLEMENTING.
- CODE_REVIEW finding `DESIGN_GAP` -> ARCHITECTURE, then re-plan affected WPs.
- VERIFYING finding `CODE_DEFECT` -> IMPLEMENTING.
- VERIFYING finding `TEST_DEFECT` -> VERIFYING after test correction.
- VERIFYING finding `REQUIREMENT_GAP` -> REQUIREMENTS.
- ACCEPTANCE failure -> classify root cause before routing.
- Any state with unavailable mandatory dependency/approval -> BLOCKED.
- BLOCKED -> prior actionable state only when blocker evidence is resolved.

## Transition Record

Record:

- transition_id
- project/increment/WP scope
- from_state
- to_state
- timestamp/version context
- triggering evidence
- gate result
- unresolved conditions
- approval reference when applicable

