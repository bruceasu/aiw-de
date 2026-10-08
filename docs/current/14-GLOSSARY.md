# Glossary

**Control Plane** — The Java application that owns authoritative engineering state, scheduling and lifecycle transitions.

**Worker** — An external process that registers capabilities, pulls tasks and executes AI/tool work.

**WorkTask** — A durable unit of work in the task graph.

**TaskDependency** — A DAG edge requiring another task to complete before readiness.

**Lease** — Temporary ownership of a task by a specific worker, represented by worker ID + lease token + expiry.

**ExecutionAttempt** — One immutable historical attempt to execute a task. Retries create new attempts.

**Capability** — A string advertised by a worker and required by a task, e.g. `java`, `coding`, `repo:core`.

**AutonomousRun** — Run-level authority for one natural-language requirement.

**Engineering Gate** — The Java-derived acceptance result for one exact code result after review and verification.

**ApprovalRequest** — Independent human authorization bound to an exact gated result.

**Workspace Assignment** — Logical Git repository/base/workspace-key/branch information sent to a repository worker.

**Workspace Result** — `resultRevision`, changed files and optional publication reference returned by a worker.

**publicationRef** — A portable result location such as `git:origin:<branch>` for cross-worker/machine handoff.

**Worker Pull** — Workers ask for tasks. The Control Plane does not push a specific assignment to a specific worker.

**Run admission** — Per-AutonomousRun concurrent-execution ceiling.

**Fleet admission** — Global concurrent-execution ceiling across the worker fleet.

**activeSlots** — Persisted per-run concurrency ledger for active run tasks.

**overcommittedSlots** — Legal temporary excess after a scheduling ceiling is reduced below existing active work.

**accounting delta** — Diagnostic difference between a persisted capacity ledger and actual authoritative active task ownership; should normally be zero.
