# AI Worker — English Reference Manuals

Start with the [documentation index](../README.md) and [system overview](../SYSTEM-OVERVIEW.md) (Chinese). This collection originated with R8/R9 and has not been fully reconciled with current code. The versions below describe its original baseline. This English collection is not guaranteed to be synchronized with newer topic guides.

**Execution-platform baseline:** R8-S13 Fix2 (`me.asu.aidev`)
**Current product layer:** R9 consolidated Web Console  
**Original Java baseline:** 21; the current root project declares Java 25
**Spring Boot:** 4.0.8  
**Persistence:** JDBC + Flyway, SQLite local / PostgreSQL shared  
**Worker model:** external, pull-based Worker Protocol v1  
**Canonical environment prefix:** `AIW_`

Chinese edition: [`../current-zh/README.md`](../current-zh/README.md).

This directory retains the R9 reference collection. Stage records are under `docs/archive/`; they are not the recommended starting point for operating the current system.

## Start here

| Audience | Document |
|---|---|
| First-time evaluator | [Quick Start](QUICK-START.md) |
| Boss / non-technical stakeholder | [3–5 minute introduction](BOSS-INTRO-3-5-MIN.md) |
| Product / engineering lead | [Executive overview](01-EXECUTIVE-OVERVIEW.md) |
| Architect | [System architecture](02-SYSTEM-ARCHITECTURE.md) |
| Engineer | [Autonomous engineering lifecycle](03-AUTONOMOUS-ENGINEERING-LIFECYCLE.md) |
| Worker developer | [Worker Protocol and reference workers](04-WORKER-PROTOCOL-AND-WORKERS.md) |
| User / operator | [API and user manual](05-API-USER-MANUAL.md) |
| Web Console user / operator | [Web Console](15-WEB-CONSOLE.md) |
| Operations | [Operations guide](06-OPERATIONS-GUIDE.md) |
| Deployment / configuration | [Configuration reference](07-CONFIGURATION-REFERENCE.md) |
| Database / DBA | [Data model and migrations](08-DATA-MODEL-AND-MIGRATIONS.md) |
| Security review | [Security, authority and trust boundaries](09-SECURITY-AUTHORITY-AND-TRUST-BOUNDARIES.md) |
| Maintainer | [Developer and extension guide](10-DEVELOPER-EXTENSION-GUIDE.md) |
| Incident handling | [Troubleshooting and recovery](11-TROUBLESHOOTING-AND-RECOVERY.md) |
| Release validation | [Testing, validation and limitations](12-TESTING-VALIDATION-AND-LIMITATIONS.md) |
| Capability review | [Current capabilities](13-CURRENT-CAPABILITIES.md) |
| Terminology | [Glossary](14-GLOSSARY.md) |

## What is authoritative

The Java Control Plane and database are authoritative for lifecycle, ownership, scheduling, gates and approvals. Workers do not directly mutate that database. Git revisions identify results in the corresponding engineering paths; not every Plan requires Git. See [Plan locations and handoffs](../PLAN-LOCATIONS-AND-HANDOFFS.md). Human approval remains separate.

## Historical documentation

Stage records are in `docs/archive/`. When documents conflict, check their scope against current code and the relevant FD; this collection is not automatically the newest authority. See the [documentation baseline](../DOCUMENTATION-BASELINE.md).

