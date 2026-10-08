---
name: grill-with-docs
description: Stress-test a requirement, plan, or design through a focused interview and capture confirmed conclusions.
disable-model-invocation: true
---

# Grill With Docs

Use deep discovery when a decision is blocked by ambiguity, conflict, an
irreversible choice, or `%% NEEDS_INPUT` that ordinary discussion cannot close.

## Requirement mode

When called from a Requirement conversation, ask one decision-centred question
at a time. Test terms, actors, rules, assumptions, constraints, and
consequences. Return confirmed conclusions to the affected Requirement Artifact
as facts, decisions, risks, or open questions.

The Requirement conversation owns durable capture and confirmation. In this
mode, preserve the Requirement Artifact boundary: do not create an ADR or
independent engineering document.

## Engineering mode

When a Task and engineering design already exist, use the same interview to
sharpen an explicit design decision. With user confirmation, record durable
engineering terminology or decisions in the Task/OpenSpec-owned location.

## Output

```markdown
## Deep Discovery Result

- Decision under review:
- Confirmed facts:
- Rejected assumptions:
- Consequences:
- Target artifact:
- Durable write requires confirmation: yes

%% NEEDS_INPUT: <remaining blocking question>
```

## Completion

Complete when the target artifact has a confirmed conclusion or the remaining
question is explicit. Report the confirmed write separately from the interview.
