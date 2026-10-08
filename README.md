# AI Dev Manager R9

AI Dev Manager is a Java Control Plane for a pull-based workforce of AI software-engineering workers.

**Execution platform baseline:** R8-S13 Fix2 (accepted)  
**Current stage:** R9 Web Console — consolidated interactive delivery  
**Java namespace:** `me.asu.aidev`  
**Java:** 21  
**Spring Boot:** 4.0.8  
**Persistence:** JDBC + Flyway; SQLite local / PostgreSQL shared  
**Worker implementations included:** Python, TypeScript, JavaScript, Go, Java

## Start here

- [Quick Start](docs/current/QUICK-START.md)
- [3–5 minute Boss introduction](docs/current/BOSS-INTRO-3-5-MIN.md)
- [Current documentation index — English](docs/current/README.md)
- [当前正式文档 — 中文](docs/current-zh/README.md)
- [Web Console](docs/current/15-WEB-CONSOLE.md)
- [System architecture](docs/current/02-SYSTEM-ARCHITECTURE.md)
- [Operations and maintenance guide](docs/current/06-OPERATIONS-GUIDE.md)
- [API / user manual](docs/current/05-API-USER-MANUAL.md)

## Current engineering flow

```text
Requirement
  -> Requirement Analysis
  -> Solution Architecture
  -> Implementation Planning
  -> one/many Implementation tasks
  -> optional Integration
  -> Code Review + Verification
  -> Repair loop when required
  -> Engineering Gate
  -> Human Approval policy
```

Workers never receive pushed assignments. They register capabilities and pull READY work through Worker Protocol v1. The Java Control Plane owns task/run lifecycle, scheduling/admission, gates and approval authority.

## Local verification

```bash
mkdir -p data
mvn clean verify
```

Expected Failsafe integration-test methods for this consolidated R9 delivery: **33**.

Start the default local SQLite profile:

```bash
mvn spring-boot:run
```

When running with Java 25, SQLite JDBC loads its native library. Add
`--enable-native-access=ALL-UNNAMED` to the application's JVM options to avoid
the restricted native access warning. In IntelliJ, put it in the Spring Boot
run configuration's **VM options** field. For Maven, use
`mvn spring-boot:run -Dspring-boot.run.jvmArguments=--enable-native-access=ALL-UNNAMED`.

Open:

```text
http://127.0.0.1:8080/ui
```

The Web Console now supports monitoring and operations: create autonomous runs, pause/resume/recover/cancel, update Run/Fleet concurrency budgets, drain/offline workers, request/approve/reject human approvals, and inspect dashboards/timelines/audit history.

## Optional Web Console authentication

Browser-console authentication is disabled by default for local use. Enable it with:

```text
AIW_WEB_CONSOLE_AUTH_ENABLED=true
AIW_WEB_CONSOLE_USERNAME=admin
AIW_WEB_CONSOLE_PASSWORD=<strong secret>
AIW_WEB_CONSOLE_ROLE=ADMIN
```

Roles: `VIEWER`, `OPERATOR`, `APPROVER`, `ADMIN`.

This protects the browser console only. The management REST APIs retain their existing trust boundary; keep the Control Plane on localhost or a protected internal network unless those APIs are separately protected.

## Persistence baseline

```text
Core/authority/audit tables: 9
Flyway: V1-V14
```

R9 Web Console adds no business table and no migration.

## Documentation policy

`docs/current/` and `docs/current-zh/` are canonical for the current codebase. Existing `docs/R3-*` through staged `R9-*` files are historical design/change records.

## R10 Process Workers

R10 adds process-specialized Go, Python, and TypeScript worker fleets. These runtimes carry forward the frozen R2 Skill packages and compose R2 Protocol -> Primary Skill instructions while retaining the R9 decentralized Worker Pull/lease model. No Java Control Plane authority or database schema is moved into workers.

Start with the [TypeScript](workers/process-typescript/README.md), [Go](workers/process-go/README.md), or [Python](workers/process-python/README.md) process-worker guide. The [Quick Start](docs/current/QUICK-START.md) shows the existing single-worker demo and how to launch a process-worker fleet; the [operations and maintenance guide](docs/current/06-OPERATIONS-GUIDE.md) covers role coverage, draining, and upgrades.
