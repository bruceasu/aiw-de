# Numbered FD operations

The portable layout is `docs/templates/TEMPLATE.md`,
`docs/features/FEATURE_INDEX.md`, `docs/features/FD-XXX_SLUG.md`, and
`docs/features/archive/<FD-ID>/<FD-ID>_SLUG.md`. The FD file owns status and
numbered Work Items. Active reports and reviews live under
`docs/features/reports/` and `docs/features/reviews/`; close moves the FD's
evidence into `archive/<FD-ID>/reports/` and `archive/<FD-ID>/reviews/`.
The index is rebuilt by `aiw fd` after stage changes. Do not create a second
status store or require a vendor-specific agent command.

## Create

Run `aiw fd new "title" [--issue ID]`. The command allocates the next number
across active and archived files, creates a Design FD from the template, and
records a Planner handoff. `--issue` requires an approved Issue record. Do not
invent an Issue ID or create an OpenSpec change unless requested.

## Explore and design

Read the FD, project guidance, relevant code, stable specs, and earlier FDs.
Compare meaningful options and record the chosen approach and reason. Use
`%% NEEDS_INPUT` for material gaps. Planner emits `design-ready` only after the
FD contains a solution, numbered Work Items, acceptance, and Verification.

`aiw fd list` shows the index and `aiw fd show FD-001` shows one FD and its
last handoff. If an event is pending, use the named role Skill or `aiw fd
resume`. If it is launching or dispatched, inspect the original process,
session, or log before trying to continue.

## Implement and review

Worker implements all ready items in order, updates the FD with real progress,
and emits `implementation-ready` with an implementation report. Independent
Reviewer emits `changes-requested` with findings or `verification-passed` with
its report. Pass `--source-event` when completing a dispatched handoff. A
business decision uses `needs-decision` and later `decision-recorded`.

Run only checks allowed by the repository. Local focused commits are allowed
under the project rules; commits do not authorize merge, push, or release.
Every numbered FD uses an isolated worktree before implementation starts; this
keeps parallel FD work from sharing writable files. An informational or
design-only discussion does not create a worktree. Capture the current branch,
commit the ready FD plan on the parent branch, and make sure the parent
workspace is clean before creating the worktree with
`aiw git wt add FD-001`. Commit unrelated parent changes separately; do
not mix them into the FD plan commit. Keep the parent clean while the FD is in
flight when possible, and wait to merge if it becomes dirty. The command records the FD ID,
parent branch, feature branch, and worktree path in
`.ai/fd/FD-001/workspace.json`. Read and verify that record immediately after
creation; use its parent branch as the merge target. Stop if the record is
missing or inconsistent. Use `aiw git wt status FD-001` to inspect both worktrees.

Use `aiw git wt local-merge FD-001` for squash delivery after review. Content
conflicts are recovered in the FD worktree and require an explicit retry.
Successful delivery validates the squash source and removes the FD worktree
and branch while preserving `.ai/fd/` receipts. Never create a Task to satisfy
a Task-only command. Archive only after delivery succeeds. Commit each completed
Work Item separately before the Reviewer handoff. Do not rebase either branch.

## Auto

`$fd-workflow auto` is a host Skill operation, not an `aiw fd auto` CLI
command. Follow `../SKILL.md`: one numbered FD, exact handoff claims, an
independent Reviewer session, and at most three Reviewer outcomes across
resumes of the same implementation cycle. New FDs go directly from Worker to
Reviewer after compile-only and static checks. Optional tests use `$fd-test`
only when requested and do not gate review. Existing Tester events remain a
legacy CLI capability; Auto does not dispatch or evaluate them. The host may
combine PM, Planner, and Worker, but it must not supply independent Reviewer
evidence. With an isolation trigger, squash-deliver the reviewed FD result to
its recorded parent before closing and archiving. Stop without merge or archive
when a review, parent state, or merge gate fails.

## Close

Use `aiw fd close FD-001 Complete` only after a passed review and, in isolated
mode, a successful merge to the recorded parent. Deferred and
Closed require `--reason "..."`. Close archives the file and updates the
index. It does not clean a worktree or deliver code. Update a project changelog
only when the repository uses one.
