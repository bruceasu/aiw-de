# Verification Decomposition

Select the lowest-cost test layer that proves the requirement while retaining enough higher-level tests to validate integration and acceptance.

## Unit
Use for deterministic domain rules and pure transformations.

## Component / Module
Use for module behavior with real internal collaborators where practical.

## Contract
Use at independently evolving interfaces such as REST APIs, events, files, or external provider adapters.

## Integration
Use for persistence, messaging, external-system adapters, framework wiring, transaction boundaries, and security integration.

## End-to-End / Acceptance
Use for critical business or operational scenarios. Map directly to `AC-*` where possible.

## Migration
Verify forward migration, compatibility period, representative data, idempotence where required, and rollback/recovery strategy.

## Security
Verify authorization boundaries, input validation, secret handling, abuse cases, audit behavior, and relevant threat controls.

## Performance / Reliability
Use measurable NFR thresholds. Specify workload, environment assumptions, metric, threshold, and pass/fail condition.

## Operational Verification
Verify health/readiness, logs, metrics, alerts, deployment configuration, backup/recovery, and runbook steps where required.

## Rule

Do not create a generic "write tests" work package at the end. Tests belong to the work that introduces the behavior, with separate system-level test packages only when they genuinely span multiple increments.
