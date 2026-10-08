# R5 — Isolated Workspace / Git Worktree / Parallel Coding

Status: implementation candidate for `mvn verify` acceptance.

## 1. Goal

Allow independent AI workers to execute coding tasks concurrently against the same repository without sharing a writable working directory.

R5 does not introduce a central pipeline. Workers continue to pull READY tasks through Worker Protocol v1.

## 2. Authority split

### Control Plane owns

- `repositoryRef` logical identity;
- immutable `baseRevision`;
- attempt number;
- deterministic `workspaceKey`;
- deterministic `branchName`;
- lease ownership;
- accepted `resultRevision` metadata;
- changed-file evidence.

### Worker owns

- mapping `repositoryRef -> local clone path`;
- machine-local worktree path;
- worktree materialization/removal;
- Codex process/session;
- local Git commit creation.

A local filesystem path is never lifecycle authority.

## 3. Task repository contract

A repository task must carry both:

```text
repositoryRef
baseRevision
```

or neither. `baseRevision` is required to be a full 40- or 64-hex Git object id rather than a mutable branch name such as `main`.

When a task has `repositoryRef=core`, task creation automatically requires capability `repo:core`.

## 4. Attempt workspace identity

Workspace identity is derived from Task ID plus attempt number:

```text
workspaceKey = <safe-task-slug>-<task-hash>-a<attempt>
branchName   = aidev/<safe-task-slug>-<task-hash>/a<attempt>
```

Therefore lease expiry/retry creates a new attempt and cannot accidentally reuse the failed worker's writable branch.

## 5. Materialization sequence

```text
claim
  -> start task
  -> verify local repositoryRef mapping
  -> git rev-parse <baseRevision>^{commit}
  -> require exact immutable commit match
  -> git worktree add -b <branchName> <localPath> <baseRevision>
  -> start/continue lease heartbeat
  -> invoke Codex with cwd=<localPath>
```

## 6. Completion sequence

```text
Codex returns
  -> git add -A
  -> if staged changes remain: worker creates one commit
  -> resultRevision = git rev-parse HEAD
  -> changedFiles = git diff --name-only baseRevision resultRevision
  -> POST complete(workspaceResult)
  -> Control Plane persists result on execution_attempt
  -> remove local worktree
```

Codex-created commits are not squashed or replaced. The worker commit only captures modifications still uncommitted when execution ends.

## 7. Failure and crash behavior

Normal failure:

```text
stop Codex
  -> report fail
  -> remove local worktree
```

Lease loss:

```text
heartbeat rejected
  -> interrupt/kill Codex
  -> do not report stale complete/fail
  -> remove local worktree when process control returns
```

Hard worker/process/host crash can leave a local orphan worktree. This is an operations cleanup concern, not an execution-correctness concern. A recovered task gets attempt N+1 with a new branch/workspace key.

Automatic destructive orphan cleanup is deliberately not implemented in R5 because a local worker cannot safely infer Control Plane ownership merely from filesystem age.

## 8. Cross-host result availability

R5 records the result commit and branch identity but does not automatically `git push` that branch. This is intentional for the local-first stage. Workers that share one repository object store can immediately consume the result. For workers on independent hosts, a shared Git remote or artifact transport is required. R6 should make publication/fetch plus review/verification handoff authoritative rather than hiding it inside worker-local behavior.

## 9. Persistence

R5 adds **zero tables**.

`V3__r5_isolated_workspace.sql` only adds repository/result columns to `work_task` and `execution_attempt`.

Total domain tables remain: **4**.

## 10. Acceptance

- R3/R4 tests remain green.
- repository task cannot be claimed by worker lacking `repo:<repositoryRef>` capability.
- claim response contains deterministic workspace assignment.
- repository task cannot complete without `workspaceResult`.
- result revision and changed files persist on the attempt.
- two worktrees can start from one base commit and modify the same file independently.
- base repository working tree/HEAD remains unchanged.
- Python/TS/JS/Go/Java examples all implement the same workspace semantics.
