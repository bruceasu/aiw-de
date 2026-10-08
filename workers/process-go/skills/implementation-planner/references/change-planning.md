# Incremental Change Planning

When an upstream baseline changes, perform impact analysis before editing the implementation plan.

## Delta Flow

`Requirement Delta -> Architecture Delta -> Implementation Delta -> Test Delta -> Release Delta`

## Procedure

1. Identify changed requirement/architecture IDs.
2. Find affected increments and work packages through traceability.
3. Classify each affected work package as `ADD`, `MODIFY`, `REMOVE`, `REORDER`, `SPLIT`, `MERGE`, `INVALIDATE`, or `UNCHANGED`.
4. Recompute dependencies and critical path.
5. Re-evaluate migrations, compatibility, rollout, and rollback.
6. Update test coverage and acceptance mapping.
7. Preserve completed-work evidence and do not rewrite history.

## Completed Work

If a baseline change invalidates completed work, create explicit remediation work. Do not silently edit the old task as though the previous implementation never existed.
