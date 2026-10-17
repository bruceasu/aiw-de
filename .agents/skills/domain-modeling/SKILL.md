---
name: domain-modeling
description: Clarify conflicting domain terms, concept boundaries, relationships, states and invariants during Issue discovery or FD design; maintain scoped vocabulary and durable decisions when authorized.
---

# Domain Modeling

Follow `skills/reviewed-skill-contract.md` and `skills/work-management.md`.
Declare `skills/workflow-methods.md`; outside Requirement mode read only
Domain clarification. This Skill changes or sharpens meaning; ordinary reading
of an existing glossary does not require a full modeling session.

Inputs: current goal/scope, terms or rule conflicts, confirmed evidence and
unresolved questions. Return precise distinctions and their implications for
acceptance/design. Preserve user goals through software requirements and FD
engineering decisions; do not let a technical term replace the original need.

## Requirement mode: snapshot only

When used as a Requirement/Issue conversation method, use only the current
host snapshot and its output contract. Do not load linked formats or shared
method files, scan code, write CONTEXT/specs/ADRs/FDs, or alter Session records.
The entry itself supplies this mode's complete compact output discipline:

- Return preferred term/definition, distinctions, relationships/ownership,
  relevant states/transitions/invariants, normal and boundary examples.
- Mark non-applicable dimensions rather than inventing a state model.
- Cite supplied evidence and distinguish proposed, code-observed and confirmed
  facts. Only host-confirmed fragments become confirmed rules.
- Name unresolved conflicts, a focused question and its readiness impact.
  Missing evidence is INCOMPLETE/BLOCKED only for affected scope, not guessed.

Host returns conclusions to the same Issue Plan under its confirmation rules.
This mode preserves the existing TermsUnclear supplement and does not require
another Skill directory or create an approval checkpoint.

## Scoped engineering or standalone discussion

Read relevant existing CONTEXT.md/CONTEXT-MAP.md and ADRs only. Follow shared
Domain clarification; compare scoped code when authorized and distinguish
observed behavior from the intended rule. Reuse established terms unless a
confirmed decision changes them. Return a proposal by default; a discussion
request alone does not authorize stable writes.

Within explicitly authorized file/FD scope, maintain confirmed vocabulary with
[CONTEXT-FORMAT.md](CONTEXT-FORMAT.md). Link settled states/invariants to stable
specs, current engineering choices to the FD, and important durable trade-offs
to [ADR-FORMAT.md](ADR-FORMAT.md). Create files lazily only with actual approved
content. No duplicate behavior specification in the glossary. A standalone
scoped documentation request need not invent an FD, and grants no Git/runtime
or FD lifecycle authority.

Complete when relevant definitions/examples are confirmed or clearly proposed,
remaining questions and impact are explicit, and any authorized edits match
the owning artifacts. Report changes, sources, skipped checks and limits.
No default tests, network, subagents, ADRs or context-map creation.
