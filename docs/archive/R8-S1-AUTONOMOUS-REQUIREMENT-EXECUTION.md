# R8-S1 — Autonomous Requirement Execution

Status: implementation candidate built on accepted R7-S3.

## Goal

R8-S1 turns one repository-scoped natural-language requirement into a pull-driven engineering task graph without reintroducing a central pipeline invoker.

```text
POST requirement
      |
      v
REQUIREMENT_ANALYSIS
      |
      v
SOLUTION_ARCHITECTURE
      |
      v
IMPLEMENTATION_PLANNING
      |
      v
IMPLEMENTATION
      |
      +--> CODE_REVIEW
      +--> VERIFICATION
               |
               v
        Engineering Gate
               |
               v
        Approval Policy
```

Every arrow means persisted Task + Dependency materialization. No completion handler invokes a Worker. Workers continue to pull compatible READY tasks.

## Persistence model

R8-S1 adds no table and no migration. Core tables remain:

1. `worker`
2. `work_task`
3. `work_task_dependency`
4. `execution_attempt`
5. `approval_request`

All tasks in one autonomous run use:

```text
subjectType = AUTONOMOUS_REQUIREMENT
subjectId   = <runId>
```

The run is therefore a projection over the existing task graph rather than a new lifecycle aggregate.

## Intake API

```http
POST /api/r8/requirements
```

Example:

```json
{
  "requestId": "health-endpoint-001",
  "requirement": "Add a health endpoint and tests without breaking existing behavior.",
  "repositoryRef": "core",
  "baseRevision": "<full immutable git SHA>",
  "technicalCapabilities": ["java", "maven"],
  "priority": "HIGH"
}
```

Response:

```json
{
  "runId": "AR-health-endpoint-001",
  "requirementTaskId": "AR-health-endpoint-001~requirement"
}
```

If `requestId` is omitted, the Control Plane generates one. Reusing the same requestId is idempotent only when the authoritative task contract is identical; incompatible reuse is rejected.

R8-S1 is intentionally repository-scoped. Greenfield project/repository creation is not part of this increment.

## Durable semantic handoff

Requirement analysis, architecture, and implementation planning do not primarily produce Git changes. Their durable output uses the already-existing completion report fields in `execution_attempt`:

```text
completion_verdict = PASS
completion_summary = <agent final response>
```

The next Worker receives that content through existing `inputs[].completionSummary`.

The Control Plane rejects completion of these three task types unless a PASS report with a non-empty summary is supplied. This prevents a task from becoming COMPLETED while its downstream engineering artifact is missing.

## Dependencies

Planning and implementation deliberately receive cumulative authoritative inputs:

```text
Architecture depends on:
  Requirement

Planning depends on:
  Requirement
  Architecture

Implementation depends on:
  Requirement
  Architecture
  Planning
```

This avoids relying on an agent to perfectly restate all earlier engineering context in every intermediate document.

## Capabilities

Role capability changes by stage while technical and repository capabilities survive:

```text
requirement-analysis
solution-architecture
implementation-planning
coding
code-review
verification
```

For example, a Java/Maven repository run materializes tasks requiring:

```text
java + maven + repo:core + <stage-role>
```

This allows separate AI employees to specialize by role while sharing the same protocol.

## Worker output contract

The five reference Workers now report `PASS + final response` for:

```text
REQUIREMENT_ANALYSIS
SOLUTION_ARCHITECTURE
IMPLEMENTATION_PLANNING
```

Review and Verification keep the existing explicit marker contract:

```text
AIW_VERDICT: PASS
AIW_VERDICT: REPAIR_REQUIRED
```

## Acceptance

`R8S1AutonomousRequirementIT` verifies one full run:

```text
Requirement
 -> Architecture
 -> Plan
 -> Implementation
 -> Review PASS
 -> Verification PASS
 -> EngineeringGate PASSED
 -> ApprovalRequest PENDING (REQUIRED policy)
```

It also verifies that architecture receives the requirement summary, planning receives both prior summaries, and implementation receives all three authoritative planning inputs.

## Non-goals

R8-S1 does not yet add:

- greenfield repository creation;
- decomposition into multiple WorkItems / parallel coding tasks;
- autonomous release/deployment;
- automatic approval bypass;
- a separate autonomous-run table;
- a new Artifact table.

The next increment should focus on plan decomposition into multiple implementation tasks while preserving the same pull-based execution model.
