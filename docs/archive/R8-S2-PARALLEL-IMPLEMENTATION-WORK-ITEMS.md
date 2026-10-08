# R8-S2 — Parallel Implementation Work Items

Status: implementation candidate built on the accepted R8-S1 baseline.

## Goal

R8-S2 turns one authoritative Implementation Plan into multiple independently executable `IMPLEMENTATION` tasks so idle Workers can implement different work items concurrently in isolated Git worktrees.

The Control Plane still does not assign Workers or invoke agents directly. It only materializes durable Tasks and Dependencies. Workers continue to pull READY tasks through Worker Protocol v1.

## Safe parallelism boundary

R8-S2 intentionally supports only work items that can be implemented independently from the **same immutable base revision**.

The planning agent must not split work merely to increase concurrency. If item B requires item A's intermediate commit, or two items are expected to edit the same tightly-coupled code region, the planner must emit one work item. Cross-branch integration/merge is explicitly deferred to a later R8 stage.

This gives R8-S2 a strong invariant:

```text
all generated implementation items
    share the same repositoryRef
    share the same immutable baseRevision
    have no implementation-to-implementation dependencies
```

## Planner manifest contract

The human-readable planning response ends with a small machine-readable manifest:

```text
AIW_IMPLEMENTATION_WORK_ITEMS_V1
AIW_WORK_ITEM_BEGIN endpoint
AIW_CAPABILITIES java,maven
Implement the health endpoint and application wiring.
AIW_WORK_ITEM_END
AIW_WORK_ITEM_BEGIN tests
AIW_CAPABILITIES java,maven
Add focused regression tests in isolated test files.
AIW_WORK_ITEM_END
AIW_IMPLEMENTATION_WORK_ITEMS_END
```

Rules:

- 1–16 items.
- key: `[a-z0-9][a-z0-9-]{0,31}`.
- keys are unique.
- `AIW_CAPABILITIES` is optional and comma-separated.
- instruction text may span multiple lines.
- prose outside the manifest is preserved in the durable planning summary.
- malformed explicit manifests fail completion materialization instead of silently guessing.

For compatibility with an R8-S1/plain manually-authored plan that contains no manifest marker, the Control Plane safely degrades to one implementation task.

## Materialization

For a run `AR-X` and items `endpoint`, `metrics`, `tests`:

```text
REQUIREMENT_ANALYSIS ───────┐
SOLUTION_ARCHITECTURE ──────┼──> IMPLEMENTATION endpoint
IMPLEMENTATION_PLANNING ────┘
                            ├──> IMPLEMENTATION metrics
                            └──> IMPLEMENTATION tests
```

Every generated implementation task depends on all three authoritative planning stages. Because those predecessors are already completed when the plan handler runs, all independent work items become READY immediately.

Task ids are deterministic and bounded to the existing 128-character task-id limit.

## Capability matching

Each implementation item receives:

```text
run technical capabilities
+ item-specific technical capabilities
+ coding
+ repo:<repositoryRef>
```

Role capabilities supplied by a planner item are ignored; the Control Plane remains authority for task role.

This allows, for example:

```text
item A -> coding + java + maven
item B -> coding + java + maven + sql
```

without allowing the plan to turn an implementation task into a reviewer/verifier task.

## Workspace isolation

R5 semantics are reused unchanged. Each claimed item receives its own attempt-specific branch/worktree:

```text
same base revision
   ├─ item endpoint -> aidev/.../a1 -> worktree A
   ├─ item metrics  -> aidev/.../a1 -> worktree B
   └─ item tests    -> aidev/.../a1 -> worktree C
```

Even if several Workers execute simultaneously, they never share a writable working directory.

## Persistence budget

R8-S2 adds:

```text
new tables:      0
new migrations:  0
new columns:     0
```

Core tables remain 5 and Flyway remains V7.

A logical WorkItem is represented by the immutable planning manifest plus its deterministic `IMPLEMENTATION` WorkTask. No `work_item` table is introduced merely for normalization.

## Explicit non-goals

R8-S2 does not yet:

- merge independent result branches;
- resolve conflicts between work items;
- sequence an item on another item's result commit;
- create one aggregate integration gate for the whole autonomous run;
- collapse per-item engineering results into one release/approval authority.

Those are integration concerns after parallel implementation itself is proven.

## Acceptance

`R8S2ParallelImplementationIT` proves that a three-item planner manifest:

1. materializes three deterministic implementation tasks;
2. makes all three READY after planning completion;
3. preserves the same immutable base revision;
4. supplies Requirement + Architecture + Plan as authoritative inputs to every item;
5. lets three independent coding Workers claim three distinct tasks;
6. gives each claim a distinct workspace branch.

`ImplementationPlanParserTest` validates the manifest parser and duplicate-key rejection.
