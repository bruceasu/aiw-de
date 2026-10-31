# FD-073: Align Secretary Agent pairing UI and documentation

**Status:** Complete
**Revision:** 2
**Review policy:** Self
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

The Secretary Agent setup card currently renders its heading, instructions and controls as literal question marks, so users cannot follow the pairing flow. The Secretary README still describes the retired local-confirmation and reusable-process-session flow, while the accepted Agent contract requires one Control Plane confirmation and a disabled new process after restart. The Control Plane API guide also lacks a current Agent pairing/authentication section. The Secretary README's mTLS deployment link points to the FD-072 pre-archive path.

## Options and decision

Keep the Agent protocol and authorization behavior from FD-072 unchanged. Repair the Secretary setup copy and status messages, and update the current Control Plane API guide and Secretary README to describe the same pairing, approval, restart, LOCAL and REMOTE rules. Keep FD-072 archived as historical design and evidence; link to its archived deployment guide instead of rewriting it.

## Solution

1. Replace the corrupted Agent setup card text with readable Chinese that explains one-time pairing, pending approval, loopback-only LOCAL connections, and HTTPS/mTLS for REMOTE connections.
2. Show distinct Secretary status/feedback for a pending identity and an approved identity whose current process still needs enablement. Do not add a Secretary-side approval or enable action.
3. Update the Control Plane API guide with generic Agent identity, route/authentication boundaries, the single human approval transaction, disabled restart/rotation behavior, and Agent-initiated communication.
4. Replace the obsolete connection instructions in the Secretary README, retain unrelated plan/action scope guidance, and fix its archived deployment-guide link.
5. Remove the obsolete `AIW_PERSONAL_CREDENTIAL` prompt from startup scripts and strip that legacy variable from the launched process; continue using paired Agent settings or `AIW_AGENT_CREDENTIAL` for an explicitly configured Agent.

## Scope

Secretary pairing UI and its status projection; retirement of the unused legacy credential prompt; `personal-secretary/README.md`; the current Chinese Control Plane API guide. Existing Owner/Worker/platform startup parameters remain unchanged. No protocol, permission, persistence, certificate, migration or route behavior changes. No edits to archived FD-072 artifacts or stable OpenSpec requirements.

## Work items

- [x] 1.1 Restore readable Secretary Agent setup copy and make pairing/approval status messages distinguish pending approval from a disabled current process.
- [x] 1.2 Align the Secretary README and Control Plane API guide with the accepted Agent protocol; repair the deployment-guide link.
- [x] 1.3 Remove the obsolete personal credential prompt from the startup scripts while preserving explicit loopback HTTP consent and the Agent credential path.

## Acceptance

- The Secretary Agent settings card displays readable Chinese labels and instructions.
- Pending pairing directs the user to one Control Plane approval that binds the Agent, exact initial Grant and current-process enablement; Secretary does not request a second authority confirmation.
- An approved but disabled process is described as requiring Control Plane re-enablement; restart or certificate rotation does not silently restore authority.
- LOCAL is loopback-only; REMOTE requires HTTPS and mTLS. Control Plane communication remains Agent-initiated and does not call back into the Secretary service.
- The Control Plane API guide and Secretary README agree with `openspec/specs/control-plane-api/spec.md` and `openspec/specs/secretary/spec.md`; the deployment link resolves to the archived FD-072 document.
- No pairing or authorization behavior is weakened or broadened.
- Startup scripts do not ask for or pass the obsolete `AIW_PERSONAL_CREDENTIAL`; an explicitly configured Agent continues to use its paired settings or `AIW_AGENT_CREDENTIAL`.

## Verification

- Passed: `& .\personal-secretary\scripts\compile-secretary-desktop.ps1` (Python and JavaScript syntax plus PowerShell/Bash launcher parsing; no imports, runtime, tests, network or packages).
- Attempted: `& .\scripts\compile-personal-secretary.ps1`; it stopped before source compilation because `mvn` is not installed in this environment.
- Passed: final static review of the changed files and relevant Control Plane/Secretary OpenSpec contracts; `git diff --check` clean, stale connection claims reconciled, and archived deployment link resolves.
- Not authorized: browser interaction, service startup, network/mTLS pairing, tests or end-to-end authorization checks.

## Sources

- `openspec/specs/control-plane-api/spec.md` — generic Agent authority, one-time approval, mTLS and Agent-initiated inbox.
- `openspec/specs/secretary/spec.md` — replaceable Agent client and private-service boundary.
- `docs/features/archive/FD-072/AGENT_MTLS_DEPLOYMENT.md` — deployment and rotation reference.
- `personal-secretary/ui/index.html`, `personal-secretary/ui/app.js`, `personal-secretary/platform_setup.py`, and the Secretary startup scripts.

**Completed:** 2026-10-31
