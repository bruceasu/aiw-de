# FD-072 implementation response r2

## Response to independent review r1

Review r1 requested that initial pairing, exact Grant creation, and current-process enablement use one human confirmation in Control Plane. The implementation now stages a newly paired Agent as `PENDING`. Until approval, its authenticated machine API is limited to creating a disabled process session and reading connection state. One CSRF-protected Control Plane action validates the owner, pending identity, current generation and process boot, and exact initial scope; it issues the Grant, changes the identity to `APPROVED`, enables the current Control Plane boot, records audit state, and writes the Grant-change outbox entry in the same database transaction. A stale session or any failed write aborts the transaction.

Existing identities are never upgraded by migration: V26 leaves existing records `LEGACY` (or marks already revoked records `REVOKED`). The API rejects any Agent credential whose approval state is neither `PENDING` nor `APPROVED`, so legacy credentials cannot use the new protocol. The UI exposes the combined approval form only for pending identities and loads certificate rotation controls only for approved Agents.

During r2 review, the Reviewer found that the separate business-action permission issuance service still accepted a pending Agent despite the UI hiding it. `BusinessActionPermissions.issue` now requires an `APPROVED` owner/Agent binding before both new issuance and idempotent replay, closing that authorization side path.

The Reviewer also identified that a loopback reverse proxy could make an external request appear local to `request.getRemoteAddr()`. LOCAL transport now rejects common forwarding headers, and the remote `agent-mtls` listener disables LOCAL profiles by default. Deployment guidance prohibits exposing LOCAL Agent API routes through public reverse proxies; this network isolation remains an operator responsibility.

## Evidence

- Independent r1 finding: [`FD-072-review-r1.md`](../reviews/FD-072-review-r1.md), event `FD-072-000006-changes-requested`.
- Updated implementation: `AgentIdentityService`, `AuthorityGrantService`, `AuthorityBoundary`, `AuthorityHttpConfiguration`, `AuthorityController`, Agent management page/controller, V26 migration, and pairing documentation/specification.
- Offline compile-only command, rerun after the review-time security fixes: Maven 3.8.1 Launcher with JDK 25.0.1, `-o -DskipTests compile`. Result: `BUILD SUCCESS`; 320 Java source files compiled.
- Static whitespace validation: `git diff --check` passed. Git only warned that three existing CRLF files may normalize to LF.
- Tests, application startup, browser interaction, mTLS certificate issuance/rotation, and deployment validation were not run. They are outside the authorized validation scope; the mTLS operational setup remains documented in `AGENT_MTLS_DEPLOYMENT.md`.

## Review handoff

This response is submitted for independent review of the r1 finding and its fixes. Formal verification for Work Item 1.7 remains pending until the Reviewer records `verification-passed`.
