---
name: finance-requirement-intake
description: Clarify a financial, operations, reporting, risk, or analytics request before solution design. Use for discovery, decision flow, scope reduction, stakeholders, and unknowns; not for implementation planning or release approval.
---

# Finance Requirement Intake

Turn a raw request into a decision-centred `Problem Brief`. This is a requirement-discussion skill, not an AIW Task or OpenSpec workflow.

## Use when

- The request is vague, feature-centred, or lacks an actor, decision, action, or impact.
- A human wants to discover the actual problem and the smallest useful scope.

## Do not use when

- A clear Problem Brief already exists; route to `finance-value-assessment`, `finance-metric-brief`, or `finance-engineering-options` according to the gap.
- The user asks to create a Task, proposal, design, code, schema, branch, or worktree.

## Inputs

- Raw request.
- Target system or business surface.

## Produce

Return a Requirement Artifact with `Artifact Type: Problem Brief` and local status `CLEAR`, `NEEDS_INPUT`, or `OUT_OF_SCOPE`.

Include: problem, actor, situation, signal seen, decision, action, downstream impact, stakeholders, current workaround, smallest useful scope, non-goals, and `%%` open questions.

`CLEAR` means the problem is ready for the next discussion stage. It does not mean the requirement is approved or ready to implement.

## Rules

- Separate facts, assumptions, and recommendations.
- Do not accept “add a page/button/report” as a sufficient problem statement.
- Do not invent owners, frequency, impact, rules, or evidence.
- Do not write files or create AIW/OpenSpec artifacts. Requirement Management
  prepares the draft and host confirmation checkpoint; do not require the human
  to construct a file path or capture command. This method must not invoke capture.
- Recommend one next stage; do not invoke sibling Skills or map its status to their status.

## Output

### When loaded by Requirement Management

Apply this method only to the financial domain identified in the supplied
context. The current host JSON contract overrides the Markdown template below;
use the template only for a requested draft or standalone discussion.
Use loaded source bodies and exact references. Do not load sibling Skills or
linked materials, invent missing evidence, or treat capture as confirmation.
Ask only the highest-impact open questions; keep conflicts and proposed
conclusions separate from host-confirmed fragments. Report missing evidence
without claiming readiness. The host owns persistence and human checkpoints.
This method does not authorize tests, external calls, or lifecycle operations.

### Draft artifact

```markdown
# Requirement Artifact

## Metadata
- Artifact Type: Problem Brief
- Requirement ID: %% NOT_ASSIGNED
- Stage: intake
- Status: CLEAR / NEEDS_INPUT / OUT_OF_SCOPE
- Based On: raw request
- Human Approval Required: yes

## Facts
## Assumptions
## Decision Flow
| Actor | Situation | Sees | Decides | Acts | Downstream Impact |
|---|---|---|---|---|---|

## Scope
### Smallest Useful Scope
### Explicit Non-Goals

## Open Questions
## Suggested Next Stage
```
