# Stage Gates

Use PASS, FAIL, or CONDITIONAL. CONDITIONAL means explicitly bounded unresolved items do not block the scoped work.

## Requirements Exit Gate

Require, as applicable:

- scope and out-of-scope defined;
- actors/stakeholders identified;
- system context and logical capabilities described;
- functional and relevant non-functional requirements have stable IDs;
- requirements are classified as confirmed/inferred/assumed/unknown/conflict where needed;
- acceptance criteria or verification intent exists for implementable requirements;
- blocking conflicts and unknowns resolved;
- dependencies/constraints recorded;
- traceability from goals/scenarios to requirements is sufficient;
- SRS baseline version established.

## Architecture Exit Gate

Require:

- architecture drivers trace to requirements/constraints;
- system/module/service boundaries are explicit;
- external interfaces and major runtime flows are defined;
- authoritative data ownership is explicit;
- security/trust boundaries are addressed where relevant;
- failure, reliability and performance concerns have architectural treatment;
- major decisions have ADRs with alternatives/tradeoffs;
- architecture gaps are non-blocking for the scoped increment;
- requirement-to-architecture traceability exists;
- architecture baseline version established.

## Planning Exit Gate

Require:

- implementation increments defined;
- Work Packages have stable IDs;
- dependencies are classified;
- each ready WP has objective, scope/out-of-scope, modification boundary, contracts and verification obligations;
- high-risk assumptions are scheduled early enough;
- acceptance requirements map to verification work;
- unresolved DESIGN-GAP/REQUIREMENT-GAP blockers are not hidden in coding tasks;
- implementation baseline/version established.

## Coding Exit Gate

For the scoped WP require:

- implementation stays within authorized scope or deviations are recorded;
- required code/build changes complete;
- required local/unit verification executed;
- no known blocker is concealed;
- implementation evidence references WP and affected requirement/design IDs;
- change summary is reviewable.

## Code Review Exit Gate

Require:

- review covers the intended diff/scope;
- blocking findings resolved or explicitly routed upstream;
- architecture/contract violations handled;
- security-sensitive changes receive required review;
- review evidence is recorded independently from author assertion where policy requires.

## Verification Exit Gate

Require:

- planned test classes executed in the correct environment;
- test results and environment/version recorded;
- acceptance criteria coverage is traceable;
- failures are classified by root layer;
- required regression/contract/integration checks pass or approved exceptions exist.

## Acceptance Exit Gate

Require:

- scoped acceptance criteria satisfied;
- deviations have explicit disposition;
- unresolved requirement ambiguity is not waived implicitly;
- acceptance owner/policy requirements satisfied.

## Release-Ready Gate

Require as applicable:

- release contents/version fixed;
- build/artifact provenance recorded;
- migration plan validated;
- rollback/recovery procedure available;
- configuration/secret changes accounted for;
- monitoring/alerting/runbook updates ready;
- compatibility and deployment-order constraints recorded;
- release approvals satisfied;
- known risks accepted by authorized owner.

