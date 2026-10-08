# R6-S2 — Development Completion Handlers

Status: implementation candidate after accepted R6-S1 baseline.

## Goal

Materialize follow-up software-engineering work from completed tasks without reintroducing a central pipeline.

R6-S2 adds a generic completion extension point:

```text
TaskExecutionService
        |
        v
TaskCompletionDispatcher
        |
        +--> TaskCompletionHandler
                 |
                 +--> create WorkTask
                 +--> create TaskDependency
```

A completion handler is allowed to create durable work graph data. It does **not** select, invoke, or wait for a Worker. Generated tasks return to the shared READY pool and are claimed by compatible idle workers.

## Implementation completion policy

The first software-development handler is `CodeChangeCompletedHandler`.

When an `IMPLEMENTATION` task completes successfully:

```text
                   IMPLEMENTATION
                         |
                  COMPLETED result R
                    /           \
                   /             \
          CODE_REVIEW          VERIFICATION
             READY                READY
```

Both downstream tasks depend directly on the completed implementation. They do not depend on each other, so review and verification may execute in parallel.

## Repository baseline

For repository-backed implementation tasks, downstream tasks use:

```text
repositoryRef = implementation.repositoryRef
baseRevision  = implementation.resultRevision
```

This is intentionally different from the implementation's original base revision.

The worker first resolves/fetches authoritative upstream `inputs[]`, then materializes its worktree at the completed implementation result commit. Therefore review and verification inspect the exact implementation result rather than the pre-change source tree.

For non-repository implementation tasks, downstream tasks remain non-repository tasks.

## Capability inheritance

Technical capabilities are inherited while execution-role capabilities are replaced.

Example implementation capabilities:

```text
coding
java
maven
repo:core
```

Generated review:

```text
code-review
java
maven
repo:core
```

Generated verification:

```text
verification
java
maven
repo:core
```

Role capabilities removed during inheritance:

```text
coding
code-review
verification
integration-verification
repair
```

`repo:<repositoryRef>` continues to be added by the normal `CreateWorkTaskCommand` rule.

## Deterministic task identity

Generated task ids are deterministic:

```text
<implementationTaskId>~review
<implementationTaskId>~verify
```

If the source id is too long for the 128-character TaskId limit, the source prefix is truncated and a SHA-256-derived suffix is appended. No extra mapping table is required.

## Completion transaction

The completion sequence is transactional:

```text
attach result/session
    -> WorkTask RUNNING -> COMPLETED
    -> ExecutionAttempt RUNNING -> COMPLETED
    -> release Worker capacity
    -> dispatch completion handlers
    -> materialize generated Task + Dependency rows
    -> refresh pre-existing dependents
    -> commit
```

If completion-handler materialization fails, the transaction rolls back rather than committing a completed implementation without its required follow-up work.

## Protocol response

Task completion response now includes both existing readiness changes and dynamically generated tasks:

```json
{
  "taskId": "IMPL-1",
  "newlyReadyTaskIds": [],
  "generatedTaskIds": [
    "IMPL-1~review",
    "IMPL-1~verify"
  ]
}
```

This is observability only. Workers are not expected to execute the returned task ids directly; normal workers still call `claim`.

## Persistence impact

None.

R6-S2 keeps the same four domain tables:

```text
worker
work_task
work_task_dependency
execution_attempt
```

No Flyway migration is added. R6-S2 is an application/domain behavior change only.

## Acceptance

`R6S2ImplementationCompletionIT` verifies:

- implementation completion generates exactly two downstream tasks;
- generated task types are `CODE_REVIEW` and `VERIFICATION`;
- both tasks are immediately READY because their only dependency is already COMPLETED;
- both depend directly on the implementation and not on each other;
- technical capabilities are inherited while role capability is replaced;
- repository identity is preserved;
- downstream base revision equals implementation `resultRevision`;
- independent reviewer/verifier workers can claim the tasks;
- downstream claims contain the authoritative implementation input.

Authoritative project gate:

```bash
mvn clean verify
```
