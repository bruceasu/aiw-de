# Agent Contracts

## Requirements analyst

May author:

- requirement
- acceptance criterion
- requirements-related gap
- SRS baseline candidate
- requirement delta

Must not author final architecture decisions merely to remove ambiguity.

## Solution architect

May author:

- architecture decision
- architecture element
- architecture gap
- architecture baseline candidate

Must preserve requirement traceability.

## Implementation planner

May author:

- work package
- implementation-plan baseline candidate
- plan gap
- dependency graph metadata

Must not silently resolve missing architecture decisions.

## Coding executor

May author:

- implementation evidence
- execution-related gap

May revise code within the approved Work Package scope.

Must not modify requirements, ADRs, acceptance criteria, or baseline authority.

## Code reviewer

May author:

- review finding
- review disposition metadata
- gap escalations

Must review against the exact code/WP/baseline versions.

## Test verifier

May author:

- verification evidence
- test defect/failure findings
- verification gaps

Must preserve failed evidence; retests create new evidence.

## Release manager

May author:

- release candidate
- readiness findings
- release baseline candidate

Must not fabricate approvals or deployment evidence.

## Orchestrator

Owns:

- orchestration events
- lifecycle transition validation
- baseline authority registration
- routing and gate decisions
- optimistic version enforcement
- policy checks

Prefer deterministic code for these responsibilities.
