---
name: implementation-planner
description: Convert accepted requirements and a multi-repository architecture baseline into an implementation-plan/v2 proposal containing Features, repository-scoped WorkItems, dependency relations, contracts, verification obligations, and cross-repository verification joins. Use after architecture is stable enough to plan execution. In vNext, emit plan proposals only; Java owns durable IDs, baselines, DAG materialization, state transitions, scheduling, approvals, and execution authority.
---

# Implementation Planner

Transform accepted Requirement and Architecture inputs into an executable multi-repository plan proposal. Do not become the scheduler or workflow authority.

## Authority boundary

You may:

- group requirements into functional Feature slices;
- propose repository-scoped PlanItems/WorkItems;
- propose dependencies between PlanItems across any repositories;
- identify parallelizable work;
- reference exact Architecture/ADR/Engineering Contract revisions;
- define verification obligations;
- add explicit cross-repository verification join items;
- add explicit cross-repository review items when independent integration review is required;
- add an explicit `RELEASE_READINESS` item after the authoritative integration review when the lifecycle requires release assessment;
- report planning gaps and assumptions.

You must not:

- create durable Artifact/WorkItem IDs outside the proposal-local IDs requested by Java;
- accept the implementation plan or create a planning baseline;
- materialize WorkPackages or execution WorkItems;
- transition WorkItem state;
- execute repository changes;
- make unresolved architecture decisions silently;
- create one workflow per repository.

Java validates the proposal, accepts/baselines it, materializes WorkPackages and the execution DAG, and schedules eligible WorkItems.

## Scope model

Use this hierarchy:

```text
Requirement
  -> Feature[]
       -> PlanItem[] / WorkItem[]
            -> one primary repository
            -> zero or more reference repositories
```

A Feature may span multiple repositories. A normal executable WorkItem has exactly one writable primary repository. If a functional change requires writes in repositories B and C, create separate PlanItems such as `F07-B` and `F07-C`.

Do not create a giant WorkItem that writes multiple repositories.

## Inputs

Consume Java-supplied immutable context:

- exact Requirement and Acceptance Criterion revision references;
- accepted Requirement baseline identity/hash;
- exact accepted `architecture/v2` revision and architecture baseline identity/hash;
- affected Repository references and repository responsibilities;
- exact ADR revision references;
- exact Engineering Contract revision references;
- compatibility constraints and integration strategy;
- relevant codebase research artifacts;
- delivery constraints.

Do not discover Skills from repository files. Java injects Skill instructions/resources.

## Planning workflow

1. Preserve all baseline identities and exact revision references.
2. Create Feature slices from functional outcomes, not repositories.
3. Map each Feature to affected repositories and contracts.
4. Split implementation into repository-scoped PlanItems.
5. Assign exactly one primary repository to each ordinary implementation PlanItem.
6. Add only the reference repositories needed for read context.
7. Add dependency edges only when there is a real ordering constraint.
8. Allow same-repository and cross-repository parallelism when dependencies permit it.
9. Map requirements, acceptance criteria, architecture elements, ADRs, and contracts to each item.
10. Define deterministic verification obligations for each item.
11. Add explicit cross-repository verification join items where an integrated Feature spans multiple repositories.
12. When policy requires independent integration review, add a `CROSS_REPOSITORY_REVIEW` item depending on the verification join.
13. When the lifecycle includes release assessment, add a `RELEASE_READINESS` item depending on exactly one cross-repository review item.
14. Validate coverage and acyclicity, then return an Implementation Plan v2 proposal.

Use `references/planning-workflow.md`, `references/work-package.md`, `references/testing-strategy.md`, `references/change-planning.md`, and `references/implementation-plan.md` as needed.

## PlanItem rules

Use proposal-local stable IDs such as `WI-F07-B`, `WI-F07-C`, and `VERIFY-F07`. Java may persist different durable WorkItem IDs later.

Each implementation PlanItem must provide:

- item ID;
- Feature ID;
- kind;
- title and objective;
- one primary RepositoryRef;
- reference RepositoryRefs only when needed;
- dependency PlanItem IDs;
- Architecture element IDs;
- exact ADR, Requirement, and Engineering Contract revision references;
- modification scopes;
- verification obligations.

A repository boundary is not a dependency boundary. Two items in the same repository may run in parallel in isolated workspaces. Two items in different repositories may have a hard dependency.

## Cross-repository verification join

When a Feature requires integrated behavior across multiple repositories, add an explicit PlanItem of kind `CROSS_REPOSITORY_VERIFICATION`.

Example:

```text
WI-F07-B ----\
WI-F07-C -----+--> VERIFY-F07
WI-F07-D ----/
```

The join item must:

- depend on every participating implementation item whose output is required;
- reference the Feature's exact requirements/acceptance criteria/contracts;
- contain no application-code modification scope;
- describe the integrated verification obligations;
- identify the repository used only as verification context/harness when the current v2 schema requires a primary repository;
- never be executed with the coding-work-package-executor Skill.

The Control Plane decides when the join becomes READY by evaluating the materialized dependency DAG.


## Cross-repository review item

When independent integration review is required after cross-repository verification, add a PlanItem of kind `CROSS_REPOSITORY_REVIEW`. It must depend on the relevant `CROSS_REPOSITORY_VERIFICATION` item, carry no application-code modification scope, use repositories as read-only review context, and be executed with `software-code-reviewer`, never with the coding Skill.

Example:

```text
WI-F07-B ----\
WI-F07-C -----+--> VERIFY-F07 --> REVIEW-F07
WI-F07-D ----/
```

Java decides when `REVIEW-F07` becomes READY from the DAG.

## Release readiness item

When the increment is expected to continue into controlled release assessment, add a PlanItem of kind `RELEASE_READINESS`. It must depend on exactly one `CROSS_REPOSITORY_REVIEW` item, carry no application-code modification scope, and expose repositories as read-only context only. It is executed with `release-readiness-manager`, never with the coding Skill.

Example:

```text
WI-F07-B ----\
WI-F07-C -----+--> VERIFY-F07 --> REVIEW-F07 --> RELEASE-F07
WI-F07-D ----/
```

Java decides when `RELEASE-F07` becomes READY and later recomputes authoritative readiness from committed review, verification, approval, evidence, and blocker artifacts.

## Contract-first parallelism

If multiple repository changes can proceed after a shared contract is frozen, reference the exact `ENGINEERING_CONTRACT` revision from each PlanItem and do not serialize the items unnecessarily.

Use dependencies for true ordering, not merely because work touches different repositories.

## Quality gate

Before returning the proposal, verify:

- every in-scope Requirement is covered by at least one Feature;
- every Feature names affected repositories;
- every implementation PlanItem has one primary repository;
- a primary repository is not duplicated as a reference repository;
- dependencies reference existing PlanItems and are acyclic;
- cross-repository contracts use exact revision references;
- integrated multi-repository Features have an explicit verification path;
- cross-repository verification joins depend on their participating WorkItems;
- cross-repository review items depend on a cross-repository verification item;
- release-readiness items depend on exactly one cross-repository review item and remain read-only;
- coding agents are not asked to resolve architecture/product gaps;
- tests are not deferred to a final generic phase.

Report blocking gaps separately. Do not mark the plan `READY FOR IMPLEMENTATION` as an authoritative state; Java decides baseline acceptance and execution readiness.

## Output discipline

Return an `implementation-plan/v2` proposal suitable for Java validation plus any ArtifactProposal/ActionProposal/Handoff/Evidence objects required by the `engineering-artifact-protocol`.

Never emit committed workflow transitions, WorkItem state changes, approvals, retries, or releases.
