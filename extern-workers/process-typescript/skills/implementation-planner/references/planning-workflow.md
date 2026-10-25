# Implementation Planning Workflow

## 1. Establish Baselines

Record the exact SRS and architecture versions. List included and excluded scope and unresolved issues inherited from upstream.

## 2. Build the Coverage Matrix

For each requirement, identify:

- architecture element(s),
- interface/data impact,
- implementation increment,
- work package(s),
- verification mechanism.

Any row without an implementation or verification path is a gap.

## 3. Inventory Implementation Units

Identify only units already implied by architecture or the existing system:

- repositories,
- build projects/modules,
- applications/services,
- libraries,
- APIs/events/files,
- database schemas and migrations,
- scheduled/batch jobs,
- deployment units,
- external integrations,
- infrastructure/configuration,
- operational artifacts.

Do not create a new service merely because a capability has a separate name.

## 4. Build the Dependency Graph

Capture hard, contract, soft, and external dependencies. Detect cycles. If a cycle is architectural rather than implementation-level, raise a design gap.

## 5. Identify Risk-Reduction Work

A spike is allowed only when a material uncertainty prevents reliable estimation or implementation. Give it:

- one question to answer,
- a fixed output,
- an exit criterion,
- a decision consumer.

A spike must not become unbounded implementation.

## 6. Define Vertical Increments

Prefer increments that exercise a real runtime path. A first increment may be a walking skeleton that proves build, persistence, interface, deployment, and observability with minimal business behavior.

## 7. Decompose Into Work Packages

Use `WP-###`. Keep each package bounded and verifiable. Record affected implementation units but do not force exact file names unless the repository is already known.

## 8. Integrate Verification

Add tests alongside the behavior. Acceptance tests should be traceable to `AC-*` IDs.

## 9. Add Delivery Work

Explicitly plan:

- schema migrations,
- compatibility,
- feature flags,
- secrets/configuration,
- monitoring/logging/metrics,
- release packaging,
- rollback,
- data migration/backfill,
- operational documentation,
- deprecation cleanup.

## 10. Validate the Plan

Check coverage, cycles, oversized work packages, missing verification, hidden design decisions, and deployment gaps.
