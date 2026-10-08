---
name: issue-management
description: Manage one AIW Issue from discovery through a decision or FD handoff.
disable-model-invocation: true
---

# Issue Management

Use this skill when the user wants to discover, refine, split, decide, or
hand off one AIW Issue about a bug, feature, or modification. Its work ends at
an Issue decision or FD handoff. For an existing Requirement, keep its `REQ`
ID and artifacts; use `requirement-management` for compatibility details.
Follow `skills/reviewed-skill-contract.md` and `skills/work-management.md`
for authorization and Task lifecycle. Keep this Skill focused on Issue
decisions; the shared file owns lifecycle rules and the installer supplies its
per-Skill copy from the canonical source.

## Route before acting

A request may name source files, exact edits, or tests while invoking this
skill. Treat those details as Issue input, not as permission to implement.
Finish the Issue stage before starting engineering design or changing code.
Use `fd-workflow` for design after handoff and `implement` for code changes
under its FD rules. If the user clearly asks to switch to
implementation, state the switch and follow `implement` before any edit.
If the intended stage is unclear, ask which stage the user wants. Do not
change source files or run implementation validation while using this skill.

## Inputs and record

Start with the user's request and any Issue ID, current source snapshot,
revisions, digests, confirmed facts, Session evidence, and loaded methods.
Read an existing Issue Plan and linked sources before editing it. A new request
has no ID until the CLI creates one; never invent an ID. Use `aiw issue` as the
supported command surface, checking its help before an unfamiliar or mutating
operation. Current records may still use `REQ` IDs under
`docs/requirements/<id>/`; preserve their paths and evidence.

Write temporary Issue drafts under `.ai/requirements/drafts/`. Keep them
project-relative, UTF-8, and within 64 KiB. Capture copies a confirmed draft
into `docs/requirements/<id>/`; a draft is not itself an approved record.

The output is one source-backed Issue Plan or decision, with any split lineage,
unresolved Gates, and a recommended next action. The Issue Plan records the
actor, observed behavior or desired change, goal, scope and non-goals,
constraints, business rules and exceptions, acceptance examples, sources,
assumptions, and remaining decisions.

## Conversation

1. **Establish the issue.** Reconcile the request with the current sources and
   record facts separately from assumptions. Show both sources for a real
   conflict. Stop discovery when every material claim is sourced or marked
   `%% NEEDS_INPUT: <missing fact or question>`.
2. **Choose the next method.** The host makes a method-selection call and a
   coverage call; return only the JSON shape requested for each. Apply the
   generic discovery baseline to every domain. Load a support method only
   when its trigger applies: finance intake, value, metric, engineering
   options, and synthesis are for finance Issues; `domain-modeling` helps when
   terms or boundaries are unstable. An unread method was not applied.
3. **Resolve decisions.** Distinguish missing business facts from engineering
   strategy choices. Use evidence and confirmed preferences to choose a
   clearly better strategy within scope, recording the rationale. Ask the
   human when options are close, a critical fact is absent, or a choice
   changes scope or risk. Ask at most three high-impact questions per turn;
   do not repeat settled questions. Continue only when the remaining unknowns
   are explicit and their effect on readiness is recorded.
4. **Assess size and split independent outcomes.** Apply the Issue size and
   splitting rules below. Retain the original scope and record which outcome
   belongs to each child.
   Link each child to its parent with `aiw issue link-parent <child-id> <parent-id>`
   before child approval, and record the relationship in both
   plans. Account for each outcome once across Issue, FD, and legacy Task handoffs.
5. **Prepare a durable action.** Use the supported CLI to prepare a draft or
   action. In chat mode, show its target, summary, and write scope, then use
   the host's confirmation checkpoint for that action. Model output, a draft,
   and Session memory are not human approval. If sources or revisions drift,
   reassess before acting. Retain an invalid model response with diagnostics;
   do not accept it through repeated retries.

## Issue size and splitting

Assess scope size, expected difficulty, and uncertainty before seeking approval
or handing an Issue to an FD. Record the assessment and the reason to keep or
split the Issue. Aim for one bounded outcome that can be approved and accepted
on its own and handed to a manageable FD with small Work Items.

If an Issue combines several independently useful outcomes, rollout stages,
or acceptance boundaries, prepare smaller child Issue drafts. Each child must
state its goal, scope, non-goals, acceptance examples, dependencies, and Gates.
Split by user-visible outcome, not by files or technical layers alone. If the
outcome is inseparable, keep one Issue and reduce scope where authorized, or
leave the implementation breakdown to `fd-workflow`.

Map every original outcome to a child or an explicitly retained or deferred
parent scope. Keep cross-child safety and release Gates, including dependencies
that prevent an early child from being released on its own. Do not duplicate
delivery scope or describe deferred work as completed.

Prepare the split before asking for confirmation. Create and link child Issues
only within the confirmed write scope, using the supported CLI; never invent
IDs. Parent approval does not approve a child. Preserve already approved
sources and existing FD handoffs; a later split must not silently rewrite their
scope or repeat their delivery.

Do not set a fixed child count or treat splitting as proof of low difficulty.
Mark unresolved scope or acceptance conflicts with `%% NEEDS_INPUT`; do not
declare the affected Issue ready for approval or FD handoff until resolved.

## Readiness and handoff

Assess the Plan from its content, not its headings: every material business
rule, exception, scope boundary, and acceptance example must be supported or
marked unresolved. Record proposed engineering postponements with reasons.
A missing business rule or source conflict cannot be waived by the agent. If
it prevents a valid decision, report `BLOCKED` or `INCOMPLETE` and the precise
`%% NEEDS_INPUT` question. Approval and promotion are separate human decisions.

After an Issue is approved, the new default handoff is a numbered FD. Use
`aiw issue promote <id>` to create it from the Issue title, or use
`aiw fd new "<title>" --issue <id>` directly. Both link the approved source
and request Planner through the FD workflow. Promotion does not create a Task
or update the legacy `[promotion]` metadata. Do not create an OpenSpec change
unless the user explicitly asks for one; stable spec updates do not imply a
change directory.
Discovery alone does not authorize implementation or establish Design
Readiness.

For a post-handoff repair, preserve approved sources, approval and Session
records, legacy Task state when present, and authored FD content. Replace only
known generated placeholders. Mark unsupported details `%% NEEDS_INPUT`, and
ask only about a material conflict or missing decision. Report repaired files
and remaining blockers without repeating the handoff.

## Completion and verification

Finish when the current requested stage has a recorded outcome: a source-backed
Plan, a documented decision, a linked split, or an approved Issue-to-FD handoff.
Report changed artifacts, source and revision evidence reviewed, commands
actually run, unresolved Gates, and the next action. Verify the resulting
record and lineage by static inspection. State skipped validation explicitly;
do not claim an unrun test, sibling method, promotion, or approval completed.
