# Autonomous Engineering Lifecycle

## 1. Requirement intake

A run starts with:

```http
POST /api/r8/requirements
```

The first durable task is `REQUIREMENT_ANALYSIS`. The request must identify an immutable repository revision.

The current task types are:

```text
REQUIREMENT_ANALYSIS
SOLUTION_ARCHITECTURE
IMPLEMENTATION_PLANNING
IMPLEMENTATION
INTEGRATION
CODE_REVIEW
VERIFICATION
INTEGRATION_VERIFICATION
REPAIR
```

Task states are:

```text
BLOCKED READY CLAIMED RUNNING COMPLETED FAILED CANCELLED
```

## 2. Planning stages

### Requirement Analysis

Produces a precise engineering requirement specification, assumptions and acceptance criteria. The worker’s final response is persisted as a `PASS` completion summary.

### Solution Architecture

Consumes the authoritative requirement summary and produces the solution architecture. It is also a semantic completion task whose full final response becomes downstream input.

### Implementation Planning

Consumes requirement + architecture and produces an implementation plan. It can either produce ordinary prose (safe fallback: one implementation task) or include the machine-readable work-item manifest described below.

## 3. Parallel implementation manifest

A planner may declare independent work items:

```text
AIW_IMPLEMENTATION_WORK_ITEMS_V1

AIW_WORK_ITEM_BEGIN endpoint
AIW_CAPABILITIES java,maven
Implement the health endpoint and application wiring.
AIW_WORK_ITEM_END

AIW_WORK_ITEM_BEGIN tests
AIW_CAPABILITIES java,maven
Add focused regression tests.
AIW_WORK_ITEM_END

AIW_IMPLEMENTATION_WORK_ITEMS_END
```

The Control Plane parses this manifest; the worker does not create tasks itself.

Use parallel work items only when they are independently implementable from the same immutable base revision. If B requires A’s unmerged changes, they should not be declared independent.

## 4. Isolated coding attempts

Repository tasks receive a workspace assignment containing:

- `repositoryRef`
- immutable `baseRevision`
- deterministic `workspaceKey`
- per-attempt `branchName`

The reference worker creates a Git worktree, runs the AI runtime there, commits uncommitted changes if necessary, derives `resultRevision`, computes `changedFiles` over `baseRevision..resultRevision`, and optionally publishes the result branch to a Git remote.

Retries create a new execution attempt and a new attempt-specific workspace/branch. A crashed attempt is not silently reused.

## 5. Parallel result integration

When planning produced multiple implementation branches, individual implementations do not independently become the final engineering result. After all required implementation tasks complete, a single `INTEGRATION` task receives every implementation result through `inputs[]`.

The integration result becomes the code result sent to final review and integration verification.

For a single implementation task, the system can proceed directly without an integration step.

## 6. Review, verification and repair

A completed code-change result fans out to independent read-only engineering tasks:

```text
CODE_REVIEW
VERIFICATION or INTEGRATION_VERIFICATION
```

Their final response must contain exactly one recognized verdict marker:

```text
AIW_VERDICT: PASS
```

or

```text
AIW_VERDICT: REPAIR_REQUIRED
```

The worker translates the marker into a structured completion report. It does not infer a missing verdict.

If either side requires repair, the Control Plane creates a `REPAIR` task. Reviewers and verifiers do not directly modify code.

A repair result fans out to review/verification again, forming a deterministic repair loop.

## 7. Engineering Gate

When both review and verification for the exact code result are complete, the Control Plane derives the engineering gate.

A gate binds the exact:

- source task;
- result revision;
- changed files;
- publication reference;
- review task and report;
- verification task and report;
- close timestamp.

A later repair has a different code result and therefore a different gate. Previous gate evidence is not rewritten.

## 8. Human approval

Approval is a separate lifecycle:

```text
PENDING -> APPROVED
PENDING -> REJECTED
```

Once decided, the decision cannot be reversed. Replaying the same decision is idempotent; attempting the opposite decision conflicts.

Approval can be manually requested through HTTP or automatically materialized by policy. Telegram is an optional transport adapter; it does not own approval authority.

## 9. Autonomous Run lifecycle

The run-level lifecycle is:

```text
RUNNING
  |
  | explicit worker task failure
  v
FAILED -- recover --> RUNNING
  |
  | Engineering Gate PASSED
  v
ENGINEERING_PASSED
  |
  | approval request exists
  v
WAITING_APPROVAL
  |            |
  | approve    | reject
  v            v
SUCCEEDED    REJECTED
```

Operator control is orthogonal:

```text
ACTIVE <-> PAUSED
  |
  +--> CANCELLED
```

### Failure vs worker crash

An explicit worker `/fail` moves the run to `FAILED` and requires operator recovery. A lost worker/expired lease is infrastructure recovery: the task is automatically requeued by lease recovery and does not by itself mean the engineering run failed.

## 10. Operator control

### Pause

- revokes current active leases;
- marks active attempts `EXPIRED` with `OPERATOR_PAUSED`;
- releases worker/run capacity;
- requeues affected work to `READY`;
- blocks new claims until resume.

### Resume

Restores claim eligibility. The next worker claim creates a new attempt.

### Cancel

Irreversibly cancels unfinished work. Active attempts are terminated with `OPERATOR_CANCELLED`.

### Recover

For a run in `FAILED`, requeues all currently failed tasks and returns lifecycle status to `RUNNING`.

## 11. Scheduling and fairness

Within eligible READY work, candidates are ranked by:

1. effective priority (descending);
2. fewer active leases for the same subject/run;
3. longer READY wait;
4. earlier creation time;
5. deterministic task ID.

Aging adds one priority point per 10 minutes of waiting, capped at +100. Base priorities are LOW=25, NORMAL=50, HIGH=75, CRITICAL=100.

## 12. Admission controls

Two scheduling ceilings are available:

- **Run maxParallelism:** 0 means unlimited; otherwise limits active tasks for one run.
- **Fleet maxParallelism:** 0 means unlimited; otherwise limits active tasks globally.

Both can be adjusted at runtime. Contraction never kills healthy leases; it blocks new claims until current work naturally drains below the new ceiling.
