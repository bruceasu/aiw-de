# R6-S4 Final Engineering Gate

Status: implementation candidate pending `mvn clean verify`.

## Goal

R6-S3 already stops generating Repair work when Review and Verification both pass. R6-S4 turns that implicit condition into a durable, queryable engineering result that later Approval / Release stages can reference directly.

The gate belongs to one immutable code-change result, not to the project as a mutable global flag.

```text
IMPLEMENTATION result B
  -> Review PASS
  -> Verification REPAIR_REQUIRED
  -> Gate(B) = REPAIR_REQUIRED

REPAIR result C
  -> Review PASS
  -> Verification PASS
  -> Gate(C) = PASSED
```

The earlier Gate(B) is never rewritten to PASSED. Repair C has its own execution attempt and its own Gate(C).

## Authority

A gate is closed only when both deterministic downstream tasks are COMPLETED:

```text
source code-change task
      |\
      | \
      v  v
  Review  Verification
      \  /
       \/
 engineering gate
```

The existing source-task row lock serializes the join. `ReviewVerificationCompletedHandler` is the single completion authority for CODE_REVIEW / VERIFICATION and writes exactly one gate disposition for the source code result.

Gate status:

```text
PASSED
REPAIR_REQUIRED
```

`PASSED` requires both semantic reports to be PASS. Any REPAIR_REQUIRED verdict closes the current source gate as REPAIR_REQUIRED and materializes the existing Repair flow.

## Persistence

R6-S4 adds no table. V6 extends the completed source `execution_attempt` with:

```text
engineering_gate_status
engineering_gate_review_task_id
engineering_gate_verification_task_id
engineering_gate_closed_at_epoch_ms
```

The same source attempt already owns:

```text
base_revision
result_revision
publication_ref
changed_files
```

so no code-result evidence is duplicated.

Total domain tables remain:

```text
worker
work_task
work_task_dependency
execution_attempt
```

## Gate identity and immutability

The source task id is the stable gate reference. An IMPLEMENTATION or REPAIR task can close one gate for its final COMPLETED execution attempt.

Gate finalization is idempotent. Replaying the same Review/Verification completion may observe the same already-closed gate, but it cannot mutate the gate to a different status or point it at different evidence tasks.

## Query model

Read-only API:

```text
GET /api/r6/engineering-gates/{sourceTaskId}
```

returns the exact gate for one IMPLEMENTATION / REPAIR result.

```text
GET /api/r6/engineering-gates/latest-passed?subjectType=WORK_ITEM&subjectId=WI-123
```

returns the latest PASSED gate for a subject. This is a convenience lookup; future Approval / Release authority should persist the exact `sourceTaskId` it approved rather than rely on a mutable "latest" lookup during replay.

Gate response includes:

```text
sourceTaskId / sourceTaskType
subjectType / subjectId
status
repositoryRef
baseRevision / resultRevision
publicationRef
changedFiles
review task + verdict + summary
verification task + verdict + summary
closedAt
```

## Acceptance

`R6S4EngineeringGateIT` verifies:

1. an implementation with Review=REPAIR_REQUIRED and Verification=PASS closes a REPAIR_REQUIRED gate;
2. no PASSED gate exists for the subject yet;
3. the generated Repair produces a new code result;
4. the new Review + Verification both PASS;
5. the Repair result closes a distinct PASSED gate;
6. the original REPAIR_REQUIRED gate remains unchanged;
7. `latest-passed` resolves to the Repair result;
8. both read-only HTTP query surfaces expose the authoritative evidence.
