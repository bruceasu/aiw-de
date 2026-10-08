# R3 Database Simplification Decision

Status: ACCEPTED / ACTIVE

## Decision

R3 is a clean-slate release. It has no database-compatibility requirement with R1 or R2.

R1/R2 code may be reused opportunistically, but R3 is not required to preserve their schema, lifecycle aggregates, APIs, or compatibility seams.

R3 starts from Flyway `V1` and only persists state required by the current architecture.

## Table budget rule

A new table is allowed only when at least one of these is true:

1. the object has an independent lifecycle;
2. it participates in concurrent ownership/locking semantics;
3. it requires indexed many-to-many/graph traversal that cannot be represented safely in the owning row;
4. it is immutable authority/audit evidence whose retention differs from its producer;
5. embedding it would create unbounded row growth or materially damage queryability.

Normalization by itself is not sufficient justification.

## R3-S2 schema

Domain tables: **4**.

### worker

Independent Worker lifecycle and current capacity accounting.

Owns:

- identity;
- runtime type;
- capabilities;
- max concurrency;
- active lease count;
- ACTIVE / DRAINING / OFFLINE status;
- heartbeat timestamps.

### work_task

Owns task identity, state, subject, skill, required capabilities and priority.

Current lease ownership is intentionally embedded here:

- current worker;
- lease token;
- acquired time;
- expiry time;
- heartbeat time;
- attempt number.

A separate lease table is not justified because only one lease can be active for a task at a time.

### work_task_dependency

Owns Task Graph edges because predecessor/dependent traversal must be indexed in both directions.

There is no dependency type column yet because only completion dependency semantics exist.

### execution_attempt

Attempt history has an independent retention/audit purpose, so it deserves its own table.

It records each claim/execution attempt and terminal result without keeping historical lease rows in `work_task`.

## Explicitly avoided tables

R3-S2 does not create:

- task_lease;
- worker_capability;
- work_task_required_capability;
- task_state_history;
- per-lifecycle-stage tables;
- R1/R2 compatibility tables.

## Forward rule

Engineering outputs should primarily become immutable Artifacts rather than a new table per requirement/architecture/planning/review/verification stage.

If a future feature needs another table, the design record must state which table-budget rule justifies it.
