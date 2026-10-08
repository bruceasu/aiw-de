---
name: finance-release-gate
description: Assess whether an implemented financial change is safe to release. Use only near launch when implementation, migration, rollback, monitoring, permission, audit, and ownership evidence are available; not for requirement discovery or design discussion.
---

# Finance Release Gate

Evaluate release evidence for an implemented AIW Task and return a `Release Review`. This is a post-implementation release gate; it is outside the requirement-discussion chain.

## Use when

- A release window is planned and the change affects money, data, permissions, reports, dashboards, risk indicators, migrations, or backfills.
- The user supplies implementation and operational evidence sufficient to assess launch risk.

## Do not use when

- The requirement is still being discovered, valued, or explored technically.
- The user asks to create rollback scripts, migrations, monitoring, or implementation code.

## Inputs

- Implemented change scope and affected systems.
- Target launch window.
- Evidence for migration/data impact, permissions, audit, rollback, monitoring, and named ownership.

## Produce

Return `Release Review` with `GO`, `GO_WITH_RISK`, or `NO_GO`, clearly separating supplied evidence from missing evidence.

## Rules

- Missing critical evidence yields `NO_GO`; do not infer procedures or owners.
- Do not create or modify Task, OpenSpec, deployment, migration, rollback, or monitoring artifacts.
- Refer missing requirement intent or metric semantics back to the appropriate Requirement Artifact; refer implementation gaps to the managed Task workflow.
- Use `%%` for missing evidence and name the required next action and owner.

## Output

```markdown
# Release Review

## Metadata
- Task ID: %% REQUIRED
- Release Window:
- Decision: GO / GO_WITH_RISK / NO_GO

## Evidence Reviewed
## Scope
## Migration and Data Impact
## Permissions and Audit
## Rollback and Operations
## Open Risks
| Risk | Severity | Evidence Gap | Owner | Release Blocker |
|---|---|---|---|---|

## Final Recommendation
## Required Next Action
```
