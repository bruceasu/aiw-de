# FD-007: Task Instruction Revision and Operator Control

**Status:** Complete
**Revision:** 20
**Priority:** High
**Test policy:** Independent
**Evidence policy:** Dual

## Problem

Operators can pause or cancel an Autonomous Run, but they cannot correct a Work Task instruction after task creation. A Worker receives the instruction when it claims the task; changing displayed data alone would not update a Worker already executing an earlier instruction. This leaves the Control Plane unable to safely correct mistaken or incomplete directions while preserving a reliable record of what each Worker was told.

Affected users are operators responsible for supervising Runs and Workers. The system must let authorized operators correct future execution, make stale attempts unable to publish results, and retain the original instruction and execution evidence for audit.

## Options and decision

1. **Edit the current task row in place.** Small initial change, but destroys the instruction history and cannot tell which instruction an existing attempt received. Rejected.
2. **Append supplemental text to the current row.** Preserves the original text only informally and does not notify a Worker that already holds a claimed task. Rejected as a complete solution.
3. **Version task instructions and bind each execution attempt to the version it claimed.** Retains history, supports stale-attempt rejection, and provides a basis for explicit stop/revise/requeue behavior. Chosen.

Use existing `OPERATOR` and `ADMIN` permissions for task-control operations; do not add a new role in this FD. Require an operator reason and audit every revision and control action. Completed tasks remain immutable; corrections use a linked follow-up task.

For tasks that may produce external side effects, default re-dispatch waits for the old Worker to acknowledge stop. An `ADMIN` may explicitly force re-dispatch before acknowledgement by confirming a duplicate-side-effect warning; the reason, actor, confirmation, and old/new attempts must be audited. `OPERATOR` cannot force this transition. This gives the control plane a final override while making the unresolved external execution risk visible and attributable.

## Solution

Add immutable instruction revisions to task authority. A task exposes a current revision; each execution attempt records the revision delivered at claim time. Task detail shows the current instruction revision, active Worker/attempt revision, lease state, stop-acknowledgement state, and revision history.

For `BLOCKED` and `READY`, an authorized operator can revise the instruction using an expected-version check. For `CLAIMED`, revoke the lease before requeueing the revised task. For `RUNNING`, invalidate the old attempt's ability to heartbeat or submit results and request cooperative stop from the Worker. Requeue after stop acknowledgement by default. If acknowledgement is absent, only `ADMIN` can force re-dispatch after an explicit duplicate-side-effect warning confirmation and reason; the old attempt remains fenced from control-plane results. For `COMPLETED`, retain the record and create a linked correction task.

The Worker protocol must carry the instruction revision and provide an explicit stop acknowledgement. Old lease tokens and stale instruction revisions must not complete tasks, publish results, or trigger dependent tasks. Preserve existing claim/complete semantics for tasks that are not revised. Any schema migration must support both SQLite and PostgreSQL dialects.

## Scope

Included:

- Task instruction revision history and operator audit metadata.
- Revision-aware claim, attempt, lease revocation, completion guards, and cooperative Worker stop acknowledgement.
- Authorized task-level actions and task-detail UI for revise, stop, requeue, and history.
- Linked correction tasks for already completed work.
- Focused migration and verification for SQLite and PostgreSQL compatibility.

Excluded:

- Creating a distinct AI "central brain" identity or changing authentication configuration.
- Editing completed execution history or approval evidence.
- Automatically deciding whether external side effects are reversible.
- General-purpose task editing of unrelated fields such as capabilities, priority, or workspace assignment.

## Work items

- [x] 1.1 Add immutable instruction revisions and audit records. **Size:** Small. **Difficulty:** Medium. **Dependencies:** none. **Completion:** task creation creates the initial revision; operator revisions require expected version, actor, and reason; schema remains portable across SQLite and PostgreSQL.
- [x] 1.2 Bind attempts to instruction revisions and reject stale lease/version operations. **Size:** Small. **Difficulty:** Medium. **Dependencies:** 1.1. **Completion:** claim returns and persists the exact revision; stale attempts cannot start, heartbeat, complete, publish, or release dependent work.
- [x] 1.3 Add Worker stop request and acknowledgement protocol. **Size:** Small. **Difficulty:** Medium. **Dependencies:** 1.2. **Completion:** an active Worker can receive a stop request, acknowledge it idempotently, and expose persisted stop state to the control plane.
- [x] 1.4 Add race-safe requeue and ADMIN force override. **Size:** Small. **Difficulty:** Medium. **Dependencies:** 1.3. **Completion:** normal requeue waits for stop acknowledgement; only ADMIN can force requeue without it after explicit duplicate-side-effect confirmation; every transition is audited and stale attempts remain fenced.
- [x] 1.5 Add operator task controls and revision history to the Run task detail. **Size:** Medium. **Difficulty:** Medium. **Dependencies:** 1.1 through 1.4. **Completion:** authorized users can revise or stop/requeue eligible tasks with a reason; UI shows current and executing revisions and stop state; completed tasks offer a linked correction path.
- [-] 1.6 Verify lifecycle, authorization, and protocol compatibility. **Size:** Small. **Difficulty:** Medium. **Dependencies:** 1.1 through 1.5. **Completion:** independent tests cover each task state, stale token/version races, stop acknowledgement, ADMIN force audit, completed-task correction, and existing Worker claim/complete flows on supported database dialects. **Cancellation reason:** this verification item is assigned to the Independent Tester stage rather than Worker. Tester r2 reports all 22 scenarios unrun. PM decision `docs/features/reports/FD-007-verification-decision-r1.md` waives the SQLite/PostgreSQL migration execution checks for this FD while recording them as unverified; the waiver is not a pass.

## Acceptance

- Every instruction revision is immutable and identifies its author, reason, timestamp, and predecessor revision.
- An attempt's recorded instruction revision matches the instruction returned to its Worker at claim time.
- Authorized revision is rejected on stale expected version; unauthorized users cannot revise, stop, or requeue tasks.
- An old lease or revision cannot start, heartbeat, complete, publish a result, or trigger downstream work after revocation.
- A `RUNNING` task is not represented as stopped until acknowledgement; force re-dispatch is a separate ADMIN-only action that requires explicit warning confirmation, reason, and audit evidence, and warns of possible duplicate side effects.
- Completed task instructions and results remain unchanged; correction work references the completed task.
- UI makes current revision, active attempt revision, Worker, lease, stop acknowledgement, and history visible.
- Existing non-revised Worker protocol behavior and run-level controls remain compatible.

## Verification

- Independent focused tests for revision creation, optimistic concurrency, per-state transition rules, authorization, and audit records.
- Worker protocol tests for claim revision delivery, stop request/acknowledgement, stale token rejection, and inability to publish stale results or trigger dependencies.
- Stop tests must prove that a legacy or lambda executor which only returns normally cannot acknowledge a stop or trigger ordinary re-dispatch; an explicitly stop-aware executor must report `STOPPED` only after it has stopped.
- Stop acknowledgement retry tests must prove that the same task, worker, and lease is idempotent without repeating requeue/resource release, while mismatched task, worker, or lease remains rejected.
- Template/controller coverage for empty, active, stopped, and completed task states and role visibility.
- Static review that V19 uses SQL compatible with SQLite and PostgreSQL. PM decision `docs/features/reports/FD-007-verification-decision-r1.md` waives applying the migration to live SQLite/PostgreSQL databases in this FD lifecycle; S21/S22 remain unverified, not passed, with the migration-runner and existing-data risks recorded.
- Static review of changed contracts and diffs; compile-only check per repository rules. Tests and database compatibility checks require the repository's test authorization workflow.

Static diff whitespace check passed. `mvn -q -DskipTests compile` could not start because Maven is not installed and no Maven/Gradle wrapper is present. Independent behavior tests and SQLite/PostgreSQL migration checks remain unrun under the repository's zero test-execution budget. The Worker stop signal is cooperative; an ADMIN force re-dispatch cannot guarantee termination of external side effects.

Reviewer r2 (source event FD-007-000013-test-accepted): changes-requested; see docs/features/reviews/FD-007-review-r2.md. F-01/F-02 are statically resolved. SQLite/PostgreSQL migration application for S21/S22 remains unverified; no migration was run.

## Sources

- User discussion: correcting task instructions, appending information after dispatch, and requiring highest operator control while retaining execution history.
- `src/main/java/me/asu/aidev/work/domain/WorkTask.java`
- `src/main/java/me/asu/aidev/work/port/WorkTaskRepository.java`
- `src/main/java/me/asu/aidev/execution/application/TaskClaimService.java`
- `src/main/java/me/asu/aidev/worker/protocol/http/WorkerProtocolV1Controller.java`
- `src/main/java/me/asu/aidev/webconsole/security/WebConsoleRole.java`
- `src/main/resources/db/migration/V1__r3_core.sql`
- `src/main/resources/db/migration/V10__r8_run_timeline_events.sql`
- `src/main/resources/db/migration/V19__task_instruction_revision_control.sql`
- `src/main/java/me/asu/aidev/work/domain/TaskInstructionRevision.java`
- PM verification waiver: `docs/features/reports/FD-007-verification-decision-r1.md`