# Scope Control and Gap Classification

## Default boundary

The Work Package is the implementation contract. Its scope may be narrowed for safety but must not be silently broadened.

## Allowed enabling changes

An adjacent change may be included when all are true:

1. It is necessary to implement or verify the WP.
2. It does not alter accepted product behavior beyond the WP.
3. It does not create a new architecture decision.
4. It does not materially increase risk or deployment scope.
5. It is documented in the execution report.

Examples: adding a missing private helper, extending an existing test fixture, correcting an internal type needed for the target behavior.

## Escalate instead of absorbing

Escalate when a discovered change would:

- add or change business behavior not present in accepted requirements;
- move responsibility between modules/services;
- change data ownership or transaction boundaries;
- introduce a new external service or infrastructure dependency;
- change a public API/event/schema beyond an already approved contract;
- require a risky migration strategy not covered by architecture;
- change security/trust boundaries;
- invalidate assumptions of other Work Packages.

## Adjacent defects

If an unrelated defect is discovered:

- do not fix it merely because it is nearby;
- record it as an `INCIDENTAL_DEFECT` finding;
- fix it only when leaving it unfixed makes the current WP unsafe or unverifiable, and document why the fix was an enabling change.

## Refactoring

Refactor only when it is:

- required to implement the WP safely;
- small enough to remain reviewable with the WP; and
- behavior-preserving outside the targeted change.

Large structural refactors should become their own WP.
