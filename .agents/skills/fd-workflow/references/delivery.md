# Local delivery, recovery, release and archive

Read after recorded self-checks or a required current independent pass, before delivery or archive.
For a delivery failure, read the recovery section below before any recovery
action. Load [blockers.md](blockers.md) at a Gate and before archive; use
[handoffs.md](handoffs.md) for event recovery. Never expand explicit stage limits.

## Deliver a ready FD

Apply skills/work-management.md's review policy. Self needs resolved Work Items
and actual Verification; Independent and unmarked historical work need the
current matching Reviewer pass. Honor no-commit/no-merge and stage limits.

Outside Git, close/archive in the current workspace and skip Git operations.
For direct Git work, make only authorized scoped commits and archive there;
there is no worktree delivery or cleanup step.

For selected worktrees, keep authored completion work there:

1. Confirm full scope and applicable verification. Run aiw fd close <id>
   Complete from the FD worktree, then commit its archive/index/evidence there.
   A close rejection stops delivery; do not hand-edit receipts or archive manually.
2. Read workspace.json and verify the recorded parent, branch and worktree.
   Require clean FD and parent workspaces, then invoke aiw git wt local-merge
   <id> for one final squash that includes implementation and archive together.
   Only this tool-managed delivery writes to the parent.
3. The tool verifies the single-parent squash, clean FD HEAD, FD-Source and
   coordinates before removing the worktree/branch. Preserve receipts. A cleanup
   failure preserves the delivered commit and remaining resources; do not repeat
   delivery or move archive work back to the parent.

Do not proactively sync every FD. A clean parent advancing alone does not
invalidate unchanged reviewed work. On actual conflicts, use the recovery
section below and resolving-merge-conflicts for one bounded supported attempt.
Preserve uncertain state and stop. Report actual archive, delivery and cleanup
results; archive alone is not full delivery of an isolated FD.

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

Record the recovery commit, diff, commands and blocker resolution. Refresh
self-check evidence after recovery. When Independent work changed outside its
current pass, do not deliver under that old pass. With no foreign or in-flight
role, use `aiw fd request-review <id> --reason "review recovered result"`.
For an existing Pending Verification FD use request-review only when its
handoff is stale and unclaimed; otherwise honor the existing valid handoff.
Update recovery evidence before requesting review, never edit receipts, and
give the separate Reviewer the current FD, original Worker report, recovery
evidence, commit and diff. Keep the active cycle's review count and stage
limits; stop at the existing review limit rather than resetting it. Resume
normal delivery only after refreshed self-check evidence, or a required current
independent pass, covers the recovered implementation and all delivery gates pass.
Do not require a new review merely
because the parent advanced without changing the reviewed FD implementation.

After recovery, restore any required review and archive again in the same
worktree before retry. Existing archived evidence stays intact; new reports
need distinct names. Once local-merge has delivered the archive-inclusive squash,
only verified cleanup remains. Do not repeat the squash or roll back delivery.

## Local delivery versus release

A required fd-review pass gates Independent delivery; its shared implementation
review method includes actual source correctness, Standards, Spec and evidence.
Do not additionally invoke standalone code-review or add its two reviewers to
every FD. A planning design review is separate evidence, not that final pass.

Self work uses recorded self-checks. Local merge/archive is not release
approval. Only when a real release is
planned, route the release-readiness request and operational evidence to
`fd-release-gate`. Its GO/GO_WITH_RISK/NO_GO assessment is outside normal
FD local completion and never grants push, publishing or deployment authority.

## Close and archive

A Self FD with resolved Work Items and actual Verification evidence can use
`aiw fd close <id> Complete` without force or a Reviewer event. Independent
and unmarked historical FDs still require a current passed Reviewer event.
Deferred and Closed need `--reason "..."`. Native archives use
`docs/features/archive/<FD-ID>/<FD-ID>_SLUG.md`; matching reports and reviews
go into that FD directory. Archive changes the FD path and index; it does not
merge or publish code. Update a changelog only if the repository uses one. Do
not commit, push, or merge as a side effect of design or archive unless the
user authorized that action. A normal numbered FD implementation request
provides the scoped local authorization described in the default lifecycle;
design-only requests, standalone commits/reviews, and explicit stage limits do not.

An explicitly chosen PM force override remains an audited exception for skipped
required evidence. Ordinary Self completion is not an override and needs no
additional confirmation. Never describe a self-check as an independent pass.
