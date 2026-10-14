# AI Worker R9 — Final Release Validation

**Release:** R9 consolidated Web Console  
**Validation record:** 2026-10-02  
**Execution platform:** accepted R8-S13 Fix2 baseline  
**Java namespace:** `me.asu.aidev`

## Release baseline

| Area | Baseline |
|---|---|
| Java | 21 |
| Spring Boot | 4.0.8 |
| Maven coordinates | `me.asu:ai-dev-manager-r8` |
| Persistence | JDBC + Flyway; SQLite local, PostgreSQL shared |
| Schema | Flyway V1–V14; 9 business, authority, and audit tables |
| Expected Failsafe integration-test methods | 33 |

R9 adds an operational browser console on the accepted R8 execution platform. It introduces no WebUI-specific business schema or second authority: browser actions delegate to existing application services.

## Delivered scope

- Dashboard and run creation, listing, detail, task, and timeline views.
- Run pause, resume, recover, and cancel controls.
- Worker fleet views and drain/offline controls.
- Approval inbox and decisions using the existing approval authority.
- Fleet scheduling controls and accounting diagnostics.
- Optional browser session login, role checks, and CSRF protection.
- Existing natural-drain behavior when Run or Fleet concurrency limits are reduced.

The console uses Spring MVC, Thymeleaf, CSS, and small vanilla JavaScript helpers. It adds no separate frontend build or persistence layer.

## Validation evidence

Generation-environment checks recorded for the consolidated R9 delivery:

| Check | Result |
|---|---|
| SQLite migration V1 → V14 | PASS |
| R9 security classes, Java 21 structural compile with stubs | PASS |
| R9 action service/controller structural compile | PASS |
| Internal `me.asu.aidev` import resolution | PASS |
| Consolidated expected Failsafe integration-test methods | 33 |

The R9-S1 stage record also reports passing Web Console run/task SQL projection, POM XML parsing, HTML lexical parsing, and scans for legacy namespace/prefix residue, Web Console mutation HTTP annotations, and Web Console DML statements.

These are generation-environment structural and smoke checks. They are not a substitute for executing the complete Maven acceptance gate against this final source tree.

## Acceptance gate and status

Run from the final source tree with JDK 21 and Maven:

```bash
mvn clean verify
```

`clean` is required to avoid stale compiled classes. The generation environment used for the R9 records did not have Maven installed, so this document does **not** claim a final-tree Maven run passed. Release acceptance is complete when the command exits successfully and reports the expected 33 Failsafe integration-test methods.

## Operational boundary

Optional session authentication protects the browser console at `/ui/**`; it does not add authentication or role-based authorization to the existing management REST APIs. Keep the Control Plane bound to localhost or behind suitable network access controls when exposed beyond the local machine.

## Known validation limits

- A live Codex-driven repository run from a natural-language requirement through review, repair, gate, and approval remains unproven.
- PostgreSQL multi-instance concurrency and chaos behavior still needs dedicated stress validation.
- Management REST API authentication is outside this release scope.
- Pull requests, merge, release, deployment, and rollback automation are not delivered by R9.

See [current testing, validation, and limitations](../docs/current/12-TESTING-VALIDATION-AND-LIMITATIONS.md) and [Web Console operations and security](../docs/current/15-WEB-CONSOLE.md) for the maintained product guidance.

## Source records

- `R9-S1-VALIDATION.md` — initial read-only console validation.
- `R9-WEB-CONSOLE-CONSOLIDATION-VALIDATION.md` — consolidated operations, security, and 33-test inventory.

This file is the release-facing summary. The source records remain available for stage-level traceability.
