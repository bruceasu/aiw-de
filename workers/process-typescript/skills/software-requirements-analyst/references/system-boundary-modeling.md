# System Boundary and Logical Capability Modeling

Use this guidance when a user describes an end-to-end business flow without knowing which systems participate.

## Principle

Users usually describe desired outcomes, not system topology. Do not require them to know application boundaries.

Requirements analysis should discover **responsibility boundaries**. Architecture later decides **technical boundaries**.

## Boundary discovery sequence

1. Identify actors and goals.
2. Follow information and state changes end to end.
3. Identify authoritative sources of data.
4. Identify where decisions are made.
5. Identify trust/authorization boundaries.
6. Identify external ownership boundaries.
7. Identify asynchronous handoffs, reconciliation points, and failure ownership.
8. Group stable responsibilities into logical capabilities.
9. Mark the resulting system context as provisional when evidence is incomplete.

## Ask responsibility questions, not architecture questions

Prefer:

- Who owns the authoritative customer status?
- Which party decides whether a withdrawal is allowed?
- Which system must be notified after settlement completes?
- Who is responsible for retrying if the downstream party is unavailable?

Avoid asking nontechnical users:

- Should this be one service or three microservices?
- Which database should own this table?
- Should this use Kafka or REST?

## Logical capability boundary

A capability groups requirements that share a coherent business responsibility and vocabulary.

A capability may later map to:
- part of one application
- one application
- multiple services
- an external system

Never assume a one-to-one mapping.

## Cross-system responsibility matrix

For complex flows, record a matrix with columns like:

- Responsibility / decision
- Producer
- Consumer
- Authoritative owner
- Trigger
- Required outcome
- Failure owner
- Evidence state

This is a requirements artifact because ambiguity here changes observable behavior.

## Example

User statement:

> After a customer requests a withdrawal, check whether it is allowed, send it for payment, and notify the customer.

Reasonable requirement-stage decomposition:

- `CAP-001 Withdrawal Request Management`
- `CAP-002 Eligibility / Risk Decision`
- `CAP-003 Payment Instruction and Settlement Tracking`
- `CAP-004 Customer Notification`
- `CAP-005 Audit and Reconciliation`

Do **not** conclude that five services must exist.

Potential external boundaries may include:

- customer-facing channel
- account/trading platform
- payment/settlement provider
- notification provider

Whether each is an existing internal application, a new component, or an external provider is architecture/current-landscape information and should be confirmed separately.

## When capability partitioning happens

Perform capability partitioning **after business intent and system context are understood, but before detailed requirement cataloging is finalized**.

Why:

- too early: the analyst may mirror the user's vocabulary and miss hidden responsibilities
- too late: requirements become an unstructured flat list and cross-system gaps are harder to detect
- at this point: capabilities provide stable organization without constraining architecture

Technical module/service decomposition happens after SRS baseline, during architecture/design.
