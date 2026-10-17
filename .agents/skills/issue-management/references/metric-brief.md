# Metric Brief

Internal Issue discussion method. Load this reference only for the current discussion gap. Apply domain-specific rules only when relevant to the supplied evidence.

Produce a `Metric Brief` that captures the intended business meaning of a number and the decisions still required before it can become a durable metric specification.

## Use when

- A dashboard, report, KPI, risk indicator, or decision depends on a metric whose meaning or consistency is uncertain.
- Existing product, operational, finance, risk, regulatory, or management definitions may disagree.

## Do not use when

- The user asks for SQL, ETL, a dashboard implementation, migration, or a permanent OpenSpec metric spec.
- The item is purely presentational and has no business or aggregation semantics.

## Inputs

- Metric name or business question.
- Consuming decision or surface.
- Business context: who acts on it and how.

## Produce

Return a Requirement Artifact with `Artifact Type: Metric Brief` and local status `DEFINED`, `PARTIAL`, or `CONFLICT`.

Capture intended definition, formula, unit, time semantics, refresh expectation, source candidates, data correctness concerns and applicable domain rules, conflicting definitions, and required owners. It is an input to later specification, not the specification itself.

## Rules

- For any metric, clarify units, aggregation, population, time window, and source consistency where relevant. Do not require monetary or settlement fields for non-monetary metrics.

- Monetary metrics require explicit treatment of currency, precision, rounding, timezone, cut-off, settlement/value date, and snapshot-versus-transaction semantics.
- Do not invent sources, owners, formulas, or regulatory rules.
- `DEFINED` means the discussion evidence is sufficient for promotion consideration; it does not create a durable data contract. After user confirmation, AIW Issue Management (or legacy Requirement Management) may capture this artifact; this method must not invoke capture.
- Use `%%` for gaps and recommend one next discussion stage without invoking it.

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
- Artifact Type: Metric Brief
- Requirement ID: %% NOT_ASSIGNED
- Stage: metric-brief
- Status: DEFINED / PARTIAL / CONFLICT
- Based On: %% Problem Brief or Business Case
- Human Approval Required: yes

## Facts
## Assumptions
## Metric Intent
| Metric | Business Meaning | Consumer Decision | Formula Candidate | Unit | Time Semantics |
|---|---|---|---|---|---|

## Source and Data-Correctness Questions
## Conflicts and Resolution Needed
## Open Questions
## Suggested Next Stage
```
