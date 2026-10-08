# Verification Strategy

## Risk-based depth

Increase verification depth when a change affects:

- money/data integrity;
- authentication/authorization;
- irreversible state;
- concurrent processing;
- retries/idempotency;
- public compatibility;
- migrations;
- cross-system communication;
- recovery/failover;
- performance-sensitive paths.

## Test layers

Choose layers based on the claim being verified:

- unit: local logic;
- component: module behavior with realistic internal dependencies;
- contract: producer/consumer compatibility;
- integration: real interaction among components/dependencies;
- end-to-end: user/business flow across system boundary;
- acceptance: explicit AC satisfaction;
- nonfunctional: performance, security, reliability, recovery as required.

A lower test layer cannot prove a system-level property it does not exercise.

## Regression scope

Select regression based on affected contracts, shared state, reused libraries, transaction boundaries, and dependency graph. Avoid both extremes: running nothing beyond the changed test and blindly running an enormous suite without understanding failures.
