# Failure Routing

Route according to root cause, not the stage where failure was observed.

- `CODE_DEFECT` -> coding-work-package-executor
- `REQUIREMENT_GAP` / `REQUIREMENT_CONFLICT` -> software-requirements-analyst
- `DESIGN_GAP` / `ARCHITECTURE_VIOLATION` -> solution-architect
- `TEST_DEFECT` / `TEST_DATA_DEFECT` -> test-maintenance work package or verifier correction
- `ENVIRONMENT_ISSUE` -> environment/operations owner
- `DEPENDENCY_BLOCKER` -> orchestrator/dependency owner
- `BASELINE_CONFLICT` -> orchestrator baseline resolution
- `NONDETERMINISTIC_FAILURE` -> create defect/investigation WP unless cause can be isolated immediately
- `PERFORMANCE_LIMIT_BREACH` -> coding or architecture depending on root cause
- `SECURITY_FAILURE` -> coding or architecture/security design depending on root cause

Do not patch production code to satisfy a broken test.
