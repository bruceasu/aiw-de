# Baseline Change Control

## Trigger

Enter `CHANGE_ASSESSMENT` when new information affects an accepted baseline, including:

- added/modified/removed business behavior;
- changed non-functional constraint;
- changed external contract;
- security/compliance change;
- production defect revealing an upstream requirement/design flaw;
- dependency/vendor/platform change that invalidates an architectural assumption.

## Change Flow

1. Register `CHANGE-*` with source and rationale.
2. Determine earliest affected layer: requirement, architecture, plan, code/test only, or release only.
3. Produce a delta at that layer rather than rewriting unrelated artifacts.
4. Calculate transitive impact using traceability.
5. Classify downstream artifacts:
   - UNAFFECTED
   - REVIEW_REQUIRED
   - MODIFY
   - INVALIDATED
   - SUPERSEDED
6. Re-establish affected baselines.
7. Re-plan only impacted WPs and tests.
8. Resume from the earliest unresolved stage.

## Example

A new requirement adds retry after payment timeout.

Possible impact:

- SRS: add/modify retry and duplicate-processing requirements.
- Architecture: review retry ownership, idempotency and authoritative payment state; add/supersede ADR.
- Plan: modify affected WPs and add failure-path verification.
- Code: change only affected modules/integrations.
- Tests: add retry/duplicate/timeout cases plus regression scope.
- Release: review compatibility/migration only if affected.

Do not regenerate unaffected requirements, ADRs, WPs or tests merely because a change exists.

## Emergency Change

For urgent production fixes, permit an expedited path only if policy allows. Still capture:

- change scope;
- risk;
- evidence;
- required post-hoc requirement/ADR/plan reconciliation;
- rollback;
- approval authority.

Emergency does not mean untracked.

