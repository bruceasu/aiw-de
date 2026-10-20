## Purpose

Define single-machine secretary discussion, one-time arrangements and bounded
independent Plan coordination. Implementation reference: FD-021.

## Requirements

### Requirement: Saved work and advisory discussion
The system SHALL attribute new secretary work and messages to the current
console actor. It SHALL keep each work item's context separate from Goal
discussion. Model suggestions SHALL NOT save or confirm an arrangement.

#### Scenario: Discussion has no available Worker
- WHEN a message is saved without an available secretary Worker
- THEN its real request ID and queued task remain queryable without claiming a reply or execution

### Requirement: Human-confirmed one-time admission
The system SHALL support an owned existing draft Plan and a local reminder.
Confirmation SHALL bind the saved input, Plan specification/version, current
operator authority, explicit date, offset and IANA zone to one arrangement.
Arbitrary unsupported professional work SHALL NOT become executable by chat.

#### Scenario: Time arrives within its target minute
- WHEN the confirmed target minute arrives and the frozen input remains valid
- THEN the Plan's coordinator request is queued at most once, or a local reminder is recorded as due
- AND queue admission SHALL NOT imply professional execution has started

#### Scenario: Target minute was missed
- WHEN admission is attempted after the target minute
- THEN the arrangement requires a new human confirmation and SHALL NOT catch up automatically

#### Scenario: Input or authority changes
- WHEN saved work, the linked Plan or current operator authority differs from confirmation
- THEN new admission or advancement SHALL be blocked pending human handling
- AND the system SHALL show current content for a new confirmation

### Requirement: Independent bounded coordination
Secretary-managed Plans SHALL use a separate coordinator identity before initial
start and after each professional completion. A coordinator SHALL NOT execute
or review that Plan's professional tasks. Its output SHALL only request the
platform's fixed continuation or human attention. Development review SHALL
continue to use the original professional reports and independent review path.
Old unmanaged Plans SHALL retain their existing lifecycle.

#### Scenario: Duplicate or stale coordination
- WHEN a coordinator response was consumed, was superseded, or refers to changed input
- THEN it SHALL NOT start another task or modify the confirmed Plan

#### Scenario: Direct legacy control attempts to start managed work
- WHEN an old confirm, retry, jump, repair or generic correction path would admit work for a managed Plan
- THEN it SHALL be rejected outside the bounded server-side coordination scope

### Requirement: Cancellation and truthful results
Cancellation SHALL prevent further admission and advancement. Running tasks
SHALL still be able to record completion or failure. Cancellation SHALL NOT
claim that a task stopped or its side effects were reversed. Failed or
interrupted professional work SHALL require human handling instead of implicit
re-execution. Feedback and result queries SHALL NOT repeat business actions.
Automatic analysis and external notification delivery are outside this scope.

#### Scenario: Running work finishes after cancellation
- WHEN a running Worker reports its true result after a cancellation request
- THEN the result remains recorded and no later step is admitted

## Limits

With authentication disabled, local-console is shared. This capability does
not introduce multi-user isolation, historical ownership migration,
recurring/conditional schedules or long-term memory. Explicit agent permission
grants are defined separately in `../delegated-decisions/spec.md`.
