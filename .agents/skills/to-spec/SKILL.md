---
name: to-spec
description: Write or update stable OpenSpec capability specs from an Issue or FD; link an OpenSpec change only when its change workflow is useful.
disable-model-invocation: true
---

# To Spec

Follow `skills/reviewed-skill-contract.md` and
`skills/work-management.md`.

## When To Use

Use this Skill when an Issue, FD, or explicit request changes a stable
capability rule or observable behavior. It can also maintain a requested
OpenSpec change. Do not use it to split work, manage Task state, or implement
code. A spec-only request does not require an AIW Task.

## Inputs And Decisions

Read only the relevant `openspec/specs/` capabilities, confirmed Issue
evidence, linked FD, and linked change if one exists. Use settled decisions
without reopening them. Choose the clearest rule supported by the evidence and
known user preferences. Ask only when the rule, scope, or acceptance behavior
cannot be determined. Record an unresolved choice as `%% NEEDS_INPUT: ...` and
mark the affected spec work `BLOCKED` or `INCOMPLETE`.

## Write The Spec

Write or update the stable spec with requirements and observable scenarios.
Keep engineering decisions and numbered work items in the FD. Do not create a
Task or OpenSpec change solely to write a stable spec. If a change is already
linked, keep its relevant proposal and spec delta consistent. Create a change
only when the user requests it or its change workflow has a concrete purpose;
use a supported AIW/OpenSpec entry point. A missing OpenSpec CLI blocks only
that change operation.

When a spec decision changes the FD plan, identify the affected FD decision or
work item for `fd-workflow` or `to-tickets` to update. Do not silently rewrite
mapped work item IDs. A linked change's `tasks.md` is a legacy checklist view,
not the Task plan or execution source.

## Completion And Verification

Complete when the affected stable rules and scenarios agree with the confirmed
Issue and FD decisions, or report the exact Gate preventing that result.
Report changed specs, affected FD items, static evidence, and unresolved Gates.
Review the edited text and relevant references statically. Do not run tests,
create an Attempt, claim a lease, change Task status, commit, merge, archive,
or publish externally.
