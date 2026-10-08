# R8-S3 — Parallel Result Integration

## Goal

R8-S2 can materialize several independent implementation tasks from one plan. R8-S3 makes those branches converge into one authoritative repository result before final review, verification, Engineering Gate, and Approval.

## Authority rule

Parallel branches are execution results, not independent release authority.

```text
implementation A --\
implementation B ----> INTEGRATION -> review + integration verification -> gate
implementation C --/
```

For a multi-work-item autonomous run, individual implementation completion does **not** create review/verification tasks. The last branch to complete materializes one deterministic `INTEGRATION` task.

For a single-work-item run, existing behavior remains unchanged.

## Integration task

The integration task:

- has the same `subjectType` / autonomous `runId`;
- uses the same repository and immutable base revision as every branch;
- depends on every implementation task;
- therefore receives every branch as an authoritative `inputs[]` entry;
- requires the `integration` capability;
- runs in its own isolated R5 worktree;
- produces one `resultRevision` and optional publication reference.

The integration instruction explicitly requires the Worker to account for every upstream branch and not silently drop work.

## Validation after integration

`INTEGRATION` completion generates:

```text
CODE_REVIEW
INTEGRATION_VERIFICATION
```

`INTEGRATION_VERIFICATION` uses the same structured verdict contract as normal verification:

```text
AIW_VERDICT: PASS
AIW_VERDICT: REPAIR_REQUIRED
```

The Worker examples in Python, TypeScript, JavaScript, Go, and Java all recognize this task type.

## Repair lineage

If review or integration verification requests repair:

```text
INTEGRATION
  -> REPAIR (skillRef=integration-repair)
     -> CODE_REVIEW
     -> INTEGRATION_VERIFICATION
```

If another repair is needed, the integration lineage is preserved. This prevents a repair of an integrated result from accidentally reverting to single-branch verification semantics.

## Engineering Gate

`EngineeringGate` now permits these source types:

```text
IMPLEMENTATION
INTEGRATION
REPAIR
```

For parallel autonomous execution, only `INTEGRATION` (or its later `REPAIR`) reaches this point because the individual parallel implementation branches do not fan out to review/verification.

## Persistence

No schema change.

```text
core tables: 5
Flyway: V1-V7
```

R8-S3 reuses:

- `work_task` for integration work;
- `work_task_dependency` for the N-way join;
- `execution_attempt` for branch/integration results and Engineering Gate evidence;
- existing publication references for cross-machine Git visibility.

## Acceptance

`R8S3ParallelIntegrationIT` verifies:

1. three parallel implementation tasks are claimed independently;
2. first/second branch completion creates no downstream quality tasks;
3. the final branch creates exactly one integration task;
4. integration `inputs[]` contains all three branch revisions/publication refs;
5. integration produces one result revision;
6. downstream verification task type is `INTEGRATION_VERIFICATION`;
7. review + integration verification PASS closes a PASSED Engineering Gate on the integration task.
