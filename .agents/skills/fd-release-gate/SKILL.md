---
name: fd-release-gate
description: Assess whether an implemented change in any domain is ready to release. Use only near launch with implementation and relevant operational evidence; not for Issue discovery or design discussion.
---

# Release Gate

Read `skills/reviewed-skill-contract.md`, `skills/work-management.md`, and the
repository rules when present. Use this method across domains; a numbered FD
is optional. Apply domain-specific rules only when relevant to the evidence.

Evaluate release evidence for an implemented change (linked FD when present) and return a `Release Review`. This is a post-implementation release gate; it is outside the requirement-discussion chain.

Local FD merge/archive is not a release. Do not require this method for every
FD, call it from ordinary fd-review, or interpret GO as deployment permission.
Use it only when a real release is planned and its operational evidence is
available for assessment. Missing evidence is reported, not fabricated.

## Use when

- A release is planned and the implemented change needs an evidence-based readiness assessment, regardless of domain.
- The user supplies implementation and operational evidence sufficient to assess launch risk.

## Do not use when

- The requirement is still being discovered, valued, or explored technically.
- The user asks to create rollback scripts, migrations, monitoring, or implementation code.

## Inputs

- Implemented change scope and affected systems.
- Target launch window.
- Target environment and release candidate identity (commit, version, artifact
  digest, or other immutable change reference as applicable).
- Evidence for rollback, monitoring, and named ownership, plus migration/data impact, permissions, and audit where relevant. Mark inapplicable areas with a reason.

For each relevant area, identify the evidence source, observation time and
candidate/environment it covers. Separate observed results, plans, not-run
checks and unknowns. Changed candidates or environments require assessment
of affected evidence; a previous GO is not automatically reusable. Do not
require financial, migration or operational machinery that does not apply.

## Produce

Return `Release Review` with `GO`, `GO_WITH_RISK`, or `NO_GO`, clearly separating supplied evidence from missing evidence.

## Rules

- Missing critical evidence yields `NO_GO`; do not infer procedures or owners.
- `GO` means applicable critical evidence is current and no known release
  blocker remains. `GO_WITH_RISK` is only for non-critical residual risks with
  a named responsible owner and evidence of acceptance by the authorized
  decision maker; do not invent acceptance or accept a risk on their behalf.
  Unaccepted material risk or unresolved critical scope yields `NO_GO`.
- Do not create or modify FD, OpenSpec, deployment, migration, rollback, or monitoring artifacts.
- Refer missing requirement intent or metric semantics to the owning Issue or
  other requirement source; refer implementation gaps to the applicable FD or
  change owner. Do not require an FD for an external implemented change.
- Use `%% NEEDS_INPUT: ...` for missing evidence and name the next action and
  owner, or explicitly mark owner unknown. Mark an assessment with decisive
  missing inputs `INCOMPLETE` and recommend `NO_GO`; do not present that as
  verified product failure. Explain each inapplicable area briefly.
- Inspect only supplied or authorized local evidence. This Skill does not
  grant model delegation, tests, builds, network, deployment or publication.
  A GO is a readiness recommendation, not execution authorization.

## Output

```markdown
# Release Review

## Metadata
- FD ID or implemented change reference: %% REQUIRED
- Release Window:
- Candidate / Target Environment:
- Assessment Status: COMPLETE / INCOMPLETE
- Decision: GO / GO_WITH_RISK / NO_GO

## Evidence Reviewed
| Area | Source / Observed At | Candidate / Environment | Observed / Planned / Not Run / Unknown / Not Applicable |
|---|---|---|---|
## Scope
## Migration and Data Impact
## Permissions and Audit
## Rollback and Operations
## Open Risks
| Risk | Severity | Evidence Gap | Owner | Acceptance Evidence | Release Blocker |
|---|---|---|---|---|---|

## Final Recommendation
## Required Next Action
```

## Completion and verification

Complete the read-only assessment when scope/candidate, evidence applicability,
decision rationale, open risks and next actions are explicit. Missing decisive
evidence produces the incomplete result above rather than invented facts.
Review source references and reasoning statically; list checks not run and
limitations. Return the review in the conversation unless an authorized report
location is supplied; writing an assessment must not modify release artifacts.
