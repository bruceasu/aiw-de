# AI Dev Manager R9 — Current Documentation

**Execution-platform baseline:** R8-S13 Fix2 (`me.asu.aidev`)
**Current product layer:** R9 consolidated Web Console  
**Java:** 21  
**Spring Boot:** 4.0.8  
**Persistence:** JDBC + Flyway, SQLite local / PostgreSQL shared  
**Worker model:** external, pull-based Worker Protocol v1  
**Canonical environment prefix:** `AIW_`

Chinese edition: [`../current-zh/README.md`](../current-zh/README.md).

This directory is the current operational and user documentation for AI Dev Manager R9. The older `docs/R3-*` through staged `docs/R9-*` files remain useful design/history records, but they are not the recommended starting point for operating the current system.

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

The current Java Control Plane and its database state are authoritative for lifecycle, ownership, scheduling, gates and approvals. Workers execute bounded tasks but do not directly mutate Control Plane database state. Git commit revisions are the immutable code-result identity. Human approval remains a separate authority.

## Historical documentation

The parent `docs/` directory contains stage-by-stage design records such as R6 review/repair, R7 approval and R8 scheduling milestones. Those files explain why the system evolved as it did. When a historical note conflicts with this directory or the current code, the current code and this documentation baseline take precedence.

