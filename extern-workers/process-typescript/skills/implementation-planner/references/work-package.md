# Coding-Agent Work Package

Use this structure for a work package intended for an autonomous or semi-autonomous coding agent.

```text
WP-###: <bounded outcome>

Proposal state: DRAFT | BLOCKED-BY-GAP
Increment: INC-###
Priority: <delivery priority, not business priority unless specified>

Objective
- <observable implementation result>

Traceability
- Requirements: FR-..., NFR-...
- Acceptance criteria: AC-...
- Architecture: MOD-..., APP-..., INT-..., DATA-...
- ADRs: ADR-...

Prerequisites
- <dependency PlanItem IDs, exact stable contract revisions, environment, migration, decision>

Scope
- <what this package must implement>

Out of scope
- <adjacent work explicitly excluded>

Modification boundary
- Primary repository: <exact RepositoryRef; exactly one writable repository>
- Reference repositories: <read-only context only when needed>
- Project/module: <known boundary>
- Allowed components: <components>
- Avoid changing: <areas not owned by this work>

Contracts
- Inputs:
- Outputs:
- API/event/schema rules:
- Compatibility rules:

Implementation constraints
- <decisions already made upstream>

Failure and edge behavior
- <required error, retry, idempotency, concurrency, security behavior>

Verification
- Automated tests:
- Contract/integration tests:
- Commands/checks:
- Acceptance criteria exercised:

Definition of Done
- [ ] behavior implemented
- [ ] required tests pass
- [ ] compatibility/migration handled
- [ ] observability updated where required
- [ ] documentation/contracts updated
- [ ] no unresolved blocker hidden in code

Risks / Assumptions / Blockers
- ...
```

## Agent Handoff Rule

The package should contain enough context that a coding agent does not need to reinterpret the original business conversation.

It should not duplicate the whole SRS or architecture document. Reference stable IDs and include only the local context needed to implement correctly.

## Completion Evidence

A coding agent should return evidence, not only a statement that work is finished:

- changed modules/files,
- tests executed and results,
- migrations/contracts changed,
- deviations from plan,
- remaining risks,
- requirement/AC coverage.

## Multi-Repository Rule

One normal coding Work Package maps to one writable primary repository. Split a Feature into multiple Work Packages when multiple repositories require changes. Express ordering with dependency IDs, not repository ordering. Use a `CROSS_REPOSITORY_VERIFICATION` PlanItem for integrated verification joins; Java materializes runtime WorkItem states.
