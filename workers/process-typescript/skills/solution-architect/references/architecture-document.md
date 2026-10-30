# Software Architecture / Design Description

Use this structure as the default. Omit sections that are genuinely irrelevant; do not omit architecture-significant uncertainty.

## 0. Document Control

- architecture baseline/version
- source SRS baseline/version
- status
- assumptions
- open blockers

## 1. Executive Architecture Summary

- system purpose
- selected architecture style
- major applications/components
- primary architecture drivers
- most important tradeoffs

## 2. Architecture Drivers

Trace business objectives, functional drivers, NFRs, constraints, and operational constraints to consequences.

## 3. System Context

- system of interest
- users/actors
- external systems
- trust boundaries
- external flows

## 4. Architecture Principles and Constraints

List project-specific constraints and decision principles. Do not fill with generic slogans.

## 5. Candidate Architectures and Selection

Document meaningful alternatives and why the selected baseline best satisfies the stated drivers.

## 6. Logical Architecture

For each `MOD-*` / `APP-*`:

- responsibility
- owned state/data
- dependencies
- exposed contracts
- relevant requirements
- failure responsibilities

## 7. Key Runtime Scenarios

Describe critical end-to-end interactions, including normal and failure paths.

## 8. Interface and Integration Architecture

Catalog `INT-*` interactions, protocol/semantics, ownership, timeout/retry/idempotency, compatibility, security, and error behavior.

## 9. Data Architecture

- entity/data ownership
- transactional boundaries
- consistency model
- caches/read models/indexes
- migration/versioning
- retention/audit/backup

## 10. Security Architecture

- identities and trust boundaries
- authorization points
- sensitive data
- secrets
- audit
- threat-driven controls

## 11. Reliability and Failure Handling

- failure modes
- retries/idempotency
- degradation
- reconciliation
- recovery
- RTO/RPO mapping

## 12. Performance and Scalability

Tie workload assumptions and performance targets to concrete tactics.

## 13. Deployment and Operations

- deployable units
- runtime topology
- environments
- network boundaries
- config/secrets
- scaling
- observability
- rollout/rollback

## 14. Architecture Decisions

Index all `ADR-*` records.

## 15. Risks, Technical Debt, and Open Issues

Maintain explicit `RISK-*` entries with impact, likelihood/condition, mitigation, and owner/status when known.

## 16. Requirement-to-Architecture Traceability

Map requirement IDs to architecture elements and decisions. Flag requirements with no design coverage and elements with no requirement/constraint justification.

## 17. Implementation Handoff

Provide dependency order, migration constraints, externally blocked work, and architectural invariants the implementation planner must preserve.
