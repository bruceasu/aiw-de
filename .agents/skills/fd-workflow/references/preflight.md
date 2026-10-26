# Preflight and workspace selection

Read before starting or resuming implementation. Resolve shared resources via
[dependencies](dependencies.md). Read the current FD, relevant approved Issue
and stable specs, and repository limits. Inspect a receipt only when one exists.

For an existing FD, inspect recorded workspace coordinates before assuming its
design exists on the parent. New isolated plans may exist only in that worktree.
After archive, a retained worktree record means delivery/cleanup is still pending
or needs reconciliation. aiw fd resume locates that worktree and reports this
management step instead of redispatching a completed role. Invalid/stale metadata
is a recovery gate, not proof that completion is finished.

## Workspace

Select before writing the plan. Outside Git, use the current workspace and skip
all worktree/Git lifecycle operations. Do not initialize a repository. Inside
Git, the current workspace is the default; use a worktree only for an explicit
user worktree request or explicit auto intent. A full request continuing
automatically does not itself count as auto.

For auto in Git, all planning, implementation, review, evidence and archive
run in feature/<fd-id> at .wt/<fd-id>. For a new FD, use
aiw fd new "title" --worktree; it prepares the worktree before writing the plan.
For an existing committed FD, use aiw git wt add <id> before further authored
changes. Do not create a second worktree when valid coordinates already exist.

Creation needs a clean parent and ignored .wt/ and .ai/ paths. Verify fd_id,
parent_branch, branch and worktree in the recorded workspace.json. The recorded
parent is the sole final delivery target. Do not write the new plan, Worker
reports or archive on that parent. A dirty parent or unsupported CLI stops
isolated setup; never silently execute auto in the current/parent workspace.

Direct execution creates no worktree or workspace metadata. Preserve unrelated
edits and user Git limits. Workspace selection alone does not authorize
implementation or runtime checks. Keep Work Items, TODO and Verification current.

## Single host and managed handoffs

Without a runner or existing handoff, the host continues directly from the FD:
Design while planning, Open when ready, and In Progress during implementation.
No separate claim, role prompt or Worker report is needed. Do not mark Complete
before the applicable checks and review gate are satisfied.

With an existing managed handoff, read [handoffs](handoffs.md). Respect exact
event ownership, stale evidence and any in-flight role. If AIW_FD_ROLE_RUNNER
is set, the runner owns dispatch. Never take over an unknown session or reset
review/test budgets. Preserve legacy Pending Test/Test Acceptance states;
ordinary resume does not silently select that compatibility path.

Before delivery, decide the review policy under skills/work-management.md.
Routine Self work proceeds from recorded self-checks. Independent work uses
[review](review.md); request-review handles a ready direct-workspace FD without
a forced status transition. Missing decisions, scope conflicts or exhausted
repair budgets stop the affected work. Record routine blockers in the FD.
