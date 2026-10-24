# Current Capabilities

This page is a concise inventory for deciding whether additional infrastructure work is necessary.

## Engineering workflow

- Natural-language requirement intake.
- Requirement analysis.
- Solution architecture.
- Implementation planning.
- Optional machine-readable split into 1–16 independent implementation work items.
- Parallel coding from one immutable Git base.
- Isolated attempt-specific Git worktrees/branches.
- Integration task for parallel implementation results.
- Independent code review and verification.
- Repair loop when either side requests repair.
- Immutable engineering gate tied to the exact code result.
- Manual or policy-driven human approval.
- Optional Telegram Approve/Reject adapter.

## Worker platform

- External pull-based workers.
- Dynamic register/heartbeat/drain/offline.
- Capability matching.
- `maxConcurrency` per worker.
- Python, TypeScript, JavaScript, Go and Java reference workers.
- Codex SDK or Codex CLI integration examples.
- Optional Worker Protocol bearer token.
- Local filesystem paths stay private to workers.

## Reliability

- Atomic task claim.
- Lease/heartbeat ownership.
- Expired lease recovery.
- Explicit attempt history.
- Explicit failure vs infrastructure crash distinction.
- Idempotent generated-task materialization.
- Completion-handler authority validation.
- Pause/resume/cancel/recover.

## Scheduling

- Capability filtering.
- Priority.
- wait-time aging/starvation prevention.
- best-effort run fairness.
- worker capacity.
- per-run hard concurrency budget.
- runtime run budget control.
- fleet-wide hard concurrency ceiling.
- runtime fleet ceiling control.
- contraction without killing healthy leases.

## Observability / audit

- Run status API.
- Run dashboard.
- Run timeline.
- Fleet/backlog dashboard.
- failure/recovery and operator-control audit.
- runtime run-ceiling audit.
- runtime fleet-ceiling audit.
- worker/run accounting-delta diagnostics.

## Persistence / deployment

- Small JDBC-only schema.
- SQLite local mode.
- PostgreSQL shared/server mode.
- Flyway V1–V14.
- nine tables.
- Java 21 modular monolith.

## Deliberately not implemented yet

- production-grade management API auth/RBAC;
- PR/merge/release/deployment pipeline;
- capability-specific quota;
- complete cross-repository final engineering authority;
- production-tested multi-instance PostgreSQL stress/chaos baseline;
- a general Skill/tool permission-governance runtime.

The current recommendation is to validate the real autonomous engineering loop before adding more scheduler features.

## Web Console

- create autonomous runs;
- Dashboard / Run Detail / Timeline;
- Pause / Resume / Recover / Cancel;
- Run/Fleet runtime scheduling control;
- Worker Drain / Offline;
- Approval Request / Approve / Reject;
- mandatory real-session login for console business pages, with CSRF;
- VIEWER / OPERATOR / APPROVER / ADMIN.
