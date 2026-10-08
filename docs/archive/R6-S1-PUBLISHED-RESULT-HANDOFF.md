# R6-S1 — Published Result Handoff

Status: implementation candidate after accepted R5 baseline.

## Goal

Make completed repository task results consumable by downstream workers without reintroducing a fixed pipeline or a shared filesystem requirement.

R6-S1 keeps the R3/R4/R5 invariants:

- Task + Dependency remains execution authority.
- Workers pull tasks.
- Control Plane never stores worker-local filesystem paths.
- Database stays at four domain tables.
- Review/verification/recovery remain ordinary WorkTasks rather than direct agent calls.

## Result publication

`execution_attempt` gains one nullable column:

```text
publication_ref
```

A repository completion can now report:

```json
{
  "resultRevision": "<git commit>",
  "changedFiles": ["..."],
  "publicationRef": "git:origin:aidev/TASK-.../a1"
}
```

`publicationRef` is transport metadata. `resultRevision` remains the immutable content identity.

The five reference workers support:

```text
AIW_GIT_PUBLISH_REMOTE=origin
```

When configured, a worker pushes its result commit to its assigned branch and returns a publication reference in the canonical form:

```text
git:<remote-name>:<branch-name>
```

If unset, publication is local-only and `publicationRef` is null.

## Dependency result materialization

When a task is claimed, the Control Plane resolves every completed dependency to its latest completed execution attempt and returns `inputs[]`:

```json
{
  "inputs": [
    {
      "taskId": "IMPLEMENT-1",
      "taskType": "IMPLEMENTATION",
      "repositoryRef": "core",
      "baseRevision": "<base>",
      "resultRevision": "<result>",
      "publicationRef": "git:origin:aidev/IMPLEMENT-1-.../a1",
      "changedFiles": ["src/main/java/App.java"]
    }
  ]
}
```

This is derived from dependency edges. No separate artifact or handoff table is required.

## Worker behavior

Reference workers now:

1. claim a task;
2. inspect `inputs[]`;
3. fetch supported `git:*` publication references into their configured local repository;
4. materialize the assigned worktree;
5. append the authoritative upstream input metadata to the Codex instruction;
6. execute;
7. commit the result;
8. optionally publish it;
9. complete with `resultRevision`, `changedFiles`, and `publicationRef`.

## Review / verification boundary

R6-S1 deliberately does not hardcode:

```text
IMPLEMENTATION -> CODE_REVIEW -> VERIFICATION
```

It provides the data handoff required by those tasks. R6-S2 can add software-development completion handlers that create review/verification tasks while still leaving execution to the READY task pool.

## Database impact

Still four domain tables:

```text
worker
work_task
work_task_dependency
execution_attempt
```

Migration V4 adds only `execution_attempt.publication_ref`.

## Acceptance

- legacy environment prefix count is zero;
- canonical environment prefix is `AIW_`;
- V1 -> V4 SQLite migration succeeds;
- table count remains 4;
- predecessor result can be resolved as downstream `inputs[]`;
- publicationRef round-trips through execution_attempt;
- Python, TypeScript, JavaScript, Go and Java reference workers understand upstream inputs;
- all five reference workers support optional `AIW_GIT_PUBLISH_REMOTE`.

## Dependency readiness on task creation

A downstream task may be created **after** its predecessor has already completed. Task creation therefore resolves readiness after persisting dependency edges; it cannot rely only on predecessor-completion callbacks. Predecessor rows are locked in deterministic TaskId order while the initial readiness check runs, closing the create-vs-complete lost-wakeup race.

## Test isolation hardening (Fix3)

Integration tests that reuse one Spring ApplicationContext and one class-scoped SQLite database must not depend on JUnit method order. R3S1WorkTaskGraphIT, R3S3FakeWorkerRuntimeIT, and R4WorkerProtocolIT now clear the four mutable business tables before each test method while preserving the Flyway schema. This prevents READY tasks, workers, leases, and execution attempts created by one test from affecting another test.

The cleanup order is foreign-key safe:

```text
work_task_dependency
execution_attempt
work_task
worker
```

The required acceptance command is:

```text
mvn clean verify
```
