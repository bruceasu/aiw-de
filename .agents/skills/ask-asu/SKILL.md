---
name: ask-asu
description: Route an AIW request to the next FD stage without performing the work.
---

# Ask Asu

This Skill is a read-only guide. Read `skills/work-management.md` and
`references/development-workflow.md`. Identify the current Issue, numbered FD,
FD evidence, pending event, and authorization limits. Do not run a
mutating command or write an artifact while acting only as Ask Asu.

## Default path

1. For a new bug, feature, or modification with unclear scope, recommend
   `issue-management`. A clear engineering request may start with
   `fd-workflow` and `aiw fd new` without an Issue.
2. For a Design FD, route to Planner through `fd-workflow`. Record options,
   decisions, Work Items, acceptance, and Verification before `design-ready`.
3. For an Open FD or a review correction, route to `implement`. Worker handles
   all ready Work Items in order and emits `implementation-ready` when done.
4. For Pending Verification, route to `fd-review` in an independent Reviewer
   session. Use
   actual diffs and evidence. A failed review returns to Worker; a passed
   review records `verification-passed`.
5. For a completed FD, recommend a separate delivery or archive decision.
   A local commit does not imply push, merge, release, or archive.

An explicit `needs-decision` or authorization gap stops dispatch. Recommend
the smallest user question that resolves it. A pending event may be continued
with `aiw fd resume`; a dispatched event with unknown result must be checked
against its original session or log before any retry.

## Output

State the current stage, evidence, one recommended next Skill or command, the
expected handoff artifact, and any real blocker. Mark unknown material facts
with `%% NEEDS_INPUT: ...`. Distinguish implementation from verification and
verification from delivery. Do not claim an unrun check passed.
