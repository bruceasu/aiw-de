# 前言

|Worker| 类型|推荐语言|原因|
|------|-----|-------|---|
|Secretary Agent|Python|对话、工具调用、任务规划|
|Requirement Analyst|Python|需求理解、结构化分析|
|Software Architect|Python|多阶段推理、工具协作|
|Coding Agent|Go / Python|优先复用 Codex CLI|
|Code Review Agent|Python|模型推理和代码分析|
|Deployment Engineer|Go|低依赖、易分发|
|Build Engineer|Go|调用 Maven、Jenkins、Git|
|Scheduler|Java|持久化、可靠调度|
|Control Plane|Java|事务、权限、状态管理|
|Web Console|Java + 少量 JS|不必引入完整 Node 后端|

Typescript 作为原型还是比较不错的。

Python 与 TypeScript 的 AI 生态对比
|维度|Python|TypeScript|优势|
|----|------|----------|---|
|LLM API 集成|成熟|成熟|持平|
|OpenAI Agents SDK|官方支持|官方支持|持平|
|多 Agent 编排|非常丰富|丰富|Python|
|RAG / 知识库|非常丰富|丰富|Python|
|Embedding / 向量检索|非常成熟|成熟|Python|
|本地模型推理|很强|较弱|Python|
|模型训练、微调|绝对优势|很有限|Python|
|MCP 工具集成|成熟|成熟|持平|
|Web / 实时交互|成熟|很强|TypeScript|
|浏览器自动化|成熟|很强|TypeScript|
|Agent Web UI|成熟|很强|TypeScript|
|CLI 工具开发|成熟|成熟|取决于需求|
|部署与依赖管理|较复杂|较复杂|无明显赢家|
|静态类型约束|可选、逐步增强|原生开发体验更好|TypeScript|



# HANDOFF — AIW:DE
## Worker Runtime Reliability & Process Isolation

**Status:** Design approved, implementation pending  
**Baseline:** commit id: d3c92ac
**Target:** Worker Runtime Hardening  
**Date:** 2026-10-21  
**Primary language:** Java 21 (Control Plane), Python / Go / TypeScript (Workers)

---

# 1. Current Objective

Continue the AIW:DE project from the accepted baseline.

The immediate objective is to improve the reliability, cancellation handling, process isolation, and recovery behavior of the existing Worker implementations.

**This is an incremental hardening project, NOT a Worker Runtime rewrite.**

The user has explicitly accepted the following architectural principle:

> Organizational structure must not be coupled to operating-system process structure.

Consequently:

- Keep the existing flat Control Plane → Worker architecture.
- Do not introduce Worker Host, Worker Leader, or an additional scheduling hierarchy.
- Each Worker remains an independently registered execution entity with its own role and capabilities.
- A Worker may execute an Agent in-process or launch an independent Agent subprocess.
- Shared Worker Runtime libraries are permitted, but they must not become additional runtime services.
- Java Control Plane remains the authority for scheduling, task ownership, leases, admission, approval, and recovery.

The preferred architecture is:

```text
Java Control Plane (R9)
    |
    | Existing Worker Pull Protocol
    |
    +-- Secretary Worker (Python)
    |       +-- OpenAI Agents SDK
    |
    +-- Architect Worker (Python)
    |       +-- OpenAI Agents SDK
    |
    +-- Reviewer Worker (Python)
    |       +-- Agent subprocess (optional)
    |
    +-- Coding Worker (Go / Python)
    |       +-- Codex CLI / App Server
    |
    +-- Build Worker (Go)
    |       +-- Maven / Jenkins
    |
    +-- Deployment Worker (Go)
            +-- Controlled deployment executor
```

No intermediate Worker Host or Leader.

---

# 2. Frozen R9 Baseline

The accepted baseline is:

**R9 Web Console Fix1**

Previously confirmed by the user:

```text
mvn clean verify
BUILD SUCCESS
```

Baseline technologies:

| Component | Baseline |
|---|---|
| Java package root | me.asu.aidev |
| Java | 21 |
| Spring Boot | 4 |
| Spring Framework | 7 |
| Build | Maven |
| Architecture | Worker Pull |
| Persistence | 9 tables through Flyway V14 |
| Web Console | Accepted |
| Integration tests | 33 Failsafe IT methods passing |

Preserve the existing R9 architecture, including:

- Worker Pull / Claim
- Lease Ownership
- Scheduling Fairness and Priority
- Run Concurrency Budget
- Fleet Admission Budget
- Runtime Scheduling Control
- Human Approval Authority
- Telegram Approval Adapter
- Automatic Approval Policy
- Published Result Handoff
- Review / Verification / Repair Loop
- Final Engineering Gate
- Run Pause / Resume / Cancel
- Run Timeline and Observability

**Do not redesign these modules unless a verified defect requires a narrowly scoped correction.**

---

# 3. Existing Worker Source Code

The user supplied:

`workers.zip`

The previous session reviewed the existing Worker implementations.

The archive contains:

```text
process-go/
process-python/
process-typescript/
```

The new session should use the original archive if available.

If the archive is unavailable, request it from the user before conducting source-level implementation work.

Do not assume that previously reported code findings are independently verified until the corresponding source has been inspected.

## 3.1 Go Worker

Previously identified features:

- RoleProfile
- AgentRuntime abstraction
- Slot execution loop
- Task execution lifecycle
- Worker Heartbeat
- Claim / Complete / Fail
- Managed Agent Runtime
- Runtime failure detection
- Codex CLI
- Codex App Server
- OpenAI API

Existing AgentRuntime interface was reported as:

```go
type AgentRuntime interface {
    Type() string
    Capabilities() AgentRuntimeCapabilities
    Run(
        context.Context,
        AgentRunRequest,
    ) (AgentRunResult, error)
}
```

Existing runtime configuration includes:

```text
AIW_AGENT_RUNTIME=codex-cli
AIW_AGENT_RUNTIME=codex-app-server
AIW_AGENT_RUNTIME=openai-api
```

Preserve these abstractions unless there is a concrete technical reason to modify them.

## 3.2 Python Worker

Previously identified features:

- ProcessSlot
- LeaseHeartbeat
- ControlPlaneClient
- Agent Runtime abstraction
- Role and Capability profiles
- TOML-based configuration
- Task workspace preparation
- Evidence handling
- Result reporting
- Cancellation and timeout handling

The previous review also identified existing OpenAI Agents SDK integration:

```text
AIW_AGENT_RUNTIME=openai-agents
```

Reported dependencies:

```text
openai-agents==0.23.1
openai==3.26.0
```

Verify these versions against the actual archive.

**Do not create another OpenAI Agents SDK Adapter if the existing one already satisfies the requirements.**

Python ProcessSlot currently uses threading according to the previous review.

Therefore, a ProcessSlot is not equivalent to an isolated Agent subprocess.

## 3.3 TypeScript Worker

The existing TypeScript implementation must remain supported.

It is not necessary to rewrite it in Python or Go.

Its protocol behavior should eventually be covered by the same conformance tests.

---

# 4. Approved Architectural Decisions

## ADR-001: Flat Worker Organization

One Worker directly communicates with the Control Plane.

Workers are not managed by a new Worker Host or Leader.

A role can have multiple Worker instances.

A Worker instance can execute multiple tasks over its lifetime.

## ADR-002: Separate Worker and Agent Lifecycles

Worker:

- Long-running process
- Owns Worker identity
- Registers roles and capabilities
- Pulls tasks
- Maintains leases
- Reports results

Agent execution:

- Belongs to one task attempt
- Can run in-process
- Can run in a subprocess
- May use an external CLI or App Server
- Must respect cancellation and execution budgets

## ADR-003: Preserve Existing AgentRuntime SPI

Use the existing AgentRuntime abstraction.

Extend it only where required for:

- Cancellation
- Execution lifecycle
- Process termination
- Structured failure reporting
- Result reconciliation

Do not replace existing interfaces merely for naming consistency.

## ADR-004: No Duplicate Scheduler

The Worker must not implement a second global scheduler.

The Java Control Plane remains responsible for:

- Task selection
- Role matching
- Capability matching
- Priority and fairness
- Admission control
- Lease ownership
- Recovery policy

The Worker is responsible for executing tasks it has legitimately claimed.

## ADR-005: Optional Process Isolation

Support two execution modes:

```text
in-process
subprocess
```

Do not require every Agent to run in a subprocess.

Use subprocess isolation where failure containment, forced termination, or task-specific dependencies justify the overhead.

## ADR-006: Multi-Language Compatibility

Keep the existing Python, Go, and TypeScript implementations.

Unify externally observable protocol semantics, not internal implementation details.

---

# 5. Findings from Previous Review

These findings must be validated against the uploaded source before implementation.

## P0 — Python Heartbeat Cancellation

The previous review found that Python's task heartbeat response was not propagated to cancellation handling.

Specifically, the response containing:

```json
{
  "stopRequested": true
}
```

may not trigger the same cancellation behavior already present in Go.

Required behavior:

1. Parse the heartbeat response.
2. Detect stopRequested.
3. Request Agent cancellation.
4. Stop further unsafe side effects.
5. Confirm execution termination where possible.
6. Report the appropriate terminal outcome.

Do not confuse a requested stop with Lease ownership loss.

## P0 — Python Thread Cancellation

The existing Python execution path reportedly uses threads.

A Python thread cannot be forcibly terminated safely in the general case.

Consequently, a timed-out Agent operation may remain active.

The previous implementation reportedly uses:

```python
self.resource_uncertain = True
```

This is a useful safety mechanism and should be preserved.

Add an optional subprocess execution mechanism to provide stronger lifecycle control.

## P0 — Execution Resource Uncertainty

When an Agent cannot be proven terminated:

- Do not reuse its workspace.
- Do not start another task in the affected execution Slot.
- Preserve diagnostic information.
- Reconcile task ownership with the Control Plane.
- Quarantine the affected Slot or Worker when necessary.

A subprocess exit does not prove that all external operations have stopped.

## P1 — Result Submission Uncertainty

Consider:

```text
Worker sends complete()
        |
Control Plane commits result
        |
HTTP response is lost
        |
Worker cannot determine outcome
```

Do not automatically re-execute the Agent.

Required reconciliation:

- Identify the execution attempt.
- Query the authoritative task state when supported.
- Determine whether the same attempt already completed.
- Retry submission only when safe.
- Reject stale ownership.
- Prevent duplicate result publication.

Prefer existing R9 attempt identifiers and idempotency guarantees.

Introduce new protocol fields only if the existing contract cannot satisfy the requirement.

## P1 — Failure Classification

Distinguish at least:

```text
AGENT_FAILED
AGENT_CANCELLED
EXECUTION_TIMEOUT
LEASE_LOST
LEASE_UNCERTAIN
PROCESS_TERMINATION_UNCERTAIN
RESULT_SUBMISSION_UNCERTAIN
WORKER_INTERNAL_ERROR
```

These are conceptual failure categories.

Do not automatically add them as Control Plane database states.

Map them to the existing R9 task lifecycle.

## P2 — Python Profile Test

The previous review reported:

```text
Expected profiles: 10
Actual profiles:   11
```

One test failed because the profile count assertion did not match the registered profiles.

Investigate whether the extra profile is legitimate before changing the test.

---

# 6. R10 Implementation Plan

## R10-S1 — Protocol & Cancellation Hardening

Priority: P0

Scope:

1. Inspect Python and Go heartbeat implementations.
2. Compare cancellation semantics.
3. Verify R9 Claim, Heartbeat, Complete, Fail, and Cancel contracts.
4. Correct Python stopRequested handling.
5. Distinguish definitive Lease loss from temporary communication failure.
6. Ensure stale attempts cannot submit valid completion results.
7. Preserve existing role/capability matching.

Deliverables:

- Source changes
- Protocol behavior specification
- Focused unit tests
- Compatibility notes

Acceptance:

- Python receives and handles stopRequested.
- Worker cancels execution promptly.
- Temporary HTTP errors do not automatically imply permanent Lease loss.
- Confirmed Lease loss prevents stale result submission.
- Existing R9 tests remain passing.

## R10-S2 — Optional Agent Process Isolation

Priority: P0

Scope:

Introduce a subprocess executor for Python Worker without replacing existing execution paths.

Suggested configuration:

```text
AIW_AGENT_EXECUTION_MODE=in-process
AIW_AGENT_EXECUTION_MODE=subprocess
```

Requirements:

- Task-specific process launch
- Explicit execution identity
- PID/process-group tracking
- Timeout enforcement
- Cooperative cancellation
- Forced termination after grace period
- Process-tree cleanup
- stdout/stderr capture
- Structured result collection
- Workspace cleanup
- Resource uncertainty handling

Linux:

- Process groups
- Optional cgroup isolation

Windows:

- Job Objects or equivalent reliable process-tree management

Important:

Process isolation is not a security sandbox.

High-risk execution requires OS permissions, containerization, or VM isolation.

Acceptance:

- Agent subprocess crashes without crashing Worker.
- Timeout terminates managed subprocesses.
- Unconfirmed termination quarantines execution resources.
- Existing in-process execution remains supported.

## R10-S3 — Result Reconciliation & Recovery

Priority: P1

Scope:

1. Inspect existing result submission logic.
2. Identify attempt ownership and idempotency guarantees.
3. Handle lost Complete responses.
4. Reconcile authoritative task state.
5. Prevent duplicate result publication.
6. Preserve Evidence and Artifact handoff.
7. Add structured failure classification.

Acceptance:

- Lost HTTP response does not cause automatic Agent re-execution.
- Duplicate Complete requests are handled safely.
- Expired Lease cannot overwrite a newer attempt.
- Published results remain consistent with R9 authority rules.

## R10-S4 — Cross-Runtime Conformance Tests

Priority: P1

Scope:

Create shared behavioral test scenarios for:

- Python Worker
- Go Worker
- TypeScript Worker

Test cases:

1. Normal Claim → Execute → Complete
2. Agent failure
3. Stop requested
4. Lease expiration
5. Temporary heartbeat network failure
6. Execution timeout
7. Subprocess crash
8. Uncertain process termination
9. Complete response lost
10. Duplicate completion
11. Worker restart
12. Multiple Workers with the same role
13. Capability mismatch
14. Admission budget exhaustion
15. Workspace isolation

Acceptance:

The same Control Plane events produce compatible externally observable outcomes across Worker implementations.

Do not require identical internal code structures.

---

# 7. Testing Status

The previous session reported:

Python:

```text
6 tests executed
5 passed
1 failed
```

Failure:

```text
test_process_profiles.py
AssertionError: 11 != 10
```

Go:

```text
go test ./...
```

Could not complete because the environment could not download a dependency from:

```text
proxy.golang.org
```

This is not evidence of a Go source-code test failure.

The next session should rerun tests in an environment with dependencies available.

The complete R9 Control Plane source was not part of the Worker archive reviewed previously.

Therefore, server-side protocol guarantees must still be verified against the actual R9 source.

---

# 8. Engineering Constraints

The user prefers:

- Java for long-lived core services.
- Python for AI Agent SDK integration.
- Go for small, portable, low-dependency executors.
- Minimal infrastructure complexity.
- Local-first development.
- Minimal AWS-specific dependencies.
- Existing Codex CLI and Copilot CLI integration preserved.
- Telegram for human approvals and notifications.
- No unnecessary frameworks or runtime layers.

Do not introduce LangChain, LangGraph, CrewAI, or another orchestration framework without demonstrating a concrete need.

Do not add a second Control Plane inside the Worker.

Do not change accepted R9 behavior without explicit justification.

Preserve `AIW_` as the environment variable prefix.

---

# 9. Deliverable Requirements

For each implementation stage:

1. Identify exact files and functions requiring modification.
2. Explain the existing behavior and the verified defect.
3. Describe the smallest safe correction.
4. Implement the correction.
5. Add regression tests.
6. Run the relevant build and test commands.
7. Report test results and remaining limitations.
8. Provide a complete updated source package when requested.
9. Do not claim success for tests that were not executed.
10. Preserve compatibility with the accepted R9 baseline wherever possible.

Do not rewrite working modules merely to improve their style.

---

# 10. Immediate Next Action

**Begin R10-S1 — Protocol & Cancellation Hardening.**

First:

- Reinspect `workers.zip`.
- Locate Python and Go Heartbeat implementations.
- Locate Python execution cancellation and timeout logic.
- Locate result submission paths.
- Identify exact R9 protocol dependencies.
- Produce a file/function-level modification plan.

Then implement the smallest safe corrections with tests.

Do not start R10-S2 until R10-S1 has passed its acceptance criteria.

---

# 11. Final Architecture Principle

**Keep organizational structure flat while allowing flexible process isolation.**

```text
Control Plane
    |
    +-- Worker
    |     |
    |     +-- AgentRuntime
    |             |
    |             +-- In-process Agent
    |             |
    |             +-- Agent Subprocess
    |
    +-- Worker
          |
          +-- AgentRuntime
                  |
                  +-- Codex CLI
```

Control Plane decides which Worker is authorized to perform the task.

Worker manages the execution lifecycle.

AgentRuntime performs the work.

Operating-system processes are implementation details, not additional organizational roles.

**R10 is a reliability enhancement to an accepted architecture, not a redesign.**