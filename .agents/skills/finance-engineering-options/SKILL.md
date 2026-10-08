---
name: finance-engineering-options
description: Explore technical feasibility, constraints, boundaries, and trade-offs for a financial requirement before an AIW Task or OpenSpec design exists. Use for solution options; not for implementation review, final design approval, or release readiness.
---

# Finance Engineering Options

Produce `Engineering Options` for a requirement: alternatives, constraints, dependencies, security and audit implications, and decisions a later design must make. This is not a review of implemented code and does not create `design.md`.

## Use when

- A clear problem and business intent need technical feasibility or trade-off analysis before promotion to engineering work.

## Do not use when

- The request is still feature-centred or lacks a decision flow; route to `finance-requirement-intake`.
- The user asks for code review, implementation, schema migration, Task creation, final OpenSpec design, or release approval.

## Inputs

- Problem Brief or Business Case.
- Target systems and known producers/consumers.
- Metric Brief when the capability surfaces financial metrics.

## Produce

Return a Requirement Artifact with `Artifact Type: Engineering Options` and local status `FEASIBLE`, `CONSTRAINED`, or `NEEDS_DECISION`.

Describe options rather than prescribing implementation. Cover boundaries, dependencies, candidate data flow, permission and audit concerns, material failure modes, observability needs, and decisions that must be resolved during formal design.

## Rules

- Do not write code, migrations, deployment plans, OpenSpec design files, or implementation tasks. After user confirmation, AIW Requirement Management may capture this artifact; this Skill must not invoke capture.
- Do not promise backward compatibility, permissions, audit retention, or data contracts without evidence.
- Do not produce release `GO`/`NO_GO` conclusions.
- Separate business blockers from engineering choices that could wait. State
  each proposed postponement and its reason; only explicit human confirmation
  accepts it. Do not reclassify unresolved business rules as non-blocking design.
- Use `%%` for unknown contracts, owners, security requirements, and risks; recommend one next discussion stage without invoking it.

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
- Artifact Type: Engineering Options
- Requirement ID: %% NOT_ASSIGNED
- Stage: engineering-options
- Status: FEASIBLE / CONSTRAINED / NEEDS_DECISION
- Based On: Problem Brief / Business Case / Metric Brief
- Human Approval Required: yes

## Facts
## Assumptions
## Options
| Option | Benefits | Constraints | Dependencies | Risk | Open Decision |
|---|---|---|---|---|---|

## Boundary and Data Considerations
## Permission and Audit Considerations
## Failure and Operational Considerations
## Open Questions
## Proposed Design Postponements and Reasons
## Suggested Next Stage
```
