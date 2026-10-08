---
name: implement
description: Implement all ready Work Items in one numbered FD, then hand the result to an independent Reviewer.
---

# Implement

Read `skills/reviewed-skill-contract.md`, `skills/work-management.md`, and the
repository instructions. Use this Skill after a numbered FD is Open, or when
returning from a failed review. A user may limit work to one item; otherwise
continue through all ready items in dependency order. Do not design a missing
decision during implementation.

## Read and prepare

Resolve the FD ID from the user, active session, or unique Issue link. Read
the FD, approved Issue evidence when present, relevant stable specs, code,
tests, and workspace rules. Read a linked OpenSpec change only when it exists
and affects the work. For an existing legacy AIW Task, continue under its
original Task and Core contract; do not silently convert it.

Before each item, check its outcome, dependencies, acceptance, and unresolved
`%%` notes. Use the current workspace for sequential work. For parallel or
conflicting writes, use an isolated worktree tied to the FD and ensure its plan
is already committed. Stop at a real scope, authorization, or workspace
conflict. Continue independent ready items when one item is blocked.

When taking a pending Worker handoff in a host session, first claim its exact
event with `aiw fd claim <id> <event-id> --session <host-session-id>`. A role
runner dispatched by AIW has already claimed its event. Never take over an
event that names another active or unknown session.

## Implement and record

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

When all scoped Work Items are resolved, emit `implementation-ready` with
`--producer worker` and a project-relative implementation report as the
artifact. Include the source event ID when completing a dispatched handoff.
For the default path, `implementation-ready` routes directly to Reviewer after
the Worker completes the repository-authorized compile-only check and static
review. Optional testing is a separate `$fd-test` invocation and does not
create a workflow event or gate review. Existing FDs with the legacy
`Test policy: Independent` marker retain their recorded CLI routing, but the
default implementation flow does not dispatch a Tester or assess a test
report. If a new decision is needed, emit `needs-decision` with a written
question instead, then stop.

An independent Reviewer uses `fd-review` to check the FD, diff or commit, and actual evidence.
It emits `changes-requested` with findings or `verification-passed` with its
review report. A failed review returns to Worker; continue from the same FD.
Do not claim verification passed because Worker checked the FD box.

