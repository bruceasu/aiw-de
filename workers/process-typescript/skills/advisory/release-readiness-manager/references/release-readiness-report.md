# Release Readiness Report

## Release metadata

**Release/Increment:** ID  
**Candidate artifact/version:** if known  
**Engineering readiness:** READY / NOT_READY  
**Approval readiness:** READY / WAITING  
**Overall disposition:** one defined disposition

## Scope

List included WPs/features and intentional exclusions.

## Baseline traceability

| Artifact | Baseline/version | Status |
|---|---|---|
| SRS | SRS-BL-* | accepted |
| Architecture | ARCH-BL-* | accepted |
| Implementation | IMPL-BL-* | accepted |
| Code | CODE-BL-* | candidate |
| Verification | TEST-EVID-* | passed/qualified |
| Acceptance | ACC-EVID-* | accepted/pending |

## Gate assessment

Summarize:

- functional/acceptance readiness;
- review/verification findings;
- compatibility;
- migration/data;
- deployment/cutover;
- rollback/recovery;
- observability/operations;
- security/configuration;
- dependencies;
- approvals.

## Blockers

For each blocker:

### REL-BLOCK-### — title
- **Class:** blocker class
- **Affected release/WP:** IDs
- **Evidence:** concrete issue
- **Required remediation/owner:** next action
- **Routing:** Implementation / Verification / Architecture / Operations / Approval owner

If none, state `No release blockers identified`.

## Deployment readiness package

Provide the sequence/checklist only for a ready candidate:

1. pre-release checks;
2. deployment/migration order;
3. post-release verification;
4. monitoring signals;
5. rollback/recovery trigger and action;
6. reconciliation/follow-up if relevant.

## Accepted risks / exceptions

List explicit exceptions with owner and approval. Otherwise state `None`.

## Conclusion

Explain why the chosen disposition follows from the evidence.
