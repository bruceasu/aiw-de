# Examples

## Example 1: vague operational request

Input:

> I want a program that monitors processes and lets me operate on abnormal ones.

Good analysis:

- `GOAL-001 [CONFIRMED]`: Operators need to find and manage monitored processes.
- `ACTOR-001 [INFERRED]`: Human operator.
- `FR-001 [INFERRED]`: The system must expose process status for managed processes.
- `FR-002 [INFERRED]`: The system must allow an authorized operator to perform supported management actions on a selected process.
- `Q-001 [BLOCKING]`: Which actions are allowed (start, stop, restart, delete registration, change alert settings, etc.)?
- `Q-002 [IMPORTANT]`: If multiple processes match, should actions apply to all, none, or an explicit subset?
- `SEC-001 [INFERRED]`: Destructive or state-changing operations require authorization.

Bad analysis:

> Build a Go CLI with Cobra, SQLite and a REST client.

Why bad: it jumps into implementation choices not stated by the user.

## Example 2: inferred bulk behavior

Input:

> If several processes match, let me choose all, cancel, or select several of them.

Correct derivation:

`FR-010 [CONFIRMED]`: When an operation targets multiple matching processes, the system must support choosing all matches, cancelling the operation, or selecting one or more specific matches.

Possible acceptance criteria:

- `AC-010a`: Given three matches, when the user chooses all, the selected operation targets all three.
- `AC-010b`: Given multiple matches, when the user cancels, no state-changing request is issued.
- `AC-010c`: Given multiple matches, when the user selects two specific entries, only those two become operation targets.

Do not invent a maximum selection count unless the source defines one.

## Example 3: requirement versus solution constraint

Input A:

> It has to run in Java 25 because that is our supported runtime.

Result:

`CON-001 [CONFIRMED]`: The implementation must run on Java 25.

Input B:

> I need a small local knowledge base that AI can search quickly.

Do not turn this into:

`CON-001: Use Lucene.`

Instead capture behavior:

- local operation
- searchability
- expected content types
- scale and latency questions

Lucene, ripgrep, ctags, embeddings, or another index belong to solution analysis unless explicitly mandated.

## Example 4: conversational uncertainty

Input:

> Maybe users should be able to retry failed jobs automatically. I am not sure yet.

Represent as:

`Q-020 [IMPORTANT]`: Should failed jobs be automatically retried?

`A-005 [UNKNOWN]`: Retry behavior is undecided.

Do not record automatic retry as a confirmed functional requirement.

## Example 5: user describes one feature but several responsibility domains exist

Input:

> When a customer asks to withdraw money, check it, send the payment, and tell the customer when it is done.

Requirement-stage analysis should discover responsibilities such as:

- request capture
- eligibility/authorization decision
- payment instruction
- settlement status tracking
- notification
- audit/reconciliation

Represent these as logical capabilities and boundary interactions. Investigate where authoritative account state and payment status live.

Do not conclude:

> Build five microservices.

The number of deployable components is an architecture decision, not a requirement.
