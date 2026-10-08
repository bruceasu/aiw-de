# R2 Process Role -> R9 Worker Mapping

Source basis: the frozen R2 architecture defines nine core Skills and a Java-owned lifecycle/Skill runtime. This mapping carries only the process/execution responsibilities into R9 workers; it does not move Java authority into workers.

| Frozen R2 role | Classification | R9 mapping | Status |
|---|---|---|---|
| engineering-artifact-protocol | protocol skill | embedded authority boundary in every process worker | active, not a worker |
| software-requirements-analyst | primary/lifecycle skill | Requirements Analyst worker | active |
| solution-architect | primary/lifecycle skill | Solution Architect worker | active |
| implementation-planner | primary/lifecycle skill | Implementation Planner worker | active |
| coding-work-package-executor | primary/lifecycle skill | Coding Executor worker | active |
| software-test-verifier | primary/lifecycle skill | Verifier + Integration Verifier workers | active |
| software-code-reviewer | primary/lifecycle skill | Code Reviewer worker | active |
| release-readiness-manager | primary/lifecycle skill | preserved profile | blocked: no R9 task type |
| software-development-orchestrator | advisory skill | preserved profile | blocked: no R9 task type; must never become scheduler authority |

R9-native execution roles retained in addition to R2:

- Integration Executor
- Repair Executor

These are required by the current R9 completion/recovery model and remain ordinary pull workers.

## Authority preserved from R2

Workers may:

- analyze the exact task/input context;
- inspect repository evidence within assigned permissions;
- execute bounded code/test work;
- produce semantic reports, findings and result commits;
- request/report the result through Worker Protocol.

Workers may not:

- allocate authoritative durable IDs;
- change Run/Task lifecycle state directly;
- create or replace human approval;
- become the scheduler;
- widen repository or sandbox permissions;
- treat hidden Codex session state as durable system authority.
