---
name: fd-workflow
description: Plan and implement numbered Feature Designs, using self-checks for routine changes and independent review when risk or the user requires it. Continue authorized local delivery and archive within explicit stage limits.
---

# Feature Design Workflow

Use for creating, refining, implementing or closing a numbered FD. The FD is
the engineering plan and progress record. Questions and design discussions
do not start implementation. OpenSpec supplies stable capability requirements;
create a change only when requested.

## Start with the current FD

Read [dependencies](references/dependencies.md), repository instructions,
skills/reviewed-skill-contract.md and skills/work-management.md. Declare
skills/workflow-methods.md; load only a relevant method. Declare
skills/testing-methods.md; load it only for requested test support.

Resolve the explicit FD ID or a unique linked FD. Before writing a plan, read
[preflight](references/preflight.md) and select the workspace: outside Git,
stay in the current workspace; inside Git, stay there unless the user requests
a worktree or explicitly selects auto. Explicit auto in Git runs planning,
implementation, review, evidence and archive in the worktree. Ordinary automatic
continuation does not itself select auto. For new isolated work use
aiw fd new "title" --worktree [--issue ID]; otherwise omit --worktree.
Read the FD and relevant Issue, code and
stable specs. For an existing isolated FD, use its verified workspace.json to
locate the design before looking only on the parent; run FD commands there.
An archived Complete FD is done only when no recorded delivery/cleanup remains.
If its worktree record remains, continue that final management step; do not
restart implementation or redispatch completed roles.

## Default flow

1. **Plan.** State the problem, scope and acceptance, necessary Work Items,
   planned checks and unresolved questions. Small changes need a short plan.
   Read [planning](references/planning.md) when shaping a new design.
2. **Implement and self-check.** Read [preflight](references/preflight.md) and
   [implementation](references/implementation.md). Use the recorded workspace,
   make focused changes and inspect the diff. Follow the repository's compile
   budget. Keep progress, actual evidence, skipped checks and risks in the FD's
   TODO and Verification. Commit coherent changes; a small FD may use one
   implementation commit. A separate Worker report is not normally needed.
3. **Review when needed.** Before delivery, use the review policy below.
   Routine Self work proceeds after self-checks. When independent review is
   required, read [review](references/review.md) and obtain one separate
   Reviewer verdict. Handoffs and individual Work Items do not trigger review.
4. **Deliver and archive.** Read [delivery](references/delivery.md). Within a
   full implementation request, continue completion automatically when applicable
   gates pass. In a selected worktree, archive and commit there before final
   squash delivery and verified cleanup. Outside Git, skip Git operations. Preserve explicit
   planning-only, implementation-only, partial-item, no-commit and no-merge limits.

The host normally combines planning, implementation and workflow management.
Separate role prompts and stage events are needed for an actual delegated
handoff or configured runner, not for each change of responsibility in one
session. Keep existing receipts truthful; never take over a foreign or unknown
in-flight role. Read [handoffs](references/handoffs.md) when one exists.

## Review policy

New FDs declare **Review policy:** Self. Use Independent for material public
interface, permission/auth, persistence/migration, concurrency or cross-module
effects, uncertain impact, or an explicit user review request. Record a short
reason in Verification; no separate risk report is needed. Old FDs without the
field and legacy Independent Tester FDs retain independent review.

Independent review happens once before delivery. Reviewer uses a separate
session and checks the actual diff with fd-review. Rechecks focus on findings,
fixes and affected behavior. Retain the existing three-failure cap across
resumes; stop earlier when no safe actionable repair exists. Self-checks never
produce a Reviewer pass or satisfy an already-required independent review.
Design review is additional work only when explicitly requested or a concrete
unresolved design question needs it; risk alone does not add a second review.

## Evidence and boundaries

Record routine evidence and blockers in the FD, using %% NEEDS_INPUT for a
blocking unknown. Existing or explicitly selected Dual evidence retains its
Markdown/JSON contract; do not rewrite historical reports. New ordinary FDs
use Markdown evidence. Read [blockers](references/blockers.md) for external
feedback records or unresolved delivery recovery.

Tests, final builds, network, push, publication and deployment need their own
authorization. Requested test support loads [testing](references/testing.md);
it adds no default test state or coverage gate. Report actual commands and
results, skipped checks, remaining risks, review type and delivery/archive
state. Local completion is separate from release approval.

For command lookup use [portable operations](references/portable-operations.md).
Read [delivery recovery](references/delivery.md#recover-a-failed-local-delivery)
only after a delivery failure, and [platforms](references/platforms.md) for host
capability questions. If CLI/contracts cannot support the selected route,
preserve state and report the mismatch; do not invent receipts or use force
as a substitute for ordinary Self completion.
