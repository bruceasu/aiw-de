# Requirement discovery methods

Use for ambiguous intent, conflicting rules, or changed requirements. Preserve supplied
requirement IDs and baselines; return proposals through the existing requirements contract.

## Source and value
- Preserve the original actor, problem, desired outcome, and decision the software supports.
  Separate a user need from the requested implementation.
- Compare existing capability and related reports before proposing new scope. Similar words
  do not establish a duplicate; current code may leave a usability or behavior gap.
- Propose a smaller useful outcome when the evidence supports it. Do not drop acceptance
  or invent business value, priority, stakeholder agreement, or approval.
- Write settled rules as observable normal, failure, and boundary scenarios. Distinguish
  proposed behavior from implemented behavior and from a snapshot of current code.

## Terms and decisions
- For a term that changes acceptance, state its meaning, nearby distinctions, ownership,
  relevant states/invariants, and one normal and boundary example. Do not build a glossary
  for routine terms or write domain/ADR files outside the assigned artifact scope.
- For dependent decisions, identify the prerequisite and current unresolved question.
  Reuse confirmed answers; do not reopen them or infer an answer from silence.
- Investigate accessible facts within scope. Return human preferences or policy conflicts
  as concise questions with options, consequences, and a recommendation.
- A Worker returns clarification proposals to the Control Plane; it does not contact the
  user, dispatch research agents, publish issues, or run a second conversation workflow.
- Split business scope only into independently meaningful outcomes. Engineering steps
  belong to the planner; repository boundaries alone do not define separate requirements.

## Check
Check each proposed rule against its source and acceptance scenarios. Identify what can
proceed independently and what requires a missing decision. Report unknowns in existing
gap fields rather than inventing requirements or introducing a new result schema.
