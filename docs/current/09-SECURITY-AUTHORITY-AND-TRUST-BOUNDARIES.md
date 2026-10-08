# Security, Authority and Trust Boundaries

## 1. Current security posture

The current R8-S13 baseline is designed primarily for local/trusted-internal operation. It contains important authority checks, but it is **not yet a complete production edge-security layer**.

The most important rule is to distinguish **engineering authority** from **network authentication**.

The system has strong internal ownership/state controls, but the general R6/R7/R8 management APIs are not currently protected by a comprehensive authentication/authorization framework.

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

When `AIW_WORKER_TOKEN` is non-empty, every Worker Protocol request requires:

```http
Authorization: Bearer <token>
```

The filter applies only to `/api/worker-protocol/...`.

Local mode may deliberately leave the token empty. Remote/shared operation should not.

## 4. Management API authentication limitation

Current endpoints such as:

```text
/api/r8/requirements
/api/r8/runs/...
/api/r8/fleet/...
/api/r7/approvals/...
```

are not protected by Spring Security in the current codebase.

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

R9 supports optional session authentication and per-session CSRF for `/ui/**` with VIEWER/OPERATOR/APPROVER/ADMIN roles. This is a browser-console boundary only; existing management REST APIs are not automatically protected by it. Default localhost binding remains the primary safe local mode.
