# FD-087: Local Secretary automatic enrollment and remote Agent pairing

**Status:** Complete
**Revision:** 11
**Priority:** High
**Review policy:** Independent
**Evidence policy:** Markdown

## Problem

The Secretary currently connects to the Control Plane only through an Agent pairing invitation. Local Agent identities also wait for human approval and each new process remains disabled until explicitly enabled. This makes a same-device Secretary appear unregistered or unavailable for Plan and Task management, despite the user choosing a local Control Plane.

The user confirmed two connection policies:

- A local Secretary is automatically trusted by the local Control Plane, registered to its configured default administrator identity (default username `admin`), and receives the built-in Plan and Task management permissions. No interactive Owner login or pairing code is required.
- A remote Secretary keeps the existing one-time Agent pairing and mandatory mTLS flow. Do not add OAuth, PAT, or Owner password storage in this FD.

## Options and decision

1. Keep the same manual invitation and approval flow for local and remote Agents. This does not meet the confirmed local behavior.
2. Add a loopback-only local enrollment and session path while preserving remote invitation, explicit approval, HTTPS, and mTLS. **Chosen.**

The local identity is associated with the configured Web Console administrator actor (`web:<configured username>`, default `web:admin`). Plane must have its administrator password and `ADMIN` role configured; the Secretary does not request or store that password and does not create a browser login. The existing Plan and Task default-permission policy remains the source of the granted actions; unrelated administrative capabilities are excluded.

## Solution

- The Secretary UI offers local automatic connection for an explicitly entered loopback HTTP Control Plane origin. Remote HTTPS continues to use the existing one-time pairing code.
- A new local enrollment API is accepted only from a direct loopback request, rejects forwarding/proxy headers and browser-originated requests, and is unavailable on remote mTLS listeners.
- The Secretary creates and privately persists a stable local Agent ID and high-entropy Agent credential before enrollment. Enrollment is idempotent for that identity/credential pair, so a lost response can be retried without creating duplicate Agents or rotating an unknown credential.
- LOCAL HTTP requests bypass environment-configured proxies so the loopback credential is sent directly to the local Plane. REMOTE transport retains its existing proxy behavior.
- The Control Plane associates local enrollment with its configured administrator actor, creates or reuses that Owner, and registers an approved LOCAL Agent. It does not accept an Owner ID from the client.
- A LOCAL Agent session automatically enables a newly started process and ensures the existing Plan and Task default permissions. If the Plane restarts while the same Secretary process is running, a previously enabled LOCAL session is restored on re-negotiation; an explicit human disable (`enabled_boot` cleared) remains disabled. Existing valid LOCAL identities, including pending local pairings, follow this policy on their next authenticated loopback session. A revoked identity is not revived.
- Remote pairing, certificate validation/rotation, human approval, and process enablement stay unchanged. No fallback from remote to local is allowed.
- If local registration or Plane availability fails, the Secretary stays usable for local functions and reports the Plane connection as unavailable; no Plan/Task operation is sent without a confirmed session.

## Scope

Implement the Control Plane enrollment/session path, Secretary local connection and startup flow, UI status, and nearby stable specifications/documentation. Preserve existing dirty work in shared source files and stage only this FD's changes.

Out of scope: OAuth/PAT, storing Owner passwords/cookies, changing remote mTLS, granting unrelated global/admin actions, publishing the local endpoint through a reverse proxy/LAN, and changing the professional Worker protocol.

## Work items

- [x] 1.1 Add loopback-only, idempotent local Agent enrollment bound to the configured administrator actor; automatically approve and enable valid LOCAL sessions and ensure Plan/Task defaults without changing REMOTE behavior.
- [x] 1.2 Add Secretary local enrollment using a persisted Agent identity/credential, automatically reconnect LOCAL profiles on startup, and keep remote pairing unchanged.
- [x] 1.3 Update the Secretary connection UI, stable Secretary/Control Plane API specifications, and deployment/operation documentation to describe local auto-trust and remote mTLS.
- [x] 1.4 Perform static call-path review and authorized compile-only checks; record tests not run and remaining deployment risks.

## Acceptance

1. A first-time local connection to an explicit loopback Control Plane with its administrator account configured succeeds without an invitation code or interactive Owner login.
2. The Plane records the Secretary as a LOCAL Agent owned by its configured administrator actor (default `web:admin`), and exposes the identity in the Agent management page.
3. The first and subsequent LOCAL process sessions are enabled automatically and have all existing default Plan/Task actions; after a Plane restart, a previously enabled process can restore its session without restarting Secretary. An explicit current-process disable remains effective, and no unrelated administrative permissions are added.
4. Repeating enrollment with the same persisted local identity is safe and does not create duplicates or rotate a credential after an unknown response.
5. LOCAL enrollment from a non-loopback peer, via forwarding/proxy headers, from a browser Origin, or on a remote mTLS-only listener is rejected.
6. A revoked local Agent is not restored by automatic enrollment. An unavailable Plane does not stop the Secretary's local functionality or present a false connected state.
7. REMOTE continues to require one-time pairing, HTTPS, a trusted client certificate, human approval, and explicit process enablement. No OAuth/PAT or Owner credential is introduced.
8. Valid existing LOCAL identities can connect under the new local policy on their next authenticated loopback session; REMOTE identities and grants are unaffected.

## Verification

- Static self-check traced the local loopback registration endpoint through identity binding, process-session enablement and existing Plan/Task permission defaults. The remote invite, mTLS validation, human approval and enablement paths remain separate. Secretary startup persists enrollment identity before network access, bypasses configured proxies only for LOCAL HTTP transport, and reports connection only after a confirmed session. LOCAL status checks renegotiate a disabled session once; the Plane restores it after restart only when the stored prior enablement is non-null, while an explicit disable remains in force.
- Compile-only/static checks: Java `mvn` compile using the local Maven 3.9.9 launcher and JDK 25 passed (343 production sources); Python AST syntax parsing for `platform_setup.py`, `platform_client.py`, and `main.py` passed after the final Python source change; `node --check personal-secretary/ui/app.js` passed; `git diff --check` passed before the final LOCAL proxy-bypass adjustment.
- Tests and live local/remote Plane validation were not run under the repository's default runtime-validation budget. The separate Java compile did not compile or run tests.
- Residual risk: a live Plane startup/restart, local enrollment, remote mTLS certificate issuance/rotation, and browser UI flow remain unverified. Any process able to issue direct loopback requests is within the confirmed local auto-trust boundary; keep the local listener loopback-only and the remote mTLS listener's LOCAL profile disabled. The Plane administrator password and `ADMIN` role must already be configured, though Secretary never asks for or stores that password.
- Independent review is required because this changes registration, authentication boundaries, permissions, persistence behavior, and cross-module API contracts.
- The first independent review, for source event `FD-087-000004-review-requested`, requested changes for LOCAL session recovery after a Plane restart and proxy bypass for LOCAL HTTP credentials; see [review r1](reviews/FD-087-review-r1-20261109.md). Both findings are addressed in the current implementation: a previously enabled LOCAL process renegotiates after Plane restart, explicit disable remains in force, and LOCAL HTTP transport bypasses proxies while REMOTE retains existing proxy behavior. This revision is being returned for focused independent re-review; it is not yet passed.
- The focused re-review for source event `FD-087-000007-implementation-ready` found that `allow_loopback_http` could also bypass the proxy for HTTPS REMOTE startup; see [review r2](reviews/FD-087-review-r2-20261109.md). The direct-opener condition was narrowed after that handoff, so the correction still needs review against a new implementation-ready event.
- The re-review for source event `FD-087-000010-implementation-ready` passed the direct-opener correction, LOCAL Plane-restart recovery/manual-disable handling, and the unchanged REMOTE mTLS/approval boundaries; see [review r3](reviews/FD-087-review-r3-20261109.md). Tests and live validation remain unrun.

## Sources

- User-confirmed local and remote connection policy in this conversation.
- [`FD-072_AGENT_CONTROL_PLANE.md`](../archive/FD-072/FD-072_AGENT_CONTROL_PLANE.md) and [`AGENT_MTLS_DEPLOYMENT.md`](../archive/FD-072/AGENT_MTLS_DEPLOYMENT.md).
- `openspec/specs/secretary/spec.md`, `openspec/specs/control-plane-api/spec.md`.
- `personal-secretary/platform_setup.py`, `personal-secretary/platform_client.py`, `personal-secretary/main.py`.
- `AgentIdentityService`, `AuthorityHttpConfiguration`, `AgentTransportSecurity`, `BusinessActionPermissions`, and `WebConsoleSecurityService`.

**Completed:** 2026-11-09
