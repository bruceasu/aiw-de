# AIW Work Management Contract

Use this contract for AIW engineering Skills.

This file is the canonical shared contract. A Skill that needs these rules
should reference `skills/work-management.md` in its `SKILL.md` and keep its
own instructions focused on its domain. Do not copy lifecycle rules into each
Skill. `aiw skills install` and `sync` materialize this file beside the Skill
when that reference is present; treat the installed copy as generated output.
The same applies to `skills/reviewed-skill-contract.md`, so user-level
installations remain usable outside the AIW repository.

## New FD workflow

- FD-first is the normal path for new engineering work. OpenSpec supplements
  it with stable capability requirements; it does not replace the FD plan.
- A numbered file under `docs/features/FD-XXX_SLUG.md` is the source of truth
  for design, Work Items, status, Verification, and unresolved `%%` notes.
- `docs/features/FEATURE_INDEX.md` is an index. Rebuild it from FD files; do
  not treat it as a second state owner.
- An Issue may link to an FD. An AIW Task is not required for a new FD.
- `aiw fd` is the FD command surface. Read `aiw fd --help` before an unfamiliar
  or mutating operation. `aiw fd emit` records an explicit handoff; ordinary
  file saves and Git commits do not dispatch roles.
- `.ai/fd/<fd-id>/` contains dispatch receipts and logs. It does not own FD
  status or replace the Markdown plan. Never write a fake event or review.
- `docs/features/reports/` and `docs/features/reviews/` contain authored
  evidence while an FD is active. `aiw fd close` archives its design at
  `docs/features/archive/<FD-ID>/` and the FD's evidence in that directory's
  `reports/` and `reviews/` subdirectories.
  Under `**Evidence policy:** Dual`, each new report is Chinese Markdown for
  people plus same-basename JSON for CLI/AI. The Markdown contains one
  `<!-- aiw-data: <same-basename-json-file> -->` comment; JSON names its
  Markdown, FD, evidence kind, and source event. Emit the Markdown path as
  `--artifact`. Both files are archived together. Older Markdown-only
  evidence remains historical.
  Prior evidence stays archived if a completed FD is reopened for review.
- An automatic FD workflow Gate has a separate FD-prefixed blocker feedback
  Markdown report and same-basename JSON sidecar under `docs/features/reports/`.
  Use `docs/templates/BLOCKER_FEEDBACK_TEMPLATE.md` and its JSON template.
  Record observed facts, unknowns, attempts, unresolved or resolved state,
  human decision needs, and reusable improvements. Review existing feedback
  on resume and before archive. FD-prefixed pairs follow the normal `reports/`
  archive move; they do not alter an active FD handoff or authorize recovery.
- Legacy compatibility only: `aiw fd refresh-tester <id> --reason <text> --artifact <report>` replaces
  only a stale unclaimed pending Tester handoff for an independent Pending Test
  FD. It retains Worker identity and implementation provenance, validates the
  current report, and creates a PM-produced `test-requested` event. Tester
  claims that event and cites its ID in reports and new test authorization;
  refreshing grants no permission to execute tests or reuse old approvals.
  New/default FDs do not use this route.
- `aiw fd reopen <id> --reason <text>` returns an archived `Closed` or
  `Deferred` FD to active `In Progress` with a fresh Worker handoff. It keeps
  prior close metadata and evidence. Archived `Complete` uses `request-review`.
  New reports and reviews need filenames distinct from archived evidence.
- `aiw fd refresh-worker <id> --reason <text>` replaces a stale pending Worker
  handoff for an active `Open` or `In Progress` FD. It cancels the old receipt,
  records supersession, and creates a digest-bound `work-requested` event.
  Claimed or in-flight handoffs cannot be replaced.
- `aiw fd recover-worker <id> --expected-event <event-id> --expected-session
  <session-ref> --reason <text>` is an explicit PM recovery for a latest
  dispatched Worker session that PM has confirmed stopped. It records the
  abandoned session and reason, cancels the old receipt, and creates a new
  pending Worker handoff. The new Worker claims that event under its own
  session. Normal resume and refresh rules remain unchanged.
- In the normal workflow, when a pending Worker handoff has expired after
  a long pause, refresh it automatically when the workflow resumes; do not wait
  for human intervention. Use `refresh-worker`, preserve its required reason
  and provenance, and continue from the new handoff. A stale legacy Tester
  handoff is not refreshed automatically; preserve it unless the user requests
  the legacy CLI path. Do not treat elapsed time alone as proof that
  a claimed or in-flight handoff expired; inspect its recorded session or log
  before deciding how to recover it.
- OpenSpec owns stable capability specs in `openspec/specs/`. Create an
  OpenSpec change only when the user explicitly asks for one.

## Roles and handoffs

PM owns workflow state and human decisions. Planner turns approved intent into
options, decisions, acceptance, and ordered Work Items. Worker implements the
ready scope and reports actual evidence and limits. Reviewer independently
checks the current diff against the FD and records actionable findings or a
pass. Each handoff names one role, one source event, and an existing artifact;
the receiver checks that the artifact and FD revision still match before work.

New/default FDs omit `**Test policy: Independent**` and route
`implementation-ready` directly to Reviewer. The Worker performs the
repository-authorized compile-only check and static review; tests are not a
default workflow stage. Users may invoke the standalone `$fd-test` Skill to
derive black-box scenarios and, when explicitly authorized, write or execute
tests under root `tests/`. Its factual report does not change FD state, add
acceptance thresholds, or go to PM, assessors, or Reviewer for evaluation.
Existing FDs carrying the Independent marker and their Tester events retain
their legacy CLI contract; this does not make Tester a default stage for new
work.

The host may combine PM, Planner, and Worker duties, but must not author
independent Reviewer evidence from its own session. Optional test reports are
not workflow handoffs or Reviewer inputs.
PM may explicitly
override workflow gates; the record must identify skipped stages and missing
evidence, which remain unperformed rather than being represented as passed.
A failed review returns concrete, actionable findings to Worker. Material
human decisions and missing execution authorization stop only the affected
stage; continue independent work that does not depend on them.

Stage-specific role prompts live under `skills/fd-workflow/roles/`. Load only
the prompt for the role handling the current event; the contract here remains
the shared source for lifecycle and authorization rules.

Use an explicit stage event after the producing role has written its result.
The event must name an existing project-relative artifact. If the host has a
configured role runner, AIW starts the next role. Otherwise the event stays
pending for a Skill or later `aiw fd resume`. Before a host session writes for
a pending handoff, claim its exact event with `aiw fd claim <id> <event-id>
--session <host-session-id>`; include that event in `--source-event` at the
next handoff. Do not redispatch an unknown in-flight result; inspect the
recorded session or log first.

FD Work Item checkboxes record authored progress. Mark one complete only when
the required change and real evidence exist. Report checks that were not run.
Do not mark the FD Complete until all scoped items are resolved and the
Reviewer has recorded a passed result.

PM has final authority over the FD workflow and may explicitly override its
normal gates. PM may directly change an FD's status, waive any test or
verification stage, and accept without supporting test or review evidence.
PM may make any decision needed to advance the FD regardless of whether a
handoff is missing, stale, expired, or otherwise incomplete. PM may bypass or
supersede that handoff and directly change the FD's status without Reviewer
or other role approval. Keep the FD record truthful: skipped stages,
missing or stale handoffs, and absent evidence remain identified as such, and
must never be represented as performed or passed. Do not invent a handoff,
receipt, report, or verification result to make an override appear to have
followed the normal route.

## Workspace and Git

Use the primary workspace for FD design and planning. By default, every numbered
FD moves to its isolated worktree before implementation starts, so parallel FD
work does not share writable files. A user may explicitly direct implementation
in the primary workspace; treat that as an alternate workspace choice for that
FD, not as an implicit change to the default. A question or design-only
discussion does not start implementation and does not create a worktree. Legacy
Tasks keep their existing Task worktree policy.

For an explicitly authorized primary-workspace implementation, do not create a
branch, worktree, or `.ai/fd/<fd-id>/workspace.json`. Do not claim or invent a
Worker receipt when no handoff exists. Keep the FD itself current: check off
only completed Work Items, update TODO, and record actual static/compile
evidence and skipped checks in Verification. Inspect the primary-workspace diff
before handoff. File edits do not advance the FD lifecycle status automatically.
At completion, PM must explicitly route the active FD to review or archive it
under an explicit override. Primary-workspace authorization alone does not
authorize Git commits, tests, builds, deployment, or archive; follow the
repository budget and any separate user authorization.

The ordinary completion gate still applies in either workspace: a separate
Reviewer must record a pass before normal Complete archive. For a direct
implementation with no Worker handoff, use the managed FD status/review CLI
route instead of fabricating lifecycle evidence. If PM explicitly overrides
the gate, record the skipped review and missing evidence in the reason and use
`aiw fd close <id> Complete --force --reason "..."`; the operation audit is
the override record. Never describe skipped review or checks as passed.

Use `feature/<fd-id>` and `.wt/<fd-id>` for an isolated FD. Determine the
current branch before creation. Before creating the worktree, commit the ready
FD plan on the parent branch and make sure the parent workspace is clean. Do
not mix unrelated changes into the FD plan commit; if other parent changes are
uncommitted, resolve and commit them separately before proceeding. Keep the
parent clean while the FD is in flight when possible; if it becomes dirty,
wait to merge until it is clean again. These focused commits are part of the
default FD implementation workflow.
`aiw git wt add` records `fd_id`,
`parent_branch`, `branch`, and `worktree` in `.ai/fd/<fd-id>/workspace.json`.
Before creating the worktree, ensure `.wt/` and `.ai/` are ignored by Git;
`wt add` checks both paths and rejects missing rules before changing Git state.
Read that file immediately after creation and verify all four values; use its
`parent_branch` as the sole merge target. Stop if the file is missing or does
not match the created worktree. Use `aiw git wt status <fd-id>` for both worktrees,
`aiw git wt commit <fd-id> "message"` for focused commits, and
`aiw git wt local-merge <fd-id>` for squash delivery after review. It creates one
single-parent commit on the recorded parent branch with an `FD-Source` trailer
naming the delivered FD HEAD; individual Work Item commits remain on the FD
branch. If delivery finds a content conflict, `local-merge` resets the failed
parent squash and merges the parent into the FD worktree for resolution; commit
there and rerun `local-merge` to deliver. After verified delivery, the command
removes the recorded clean FD worktree and branch automatically. Before Git
removes a worktree `.ai` junction, it verifies that the target is the primary
workspace `.ai` and removes only the link itself. It preserves FD receipts; if
a cleanup check or step fails, it reports the state and preserves resources
not already safely removed. Do not create a Task to satisfy a Task-only
command. Keep each FD's writing role in one workspace at a time and do not mix
unrelated files into its commits.
Commit each completed, independently reviewable Work Item before starting the
next, staging only its files. Commit final FD evidence before handoff to
Reviewer. Do not rebase either branch as part of the FD workflow. Successful
`local-merge` performs worktree and branch cleanup only after checking that
parent history contains this FD's single-parent squash commit and its
`FD-Source` trailer equals the current clean FD HEAD. The FD branch is not an
ancestor after squash, so use `git branch -D` only after that check.

The user permits local Git commits for the new FD workflow. A Worker may make
a focused commit for an independently reviewable slice. A Reviewer checks a
specific commit or diff. When an isolation request asks for the complete
worktree lifecycle, it authorizes local commits, squash-delivering that FD
result to its recorded parent after a passed review, and the verified cleanup
that `local-merge` performs. It authorizes archiving after delivery succeeds,
but not push, release, or deployment.
For other requests, commit does not authorize delivery or archive. A squash conflict
or dirty parent workspace is a stop condition; preserve the worktree and active
FD until the parent is clean. Follow any narrower local rule.

## Validation

Static review is the default. After code edits, run one compile-only check
using a repository `compile*` script when available. Tests, final builds,
formatters, linters, vet, network calls, and deployment follow the repository's
authorization rules. Report actual commands, skipped checks, and risks.
