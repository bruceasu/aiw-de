# R7-S3 — Automatic Approval Policy

## Purpose

R7-S3 closes the gap between an immutable `PASSED` Engineering Gate and human approval materialization.
The Development domain publishes a finalized-gate fact through `EngineeringGateFinalizedListener`; the
Approval domain interprets policy and, when required, creates exactly one durable `ApprovalRequest`.

The Control Plane still owns authority. Telegram/Teams/Web/CLI remain transports only.

## Policy modes

```text
MANUAL    PASSED gate does not automatically create approval. Existing HTTP/CLI/operator request flows remain valid.
REQUIRED  every PASSED gate automatically creates one PENDING ApprovalRequest in the same transaction.
```

Default is `MANUAL` for backward-compatible R7 adoption.

Environment configuration:

```text
AIW_APPROVAL_POLICY=MANUAL|REQUIRED
AIW_APPROVAL_REQUESTED_BY=control-plane:approval-policy
AIW_APPROVAL_CHANNEL=API|TELEGRAM|TEAMS|WEB|CLI
AIW_APPROVAL_CHANNEL_REF=
```

Example for Telegram:

```text
AIW_APPROVAL_POLICY=REQUIRED
AIW_APPROVAL_CHANNEL=TELEGRAM
AIW_APPROVAL_CHANNEL_REF=chat:123456789
AIW_TELEGRAM_ENABLED=true
AIW_TELEGRAM_BOT_TOKEN=...
AIW_TELEGRAM_ALLOWED_USER_IDS=123456789
```

## Transaction semantics

```text
Review + Verification complete
        ↓
Engineering Gate finalized
        ↓
EngineeringGateFinalizedListener
        ↓
ApprovalPolicy
        ↓ REQUIRED + PASSED
ApprovalService.request(...)
        ↓
PENDING approval persisted
        ↓ transaction commit
ApprovalNotificationPort notified
```

The gate and required approval materialization therefore commit atomically. Notification remains after-commit and
cannot roll back authority if Telegram/Teams delivery fails.

## Idempotency

`approval_request.source_task_id` remains unique. Gate listener replay, transaction retry, or repeated policy
observation returns the already-authoritative ApprovalRequest rather than creating duplicates.

## Persistence

No new table and no new migration are required. R7-S3 remains at five core tables and Flyway V7.
