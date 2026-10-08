# R6-S3 Review / Verification Result -> Repair Loop

Status: ACCEPTED / `mvn clean verify` PASS.

## Goal

Turn semantic review/verification findings into durable repair work without allowing reviewer/verifier workers to modify the authoritative code result directly.

The execution state and semantic verdict are deliberately separate:

```text
worker execution succeeded  -> task state COMPLETED
review/verification verdict -> PASS | REPAIR_REQUIRED
```

`REPAIR_REQUIRED` is not a worker/runtime failure. It means the review or verification work completed successfully and found defects that require a new code-changing task.

## Completion report

Review and verification completion now carries:

```json
{
  "report": {
    "verdict": "PASS | REPAIR_REQUIRED",
    "summary": "human/agent readable evidence"
  }
}
```

The report is stored on the existing `execution_attempt` row:

```text
completion_verdict
completion_summary
```

R6-S3 therefore adds **zero tables**. Flyway V5 only adds these two columns.

## Parallel join semantics

Implementation or Repair completion fans out as before:

```text
CODE CHANGE
   |\
   | \
   v  v
 REVIEW  VERIFICATION
```

The first branch to complete does not create Repair. The handler waits until both branches are `COMPLETED`.

Then:

```text
review PASS + verification PASS
    -> no Repair

review REPAIR_REQUIRED + verification PASS
    -> one Repair

review PASS + verification REPAIR_REQUIRED
    -> one Repair

review REPAIR_REQUIRED + verification REPAIR_REQUIRED
    -> one Repair
```

The common source code-change task row is locked while joining the two branches. This prevents concurrent review and verification completions from either losing the join or creating duplicate Repair tasks.

## Repair task

A generated Repair depends on all three authoritative predecessors:

```text
source code-change task
review task
verification task
```

Its claim therefore receives `inputs[]` containing:

- the exact source result revision/publication reference;
- review verdict + summary;
- verification verdict + summary.

The Repair `baseRevision` is always the source code-change `resultRevision`. Reviewer/verifier worktree commits, if any, never become the Repair base.

## Loop

Repair is itself a code-changing task. When it completes:

```text
REPAIR result
   |\
   | \
   v  v
 REVIEW  VERIFICATION
```

If both new branches pass, the repair loop terminates. If either requests repair, another Repair task is materialized.

No handler directly invokes workers. Every new task returns to the READY pool and is claimed by a compatible idle worker.

## Worker contract

The five reference workers require review/verification agents to finish their final response with exactly one marker:

```text
AIW_VERDICT: PASS
```

or

```text
AIW_VERDICT: REPAIR_REQUIRED
```

The worker parses the marker and sends the structured completion report. Missing/invalid verdict markers are treated as worker execution failures instead of guessing a semantic result.

## Acceptance

`R6S3RepairLoopIT` verifies:

1. implementation creates parallel Review and Verification;
2. Review=`REPAIR_REQUIRED`, Verification=`PASS` creates exactly one Repair;
3. Repair depends on source + Review + Verification;
4. Repair claim receives both semantic reports in authoritative `inputs[]`;
5. Repair completion creates a fresh Review/Verification pair;
6. the next pair both PASS and does not create another Repair.
