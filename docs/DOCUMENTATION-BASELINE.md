# Documentation Baseline

Current product baseline: **AI Worker R9 Web Console consolidated**, built on the accepted **R8-S13 Fix2 execution platform** (`me.asu.aidev`).

Canonical current documentation:

- English: `docs/current/`
- Chinese: `docs/current-zh/`

Historical stage notes remain in the parent `docs/` directory for design traceability.

Current engineering baseline:

```text
Java 21
Spring Boot 4.0.8
Maven groupId me.asu
Package root me.asu.aidev
Flyway V1-V14
9 persisted business/authority/audit tables
33 expected Failsafe integration-test methods
R9 Web Console: monitoring + operations + optional session auth/CSRF
```

R9 does not add a second business authority or a WebUI-specific database schema. Browser mutations delegate to the same application services used by the existing adapters.
