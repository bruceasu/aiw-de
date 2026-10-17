---
name: grill-with-docs
description: Improve an Issue, requirement, plan, or design through focused questions that test assumptions, rules, scope, and acceptance. Use during substantive discussion and before decision handoff; deepen when conflicts or consequential choices remain.
disable-model-invocation: true
---

# Grill With Docs

Use this method during meaningful discussion, not only after a decision is
blocked. Scale the questions to the remaining uncertainty and consequences.

## Issue and legacy Requirement mode

`issue-management` explicitly routes this method alongside the selected
discussion method. The primary method supplies the analysis perspective;
this method tests the quality and completeness of the discussion.

Use the checkpoint relevant to the current conversation:

- After an initial understanding forms, test the actual problem, goal,
  smallest useful scope, and unsupported assumptions.
- While discussing key rules or options, test boundaries, exceptions,
  constraints, and consequences with concrete examples.
- Before a human decision or FD handoff, check consistency, unresolved
  decisions, and whether acceptance examples can be judged.

Ask one high-impact, decision-centred question at a time. A simple Issue may
need only one or two questions in total. Deepen for material ambiguity,
conflict, or consequential choices. Do not repeat confirmed answers without
new evidence, rerun every checkpoint each turn, or prolong discussion when
the required evidence and decisions are already clear. If only an external
fact is missing, identify its source or owner rather than speculate.

Return to the host once the current question is resolved or its remaining
evidence gap is explicit. Return confirmed facts, decisions, risks, and open
questions to the same Issue Plan or legacy Requirement Artifact. The host
owns durable capture, human confirmation, readiness, and approval; this
method adds no approval gate and writes no independent ADR or document.
When the host requests JSON, follow that contract instead of the standalone
Markdown template. Do not treat proposed answers as confirmed conclusions.

## Engineering mode

When an FD and engineering design already exist, use the same interview to
sharpen an explicit design decision. With user confirmation, record durable
engineering terminology or decisions in the FD/OpenSpec-owned location.

## Output

```markdown
## Discussion Quality Result

- Decision under review:
- Confirmed facts:
- Rejected assumptions:
- Consequences:
- Target artifact:
- Durable write requires confirmation: yes

%% NEEDS_INPUT: <remaining blocking question>
```

## Completion

Complete when the current discussion checkpoint has supported conclusions or
an explicit remaining question. Verify conclusions against supplied evidence;
do not claim tests, sibling methods, approval, or writes that were not performed.
Report the confirmed write separately from the interview.
