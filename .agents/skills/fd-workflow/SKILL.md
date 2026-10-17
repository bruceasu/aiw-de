---
name: fd-workflow
description: Create or refine a numbered FD, or run its implementation through independent review and default automatic local delivery and archive, respecting explicit stage limits.
---

# Feature Design Workflow

Read `skills/reviewed-skill-contract.md`, `skills/work-management.md`, and the
repository instructions. Declare `skills/workflow-methods.md` for the shared
splitting, specification and review methods; read only the current stage's
section. Declare `skills/testing-methods.md` for opted-in test support; load
`references/testing.md` only when that support is requested. Use this
Skill when the user wants to create, refine, review, or close a Feature Design.
New engineering work is FD-first. OpenSpec supplements the FD with stable
capability requirements when needed; it is not a second work plan. Use the
automatic lifecycle below for an ordinary numbered FD implementation request,
including requests to execute ready FD Work Items. Explicit `auto`, full
lifecycle, isolation, and worktree requests remain supported. A design-only
request or question does not start implementation. Respect implementation-only,
review-only, partial-item, and no-merge limits instead of expanding their scope.

## Implementation entry and stage limits

Use this Skill for both FD design and implementation; there is no separate
implementation Skill or compatibility alias. Resolve the FD and pending role
first. An Open/In Progress FD or an assigned repair routes to Worker; read
`roles/worker.md` and `references/implementation.md` only when implementing.
Design discussion and questions do not start implementation.

Natural-language examples (host Skill invocation, not new CLI flags):

- `$fd-workflow FD-XXX implement` requests the normal full FD lifecycle.
- `$fd-workflow FD-XXX implementation only; no review or merge` stops after
  implementation evidence and scoped commits, before Reviewer dispatch.
- `$fd-workflow FD-XXX only Work Item 1.1` limits implementation to that item;
  do not mark the whole FD ready or dispatch review without separate authority.
- `$fd-workflow FD-XXX implement and review; no merge` permits independent
  review but stops before delivery and archive.
- `$fd-workflow FD-XXX auto with test` or `FD-XXX 自动执行，带测试` selects
  test support by meaning, not fixed syntax: suitable core-rule TDD plus
  independent fd-test before implementation review. More specific limits win.

Preserve an explicit design-only, review-only or other narrower request.
Dispatched roles never expand their assignment into the host PM lifecycle.

## Resolve the FD

Use an explicit FD ID first, then a unique FD linked from the current Issue or
conversation. Ask when several FDs match. For a new FD, use `aiw fd new
"<title>" [--issue <id>]`; this allocates an unused `FD-XXX` number, creates
the file from `docs/templates/TEMPLATE.md`, updates the index, and records a
`design-requested` event. The FD owns design and execution; do not create a
second engineering plan or ticket-mapping stage.

## Design

Planner owns this stage. Load `references/planning.md` for readiness,
specification collaboration and conditional independent design review.

Read the FD, approved Issue evidence when present, relevant code, prior FDs,
and stable specs. Write the problem, credible options, decision and reason,
scope, compatibility effects, numbered Work Items, acceptance, and a realistic
Verification plan. Use `%% NEEDS_INPUT: ...` for material unknowns. Do not
turn a routine technical choice with clear evidence into a human question.
Keep Work Item IDs stable after implementation starts.

### Work Item size and splitting

Before marking a design ready, assess every Work Item's size and difficulty.
Each item must be small, with low or medium difficulty, and deliver one result
that can be reviewed on its own. Aim for half a day or less per item; this is
a planning estimate, not a time guarantee.

Record the size, difficulty, dependencies, and completion criteria for each
item. Split items that combine several outcomes, mechanisms, or independent
decisions. Separate unresolved design work from implementation, and keep
shared logic in one owner item rather than duplicating it across the split.

Splitting does not remove technical uncertainty or security risk. If an item
depends on an unknown external contract, platform strategy, or security
design, record a `%% NEEDS_INPUT` Gate and resolve it before treating the
implementation as low or medium difficulty. Split again if the resolved work
still exceeds the size or difficulty target.

Preserve scope and acceptance coverage when splitting. Record how the old
items map to the new ones, update dependencies, TODO, and Verification, and
keep IDs stable after implementation starts. After implementation starts, add
new child IDs and explicitly cancel superseded unfinished items with a reason;
do not reuse IDs or erase completed work. Do not aim for a fixed item count.

If the human later changes an acceptance or verification decision, reconcile
the FD before requesting another review: record the decision and its scope,
update the affected Work Items and Verification plan, and remove superseded
`%% NEEDS_INPUT` gates. A waived check is recorded as not run with its residual
risk; it is never described as passed. Do not infer that waiving a check also
waives the required behavior. A previous review finding remains historical
evidence, not the current acceptance policy. Request a fresh independent review
against the revised FD and report.

`Design` becomes `Open` only when the FD contains all required sections,
numbered Work Items, and no material `%% NEEDS_INPUT` note. The Planner then
emits `design-ready` with `--producer planner --artifact <fd-path>` and the
current source event ID when one is dispatched. This records the handoff to
Worker. Do not emit an event merely because a heading or template exists.

Use `aiw fd list`, `show`, and `resume` for status and recovery. A pending event
can be handled by the named role in the current host after `aiw fd claim <id>
<event-id> --session <host-session-id>`. A dispatched event with unknown outcome
must be reconciled against its original session or log.

The `--session` value is an opaque ownership reference for this host session;
it need not be a platform-provided ID. Prefer a stable ID supplied by the host.
If none is available, the role handling the event must generate its own unique
reference (for example, `fd011-reviewer-20261001-8f3c2a`, with a freshly
generated suffix), keep it for the whole session, and reuse it for an
idempotent claim. Do not ask the human to
provide an agent session ID or borrow another role's reference. Check the
latest receipt immediately before claiming: only claim a pending event for
the current role, and never replace a launching or dispatched owner. Record
the chosen reference in the role's report with the source event ID.

For an active `Pending Verification` FD blocked by a stale, unclaimed handoff,
`aiw fd request-review <id> --reason "..."` creates a fresh Reviewer request
for the current FD. It does not fabricate Worker completion. An independent
Reviewer claims that event and reports findings. Do not use this recovery path
for an in-flight event or to bypass unresolved `%% NEEDS_INPUT` gates.

For an active `Open` or `In Progress` FD whose latest pending Worker handoff
no longer matches the FD revision or digest, PM uses `aiw fd refresh-worker
<id> --reason "..."`. It cancels the stale receipt and creates a new
`work-requested` Worker handoff bound to the current FD. Claim the new event
and cite it on `implementation-ready`. Never use this operation to replace
a claimed or in-flight handoff, or edit the old receipt by hand.

To continue an archived `Closed` or `Deferred` FD, use `aiw fd reopen <id>
--reason "..."`. It preserves the earlier disposition and evidence, returns
the FD as `In Progress`, and creates a new Worker handoff. Claim that exact
event before editing and cite it on `implementation-ready`. Keep previous
Work Item checkboxes as historical progress; revise them only when the renewed
scope warrants it. Use distinct filenames for new reports and reviews (for
example, a revision suffix), since `close` preserves earlier archived evidence
and rejects collisions. An archived `Complete` FD uses `request-review` instead.
If a reopen reason is unreadable or mistaken, correct it with `aiw fd reopen
<id> --reason "..." --correct-reason` before claiming the pending Worker
event. This managed correction keeps the event ID, records the previous value,
and updates the FD and receipt digest together. Never hand-edit a receipt.

## Default lifecycle: one FD through review, merge, and archive

Apply this procedure by default to one numbered FD implementation request.
`$fd-workflow auto` is an explicit alias for this lifecycle, not a second
approval required after a pass. Stop at any narrower user-requested stage;
an incomplete or partial FD is not eligible for delivery or archive.
The host agent is PM, Planner, and Worker; Reviewer uses a separate subagent.
A dispatched role stays within its own assignment while the host PM owns
continuation. Use the recorded FD branch/worktree unless the user explicitly
selected the primary workspace. The shared contract authorizes focused local
commits, squash delivery to the recorded parent after a current passed review,
verified CLI cleanup, and successful-delivery archive plus its focused commit.
This default does not authorize tests,
final builds, network access, permission escalation, push, deployment, or
publishing. Follow narrower repository rules.

Load the stage prompt in `roles/` only when performing that role: `pm.md`,
`planner.md`, `worker.md`, or `reviewer.md`. The shared lifecycle,
handoff, and authorization contract remains in `skills/work-management.md`;
these short prompts define the current role's inputs, output, and boundaries.
The host may combine PM, Planner, and Worker. Reviewer evidence must come from
a session separate from Worker. Test support is optional; when explicitly
selected, the host follows `references/testing.md` without adding Tester events
or relaxing runtime authority. Standalone fd-test remains available.

### Blocker feedback for every stop

Whenever the default lifecycle or explicit Auto cannot continue at preflight, design, Worker, PM, Reviewer,
delivery, archive, or cleanup Gate, write one factual feedback pair
under `docs/features/reports/FD-XXX-blocker-<unique-suffix>.md` and `.json`
before reporting the stop. Use
`docs/templates/BLOCKER_FEEDBACK_TEMPLATE.md` and
`docs/templates/BLOCKER_FEEDBACK_DATA_TEMPLATE.json`; the Markdown must be Chinese and
contain one same-directory `aiw-data` reference. Use the FD ID, stage, role,
exact source event when available, observed symptom, confirmed cause or
unknown, actual recovery attempts and outcomes, unresolved status, human
decision need, next step or risk, and a concrete reusable improvement or
`none`. If no event exists, use JSON `source_event: null` and explain why
in the report. Make the suffix unique across resumes and archived records.
Keep the pair in the writing role's workspace and commit tracked feedback
before a handoff or merge in isolated mode. In direct-workspace mode, commit
only within the shared contract's scoped FD authorization and any explicit
user limits; do not edit an active handoff's FD body or
receipt merely to record a blocker. Feedback is supplemental evidence, not a new
handoff artifact, and does not itself authorize tests, rule changes, Gate
overrides, or risky recovery.

When a blocker is resolved, update that pair with the actual resolution and
time, preserving the original facts and failed attempts. If the writing role
cannot safely update it, add a linked follow-up pair. At the start of each
Auto resume and before archive, review this FD's active and archived blocker
records. State which repeated or reusable issues warrant a specific skill,
stable spec, or template change; use the normal FD scope, authorization, and
review process for any such change. An unresolved record stays unresolved
until evidence supports a result. Include the record path and current status
in the user-facing Gate and final report.

1. **Preflight and resolve.** Read the repository instructions and this FD's
   Issue, stable specs, and current receipt. If `AIW_FD_ROLE_RUNNER` is set,
   stop before creating or claiming a handoff: the configured runner owns
   dispatch. For a new feature request, use `aiw fd new`, then claim its exact
   pending `design-requested` event as this host Session. For an existing FD,
   use its unique ID and inspect the latest receipt and recorded Session.
   Before claiming any existing Worker handoff, count prior Reviewer outcomes
   in the active implementation cycle. If the latest result is
   `changes-requested`, read its report and assess actionable repair. At
   three failed outcomes or with no actionable repair, leave the Worker
   handoff pending and report the human-directed recovery Gate. Apply this
   check again after interruption; do not reset the count on a new invocation.
   Claim only a pending event addressed to the current role. Never take over
   a foreign claim, restart a launching/dispatched role, synthesize missing
   receipts, or treat an old direct review as a current Reviewer event. If a
   handoff is stale or ambiguous, stop and report the recovery command and
   state. Route an existing `Design` FD to Planner, `Open` or `In Progress` to
   Worker, `Pending Verification` to an independent Reviewer, and an active
   `Complete` FD with a current pass to close. A legacy `Pending Test` or
   `Pending Test Acceptance` state is not part of the default workflow; preserve
   its receipt and continue only when the user explicitly requests its legacy
   CLI path. Skip already completed stages;
   an archived Complete FD is already done. Before any implementation, capture
   the current branch. For isolated work, commit the ready FD plan on the parent
   branch and make sure the parent workspace is clean. Do not mix unrelated
   changes into the FD plan commit; if other parent changes are uncommitted,
   resolve and commit them separately first. Unless the user explicitly directs
   primary-workspace implementation, ensure `.wt/` and `.ai/` are ignored by
   Git; `wt add`
   rejects missing rules before changing Git state. Create the branch/worktree
   with `aiw git wt add <id>` (`feature/<id>` and `.wt/<id>`), the default for
   numbered FDs. Immediately read `.ai/fd/<id>/workspace.json` and verify
   `fd_id`, `parent_branch`, `branch`, and `worktree` against the created
   worktree. This file records the exact parent for later merge; stop if it is
   missing or inconsistent. Keep all Worker writes in that worktree.

   If the user explicitly directs work in the primary workspace, use the
   direct-workspace path in `skills/work-management.md`: do not create a
   worktree or workspace record, and do not invent a Worker event or receipt.
   Keep the FD's Work Items, TODO, and Verification current, inspect the
   primary-workspace diff, and preserve skipped checks as unrun. The FD
   implementation request includes focused local commits and completion under
   the shared contract, unless explicitly limited. Workspace selection alone
   does not grant implementation or runtime authority. To obtain independent
   review without a Worker handoff, PM sets the active FD to `Pending Verification`
   with `aiw fd set-status <id> "Pending Verification" --operator <pm>
   --reason "..."`, then creates the Reviewer handoff with
   `aiw fd request-review <id> --reason "..."`; cite only evidence that actually exists. If the
   user explicitly chooses PM override, archive with `aiw fd close <id>
   Complete --force --reason "..."` and state the skipped review/evidence in
   the reason. Never edit receipts or represent skipped stages as passed.
2. **Plan and split when in Design.** Load `roles/planner.md`. As Planner, resolve routine choices from evidence and
   write Problem, options and decision, scope, ordered numbered Work Items,
   acceptance, TODO, Verification, and relevant spec updates. Split items by
   independently reviewable outcome and dependency order; keep IDs stable.
   Stop for a material `%% NEEDS_INPUT` decision. When ready, emit
   `design-ready --producer planner --artifact <fd-path> --source-event
   <claimed-design-event>`. Claim the resulting Worker handoff as this host.
3. **Implement when assigned Worker.** Load `roles/worker.md` and
   `references/implementation.md`. The reference owns preparation, per-item
   changes, compile/static evidence and the implementation-ready handoff.
   Apply the shared workspace/authorization contract and the user's stage
   limits before any next-role event. Commit final scoped evidence before
   handoff. For primary-workspace work without a Worker handoff, use the
   managed status/review route in step 1. An incomplete FD cannot emit a
   full-FD completion handoff. Then continue only to an authorized stage.
3a. **Opted-in test support.** Ordinary workflow does not generate or run tests.
   If selected in any equivalent wording, load `references/testing.md`: TDD
   during Worker implementation, then one independent fd-test assignment
   before emitting implementation-ready. Retain authorization and failure
   budgets across resumes. Exhaustion switches to bounded static diagnosis
   and independent remaining work, not repeated repair or execution. Related
   facts and defects may go to PM/Reviewer. No Tester event, test acceptance
   vote or coverage threshold is added. Preserve legacy `Pending Test` and
   `Pending Test Acceptance` records; Auto does not resume that legacy route.
4. **Independent review when Pending Verification.** Load `roles/reviewer.md`. Count Reviewer outcome events for this FD's active
   implementation cycle, including earlier auto invocations; the limit is
   three, not three new attempts on every resume. Spawn one separate Reviewer
   subagent through the host's subagent capability. Give it the FD ID, exact
   pending Reviewer event ID, current FD path, Worker report, applicable
   `fd-review` Skill, and repository authorization limits. It must claim the
   event in its own Session, inspect the actual diff and evidence, write a
   review report, and emit `verification-passed` or `changes-requested` with
   `--source-event` pointing to that claim. The host must not write the
   review report, emit a Reviewer event, or substitute a same-session review.
   Reviewer checks the FD acceptance against the actual diff, Worker report,
   and relevant implementation/test evidence, including oracle quality,
   revision coverage and limits. Optional reports add no automatic threshold
   or extra review round. Its session must differ from Worker.
   If subagents are unavailable, stop and leave the handoff pending.
5. **Repair, deliver, and close.** Inspect the subagent's report and latest receipt;
   subagent prose alone is not a pass. On `changes-requested`, first count
   Reviewer outcomes and assess whether the findings have an actionable fix.
   If this is the third failed review or no fix is actionable, stop before
   claiming the new Worker handoff; leave it pending, preserve the report, and
   state the Gate. Otherwise claim that handoff, fix the concrete findings,
   update the FD/report, and emit `implementation-ready` again. On
   `verification-passed`, the host PM MUST continue without another auto/merge
   confirmation when the requested full FD scope is complete, no unresolved
   blocker exists, current review covers the implementation, and the latest
   receipt matches the FD revision and content. Preserve explicit stage limits.
   In direct-workspace mode, the reviewed changes are
   already in the primary workspace; skip worktree delivery and proceed to
   archive. In isolated mode, read `.ai/fd/<id>/workspace.json` and take its
   `parent_branch`, `branch`, and `worktree` as the merge coordinates. Confirm
   the parent branch is clean and still matches the recorded parent. Run
   `aiw git wt local-merge <id>` directly for one squash commit on the parent
   branch. Do not proactively `sync` every FD before review or delivery; a
   clean parent advancing alone does not invalidate unchanged reviewed FD work. On a
   parent-side content conflict, the command resets the failed squash, verifies
   the parent is clean, and merges the parent into the FD worktree for resolution.
   Pause delivery and load `resolving-merge-conflicts` for the bounded
   recovery below. If any conflict cannot be safely resolved, preserve the
   state and report the unresolved gate without aborting or retrying. Do
   not blindly run commit/sync/retry for every error. If recovery fails, leave the FD active and preserve
   the worktree and branch. On success,
   `local-merge` verifies the clean FD HEAD, exact recorded coordinates, the
   single-parent squash, and its `FD-Source` before removing the worktree and
   branch. It safely detaches only a verified `.ai` junction to the primary
   workspace and preserves `.ai/fd/<id>` receipts. If cleanup partly fails,
   report the delivered commit and remaining resources; do not roll back the
   delivery. Then close with `aiw fd close <id> Complete` from the parent
   workspace and commit only the archive/index changes. A close rejection is a
   Gate, never a reason to edit a receipt or archive manually.
   Report the merge result, archive path, review count, commands actually run,
   unrun checks, and residual risks.

Treat an interruption or a new user instruction as a checkpoint: inspect the
latest FD and receipt before continuing. Do not reset the three-review limit
on resume. Never collapse a failed review into a passed one to meet the cap.

## Recover a failed local delivery

Keep full failure output and inspect both workspaces, the delivered commit if
any, `MERGE_HEAD`, and the unmerged index. Distinguish dirty-workspace or
coordinate errors, actual content conflicts, and delivery that succeeded but
failed cleanup. Diagnose first; a failed command alone does not call for sync.
Never discard unrelated changes or repeat an unchanged failed command.

For an actual conflict in this full FD lifecycle, load the standalone
`resolving-merge-conflicts` Skill and attempt one bounded resolution based on
the FD, both histories and local evidence. This is authorized recovery, not a
waiver of gates or runtime limits. If the Skill is unavailable, intent is
unclear, or any conflict cannot be safely resolved, preserve the in-progress
merge and remaining conflict entries, report resolved and unresolved paths,
and stop. Keep any clearly resolved edits documented; do not abort, reset,
force a side or commit a partial resolution. Explicit stage limits and foreign
role ownership still apply. CLI recovery may already have merged the parent
into the FD; finish or diagnose that merge before considering a separate sync.
If the cause
requires a separate sync, use `aiw git wt sync <id> <parent_branch>` with the
recorded parent explicitly. Inspect and resolve each conflict, including
modify/delete intent, before staging. `wt commit` runs `git add -A` and commits
all changes; it does not choose a correct resolution or remove conflict markers.
Use it only when every changed file is intentionally resolved and in scope.

Record the recovery commit, diff, commands and blocker resolution. When the
recovered implementation is outside the current passed review, do not deliver
under that old pass. With no foreign or in-flight role, the host PM uses
`aiw fd set-status <id> "Pending Verification" --operator <pm>
--reason "recovery changed reviewed implementation"` if the active status is
Complete, then `aiw fd request-review <id> --reason "review recovered result"`.
For an existing Pending Verification FD use request-review only when its
handoff is stale and unclaimed; otherwise honor the existing valid handoff.
Update recovery evidence before requesting review, never edit receipts, and
give the separate Reviewer the current FD, original Worker report, recovery
evidence, commit and diff. Keep the active cycle's review count and stage
limits; stop at the existing review limit rather than resetting it. Resume
normal delivery only after a current independent pass covers the recovered
implementation and all delivery gates pass. Do not require a new review merely
because the parent advanced without changing the reviewed FD implementation.

`local-merge` verifies delivery and cleans Git resources. The host then runs
`aiw fd close <id> Complete` and commits the archive/index separately. If only
cleanup failed after a verified squash, follow the CLI cleanup recovery path;
do not create another squash, revert delivery or call an unarchived FD done.

## Local delivery versus release

A current fd-review pass gates local delivery; its shared implementation
review method includes actual source correctness, Standards, Spec and evidence.
Do not additionally invoke standalone code-review or add its two reviewers to
every FD. A planning design review is separate evidence, not that final pass.

Local merge/archive is not release approval. Only when a real release is
planned, route the release-readiness request and operational evidence to
`fd-release-gate`. Its GO/GO_WITH_RISK/NO_GO assessment is outside normal
FD local completion and never grants push, publishing or deployment authority.

## Close and archive

A passed Reviewer event is required before `aiw fd close <id> Complete`.
Deferred and Closed need `--reason "..."`. Native archives use
`docs/features/archive/<FD-ID>/<FD-ID>_SLUG.md`; matching reports and reviews
go into that FD directory. Archive changes the FD path and index; it does not
merge or publish code. Update a changelog only if the repository uses one. Do
not commit, push, or merge as a side effect of design or archive unless the
user authorized that action. A normal numbered FD implementation request
provides the scoped local authorization described in the default lifecycle;
design-only requests, standalone commits/reviews, and explicit stage limits do not.

Read `references/portable-operations.md` for numbered FD conventions and
`references/templates.md` for layout examples. When they conflict with the
FD-first contract above, use this Skill and the current CLI behavior.
