# Developer and Extension Guide

## 1. Technology baseline

```text
Java 21
Spring Boot 4.0.8
Maven
JDBC only
Flyway
SQLite / PostgreSQL
JUnit / Spring integration tests
```

Canonical namespace:

```text
me.asu.aidev
```

No ORM/JPA is used.

## 2. Source layout

```text
me.asu.aidev
  approval/
  autonomous/
  development/
  execution/
  fleet/
  work/
  worker/
```

Within modules, the current code generally separates:

```text
domain
application
port
jdbc / protocol
```

This is a pragmatic modular-monolith boundary, not a microservice split.

## 3. Adding a new task type

Typical steps:

1. add the `TaskType` enum value;
2. define who materializes the task and its dependencies;
3. define required capabilities/role capability;
4. define whether it requires a Git workspace;
5. if completion creates new work, implement a `TaskCompletionHandler`;
6. declare the handler’s `supportedTypes()` explicitly;
7. add integration tests for readiness, claim, completion and replay/idempotency;
8. update current documentation.

Do not directly invoke a worker from a completion handler.

## 4. Completion handler rules

Completion handlers are graph materializers, not schedulers.

Correct:

```text
task completes
   -> createIfAbsent downstream task(s)
   -> persist dependencies
   -> READY/BLOCKED evaluation
   -> return
```

Incorrect:

```text
task completes
   -> call reviewer worker directly
```

Generated task IDs are deterministic and materialization is idempotent.

Each TaskType should have one completion authority. The dispatcher validates duplicate handler authority at startup.

## 5. Adding another worker implementation

A new worker only needs Worker Protocol v1 plus the repository-workspace contract.

Minimum responsibilities:

- register/heartbeat/drain/offline;
- claim/start/task heartbeat;
- honor lease loss;
- execute instruction;
- for repository tasks, materialize assigned worktree;
- report `WorkspaceResult`;
- produce semantic completion reports where required;
- report explicit failures;
- clean up local worktree.

Do not give a worker direct database access.

## 6. Capability naming

Role capabilities currently use lower-case hyphenated names:

```text
requirement-analysis
solution-architecture
implementation-planning
coding
integration
code-review
verification
integration-verification
repair
```

Repository affinity uses:

```text
repo:<repositoryRef>
```

Technology capabilities are free-form but should remain stable and predictable (`java`, `maven`, `sql`, etc.). Avoid encoding transient scheduler state as a capability.

## 7. Persistence-change rule

Before adding a table ask whether it has at least one of:

- independent lifecycle;
- independent concurrency ownership;
- independently indexed relationship;
- audit retention that existing facts cannot reconstruct.

If not, prefer an existing aggregate/table.

All schema changes require a new Flyway migration; never edit an applied migration.

## 8. Spring bean rule

Spring-managed classes should not be `final` when framework proxying may be required.

If a Spring bean has multiple constructors, explicitly mark the production constructor with `@Autowired`. R8-S12 exposed an ApplicationContext-wide failure when a new service omitted this annotation.

## 9. SQLite rule

The SQLite profile has one connection. Never perform nested JDBC queries from inside a `RowMapper`/callback. Fetch required data in one SQL query or perform sequential queries after the first callback has returned.

## 10. Tests

Run:

```bash
mvn clean verify
```

Current baseline has 29 Failsafe integration-test methods plus unit tests.

Important categories include:

- task graph/readiness;
- worker leases/crash recovery;
- Worker Protocol;
- isolated workspace/result publication;
- review/verification/repair;
- engineering gate;
- approval/Telegram;
- autonomous lifecycle;
- parallel implementation/integration;
- operator control;
- timeline/dashboard;
- pull scheduling fairness;
- run/fleet admission and runtime control.

When adding concurrency behavior, add a regression test for both success and release/recovery paths.
