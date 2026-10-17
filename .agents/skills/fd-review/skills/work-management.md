# AIW Work Management Contract

Use this contract for AIW engineering Skills.

This file is the canonical shared contract. A Skill that needs these rules
should reference `skills/work-management.md` in its `SKILL.md` and keep its
own instructions focused on its domain. Do not copy lifecycle rules into each
Skill. `aiw skills install` and `sync` materialize this file beside the Skill
when that reference is present; treat the installed copy as generated output.
The same applies to `skills/reviewed-skill-contract.md`, the stage-specific
sections of `skills/workflow-methods.md`, and opted-in `skills/testing-methods.md`, so independently installed Skills
remain usable outside the AIW repository. Declare every needed shared resource
in the entry SKILL.md; references alone are not scanned by the installer.

## New FD workflow

- FD-first is the normal path for new engineering work. OpenSpec supplements
  it with stable capability requirements; it does not replace the FD plan.
- A numbered file under `docs/features/FD-XXX_SLUG.md` is the source of truth
  for design, Work Items, status, Verification, and unresolved `%%` notes.
- `docs/features/FEATURE_INDEX.md` is an index. Rebuild it from FD files; do
  not treat it as a second state owner.
- An approved Issue may link to its numbered FD; the FD is the engineering plan.
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

## Stage methods and ownership

Issue owns business scope and child approval; Planner owns the sole FD design
and engineering Work Items. Use only the current stage's section of
`skills/workflow-methods.md`, not a second checklist or ticket mapping.
Domain clarification supplies definitions, concept/state/rule distinctions and
examples; snapshot Requirement mode returns proposals without stable writes.
For large uncertain scope, keep decision dependencies in the owning Issue Plan
or FD and use existing grilling methods for discussion. No standalone map or
wayfinder stage remains. Triage sorts incoming user requests and next actions;
its brief is input, not software approval, FD readiness or a PR merge verdict.
Keep user goals linked to software acceptance and engineering decisions.
Planner loads fd-workflow's planning reference, collaborates on stable specs
only when settled capability rules change, and obtains conditional independent
design feedback for material design decisions. Design evidence does not emit
implementation verification events. A design-ready gate still requires all
material decisions resolved. No standalone legacy ticket stage remains.

fd-review owns the current FD's independent implementation verdict, reviewing
actual code/contracts, Standards, Spec and evidence with the shared method.
Standalone code-review uses the same method for branches/PRs without invoking
FD lifecycle operations. Loading methods does not spawn extra agents or add
review rounds. Do not run both full workflows for every FD.

Local completion is distinct from release readiness. fd-release-gate
assesses operational evidence only for a planned release; GO is not deployment
permission and the release assessment is not a gate on ordinary local archive.

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
default workflow stage. Explicit test-support intent in any wording/language
(for example with test or 带测试) selects suitable core-rule TDD and an independent
fd-test round before implementation-ready, following fd-workflow's testing
reference. More specific cases-only/no-TDD/no-execution/stage limits prevail.
Standalone fd-test remains available. Assigned tests belong under root tests/.
Test selection requests the subflow, not unrestricted execution; exact command,
revision, side-effect and local language restrictions still apply. No new
Tester event, state, coverage threshold or PM test vote is introduced. Relevant
test facts and confirmed defects may be read by Worker/PM/Reviewer.
Existing FDs carrying the Independent marker and their Tester events retain
their legacy CLI contract; this does not make Tester a default stage for new
work.

The host may combine PM, Planner, and Worker duties, but must not author
independent Reviewer evidence from its own session. Optional test reports are
supporting evidence, not workflow handoffs or independent delivery verdicts.
Their absence is not a default gate. Explicitly requested but incomplete scope
must be recorded, never silently waived. When authorization is required,
record the exact command and current revision before execution. Planned TDD
Red/Green phases need authority; expected Red is not an unexpected repair retry.
After the local unexpected-failure budget is exhausted (here one focused run
and at most one rerun after relevant change), or no safe repair/cause exists,
automatically stop affected test repair/execution and perform bounded static
diagnosis-only, then continue independent work. Preserve failures, versions,
ledger and unresolved behavior across resume/review; new commands/sessions do
not reset the budget. Restart requires explicit new direction and valid
authority. Confirmed defects, material unknowns and incomplete implementation
still block whole-FD readiness and delivery. Environment failure alone does not
prove a product defect; testing incomplete must remain explicit even if other
evidence supports behavior. Never fabricate full-FD readiness to obtain partial
review, weaken assertions or mark unrun checks passed.
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
discussion does not start implementation and does not create a worktree.

For an explicitly authorized primary-workspace implementation, do not create a
branch, worktree, or `.ai/fd/<fd-id>/workspace.json`. Do not claim or invent a
Worker receipt when no handoff exists. Keep the FD itself current: check off
only completed Work Items, update TODO, and record actual static/compile
evidence and skipped checks in Verification. Inspect the primary-workspace diff
before handoff. File edits do not advance the FD lifecycle status automatically.
At completion, the host PM continues the default review and archive path below;
do not treat the workspace choice as an implementation-only limit. An explicit
FD implementation request includes focused local commits and normal archive
after a current independent pass, unless the user narrows the stage or Git
scope. A workspace choice alone grants no implementation or runtime authority.
Follow the repository budget and preserve unrelated primary-workspace changes.

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
`aiw git wt local-merge <fd-id>` for squash delivery after review. Do not
proactively sync the parent into every FD before review or delivery. A clean
parent advancing alone does not invalidate a review of unchanged FD work.
Only investigate parent synchronization when delivery fails and the diagnosed
cause calls for it. It creates one
single-parent commit on the recorded parent branch with an `FD-Source` trailer
naming the delivered FD HEAD; individual Work Item commits remain on the FD
branch. If delivery finds a content conflict, `local-merge` resets the failed
parent squash and merges the parent into the FD worktree for resolution.
Pause delivery at that conflict and load `resolving-merge-conflicts` for one
bounded recovery attempt under the full FD lifecycle authorization. Resolve
only conflicts whose intent is supported by the FD and both histories. If any
conflict is uncertain or cannot be safely resolved, preserve the merge state,
remaining unmerged entries and any clearly resolved edits; report the blocker
and stop without abort, reset, blind side selection or retry. Commit only
a fully inspected, resolved and in-scope merge. Explicit stage limits prevail.
`wt commit` stages all changes; it does not resolve conflicts semantically.
Do not repeat `sync` when CLI recovery already started the parent merge. If
a separate sync is needed after diagnosis, use `aiw git wt sync <fd-id>
<parent_branch>` with the recorded parent explicitly. Preserve full command
output and inspect both workspaces and any merge in progress before recovery.
If recovery changes implementation beyond the current review, obtain a fresh
independent review through the managed route in `fd-workflow` before retrying
delivery; retain historical evidence and the active cycle review count.
After verified delivery, the command
removes the recorded clean FD worktree and branch automatically. It does not
archive the FD; the host must separately close and commit the archive. Before Git
removes a worktree `.ai` junction, it verifies that the target is the primary
workspace `.ai` and removes only the link itself. It preserves FD receipts; if
a cleanup check or step fails, it reports the state and preserves resources
not already safely removed. Keep each FD's writing role in one workspace at a time and do not mix
unrelated files into its commits.
Commit each completed, independently reviewable Work Item before starting the
next, staging only its files. Commit final FD evidence before handoff to
Reviewer. Do not rebase either branch as part of the FD workflow. Successful
`local-merge` performs worktree and branch cleanup only after checking that
parent history contains this FD's single-parent squash commit and its
`FD-Source` trailer equals the current clean FD HEAD. The FD branch is not an
ancestor after squash, so use `git branch -D` only after that check.

## Default completion and authorization

A request to implement a numbered FD authorizes that FD's normal local
lifecycle: prepare and commit its ready plan, create its isolated workspace,
make focused implementation/evidence commits, obtain independent review,
squash-deliver to its recorded parent, use the CLI's verified cleanup, and
archive Complete with a focused archive/index commit after delivery succeeds.
The host PM MUST continue these steps when their gates pass; do not stop at a
passing review merely to request another `auto` command or merge confirmation.
`fd-workflow auto` remains an explicit entry to the same lifecycle.

A request limited to planning, implementation only, review only, selected Work
Items, or no merge MUST retain that limit. Completing a partial scope is not
permission to complete or archive the whole FD. A design-only discussion does
not start implementation. A standalone commit or review is not a delivery
request. More specific user limits and applicable repository rules prevail.
Check stage limits before emitting any next-role event, invoking a runner, or
using a managed review command, not only before merge. Save scoped evidence
and report an intentional stage stop without dispatching beyond it. A no-merge
limit can permit review but cannot authorize delivery.
An implementation-ready event requires the whole FD implementation scope to
be complete. Separately authorized review of an unfinished FD's partial scope
must not emit a full-FD completion/review handoff or mark that FD ready.

Before delivery, require all FD scope complete, no unresolved blocking question
or feedback, a current independent passed event matching FD revision/digest,
no implementation drift since review, correct recorded coordinates, and clean
FD and parent workspaces. Required evidence remains real; a skipped check
cannot be labelled passed. In an explicitly selected primary workspace, skip
worktree creation and merge; commit only scoped changes, obtain independent
review through the supported managed route, then archive and commit that FD.
If unrelated changes prevent a safe focused commit or clean completion, stop
the affected step without staging or discarding them.

The host PM owns continuation after role handoffs. A dispatched Worker or
Reviewer remains confined to its assigned role; never take over an in-flight
or foreign claim, fabricate a handoff, or self-review. Missing decisions,
required role capability, stale evidence, a dirty parent, failed command,
unresolved merge conflict, or archive rejection stop the affected automatic
stage. Preserve state and report the exact gate. CLI conflict recovery is
unchanged; the host may use the bounded `resolving-merge-conflicts` attempt
above before reporting an unresolved conflict. Retry delivery only after the
cause is resolved and a current independent pass covers the recovered result. Record blocker feedback under the
fd-workflow procedure. Successful delivery followed by cleanup/archive failure
is reported as partial completion, never rolled back or described as complete.

This authorization does not include push, release, deployment, tests, final
builds, network/downloads, privilege changes, or bypassing workflow gates.
Follow existing validation budgets and explicit stage limits.

## Validation

Static review is the default. After code edits, run one compile-only check
using a repository `compile*` script when available. Tests, final builds,
formatters, linters, vet, network calls, and deployment follow the repository's
authorization rules. Report actual commands, skipped checks, and risks.
