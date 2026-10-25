---
name: domain-modeling
description: Clarify terms, concept boundaries, ownership, states and invariants that affect an assigned business or software decision.
---

# Domain Modeling

Use when terms conflict, two concepts are being treated as one, or unclear rules
affect a decision. Do not build a complete ontology for a simple wording issue.
Keep the primary role, frozen inputs, read-only setting and result contract.

## Establish the evidence

Start with the decision, disputed terms, supplied examples and confirmed rules.
Respect the task's source mode. A discussion limited to a frozen snapshot stays
within that snapshot; this Skill does not grant workspace access. An engineering
task may inspect permitted documents and code to find current usage.

Distinguish confirmed business meaning, observed implementation, proposed
meaning and assumptions. Existing code is evidence of behavior, not automatic
proof of the intended business rule. Preserve source terms when comparing them.

## Model only what changes the decision

1. State the conflict with one concrete example. For each important term, give
   a plain definition, an included example and a nearby concept it excludes.
2. Check whether identity, owner, lifecycle or allowed actions differ. Split
   concepts only when the distinction explains a real rule or decision.
3. Describe relevant relationships and cardinality. Name who owns each state
   and who may change it; do not assume two records are the same entity just
   because they share a label or identifier value.
4. For behavior questions, map current state, trigger, precondition, next state
   and rejected transition. State the invariant that must hold across changes.
   For a naming-only question, skip an unnecessary state model.
5. Check a normal example, a boundary case and a counterexample that would
   invalidate the proposed definition or rule. Expose conflicting sources rather
   than silently choosing one. Separate historical data from a future policy.
6. Compare plausible meanings and explain their effect on the assigned scope,
   permissions, data, acceptance or user wording. Recommend one when evidence
   supports it; otherwise ask the highest-impact question through the existing
   question/gap mechanism. Reuse confirmed answers.

## Deliver the useful model

Use the primary output fields for a small term table or relationship/state sketch,
source references, examples, proposed decision, downstream effects and open
questions. Completion means the ambiguous decision can be assessed, or the exact
missing rule and its impact are clear. Do not present a proposal as an approved
rule, or treat a diagram as executed validation.

Update a glossary, specification or decision record only if that file and change
are already in the task's write scope. Otherwise return a proposal. Do not create
a second domain registry, change a schema, approve requirements or start an FD
lifecycle. This Skill supplements the assigned role; it cannot grant authority
to settle a business rule that the task leaves to another decision maker.
