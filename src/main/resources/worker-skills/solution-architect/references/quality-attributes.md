# Quality Attribute Design

Architecture-significant NFRs must become concrete design tactics and verification targets.

## Performance

Capture workload, peak concurrency, latency percentile, data volume, and growth assumptions. Select tactics only after identifying the actual bottleneck class.

## Availability and reliability

Define failure scope, acceptable outage, recovery objectives, retry ownership, idempotency, data durability, reconciliation, and degraded modes.

## Security

Define identity boundaries, authorization responsibility, secrets, sensitive data, encryption, auditability, trust boundaries, and abuse/failure cases.

## Operability

Define logs, metrics, traces, health/readiness, alerting, runbooks, maintenance behavior, safe rollout, and rollback.

## Maintainability

Define module dependency rules, compatibility/versioning policy, testing seams, configuration ownership, and migration discipline.

## Verification rule

For every architecture-significant quality attribute, capture:

1. source requirement
2. architecture tactic
3. affected element
4. measurable verification method

Avoid vague statements such as "high performance", "highly available", or "secure by design" without measurable meaning.
