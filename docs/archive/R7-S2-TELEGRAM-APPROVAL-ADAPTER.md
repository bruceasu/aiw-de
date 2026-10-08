# R7-S2 — Telegram Approval Adapter

Status: implementation candidate after accepted R7-S1 baseline.

## 1. Goal

R7-S2 connects the transport-neutral Human Approval Authority from R7-S1 to Telegram without making Telegram part of the approval domain.

```text
EngineeringGate(PASSED)
        |
        v
ApprovalRequest(PENDING)
        |
        v
ApprovalNotificationPort
        |
        v
Telegram Bot API
   inline buttons
   /          \
Approve      Reject
   \          /
    callback_query
          |
          v
    ApprovalService.decide
          |
   APPROVED / REJECTED
```

The Telegram adapter expresses the human decision. `ApprovalService` remains the only authority that can commit the decision.

## 2. Why long polling first

The first Telegram transport uses Bot API `getUpdates` long polling instead of webhooks.

Reasons:

- local-first and internal-network operation;
- no public HTTPS endpoint is required;
- no additional reverse proxy or certificate configuration;
- easy local testing;
- the transport can later be replaced with a webhook adapter without changing Approval Domain APIs.

Telegram allows either `getUpdates` or webhooks for receiving updates, not both at the same time.

## 3. Security model

Enabling Telegram approval requires an explicit allow-list:

```text
AIW_TELEGRAM_ALLOWED_USER_IDS
```

Example:

```text
AIW_TELEGRAM_ALLOWED_USER_IDS=123456789,987654321
```

A callback from any other Telegram user is answered but cannot modify approval authority.

Callback payloads never carry a Git revision, subject, gate status, or approval decision record. They contain only:

```text
aiw:a:<approvalRequestId>
aiw:r:<approvalRequestId>
```

The Control Plane reloads the authoritative `ApprovalRequest` from the database before accepting a decision.

When a numeric `channelRef=chat:<id>` is present, the callback message chat must match it.

## 4. Environment configuration

Telegram is disabled by default.

```text
AIW_TELEGRAM_ENABLED=false
AIW_TELEGRAM_BOT_TOKEN=
AIW_TELEGRAM_CHAT_ID=
AIW_TELEGRAM_ALLOWED_USER_IDS=
AIW_TELEGRAM_API_BASE=https://api.telegram.org
AIW_TELEGRAM_POLL_TIMEOUT_SECONDS=30
AIW_TELEGRAM_BACKOFF_MS=1000
```

Typical local configuration:

```text
AIW_TELEGRAM_ENABLED=true
AIW_TELEGRAM_BOT_TOKEN=<bot token>
AIW_TELEGRAM_CHAT_ID=123456789
AIW_TELEGRAM_ALLOWED_USER_IDS=123456789
```

An individual ApprovalRequest can override the default chat by using:

```text
channel = TELEGRAM
channelRef = chat:123456789
```

## 5. Notification

When the approval transaction commits, the adapter sends a plain-text Telegram message containing:

```text
approvalRequestId
subjectType / subjectId
sourceTaskId
resultRevision
requestedBy
```

with an inline keyboard:

```text
[ Approve ] [ Reject ]
```

Transport failure cannot roll back the already committed ApprovalRequest.

## 6. Decision callback

The poller subscribes only to `callback_query` updates.

For each callback:

1. validate Telegram user id against the allow-list;
2. parse the small callback action;
3. reload the ApprovalRequest;
4. require `channel == TELEGRAM`;
5. validate numeric chat id when available;
6. call `ApprovalService.decide`;
7. call Telegram `answerCallbackQuery`;
8. remove the inline keyboard after a successful decision.

The actor stored in approval authority is:

```text
telegram:user:<telegramUserId>
```

Decision notes are deterministic so duplicate Telegram delivery remains compatible with R7-S1 idempotency.

## 7. Restart and duplicate updates

R7-S2 does not create a Telegram-offset table. The long-poll offset is in-memory transport state.

After a restart Telegram may redeliver an unconfirmed callback. This is safe because:

- ApprovalRequest decisions are immutable;
- exact replay is idempotent;
- conflicting decisions are rejected.

This keeps persistence at five core tables.

## 8. Database budget

No schema changes.

```text
worker
work_task
work_task_dependency
execution_attempt
approval_request
```

Flyway remains V7.

## 9. Acceptance

`R7S2TelegramApprovalIT` uses a local mock Telegram Bot API and validates:

1. a PASSED EngineeringGate creates a Telegram approval request;
2. the adapter sends an inline-keyboard notification;
3. callback data contains only the approval id/action;
4. an unauthorized Telegram user cannot decide the approval;
5. an authorized inline-button callback becomes durable APPROVED authority;
6. `decidedBy` records the exact Telegram user id;
7. the approved Git result revision remains the exact engineering-gate result.

No live Telegram network call is made by `mvn clean verify`.
