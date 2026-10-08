---
name: to-tickets
description: Split an Issue or Feature Design into ordered, actionable AIW Task work items, with an optional OpenSpec change.
disable-model-invocation: true
---

# To Tickets

Follow `skills/reviewed-skill-contract.md` and
`skills/work-management.md`.

## When To Use

Use this Skill to turn an approved Issue and FD into actionable work items for
one AIW Task, or to revise that Task's existing work plan. Do not use it for
spec writing, implementation, Task execution, or external ticket publishing.
An OpenSpec change is optional.

## Inputs And Decisions

Resolve the Task, its approved Issue handoff, linked FD, relevant stable specs,
and nearby code only as needed. For an older Task without an FD, use its
existing OpenSpec `tasks.md` as the plan until migration is deliberate. If the
FD is missing or a material design decision is unresolved, route that decision
to `fd-workflow` before marking affected items ready. Keep settled decisions.

When a split strategy needs comparison and delegation is authorized, give a
bounded sub-agent the confirmed scope and known user preferences, then choose
the clear best option. Otherwise make the routine choice from the same
evidence. Ask the human only when options are materially close, critical
information is missing, or a split changes delivery or authorization. Record
an unresolved choice as `%% NEEDS_INPUT: ...`; keep the FD `BLOCKED` until
the affected design decision is resolved rather than mapping it as ready.

## Write And Map Work Items

Break the work into small, complete outcomes. In the FD's `## Work Items`, use
top-level, stable, dotted numbered checkboxes such as `- [ ] 1.1 Implement X`.
For each item, state acceptance evidence and verification intent in the FD.
Express a real dependency in the checkbox title with
`<!-- aiw:depends-on=1.1 -->`; use comma-separated IDs for several
dependencies. Keep IDs stable once mapped, including completed IDs. Do not
turn prose ordering into a dependency unless the work truly requires it.

Use the FD workflow to reconcile the authored checklist with implementation
work items. The FD owns item meaning and order.
its existing `tasks.md` without creating a second checklist. A linked
change's `tasks.md` is not automatically a second plan to maintain. Preserve
its authored text and completed IDs if an explicit compatibility update is
needed.

Create a separate child Issue or Task only for an independently deliverable
outcome, and preserve parent lineage. Do not create a worktree, Attempt, or
lease, and do not mark an item complete here.

## Completion And Verification

Complete when each ready item has a clear outcome and acceptance evidence and
the Task mapping succeeds. Review the checklist, IDs, dependencies, and mapped
results statically. Report proposed Work Items, static evidence, failed
mappings as Gates, and the next action. Do not run tests, commit, or publish
external tickets.
