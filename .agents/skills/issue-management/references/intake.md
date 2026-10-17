# Requirement Intake

Internal Issue discussion method. Load this reference only for the current discussion gap. Apply domain-specific rules only when relevant to the supplied evidence.

Turn a raw request into a decision-centred `Problem Brief`. This is a requirement-discussion method, not an FD implementation or OpenSpec change workflow.

## Use when

- The request is vague, feature-centred, or lacks an actor, decision, action, or impact.
- A human wants to discover the actual problem and the smallest useful scope.

## Do not use when

- A clear Problem Brief already exists; route to [value-assessment](value-assessment.md), [metric-brief](metric-brief.md), or [engineering-options](engineering-options.md) according to the gap.
- The user asks to create an FD implementation plan, proposal, design, code, schema, branch, or worktree.

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
- Recommend one next stage; do not load other methods or map its status to their status.

## Output

### When loaded by Issue Management or legacy Requirement Management

Apply this method to the current Issue and discussion gap in any domain.
Use domain-specific checks only when the supplied context requires them. The current host JSON contract overrides the Markdown template below;
use the template only for a requested draft or standalone discussion.
Use loaded source bodies and exact references. Do not load other references, Skills, or
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
