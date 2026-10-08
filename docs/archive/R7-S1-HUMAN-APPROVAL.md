# R7-S1 — Human Approval Authority

Status: implementation candidate after accepted R6-S4 baseline.

## 1. Goal

R7-S1 turns a `PASSED` Final Engineering Gate into an explicit human approval authority without coupling the control plane to Telegram, Teams, Web UI, or any other interaction transport.

```text
EngineeringGate(PASSED)
        |
        v
ApprovalRequest(PENDING)
        |
        +--> APPROVED
        |
        +--> REJECTED
```

Human approval is not an AI execution attempt and is therefore not stored in `execution_attempt`.

## 2. Why a fifth table is justified

R3-R6 intentionally kept the core database at four tables. Approval is the first R7 concept that meets the agreed threshold for a new table because it has:

- its own lifecycle and waiting state;
- an external human actor;
- immutable authority/audit semantics;
- a lifetime independent of Worker leases and AI execution attempts.

R7-S1 adds exactly one table: `approval_request`.

## 3. Exact authority reference

An approval request is bound to one exact passed engineering result:

```text
sourceTaskId
resultRevision
engineeringGateClosedAt
```

The service never persists `latest-passed` as authority. `latest-passed` remains a convenience discovery query only.

A code result with `REPAIR_REQUIRED` cannot be submitted for approval.

## 4. Lifecycle

```text
PENDING
  |\
  | \
  |  +--> REJECTED
  +-----> APPROVED
```

Terminal decisions are immutable. Replaying the exact same decision/actor/note is accepted idempotently. A conflicting second decision is rejected.

One `source_task_id` has at most one approval request. Rejection of an immutable code result means a new code result/gate is required before another approval can exist.

## 5. Transport-neutral interaction boundary

Core approval does not import Telegram/Teams SDKs.

```text
ApprovalService
      |
      +--> ApprovalRequestRepository
      |
      +--> ApprovalNotificationPort
                    |
           +--------+--------+
           |        |        |
        Telegram  Teams    Web/CLI
```

Notification callbacks run only after the database transaction commits. A message transport cannot roll back the approval authority transaction.

R7-S1 records `channel` and optional `channelRef` as audit/routing metadata. Actual Telegram Bot integration belongs in the next adapter stage.

## 6. HTTP API

Create or idempotently retrieve the approval for an exact source gate:

```http
POST /api/r7/approvals
Content-Type: application/json

{
  "sourceTaskId": "WI-123~repair",
  "requestedBy": "control-plane",
  "channel": "TELEGRAM",
  "channelRef": "chat:123"
}
```

Decide:

```http
POST /api/r7/approvals/{approvalRequestId}/decision
Content-Type: application/json

{
  "decision": "APPROVE",
  "decidedBy": "human:s",
  "note": "Approved after engineering review"
}
```

Query:

```http
GET /api/r7/approvals/{approvalRequestId}
GET /api/r7/approvals/by-source/{sourceTaskId}
```

## 7. Persistence

Flyway V7 adds:

```text
approval_request
```

Core domain table count becomes five:

```text
worker
work_task
work_task_dependency
execution_attempt
approval_request
```

No Worker Protocol change is required.

## 8. Acceptance

`R7S1HumanApprovalIT` validates:

1. implementation result closes a PASSED EngineeringGate;
2. that exact gate creates a PENDING approval request;
3. repeated request is idempotent and returns the same approval id;
4. APPROVE stores exact human actor/note/time;
5. replay of the same approval is idempotent;
6. conflicting REJECT after APPROVE returns conflict;
7. query-by-source returns the same immutable authority.
