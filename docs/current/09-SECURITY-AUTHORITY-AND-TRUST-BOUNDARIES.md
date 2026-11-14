# Security, Authority and Trust Boundaries

## 1. Current security posture

The current R8-S13 baseline is designed primarily for local/trusted-internal operation. It contains important authority checks, but it is **not yet a complete production edge-security layer**.

The most important rule is to distinguish **engineering authority** from **network authentication**.

The system has internal ownership/state controls and custom MVC authentication checks. Console and legacy management APIs require an administrator session and CSRF for writes. Dedicated Agent and Worker protocols retain their own machine authentication; they do not grant console access.

## 2. Trust boundaries

```text
Untrusted / external
        |
        |  should be terminated/authenticated before production use
        v
+--------------------------+
|      Control Plane       |
| lifecycle + authority    |
+------------+-------------+
             |
     Worker Protocol token
             |
             v
+--------------------------+
|       AI Workers         |
| local Git / AI runtime   |
+--------------------------+
```

## 3. Worker Protocol authentication

Factory Workers receive a separate ephemeral token automatically. To connect an external Worker, open Agent factory → External Worker access, create a credential for its stable Worker ID, and save the one-time token as `AIW_WORKER_TOKEN` on that device. Requests use:

```http
Authorization: Bearer <token>
```

MVC authentication applies to `/api/r1/worker-protocol/...`, including normalized route aliases. Credentials bind requests to one Worker ID. The database stores only token hashes; a lost token must be revoked and replaced. Stop new work allows existing work to finish; revoke denies further protocol traffic, without claiming the remote process stopped.

The default `AIW_BIND_ADDRESS=127.0.0.1` deployment automatically trusts direct local access and creates an administrator session with CSRF. Set `AIW_REMOTE_ACCESS=true` and `AIW_WEB_CONSOLE_PASSWORD` when opening remote access or using a reverse proxy, even if that proxy connects over loopback. Non-loopback bindings require login automatically. Remote connections require HTTPS; forwarded headers never enable automatic local trust. Only the administrator role is supported in this phase.

External Workers set `AIW_WORKER_ID`, `AIW_WORKER_TOKEN` and an HTTPS `AIW_CONTROL_PLANE_URL`; loopback HTTP is allowed. They manage their own model credentials. The old Plane-wide `aidev.worker-protocol.token` setting is no longer used. A TLS-enabled Plane launches local Workers with HTTPS; when its certificate needs a named host, configure `aidev.agent-factory.control-plane-url` and standard CA trust rather than disabling certificate verification.

## 4. Management API authentication limitation

Current endpoints such as:

```text
/api/r1/requirements
/api/r1/runs/...
/api/r1/fleet/...
/api/r1/approvals/...
```

use the same administrator session boundary as console APIs, including reads. Writes also require CSRF. External Workers must use the dedicated Worker protocol, not these management APIs. Local administrator trust does not remove individual dangerous-operation confirmation or task ownership checks.

Therefore:

- keep the default bind `127.0.0.1` for local use;
- for a server deployment, place these APIs behind an authenticated internal gateway/reverse proxy or add application authentication before exposing them;
- do not bind `0.0.0.0` on an untrusted network and assume Worker Protocol token protects all APIs—it does not.

## 5. Task ownership security

A task execution action must match the current:

- task ID;
- worker ID;
- lease token;
- allowed state.

Completion/failure/heartbeat is therefore bound to the specific ownership attempt. A stale worker cannot normally complete a task after pause/cancel/lease loss because its ownership token/state no longer matches.

## 6. Human approval authority

Approval is deliberately separate from AI engineering output.

An ApprovalRequest binds:

- exact source task;
- exact result revision;
- engineering-gate close time;
- requester and channel;
- immutable final decision actor/time.

A reviewer/verifier/worker cannot self-authorize a human approval by returning a special message.

## 7. Telegram controls

When Telegram is enabled:

- Bot token is required;
- allowed user IDs are required;
- callback user ID is checked;
- approval request must be a TELEGRAM-channel request;
- numeric chat identity is checked when an expected chat is available;
- the resulting audit actor is `telegram:user:<id>`.

Telegram remains only a transport. `ApprovalService` owns the decision.

## 8. Git trust and execution

Workers execute AI tooling against local Git repositories and worktrees. Operationally:

- give workers access only to repositories they need;
- use dedicated OS accounts/containers where practical;
- treat `AIW_CODEX_NETWORK_ACCESS` and sandbox policy as security-sensitive;
- protect Git credentials used by `AIW_GIT_PUBLISH_REMOTE`;
- avoid putting secrets in task instructions or completion summaries;
- ensure repository hooks/build scripts are trusted, because verification/coding workers may execute project commands.

## 9. Secrets

Sensitive values include:

- `AIW_WORKER_TOKEN`
- `AIW_DB_PASSWORD`
- `AIW_TELEGRAM_BOT_TOKEN`
- Git credentials
- AI runtime credentials

The current project consumes them from environment/configuration. Production deployment should supply them through the organization’s secret-management mechanism rather than committed files.

## 10. Security work still required for production exposure

Recommended future hardening:

1. authenticated and authorized management API;
2. TLS/mTLS/network policy appropriate to deployment;
3. role separation for requester/operator/approver;
4. secure secret rotation;
5. audit export/retention policy;
6. worker sandbox/OS permission hardening;
7. explicit tool/Skill permission governance;
8. security-focused threat model and penetration testing.

## Web Console security

All console business pages under `/ui` and `/ui/**` require real session authentication, with per-session CSRF and VIEWER/OPERATOR/APPROVER/ADMIN roles. Only login and its designated public resources bypass the browser gate; the former enabled=false mode cannot grant anonymous authority. This is a browser-console boundary only; existing management REST APIs are not automatically protected by it. Default localhost binding remains the primary safe local mode.
