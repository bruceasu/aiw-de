# Executive Overview

## 1. What AI Worker is

AI Worker is a software-engineering **Control Plane for AI workers**. Its purpose is not to replace a coding model with another chat interface. It coordinates multiple AI workers through a durable task graph and keeps engineering authority in deterministic Java services.

The system accepts a natural-language requirement, turns it into engineering work, lets compatible workers pull tasks, isolates coding attempts in Git worktrees, performs review and verification, creates repair work when required, produces an engineering gate, and can request a human approval before declaring the run successful.

## 2. The problem it solves

A standalone AI coding CLI is effective at one task, but a development organization also needs:

- durable state across worker crashes and retries;
- independent workers that can join or leave at runtime;
- parallel implementation without sharing one mutable workspace;
- explicit dependency and handoff semantics;
- review and verification separated from coding;
- human approval where policy requires it;
- operational pause, resume, cancellation and recovery;
- visibility into backlog, worker capacity and concurrency;
- auditable result revisions instead of “the agent said it finished”.

AI Worker supplies that coordination layer while keeping the AI runtime replaceable.

## 3. Current end-to-end flow

```text
Natural-language requirement
        |
        v
REQUIREMENT_ANALYSIS
        |
        v
SOLUTION_ARCHITECTURE
        |
        v
IMPLEMENTATION_PLANNING
        |
        +------ one or many implementation work items ------+
        |                                                    |
        v                                                    v
IMPLEMENTATION A ...                                  IMPLEMENTATION N
        |                                                    |
        +--------------------+-------------------------------+
                             |
                        INTEGRATION
                     (when parallel)
                             |
                  +----------+----------+
                  |                     |
             CODE_REVIEW        (INTEGRATION_)VERIFICATION
                  |                     |
                  +----------+----------+
                             |
                       Engineering Gate
                             |
                      Approval Policy
                             |
                  APPROVED / REJECTED
```

If review or verification reports `REPAIR_REQUIRED`, the Control Plane creates a new `REPAIR` task. The reviewer/verifier does not silently change code.

## 4. Architecture principles

1. **Worker Pull, not central Push.** Workers register capabilities and ask for the next compatible task.
2. **Java owns authority.** Lifecycle, scheduling, task ownership, gates, approval state and operator controls live in the Control Plane.
3. **Workers are replaceable.** Reference workers exist in Python, TypeScript, JavaScript, Go and Java.
4. **Attempts are immutable history.** A retry creates another execution attempt rather than rewriting the previous one.
5. **Git revisions identify code results.** `baseRevision`, `resultRevision`, `publicationRef` and `changedFiles` carry code handoff.
6. **Human approval is independent.** A successful AI process is not equivalent to a human authorization.
7. **Local-first operation.** SQLite is the default local profile; PostgreSQL is the shared/server mode.
8. **Small persistence model.** The current system uses nine justified business/authority/audit tables rather than a large framework schema.

## 5. Current maturity

The current baseline has passed the project’s Maven verification gate with **29 Failsafe integration-test methods** after the namespace migration to `me.asu.aidev`.

It should be viewed as an **engineering baseline**, not yet a finished production release platform. In particular, the current lifecycle ends at approved/succeeded engineering work; PR creation, merge/release/deployment, production authentication around management APIs, PostgreSQL multi-instance stress testing and long-running real-Codex end-to-end validation are still candidates for the next phase.

## 6. Why the architecture is useful

The key benefit is organizational flexibility. Additional AI employees can be introduced without redesigning the orchestration pipeline. A Java, Go, Python or TypeScript worker can join the same pool as long as it implements Worker Protocol v1 and advertises the required capabilities.

The Control Plane remains stable while individual AI runtimes, models, tools and worker implementation languages evolve independently.
