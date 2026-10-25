---
name: delegation-planning
description: Decide whether assigned work should stay local or be proposed for isolated research, independent review or parallel execution, with traceable inputs and a bounded handoff.
---

# Delegation Planning

Use when the assigned task asks how to divide work or whether another execution
context would help. Do not turn an ordinary edit into a team. Keep the primary
role, fixed task, tools, source scope and result contract. This Skill proposes
execution boundaries; it does not launch agents or change platform state.

## Understand the decision

Identify the outcome, supplied evidence and revisions, important decisions,
dependencies, writable scope and the consumer of the result. Separate a question
that can be answered independently from a decision that needs continuous shared
context. Missing requirements or tool access are gaps, not reasons to spawn.

## Choose a useful boundary

1. Prefer local work when inputs are small, the outcome is unclear, or each step
   depends on the previous hypothesis. Keep iterative debugging and tightly
   coupled edits together unless an explicit independent check is required.
2. Propose isolated research when a bounded question needs extensive exploration
   but can return a concise answer with source locations and limits. The consumer
   must be able to inspect the original evidence when a conclusion matters.
3. Propose independent review when separation of judgment is needed. Its value
   does not depend on a minimum token count. Review must inspect the actual
   revision and evidence, not approve the author's summary alone.
4. Propose parallel work only when inputs and outputs can be stated in advance,
   hidden decisions are shared explicitly, and tasks do not compete for the
   same mutable state. Name dependencies and joins; do not call conflicting
   edits independent because their filenames differ.
5. Compare the expected benefit with setup, repeated context, coordination and
   rework. Use measured data when supplied. Do not treat an input-token threshold,
   an overhead ratio or an anecdotal saving as a universal dispatch rule.

## Specify the proposal

In the current result's existing descriptive fields, give the chosen approach
and reason, bounded question/outcome, exact known input references, relevant
decisions, required Skill/role references, dependencies, output shape, completion
criterion, consumer and missing evidence. A role template can be reused in local
or independent execution; a new business role does not require a new Worker type.

State the minimum tool and repository access needed. A research proposal usually
needs read access; writing, commands and external sources require their actual
authority. Refer only to supplied, approved model profiles or describe capability
needs. Do not route by vendor labels or silently replace a fixed model. Routine
state tracking belongs in platform code; planning and trade-offs still need
appropriate reasoning ability.

## Keep results traceable

Request a short answer plus original artifact/source references, versions,
supporting and conflicting evidence, decisions, checks actually run, unknowns
and the next responsible action. Preserve raw evidence in its authorized home;
do not copy huge logs, credentials or private conversation into the handoff.
Selective re-reading is useful for decisive or changed evidence, not a failure
to optimize. An inaccessible source stays a stated limit.

Report actual supplied consumption and outcomes separately from estimates.
Unknown startup overhead, quality or savings remain unmeasured. A repeated read
is not automatically waste; a Worker verdict is not independent acceptance.
Keep existing retry/unknown-operation restrictions. Finish with a usable proposal
or precise gap in the primary output schema, without creating tasks, dispatching
Workers, installing tools, granting permissions or enforcing a new budget.
