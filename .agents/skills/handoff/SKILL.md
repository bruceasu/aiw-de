---
name: handoff
description: Compact the current conversation into a handoff document for another agent to continue.
argument-hint: "What will the next session focus on?"
disable-model-invocation: true
---

Follow `skills/reviewed-skill-contract.md` and `skills/work-management.md` when
present. This Skill prepares a handoff document; invoking it does not dispatch
another agent or change workflow state.

## Trigger and boundaries

Use this Skill when the user explicitly invokes `handoff` or asks to prepare a
document for another agent or session. Do not create a handoff just because the
conversation mentions a future agent, transfer, or next step.

The output is a concise, evidence-based continuation brief. It does not
complete work, approve a plan, close an Issue, complete an Attempt, release a
lease, advance Task or FD state, commit, or start another Thread. If the user
explicitly requests operational dispatch, follow the applicable workflow
contract and report that action separately from preparing the document.

## Inputs and source review

Treat the skill argument as the next session's focus. Use the current
conversation for intent, decisions, preferences, authorizations, and completed
work. Resolve the relevant AIW Task, linked FD, worktree, and Session when they
exist. Read a linked OpenSpec change only when one exists and affects the work.

Prefer current artifacts and repository evidence over stale conversational
summaries. Read only artifacts needed for the requested focus. Distinguish
confirmed facts from proposals, assumptions, and unknowns; do not turn an
unanswered question into a decision. Record material missing information as
`%% NEEDS_INPUT: <question or missing evidence>`.

## Handoff document

Write the document to the current AIW Session artifact location when available.
Otherwise use `.ai/tmp/` in the current workspace, choose a unique filename,
and tell the user the absolute path. Keep it concise and project-relative paths
should be used for repository artifacts.

Include:

- purpose and requested focus for the next session;
- current status and the concrete next action;
- confirmed decisions, constraints, and authorizations relevant to that focus;
- completed work and actual evidence, including commands actually run;
- unresolved questions, blockers, risks, and skipped checks;
- links or paths to source artifacts instead of copying their contents;
- a `Suggested skills` section naming only skills relevant to the next action.

For managed work, include Task ID, Work Item, Attempt, relevant Evidence, and
unresolved Gates when known. For FD work, identify the FD and its current
status, relevant Work Items, verification evidence, and pending role handoff
or review when known. Never invent an ID, status, result, or dispatched event.

Do not duplicate material already captured in an Issue, FD, OpenSpec spec or
change, plan, ADR, external Issue, commit, or diff. Reference it by path or URL.
Redact secrets and personal information that the next agent does not need.

## Operational handoff

Preparing the document is the default. Do not start a new Thread automatically.
For FD work, follow the explicit event and role-handoff rules in
`skills/work-management.md`; an ordinary save or Git commit is not a dispatch.

## Completion

The handoff is complete when it exists at the correct location, names the
requested focus and next action, links relevant source artifacts, distinguishes
confirmed information from unresolved items, includes applicable workflow
identity and evidence, and contains no unnecessary duplicate or sensitive
content. Tell the user where it was written and state any material information
that remains unknown.
