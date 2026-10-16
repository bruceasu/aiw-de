---
name: workflow-recovery-decider
description: Decide whether one explicit human-approved repair grant can address a completed review.
---

# Bounded Recovery Decision

Use only for the assigned `workflow-repair-authorization` task.

1. Read the exact source review, implementation, accepted upstream outputs, grant scope and expiry.
2. Check whether one repair execution and its independent review can address the findings inside that scope.
3. Return `REPAIR_REQUIRED` if a requirement decision, tool permission or environment change is needed outside the grant.
4. Otherwise return `PASS` with the concrete reason to use this grant.

Do not edit files, grant new permissions, change acceptance criteria, extend the scope, renew a grant or approve your own work.
Your report cannot replace the fixed grant scope. The Control Plane validates identity, expiry, source versions and single use.
Do not claim tests were run unless actual execution evidence was supplied and inspected.

End with exactly one final line: `AIW_VERDICT: PASS` or `AIW_VERDICT: REPAIR_REQUIRED`.
