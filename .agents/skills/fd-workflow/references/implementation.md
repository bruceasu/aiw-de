# FD implementation

Read skills/work-management.md, repository instructions and the current FD.
The FD is the plan and execution record. Read its approved Issue when present,
relevant stable specs and nearby code/config/tests. Read a linked OpenSpec
change only when one exists and affects the work.

Use [preflight](preflight.md) for workspace selection. A single host implements
directly from the FD. For an existing delegated handoff, read [handoffs](handoffs.md)
and claim only its exact pending event. Do not invent a claim for direct work.

## Implement and record

Complete ready Work Items in dependency order. Make focused changes; preserve
scope, compatibility and package boundaries. For an unclear bug, use available
static evidence and diagnosing-bugs when needed, within existing authority.
Material missing decisions return to planning; record %% NEEDS_INPUT rather
than guessing. Continue independent ready items when one item is blocked.

Update a checkbox only when the change and required evidence exist. Keep TODO,
Verification, actual commands/results, skipped checks and residual risks in
the FD. A separate Worker report is needed only for a real machine/delegated
handoff or an explicit report request. Commit coherent reviewable changes
within authorization; small FDs may use one implementation commit instead of
one commit per Work Item.

Inspect the actual diff and trace affected contracts and call paths. After code
edits, perform the repository-authorized compile-only check. Tests, final builds,
linters, formatters, vet and network need their own authorization. An unrun
check is never passed. Requested test support follows [testing](testing.md),
with the recorded scope, execution authority and persistent budgets.

## Finish the authorized scope

Check stage limits before review, handoffs or delivery. Partial-item and
implementation-only assignments save scoped evidence and stop. No-commit and
no-merge limits remain binding; do not emit events that dispatch work outside
the request. Whole-FD readiness requires every scoped item resolved and no
blocking defect or unknown.

Before delivery, choose Self or Independent under the shared review criteria.
Self work needs recorded self-check evidence; it does not emit verification-passed.
Independent work follows [review](review.md) once for the combined FD result.

For an actual managed Worker handoff, emit implementation-ready using its
claimed source event after all FD scope and selected test support are complete.
A Self FD routes to PM/Complete as a self-check result; an Independent FD routes
to Reviewer/Pending Verification. Existing Independent Tester FDs preserve their
legacy route. With Dual evidence, the machine handoff still needs its structured
Worker report; do not pass the FD itself off as that report.

A dispatched Worker ends at its assigned handoff. Otherwise the host continues
[delivery](delivery.md) automatically within the full implementation request.
