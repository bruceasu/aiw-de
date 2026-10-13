# Engineering Artifact Registry

## Baseline Types

Track at least these logical artifacts when applicable:

- `SRS-BL-*` Requirements baseline
- `ARCH-BL-*` Architecture baseline
- `IMPL-BL-*` Implementation-plan baseline
- `CODE-BL-*` Code/build baseline or commit/revision reference
- `TEST-EVID-*` Test evidence set
- `ACC-EVID-*` Acceptance evidence
- `REL-BL-*` Release baseline

Supporting records:

- `CHANGE-*` change request/delta
- `ADR-*` architecture decision
- `WP-*` Work Package
- `ORCH-*` orchestration decision
- `GAP-*` requirement/design/plan gap
- `FINDING-*` review/security/test finding
- `APPROVAL-*` human/policy approval

## Minimum Metadata

For every baseline or evidence set record:

- stable ID
- artifact type
- version/revision
- status: DRAFT | REVIEW | ACCEPTED | SUPERSEDED | REJECTED
- scope/project/increment
- source artifacts / parent baseline
- created/updated evidence
- owner
- approval if required
- supersedes / superseded_by
- traceability links

## Authority Rules

1. Accepted baseline beats a draft unless an explicit change process supersedes it.
2. A newer timestamp does not automatically make an artifact authoritative.
3. Downstream artifacts must identify which upstream baseline versions they used.
4. If a baseline is superseded, evaluate downstream invalidation; do not mark everything stale automatically.
5. Preserve historical IDs and evidence for auditability.

## Evidence vs Assertion

Examples of evidence:

- commit/diff/build artifact reference;
- actual test execution output or signed test report;
- reviewed ADR;
- accepted SRS version;
- migration dry-run result;
- explicit human approval record.

Examples that are not sufficient evidence by themselves:

- "done";
- generated TODO list;
- coding-agent claim that tests pass without execution evidence when execution is required;
- existence of a filename implying approval.

