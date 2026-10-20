# Handoff: AI Worker: Digital Employee— AI Secretary & Scheduling Architecture

**Status:** Architecture proposal / Pending implementation
**Baseline:** Web Console
**Target:**  AI Secretary, Persistent Scheduling, Specialized Worker Orchestration
**Date:** 2026-10-20

## 1. Project Background

We are continuing the AI Dev Manager project.

The current objective is to introduce a persistent, conversational **AI Secretary** capable of receiving natural-language instructions, scheduling future work, coordinating specialized AI Workers, tracking execution, and proactively notifying the user of results.

The AI Secretary should behave like a personal technical assistant rather than a simple chatbot.

Example user instruction:

> 明天下午 3 点，在 SIT 环境部署 FO API 2.2.0 版本。完成后通知我。如果失败，提供分析报告和建议。

Expected behavior:

1. Understand the user's instruction.
2. Resolve the execution time and timezone.
3. Identify the application, target environment, and version.
4. Validate the requested operation and permissions.
5. Create a persistent scheduled task.
6. Confirm the arrangement to the user.
7. Trigger the deployment at the scheduled time.
8. Delegate execution to a specialized Deployment Engineer Worker.
9. Monitor execution through platform events.
10. Notify the user of success or failure.
11. If deployment fails, delegate diagnosis to an Analysis Worker.
12. Provide a failure report with evidence and recommendations.
13. Support follow-up conversations, task cancellation, rescheduling, and additional actions.

The user should be able to interact with the Secretary at any time, including while scheduled tasks are pending or running.

## 2. Frozen Baseline

The accepted baseline is **R9 Web Console Fix1**.

| Component         | Baseline                          |
| ----------------- | --------------------------------- |
| Java namespace    | `me.asu.aidev`                    |
| Java              | 21                                |
| Spring Boot       | 4                                 |
| Spring Framework  | 7                                 |
| Build             | Maven                             |
| Architecture      | Decentralized Worker Pull         |
| Persistence       | Existing R9 database / Flyway V14 |
| Web Console       | Implemented and accepted          |
| Integration tests | 33 Failsafe IT methods passed     |

Existing capabilities include:

- Worker Pull and Lease Ownership
- External Worker Protocol
- Published Result Handoff
- Parallel Code Review and Verification
- Repair Loop
- Final Engineering Gate
- Human Approval Authority
- Telegram Approval Adapter
- Automatic Approval Policy
- Autonomous Requirement Execution
- Parallel Implementation and Integration
- Run Pause / Resume / Cancel
- Run Timeline and Observability
- Fleet / Backlog Dashboard
- Scheduling Fairness and Priority
- Run Concurrency Budget
- Fleet Admission Budget
- Runtime Fleet Scheduling Control

**Do not redesign or replace these existing capabilities.**

R10 must extend the accepted architecture.

The R9 baseline remains frozen unless a change is explicitly necessary and approved.

## 3. Core Architecture Decision

Adopt:

**AI Secretary Worker + Platform Scheduler + Specialized Execution Workers**

The Scheduler must be a deterministic platform component, not a normal AI Worker.

The AI Secretary is responsible for interpreting user intent and coordinating work.

The Platform Scheduler is responsible for durable, reliable time-based triggering.

Specialized Workers are responsible for actual execution.

### Responsibility boundaries

| Component            | Responsibility                                               |
| -------------------- | ------------------------------------------------------------ |
| AI Secretary         | Conversation, intent interpretation, planning, delegation, user communication |
| Scheduler            | Persistent schedules, trigger calculation, due-task dispatch |
| Control Plane        | Authorization, approval, admission, execution authority, task lifecycle |
| Deployment Engineer  | Deployment execution and verification                        |
| Failure Analyst      | Log analysis, diagnosis, recommendations                     |
| Notification Service | Reliable delivery of user notifications                      |

The Secretary must not bypass Control Plane authorization or execute privileged operations directly.

## 4. High-Level Architecture

```
                 User
                  |
          Telegram / Web UI
                  |
                  v
          Conversation Gateway
                  |
                  v
          AI Secretary Worker
                  |
       +----------+-----------+
       |                      |
       v                      v
Conversation Memory     Task Management API
                              |
                              v
                      Java Control Plane
                              |
             +----------------+----------------+
             |                |                |
             v                v                v
         Scheduler         Approval          Task Registry
             |              Policy              |
             +----------------+----------------+
                              |
                              v
                      Run / WorkItem
                              |
                              v
                  Existing Admission Control
                              |
                              v
                     Existing Worker Pull
                              |
                 +------------+------------+
                 |                         |
                 v                         v
          Deployment Engineer       Other Workers
                 |
                 v
           Deployment Tools
           Jenkins / AWS / S3
                 |
                 v
          Execution Result Event
                 |
          +------+------+
          |             |
       Success        Failure
          |             |
          |             v
          |       Failure Analyst
          |             |
          +------+------+
                 |
                 v
         Notification Service
                 |
                 v
            AI Secretary
                 |
                 v
               User
```

The diagram is conceptual. Final component boundaries and event routing must be reconciled with the actual R9 codebase.

## 5. AI Secretary Design

### 5.1 Logical identity

The Secretary is a persistent logical Agent, not necessarily a continuously running process.

It should have a stable identity and support long-running conversations.

It can be activated by:

- Incoming user messages
- Scheduled task events
- Worker completion events
- Failure events
- Approval events
- Relevant task state changes

The Secretary does not need to keep an LLM session or operating-system process running while waiting.

### 5.2 Memory model

Separate the following information:

**Conversation Memory**

- Recent conversation history
- Summarized historical context
- Relevant previous instructions

**User Preferences**

- Preferred timezone
- Default notification channel
- Common applications and environments
- Explicitly configured approval preferences

**Task Registry**

- Scheduled tasks
- Running tasks
- Completed tasks
- Cancelled tasks
- Failed tasks

**Execution History**

- Deployment results
- Logs and artifact references
- Analysis reports
- Recommendations
- Approval decisions

Task state must never depend solely on LLM memory.

The database is the authoritative source for operational state.

### 5.3 Conversation capabilities

The Secretary should understand requests such as:

> 明天下午 3 点部署 FO API 2.2.0 到 SIT。

> 我今天安排了什么任务？

> FO API 部署怎么样了？

> 取消下午的部署。

> 改到明天上午 10 点。

> 为什么部署失败？

> 根据失败报告安排修复。

> 每天早上检查 Jenkins 构建状态。

The Secretary must resolve task references through persisted task records.

It must not invent task status, execution results, or schedule identifiers.

## 6. Scheduling Architecture

### 6.1 Platform Scheduler

Implement the Scheduler as a module inside the Java Control Plane.

Initial implementation should use:

- Java 21
- Spring Scheduling
- Existing database infrastructure
- PostgreSQL locking where PostgreSQL is the selected backend
- Existing Run / WorkItem creation mechanisms

Avoid introducing Quartz, Redis, Kafka, or another orchestration platform unless there is a demonstrated requirement.

### 6.2 Supported schedule types

**One-Time Schedule**

Execute once at a specific timestamp.

**Recurring Schedule**

Execute according to a Cron expression and timezone.

**Delayed Schedule**

Execute after a specified delay.

**Conditional Follow-Up**

An Agent may request another check after a delay, subject to policy constraints.

### 6.3 Critical separation

The platform must distinguish:

```
Schedule:
When should the task become due?

Admission:
Is the task currently allowed to run?

Execution:
Which Worker will execute it?
```

A task becoming due must not bypass existing admission and concurrency policies.

If no Worker is available, the task should remain queued according to the existing scheduling policy.

### 6.4 Scheduler reliability

The implementation must address:

- Atomic due-schedule claiming
- Concurrent Control Plane instances
- Trigger idempotency
- Duplicate suppression
- Restart recovery
- Missed execution policy
- Timezone and DST handling
- Retry policies
- Cancellation
- Schedule modification
- Auditability
- Maximum execution frequency
- Maximum retry count

Exactly-once external side effects must not be assumed.

Use durable trigger records and idempotent execution operations wherever possible.

## 7. Proposed Persistence Model

The following tables are conceptual additions. Reuse existing R9 entities where appropriate.

### schedule_definition

Stores scheduling configuration.

Suggested fields:

```
id
owner_id
schedule_type
cron_expression
timezone
execute_at
next_fire_at
task_template
status
misfire_policy
max_executions
execution_count
created_at
updated_at
version
```

### schedule_firing

Stores individual scheduled trigger occurrences.

```
id
schedule_id
scheduled_fire_at
actual_fire_at
run_id
status
idempotency_key
created_at
```

Add a uniqueness constraint for each logical firing.

### secretary_conversation

Stores logical conversations and metadata.

```
id
secretary_id
user_id
channel
status
created_at
updated_at
```

### secretary_message

Stores conversation messages.

```
id
conversation_id
role
content
created_at
```

### notification_outbox

Stores durable notification requests.

```
id
recipient_id
channel
event_type
payload
delivery_status
attempt_count
next_attempt_at
created_at
delivered_at
```

Final schema must be designed after inspecting the existing R9 persistence model.

Avoid duplicating existing task, run, approval, or event tables.

## 8. Secretary Tool Interface

The AI Secretary should access platform capabilities through controlled tools.

Initial proposed tools:

```
schedule_task
list_scheduled_tasks
get_task_status
reschedule_task
cancel_scheduled_task
get_execution_result
get_failure_report
request_approval
list_worker_capabilities
```

These tools should be implemented as deterministic Control Plane APIs.

The Secretary should not generate SQL or directly manipulate operational tables.

### Example schedule request

```
{
  "tool": "schedule_task",
  "arguments": {
    "task_type": "deployment",
    "application": "fo-api",
    "version": "2.2.0",
    "environment": "SIT",
    "execute_at": "2026-10-21T15:00:00+09:00",
    "notification_policy": {
      "on_success": true,
      "on_failure": true,
      "include_failure_analysis": true
    }
  }
}
```

The timestamp is illustrative.

The Secretary must validate ambiguous or past execution times before submitting a schedule.

A user-facing confirmation must only be issued after the platform has successfully persisted the schedule.

## 9. Deployment Engineer Worker

The Deployment Engineer is a specialized Worker using the existing Worker Pull architecture.

Its responsibilities include:

1. Validate deployment parameters.
2. Confirm authorization and approval.
3. Locate the requested deployment artifact.
4. Verify artifact version and integrity.
5. Check target environment prerequisites.
6. Trigger the deployment process.
7. Monitor Jenkins or the configured deployment tool.
8. Collect deployment logs and artifacts.
9. Verify the deployed application version.
10. Perform health checks and smoke tests.
11. Publish a structured execution result.

Initial integration targets may include:

- Jenkins
- AWS EC2
- S3 artifact repository
- Existing deployment scripts
- Application health endpoints

The implementation must not assume that these integrations already exist in R9.

Deployment execution should be deterministic wherever possible.

LLM reasoning should be used for planning, exceptional situations, and analysis, not for replacing reliable deployment scripts.

### Deployment state model

Suggested states:

```
PENDING
VALIDATING
WAITING_APPROVAL
QUEUED
DEPLOYING
VERIFYING
SUCCEEDED
FAILED
CANCELLED
```

Map these to existing R9 task/run states where possible.

Do not create a conflicting second lifecycle authority.

## 10. Failure Analysis Worker

On deployment failure:

1. Persist the failure event.
2. Notify the user promptly.
3. Create a failure-analysis task.
4. Collect relevant logs and execution artifacts.
5. Identify the failing stage.
6. Determine likely causes.
7. Distinguish confirmed facts from hypotheses.
8. Generate remediation recommendations.
9. Publish the report.
10. Send a follow-up notification.

The analysis report should include:

```
Deployment Identification
Application / Version / Environment
Execution Timeline
Failure Stage
Observed Errors
Evidence References
Confirmed Findings
Possible Root Causes
Confidence / Uncertainty
Recommended Actions
Rollback Assessment
Final Status
```

A failed analysis task must not suppress the original deployment-failure notification.

The user should receive an initial failure notification even if the detailed analysis is delayed or unavailable.

## 11. Approval and Security

Reuse the existing R9 Human Approval Authority and Automatic Approval Policy.

Suggested initial policies:

| Environment | Proposed policy                     |
| ----------- | ----------------------------------- |
| SIT         | Allow approved automatic deployment |
| UAT         | Policy-controlled deployment        |
| LIVE        | Require explicit human approval     |

These are proposed defaults, not existing confirmed policies.

The platform must enforce:

- User identity and authorization
- Environment-specific deployment permissions
- Approval requirements
- Worker tool permissions
- Allowed applications and environments
- Resource budgets
- Task creation quotas
- Retry limits
- Audit records
- Secret isolation

The Secretary must not be allowed to override these controls through natural-language instructions.

Automatic rollback should be a separate, explicitly governed capability.

## 12. Notification Architecture

Use event-driven notification rather than continuous polling by the Secretary.

Conceptual events:

```
ScheduleCreated
ScheduleTriggered
RunStarted
DeploymentSucceeded
DeploymentFailed
FailureAnalysisCompleted
ApprovalRequired
TaskCancelled
```

The notification mechanism should provide:

- Durable outbox
- Retry with backoff
- Idempotent delivery handling where supported
- Delivery status tracking
- Correlation with the original task
- User notification preferences

Telegram is the preferred initial notification channel.

Reuse the existing Telegram Approval Adapter where practical, but separate general notifications from approval authority.

A Telegram message should not be considered delivered until the transport reports success.

## 13. Example End-to-End Scenario

User:

> 明天下午 3 点在 SIT 部署 FO API 2.2.0。完成后通知我，失败的话提供分析和建议。

### Phase A: Scheduling

```
User
  -> Secretary
  -> Interpret instruction
  -> Resolve date/time
  -> Validate parameters
  -> Check authorization
  -> schedule_task()
  -> Persist schedule
  -> Confirm schedule to user
```

### Phase B: Triggering

```
15:00 JST
  -> Scheduler detects due schedule
  -> Atomic trigger claim
  -> Create Run / WorkItem
  -> Existing Admission Control
  -> Existing Worker Pull
  -> Deployment Engineer claims work
```

### Phase C: Deployment

```
Deployment Engineer
  -> Preflight validation
  -> Artifact verification
  -> Jenkins deployment
  -> Deployment monitoring
  -> Health checks
  -> Publish result
```

### Phase D: Success

```
DeploymentSucceeded
  -> Notification Outbox
  -> Secretary notification handling
  -> Telegram / Web Console
  -> User receives completion report
```

### Phase E: Failure

```
DeploymentFailed
  -> Persist failure
  -> Immediate failure notification
  -> Create Failure Analysis WorkItem
  -> Analysis Engineer
  -> Collect logs and evidence
  -> Generate report
  -> FailureAnalysisCompleted
  -> Follow-up notification
```

## 14. Worker Capability Registry

The Secretary should not hardcode individual Worker implementations.

Introduce or extend a Worker Capability Registry.

Example capabilities:

```
deployment.execute
deployment.verify
deployment.rollback
failure.analyze
jenkins.build
repository.inspect
report.generate
```

Each capability should define:

- Capability identifier
- Input schema
- Output schema
- Required permissions
- Supported execution environment
- Timeout and resource limits
- Worker compatibility
- Version

The Secretary plans tasks based on capabilities.

The Control Plane resolves capabilities to eligible Workers.

Reuse existing Worker registration and routing mechanisms if available.

## 15. Recommended Implementation Phases

### R10-S1: Scheduler Foundation

Implement:

- Persistent one-time scheduling
- Schedule CRUD
- Due-task triggering
- Existing Run / WorkItem integration
- Idempotency and restart recovery
- Integration tests

Acceptance criteria:

A scheduled task is triggered at the appropriate time and enters the existing execution pipeline without bypassing admission control.

### R10-S2: Secretary Conversation Foundation

Implement:

- Secretary identity
- Conversation gateway
- Conversation persistence
- Basic memory retrieval
- Structured intent interpretation
- Schedule tools
- Task status queries
- Task cancellation and rescheduling

Acceptance criteria:

A user can create, inspect, reschedule, and cancel tasks using natural language.

### R10-S3: Deployment Engineer

Implement:

- Deployment capability contract
- Deployment Worker
- Jenkins adapter
- Artifact verification
- Environment preflight
- Deployment result publishing

Acceptance criteria:

A scheduled deployment can be executed through the existing Worker Pull architecture.

### R10-S4: Notifications

Implement:

- Event subscription
- Durable notification outbox
- Telegram notifications
- Success and failure messages
- Notification delivery tracking

Acceptance criteria:

A task result is delivered reliably without requiring an active conversation.

### R10-S5: Failure Analysis

Implement:

- Failure-analysis task creation
- Log and artifact collection
- Analysis Worker
- Structured report
- Follow-up notifications

Acceptance criteria:

A failed deployment produces both an immediate failure notification and a subsequent diagnostic report.

### R10-S6: Advanced Secretary

Implement:

- Recurring schedules
- Conditional follow-up scheduling
- Task dependency tracking
- Worker capability discovery
- Long-term conversation summaries
- Intelligent execution recommendations

Acceptance criteria:

The Secretary can coordinate multi-step tasks and resume conversations using authoritative persisted state.

## 16. Engineering Constraints

The following principles remain mandatory:

1. Java remains the Control Plane implementation language.
2. Use the existing `me.asu.aidev` namespace.
3. Preserve the decentralized Worker Pull architecture.
4. Do not reintroduce R2 centralized orchestration.
5. Reuse existing R9 execution, approval, scheduling, and admission infrastructure.
6. Workers may use Go, Java, Python, or TypeScript where appropriate.
7. Codex CLI and Copilot CLI remain first-class Agent Runtime options.
8. Keep external dependencies minimal.
9. Prefer local-first development.
10. Avoid unnecessary AWS-specific coupling.
11. Use PostgreSQL where suitable, while preserving the project's local testing strategy.
12. Use Flyway migrations consistent with the accepted R9 baseline.
13. Require automated integration tests for new capabilities.
14. Maintain compatibility with existing R9 behavior unless an explicit migration is approved.
15. Do not assume an LLM process must remain alive between scheduled executions.

## 17. Important Design Questions

Before implementation, resolve:

**A. Secretary runtime**

Should the Secretary be a dedicated long-running Worker process, or a logical Agent instantiated on demand through an Agent Runtime SPI?

Preferred direction: logical Agent instantiated on demand.

**B. Conversation execution**

How should user conversations be handled without occupying normal Worker leases for extended periods?

Evaluate a separate interactive execution path that still uses platform authorization and resource budgets.

**C. Scheduler persistence**

How should the Scheduler coordinate with existing Run, WorkItem, scheduling, and admission tables?

Inspect the actual R9 schema before finalizing migrations.

**D. Event delivery**

Does R9 already provide sufficient durable event infrastructure, or is a transactional outbox required?

**E. Task ownership**

Which component is authoritative for task cancellation, approval, retry, and terminal state?

Avoid duplicated lifecycle ownership.

**F. Deployment integration**

What is the actual Jenkins deployment interface, artifact repository structure, and target-environment configuration?

Design the integration contract without assuming undocumented infrastructure.

**G. Secretary memory**

How should conversations be summarized, retrieved, and linked to authoritative task records while controlling token costs?

## 18. Immediate Next Action for the New Session

Do not begin by generating the entire R10 implementation.

First:

1. Review this Handoff.
2. Inspect the actual R9 Web Console Fix1 source code and persistence schema, if available.
3. Identify reusable components and extension points.
4. Confirm the architectural boundaries.
5. Produce a detailed **R10-S1 Scheduler Foundation design**.
6. Define APIs, database migrations, concurrency semantics, idempotency, and failure recovery.
7. Define integration-test cases.
8. Identify any required changes to the existing R9 interfaces.
9. Only then proceed with implementation.

If the R9 source is unavailable, explicitly request it rather than inventing existing interfaces or classes.

## 19. Final Architectural Principle

**The AI Secretary is the conversational coordinator, not the execution authority.**

The Secretary may:

- Interpret instructions
- Plan work
- Request schedules
- Delegate capabilities
- Track execution
- Analyze outcomes
- Communicate with the user

The Control Plane must remain authoritative for:

- Scheduling persistence
- Permissions
- Approval
- Admission
- Execution state
- Task ownership
- Retry and cancellation
- Auditability

Specialized Workers perform actual operations.

The intended result is a reliable, extensible, long-lived AI Secretary capable of coordinating engineering tasks and other future operational workflows without depending on continuously running LLM sessions.

**End of Handoff — AI Secretary & Scheduling Architecture**