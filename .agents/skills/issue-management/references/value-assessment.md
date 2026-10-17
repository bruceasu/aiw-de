# Value Assessment

Internal Issue discussion method. Load this reference only for the current discussion gap. Apply domain-specific rules only when relevant to the supplied evidence.

Assess the value claim behind a clear requirement and produce a `Business Case` draft. This is a human decision aid, not a project approval or implementation gate.

## Use when

- A Problem Brief exists and a human needs evidence for priority, scope, or a validation experiment.

## Do not use when

- The decision flow is unclear; route to [intake](intake.md).
- The request asks for metric definition, engineering design, implementation planning, code, or release approval.

## Inputs

- Problem Brief or equivalent decision flow.
- Target system.

## Produce

Return a Requirement Artifact with `Artifact Type: Business Case` and local status `SUPPORTED`, `VALIDATE_FIRST`, or `NOT_SUPPORTED`.

Assess evidence for user outcomes, reliability, time savings, revenue, risk reduction, labour cost, decision efficiency, regulatory need, or customer experience, as applicable. Compare smaller alternatives and identify a measurable validation path.

## Rules

- Evidence must name a person, incident, metric, research finding, or applicable regulatory source; do not treat sentiment as evidence.
- Prefer a smaller scope or validation experiment where credible.
- Do not infer human approval from `SUPPORTED`.
- Do not create tasks, tickets, OpenSpec artifacts, or implementation plans. After user confirmation, AIW Issue Management (or legacy Requirement Management) may capture this artifact; this method must not invoke capture.
- Use `%%` for missing evidence, owners, or decisions; recommend one next discussion stage without invoking it.

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
- Artifact Type: Business Case
- Requirement ID: %% NOT_ASSIGNED
- Stage: value-assessment
- Status: SUPPORTED / VALIDATE_FIRST / NOT_SUPPORTED
- Based On: Problem Brief
- Human Approval Required: yes

## Facts
## Assumptions
## Value Evidence
| Dimension | Evidence | Confidence | Notes |
|---|---|---|---|

## Scope and Alternatives
## Validation Path
## Decision / Recommendation
## Open Questions
## Suggested Next Stage
```
