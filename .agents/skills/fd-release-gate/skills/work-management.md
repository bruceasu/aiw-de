# AIW Work Management Contract

This is the canonical shared contract for AIW engineering Skills. Reference
skills/work-management.md rather than repeating lifecycle rules in each Skill.
The installer materializes this file and declared shared reviewed-skill,
workflow-methods and selected testing-methods resources. Installed copies are
generated output; edit the canonical sources.

## FD and evidence

New engineering work is FD-first. A numbered design under
docs/features/FD-XXX/FD-XXX_SLUG.md owns scope, decisions, Work Items, status,
TODO and Verification. An approved Issue supplies confirmed business intent.
OpenSpec supplies stable capability specs; create a change only when requested.

Each FD owns index.md and its evidence. FEATURE_INDEX.md is fixed navigation;
aiw fd list derives current status. Never maintain a shared mutable status
table. Use aiw fd new to allocate a number. Read help before an unfamiliar
mutating CLI operation. Existing flat layouts remain readable.

Keep routine progress, self-check evidence, skipped checks, risks and blockers
in the FD. A separate Worker report is needed only for a real delegated/machine
handoff or explicit report request. Do not duplicate the same record across
artifacts. New routine FDs use Evidence policy: Markdown. Existing or explicitly
selected Evidence policy: Dual retains Chinese Markdown plus a same-basename
JSON sidecar, one aiw-data reference, matching FD/kind/source event/Markdown
fields, and the existing machine validation contract. Preserve historical
reports unchanged. Repository-specific report requirements still apply.

Record a blocking unknown with %% NEEDS_INPUT. Ordinary blocker notes belong
in TODO or Verification. Use separate feedback reports only when requested,
required by a machine consumer/repository policy, or needed for substantial
delivery-recovery evidence. Record actual attempts, unknowns, state and next
actions; update resolutions without erasing useful failure history. Review
existing blocker records on resume and before archive.

## Normal execution

The host normally combines Planner, Worker and PM responsibilities:

1. Write a proportionate plan: problem, scope/acceptance, necessary numbered
   Work Items, planned checks and unresolved questions. Add options, detailed
   interfaces or compatibility analysis only when they resolve a real decision.
2. Implement ready scope and self-check the diff, affected contracts and call
   paths. Keep Work Items, TODO and Verification current. After code edits,
   perform the repository-authorized compile-only check.
3. Before delivery, apply the review policy below. Routine work uses recorded
   self-checks; required independent review uses one separate Reviewer.
4. Within a full implementation request, continue authorized local delivery,
   verified cleanup and Complete archive automatically when applicable gates
   pass. No second auto or merge confirmation is needed.

Small FDs may have one implementation commit. Commit coherent reviewable
changes; there is no mandatory commit per Work Item. Stage only scoped files.
Never claim completion of partial scope or label skipped checks passed.

Use only relevant workflow-methods sections. Domain clarification and design
methods supply decisions inside the owning FD/Issue; they do not add a second
plan, mandatory review round or agent dispatch. A design review is additional
work only when explicitly requested or a concrete unresolved design question
needs independent feedback. Material questions still need resolution before
implementation; final review does not authorize guessing.

## Review policy

New FDs declare **Review policy:** Self. Use **Review policy:** Independent for
material public-interface/compatibility, permission/auth, persistence/migration,
concurrency or cross-module effects, uncertain impact, or an explicit review
request. Assess the actual change before delivery and record a short reason
in Verification. No separate risk assessment report is needed. Historical FDs
without a review policy and legacy Independent Tester FDs keep independent review.

A handoff, role change or completed Work Item does not trigger review.
Independent review occurs once for the combined implementation before delivery.
Reviewer must use a separate session, inspect actual code/contracts and evidence,
and author a concise findings/pass report. The FD itself can supply Worker
evidence. fd-review owns that verdict; do not also invoke standalone code-review
or additional reviewers by default. Apply shared correctness, standards, spec
and evidence methods. Self-checks never generate a Reviewer pass.

For direct work, aiw fd request-review accepts a ready Open/In Progress FD,
sets Independent/Pending Verification and creates the Reviewer handoff.
For managed work, implementation-ready follows the recorded review policy.
Required independent completion needs the current unforced Reviewer pass,
matching FD revision/content, with no implementation drift. Missing capability
blocks that review; never replace independence with same-session findings.

Repairs address concrete findings. Rechecks focus on fixes and affected behavior.
Retain history and the three-failed-review cap across resumes; stop sooner if
no safe actionable repair exists. Changing sessions does not reset budgets.
Unresolved defects and material unknowns block either completion route.

Self FDs may close Complete with resolved Work Items and actual Verification
evidence, without Reviewer receipts or force. Old/Independent FDs still require
a current pass. Do not silently downgrade an already-required independent
review. Explicit PM overrides remain separately audited exceptions: skipped
evidence is missing, never performed or passed.

Recorded Reviewer requests and outcomes keep that FD's independent-review gate
across Worker refresh/recovery. Changing the policy text to Self does not erase
that history; an explicit force override remains separate from normal completion.

## Optional tests

Tests are not a default stage. New FDs omit Test policy: Independent. Explicit
test-support intent selects suitable core-rule TDD and independent fd-test
support before whole-FD readiness, respecting narrower cases-only, no-TDD,
no-execution and stage limits. Load only needed testing-methods sections and
fd-workflow testing guidance. Test selection grants no unrestricted execution.
Assigned test code belongs under root tests/.

Keep exact command/revision authorization, side-effect and language limits,
actual versions/results and finite retry budgets. Do not add a Tester state,
coverage threshold or PM test vote to new work. Test evidence supplies facts;
confirmed acceptance defects and material unknowns cannot be ignored.

After the authorized unexpected-failure budget is exhausted, stop affected
execution/repair, diagnose statically and continue independent work. Preserve
failures, ledger and diagnosis-only state across resumes. Planned authorized
TDD phases are distinct from unexpected retries. Missing execution authority
and incomplete requested scope remain explicit. Environment failure alone
does not prove a product defect.

Existing Test policy: Independent FDs retain their legacy Tester/PM events and
validation, including refresh-tester. Ordinary resume does not silently select
or restart that path. A Tester handoff is not execution approval.

## Managed roles and recovery

Without a configured runner or existing handoff, aiw fd new creates a Design
record and the host works directly from it. Set Open when the plan is ready
and In Progress while implementing. Do not create events or claims just to
change responsibilities in the same session. Explicit --handoff or a configured
AIW_FD_ROLE_RUNNER selects managed dispatch.

For actual handoffs, .ai/fd/<id>/ holds real receipts/logs, not a second plan.
Each event names one role, exact source event and existing artifact. Only an
explicit stage operation dispatches work; file saves and commits do not.
Claim only the exact pending event in the receiving session, cite it on the
result, and honor runner ownership. Never hand-edit receipts, take over a
foreign/unknown session, or restart launching/dispatched work.

Inspect session/log evidence before recovery. Refresh only stale unclaimed
Worker handoffs with refresh-worker. Recover a confirmed-stopped dispatched
Worker with recover-worker and its exact event/session. Preserve supersession
history. Legacy Tester refresh remains explicit. Closed/Deferred archives use
reopen; Complete archives use request-review. Preserve old evidence and use
distinct report names. Read fd-workflow handoff guidance for these rare paths.

## Workspace, delivery and archive

Select the workspace before writing an FD plan:

- Outside Git, stay in the current workspace. Do not initialize Git, create a
  worktree or run commit/merge/cleanup operations. Keep FD/runtime evidence local.
- Inside Git, stay in the current workspace by default. A worktree requires
  explicit user intent to use one or an explicit auto request.
- Explicit auto inside Git runs the entire authorized FD process in its
  worktree: planning, implementation, required review, evidence and archive.
  Ordinary automatic continuation of a full implementation request is not
  itself an auto workspace selection.

Reuse valid existing FD workspace coordinates when resuming; never abandon an
in-flight worktree or silently switch auto back to the parent. A workspace
choice grants no extra runtime/stage authority. More specific no-commit,
no-merge and stage limits prevail.

Resolve recorded worktree coordinates before looking for the FD only on the
parent. An archived Complete FD with retained workspace metadata still needs
delivery/cleanup or reconciliation; it is not fully complete. Resume reports
that management step without redispatching completed roles. Stale or unsafe
metadata blocks the affected recovery rather than silently selecting the parent.

For a new isolated FD, aiw fd new "title" --worktree allocates its ID, prepares
feature/<fd-id> at .wt/<fd-id> through aiw git wt add <id> --prepare, and writes
the FD there. No authored plan is written or committed on the parent first.
Existing committed FDs use the ordinary add command. Require a clean parent
before creation and delivery; do not commit unrelated edits. Both .wt/ and
.ai/ must be ignored. Verify fd_id, parent_branch, branch and worktree in the
recorded workspace.json. Shared coordination receipts may remain in .ai,
but authored FD artifacts and all roles stay in the selected worktree.

A full FD request authorizes its scoped local lifecycle within the selected
workspace, applicable Git/stage limits and review policy. Commit coherent scoped
changes only. Before isolated completion, require resolved scope, no blocker,
and actual self-check evidence or a current matching independent pass.
Close/archive the FD and commit its archive/index/evidence in the same worktree.
Archive-only bookkeeping does not claim another implementation review.
Then use aiw git wt local-merge for one final squash to the recorded parent;
the parent receives the implementation and archive together. The tool checks
clean workspaces, recorded coordinates, a single-parent squash and FD-Source
matching clean FD HEAD before cleanup. Keep receipts and detach only a verified
.ai link, never its target. Ordinary direct Git execution commits/archives in
the current workspace without a worktree or squash step.

Diagnose delivery errors before retry. For actual conflicts, load
resolving-merge-conflicts for one bounded supported resolution. Preserve uncertain
merges; do not abort/reset, choose sides blindly or commit partial resolutions.
Do not sync again if CLI recovery already started the parent merge. Any separately
needed sync names the recorded parent; do not rebase. Refresh self-checks after
recovery. Changed Independent work needs focused fresh independent review,
followed by re-archive in that worktree before retry.

Successful delivery with failed cleanup is partial completion: preserve the
delivered commit and remaining resources, and use supported verified cleanup
recovery. Do not repeat the squash or roll it back. A close/archive failure before
delivery preserves the worktree and stops delivery; never move archival work
into the parent to bypass it.

aiw fd close archives under docs/features/archive/<FD-ID>/ with matching
reports/reviews and sidecars. Preserve historical evidence and reject collisions.
An unarchived FD or an undelivered required squash is not full local completion.
Local completion grants no release approval; use fd-release-gate only for an
actual planned release.

## Authorization and reporting

Repository/user limits govern runtime execution. Tests, final builds, linters,
formatters, vet, network/downloads, privilege changes, push, publication and
deployment require their own authority. Report changed artifacts, actual
commands/results, unrun checks, review type, residual risks and actual delivery,
cleanup/archive state. Stop affected work at missing authority, unsafe state or
unresolved blockers; continue only independent permitted work.
