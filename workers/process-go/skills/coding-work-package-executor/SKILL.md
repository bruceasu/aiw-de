---
name: coding-work-package-executor
description: Execute one approved repository-scoped software Work Package under frozen requirement, architecture, contract, repository, permission, and verification constraints. Use when an Agent is asked to implement or fix code for one WorkItem whose primary writable repository and any read-only reference repositories have already been selected by the Java Control Plane. Produce implementation evidence and structured blocker/handoff proposals. Never discover Skills, broaden repository access, change lifecycle state, accept baselines, or redefine upstream requirements/architecture.
---

# Coding Work Package Executor

## Mission

Implement exactly one approved Work Package as a small, reviewable engineering change.

Treat the execution context delivered by the Java Control Plane as authoritative. The current execution has exactly one writable primary repository. Reference repositories, when supplied, are read-only context unless a later WorkItem explicitly targets them.

Do not act as requirements authority, architecture authority, workflow state machine, approval authority, or release authority.

## Authority boundary

The Java Control Plane owns and freezes:

- WorkItem and Work Package identity;
- primary repository and exact base revision;
- reference repositories and their exact revisions;
- requirement, architecture, acceptance, contract, and handoff Artifact revisions;
- resolved Skill snapshots;
- permissions, budget, modification boundary, and runtime policy;
- lifecycle transition, retry, approval, baseline acceptance, and release decisions.

Consume these facts. Do not replace or reinterpret them.

Never discover or load Skills from `.agent/skills`, `.claude/skills`, repository-local Skill folders, or other filesystem locations. Skill instructions and authorized Skill resources are delivered by the Java runtime.

## Repository scope

One execution equals:

```text
one WorkItem
one writable primary repository
zero or more read-only reference repositories
```

Only modify the primary repository. Reading a reference repository does not grant write authority to it.

If the requested implementation requires a code change in another repository, emit a `PLAN_GAP` or structured cross-repository follow-up proposal identifying the affected repository and required dependency. Do not edit multiple repositories in one WorkItem.

## Inputs

Use only inputs present in the frozen execution context, including when available:

1. WorkItem and exact Work Package revision.
2. Feature ID and requirement/acceptance revisions.
3. Architecture/ADR revisions.
4. Engineering Contract revisions.
5. Primary repository identity and exact Git revision.
6. Read-only reference repository identities and exact revisions.
7. Dependency handoff/result Artifacts.
8. Modification boundary and permission policy.
9. Verification plan and execution budget.

Do not substitute `latest`, a branch head, an unpinned file, or an unrelated conversation message for a frozen input.

## Execution workflow

1. Establish the frozen authority set.
   - Identify the WorkItem, Work Package, Feature, repository scope, contracts, and acceptance criteria.
   - Distinguish writable primary repository from read-only references.

2. Restate the implementation boundary internally.
   - Objective and observable behavior.
   - Allowed change surface.
   - Explicit out-of-scope behavior.
   - Compatibility constraints.
   - Required verification.

3. Inspect before editing.
   - Read relevant code and tests in the primary repository.
   - Read only necessary context from reference repositories and frozen Artifact/Skill resources.
   - Prefer existing extension points over parallel abstractions.

4. Classify blockers before coding.
   - `REQUIREMENT_GAP`
   - `DESIGN_GAP`
   - `PLAN_GAP`
   - `BASELINE_CONFLICT`
   - `DEPENDENCY_BLOCKER`
   - `PERMISSION_BLOCKER`
   - `ENVIRONMENT_ISSUE`

   Do not invent an upstream decision to avoid a blocker.

5. Implement the smallest coherent change in the primary repository.
   - Stay inside the modification boundary.
   - Preserve behavior not targeted by the WorkItem.
   - Avoid opportunistic refactoring.
   - Do not silently introduce architecture or contract changes.

6. Verify continuously.
   - Run the narrowest useful compile/test check first.
   - Broaden verification according to the frozen verification plan and risk.
   - Never claim a command or test passed unless it actually ran successfully.

7. Self-review.
   - Compare the final diff with the Work Package and acceptance criteria.
   - Check accidental edits, compatibility, migrations, concurrency/error paths, secrets, debug code, generated files, and disabled tests.

8. Produce structured implementation evidence and handoff.

## Cross-repository contract rules

If implementation depends on an `ENGINEERING_CONTRACT`, implement against the exact frozen contract revision.

Do not change a shared API/schema/protocol contract merely because changing it would simplify the local implementation. Emit a structured proposal instead. Contract revision acceptance belongs to the Control Plane and the appropriate architecture/planning workflow.

## Skill resources and scripts

Skill references may be provided progressively by the Java Skill Runtime. Request/read only resources relevant to the current WorkItem.

A script bundled with a Skill is not executable merely because it exists. Execute it only through an explicitly granted controlled capability or runtime permission.

## Completion outcome

Return exactly one engineering outcome in the result payload:

- `IMPLEMENTED_AND_VERIFIED`
- `IMPLEMENTED_WITH_LIMITED_VERIFICATION`
- `PARTIALLY_IMPLEMENTED_BLOCKED`
- `BLOCKED_BEFORE_IMPLEMENTATION`
- `NO_CHANGE_REQUIRED`

These are execution results, not lifecycle state transitions. Do not mark the WorkItem accepted, merged, released, or complete in the Control Plane.

## Required evidence / handoff

Include:

- WorkItem and Work Package revision;
- primary repository and base revision;
- implementation outcome;
- changed files/components;
- externally observable behavior implemented;
- requirement/architecture/contract traceability;
- commands/tests actually executed and their results;
- acceptance-criteria coverage;
- deviations, risks, blockers, or unverified areas;
- any required follow-up WorkItem or upstream proposal;
- repository state/diff reference when available.

Use `engineering-artifact-protocol` structures for Artifact proposals, execution handoff, evidence, and action proposals. Emit proposals only. Java creates durable IDs, accepts Artifacts/Baselines, and commits transitions.

## References

- Read `references/execution-report.md` for the completion/evidence format.
- Read `references/scope-control.md` when scope expands, an adjacent defect appears, or another repository seems to require a change.
- Read `references/testing-and-evidence.md` for verification standards.
- Read `references/repository-change-guidelines.md` for repository-level change discipline.
