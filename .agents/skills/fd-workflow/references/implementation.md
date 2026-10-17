# FD implementation

Read `../skills/reviewed-skill-contract.md`, `../skills/work-management.md`, and
the repository instructions. Load this reference for the Worker stage after
a numbered FD is Open, or when
returning from a failed review. A user may limit work to one item; otherwise
continue through all ready items in dependency order. A normal FD implementation
request includes the shared contract's default local lifecycle; a separate
`fd-workflow auto` request is not needed after review. Preserve explicit
implementation-only, review-only, partial-item, and no-merge limits. Do not
design a missing decision during implementation.

## Read and prepare

Resolve the FD ID from the user, active session, or unique Issue link. Read
the FD, approved Issue evidence when present, relevant stable specs, code,
tests, and workspace rules. Read a linked OpenSpec change only when it exists
and affects the work. Use the FD as the sole engineering plan.

For an explicitly selected primary workspace without a Worker handoff, use the
managed status/review route in the fd-workflow entry; do not fabricate a claim
or emit implementation-ready without a claimed Worker event.

Before each item, check its outcome, dependencies, acceptance, and unresolved
`%%` notes. Use the FD's recorded workspace, isolated by default unless the
user explicitly selected primary-workspace implementation. Ensure the ready
plan is committed before creating its worktree. Stop at a real scope, authorization, or workspace
conflict. Continue independent ready items when one item is blocked.

When taking a pending Worker handoff in a host session, first claim its exact
event with `aiw fd claim <id> <event-id> --session <host-session-id>`. A role
runner dispatched by AIW has already claimed its event. Never take over an
event that names another active or unknown session.

## Implement and record

For a reported bug or an unexplained implementation failure, use diagnosing-bugs
when its bounded evidence method would clarify the cause. Start from preserved
outputs and source; no runtime reproducer is required for static diagnosis.
Loading it does not expand command, repair or retry authority. Respect an
existing diagnosis-only state before any action; cite an existing analysis
rather than repeating it. Return evidence to this FD/Worker report, then make
only repairs already authorized outside that state. For a local interface
question use codebase-design; a structural scope change returns to Planner or
becomes a follow-up proposal, not an unplanned architecture refactor. Missing
supporting Skills are reported; use the available scoped method without a
false claim of invocation. Passing work does not need a diagnostic stage.

Make the smallest complete change for each item. Update its checkbox only
when the change and required evidence are real. Record TODO, Verification,
unresolved `%%` notes, and paths or commits that a Reviewer should inspect.
Focused local commits are allowed for independently reviewable slices when
the repository rules permit them. Stage only files that belong to this FD and
use `FD-XXX: description` unless the repository has a stronger convention.

Follow the repository's validation budget. Review the final diff statically.
After code edits, run one compile-only check. Do not run tests, final builds,
formatters, linters, vet, or network checks without their required authority.
Never write a passing result for an unrun command.

Before any next-stage event or managed review command, check the user's stage
and scope limit. For implementation-only or no-review requests, or selected
Work Items without authorization to review them, save the scoped result and
evidence and stop before dispatching Reviewer. A no-merge limit may permit
review but never delivery. An intentional stage limit is a requested stop,
not a failed gate or permission to fabricate completion of the whole FD.
If review is separately authorized for part of an unfinished FD, keep it scoped
without emitting a full-FD implementation-ready event or marking that FD ready.

For selected test support, load `testing.md` before implementing: use suitable
core-rule TDD, then host arranges independent fd-test before this handoff.
Do not emit implementation-ready first and attempt to insert tests after the
CLI has already dispatched Reviewer. Report actual versions, failures and
diagnosis-only in Worker evidence; continue independent work without bypassing
whole-FD readiness or restarting exhausted test repair/execution budgets.

Only when the stage permits independent review and the whole FD implementation
scope is complete, emit `implementation-ready` with
`--producer worker` and a project-relative implementation report as the
artifact. Include the source event ID when completing a dispatched handoff.
For the default path, `implementation-ready` routes directly to Reviewer after
the Worker completes the repository-authorized compile-only check and static
review and any explicitly selected testing support has been accounted for.
Optional testing uses tdd/fd-test methods under `testing.md` without creating a
Tester event or automatic coverage gate. Existing FDs with the legacy
`Test policy: Independent` marker retain their recorded CLI routing, but the
default implementation flow does not dispatch a Tester or assess a test
report. If a new decision is needed, emit `needs-decision` with a written
question instead, then stop.

An independent Reviewer uses `fd-review` to check the FD, diff or commit, and actual evidence.
It emits `changes-requested` with findings or `verification-passed` with its
review report. A failed review returns to Worker; continue from the same FD.
Do not claim verification passed because Worker checked the FD box.

## Return to the lifecycle

After the assigned implementation output is saved, return control to the host
PM. The fd-workflow entry owns independent review, repair, delivery, conflict
recovery and archive. This reference is not another Skill invocation and does
not call an implementation entry. A Worker-only assignment ends at its handoff;
an explicit implementation-only or partial-scope limit ends before any
unauthorized next-role event. The normal full-FD request continues through the
entry's lifecycle without another auto confirmation.
