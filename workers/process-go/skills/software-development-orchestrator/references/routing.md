# Routing and Feedback Classification

## Primary Routing

| Need / Finding | Route To | Expected Evidence |
|---|---|---|
| Vague intent, missing behavior, conflicting user expectations | Requirements Analyst | SRS delta / resolved requirement |
| System boundaries, service/module split, interface/data ownership decision | Solution Architect | Architecture delta / ADR |
| Task decomposition, sequencing, WP boundary, test-plan mapping | Implementation Planner | Plan/WP delta |
| Implementation within approved WP | Coding Agent | Code + implementation evidence |
| Code quality/conformance/security-sensitive implementation review | Code Reviewer | Review findings/disposition |
| Executed verification and regression/acceptance evidence | Test Agent | Test evidence |
| Threat/control-specific concern | Security Reviewer | Security finding/control evidence |
| Release/migration/rollback/operational preparation | Release Agent | Release-readiness evidence |
| Human-reserved scope, risk or destructive action | Human Approver | Explicit approval/rejection |

## Failure Classification

### REQUIREMENT_GAP
Use when desired behavior is absent, ambiguous, contradictory, or not testable enough to decide correctness.

### REQUIREMENT_CONFLICT
Use when two accepted or stakeholder-provided requirements cannot simultaneously hold as written.

### DESIGN_GAP
Use when requirements are clear but implementation cannot proceed safely because an architecture/interface/data/security/reliability decision is missing.

### PLAN_GAP
Use when design is sufficient but WP scope, dependency, sequencing, modification boundary, or verification definition is inadequate.

### CODE_DEFECT
Use when approved requirements/design are sufficiently clear and implementation violates them.

### REVIEW_FINDING
Use for maintainability, correctness, quality, policy or security findings discovered in review. Reclassify upstream if the root cause is not code-local.

### TEST_DEFECT
Use when the test, fixture, oracle, data setup, or automation is wrong rather than the product.

### ENVIRONMENT_ISSUE
Use when infrastructure/configuration/toolchain/environment prevents reliable execution or makes results invalid.

### SECURITY_GAP
Use when a missing/incorrect security requirement, design, implementation, permission, trust boundary or control is discovered. Route to the earliest layer where the gap originates.

### DEPENDENCY_BLOCKER
Use for unavailable external service, vendor decision, upstream artifact, credential, environment, or other prerequisite.

### BASELINE_CONFLICT
Use when multiple artifacts claim authority or later work is based on incompatible baseline versions.

## Escalation Rule

Route to the earliest layer that can correctly resolve the root cause. Do not fix symptoms in downstream code when the authoritative decision belongs upstream.

