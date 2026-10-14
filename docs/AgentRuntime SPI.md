# AI Worker — AgentRuntime SPI 设计方案

> 实施对应 FD-009：TypeScript Worker 使用进程内 AgentRuntime SPI，将 Codex SDK 与 OpenAI Agents SDK 收敛到 `workers/process-typescript` 单包、单入口；通过 `AIW_AGENT_RUNTIME` 显式选后端。旧 `process-typescript-with-api` 包和启动路径已删除。本文下方的 Java/Python 伪代码及 DirectResponses、Copilot 分阶段建议仍是设计参考，不表示这些能力已实现。

## 1. 目标

AI Worker 的 Worker 不应该直接依赖某个具体 LLM SDK、CLI 或 Provider。

目标结构：

```text
Control Plane
    |
    | Task / Lease / Budget / Policy
    v
Worker
    |
    v
AgentRuntime SPI
    |
    +-- OpenAIAgentsRuntime
    +-- OpenAIResponsesRuntime
    +-- CodexCliRuntime
    +-- CopilotCliRuntime
    +-- FutureRuntime...
```

核心原则：

```text
Worker 决定：
    WHAT TO DO

AgentRuntime 决定：
    HOW TO EXECUTE AGENT

Provider/Backend 决定：
    HOW TO TALK TO MODEL
```

这样 Worker 不需要知道：

- 是否使用 OpenAI Agents SDK
- 是否直接调用 Responses API
- 是否执行 Codex CLI
- 是否执行 Copilot CLI
- 使用什么具体模型
- LLM session 如何保存
- tool loop 如何执行
- context 如何 continuation
- tracing 如何实现

---

# 2. 架构边界

整体结构：

```text
                    AI Worker
                     Control Plane
                           |
                    task / lease
                           |
        +------------------+------------------+
        |                  |                  |
        v                  v                  v
 CodingWorker        ReviewWorker        TestWorker
        |                  |                  |
        +------------------+------------------+
                           |
                           v
                 +---------------------+
                 | AgentRuntime SPI    |
                 +---------------------+
                    |      |       |
          +---------+      |       +------------+
          |                |                    |
          v                v                    v
     Agents SDK        Codex CLI          Copilot CLI
          |
          v
      Responses API
```

这里必须严格避免：

```text
Worker
  |
  +-- OpenAI SDK
  +-- Codex CLI
  +-- prompt handling
  +-- retry handling
  +-- token handling
  +-- session handling
```

否则几个 Worker 很快会产生大量重复代码。

---

# 3. AgentRuntime SPI 的职责

AgentRuntime 应负责：

```text
1. Agent execution
2. continuation / resume
3. cancellation
4. event streaming
5. usage collection
6. backend-specific state
7. tool invocation integration
8. timeout
9. execution limits
10. backend error normalization
```

但 AgentRuntime 不负责：

```text
Task scheduling
Worker lease
Fleet management
Global admission control
Business workflow
Task priority
Cross-worker orchestration
Database persistence policy
```

这些仍然属于 Control Plane。

---

# 4. 核心 SPI

逻辑接口建议保持很小。

伪代码：

```java
public interface AgentRuntime {

    AgentRunResult run(
        AgentRunRequest request,
        AgentRunContext context
    );

    AgentRunResult resume(
        AgentRunState state,
        AgentResumeInput input,
        AgentRunContext context
    );

    void cancel(AgentRunId runId);

    AgentRuntimeCapabilities capabilities();
}
```

如果 Worker 使用 Python 或 Go，也应保持同样的语义，而不是完全照搬 Java 类型。

---

# 5. AgentRunRequest

一次 Agent 执行的输入。

```java
record AgentRunRequest(
    String taskId,
    AgentProfile profile,
    AgentInput input,
    ToolSet tools,
    ExecutionPolicy policy,
    Budget budget
) {}
```

其中：

```text
taskId
    Control Plane Task ID

profile
    这个 Agent 是谁

input
    本次任务输入

tools
    允许使用哪些能力

policy
    权限和执行约束

budget
    最大成本 / token / turn / 时间
```

重要的是：

> AgentRunRequest 是 runtime-neutral 的。

里面不能出现：

```text
OpenAI Response object
Agents SDK RunContext
Codex CLI process object
Anthropic message object
```

---

# 6. AgentProfile

AgentProfile 描述的是一个 Worker 中 Agent 的“角色”。

例如：

```yaml
id: coding-agent

instructions: |
  You are responsible for implementing the assigned software task.

model:
  family: coding
  preferred: gpt-5.x

limits:
  max_turns: 40

output_schema:
  type: coding-result

tool_policy:
  filesystem: workspace-write
  shell: restricted
  network: denied
```

它包含：

```text
Agent identity
Instructions
Model preference
Model settings
Allowed tools
Guardrail
Output schema
Default execution limits
```

不应该包含：

```text
Worker lease
Task queue information
Database ID
Fleet state
```

---

# 7. AgentRunContext

Context 是 runtime 执行过程中需要的环境信息：

```java
record AgentRunContext(
    Workspace workspace,
    SecretProvider secrets,
    ToolRegistry toolRegistry,
    ApprovalHandler approvalHandler,
    EventSink eventSink,
    CancellationToken cancellationToken
) {}
```

它是一个非常重要的设计点。

这样 AgentRuntime 不需要：

```text
@Autowired
DatabaseService
WorkerService
TaskRepository
```

Runtime 只从 Context 获取执行需要的能力。

---

# 8. ToolSet

Tools 不应该直接绑定 Agents SDK。

错误：

```python
@function_tool
def git_diff():
    ...
```

然后所有业务代码依赖 Agents SDK。

推荐：

```text
Tool
 |
 +-- name
 +-- description
 +-- input schema
 +-- output schema
 +-- execute()
```

例如：

```java
interface AgentTool {

    ToolDefinition definition();

    ToolResult execute(
        ToolCall call,
        ToolExecutionContext context
    );
}
```

然后不同 Runtime 做 adapter：

```text
AgentTool
   |
   +--------------------+
   |                    |
   v                    v
AgentsSdkToolAdapter   MCP Adapter
```

这样工具层不会被 SDK 锁死。

---

# 9. Runtime Implementations

第一阶段建议支持三个 runtime。

## 9.1 OpenAIAgentsRuntime

推荐作为通用 Agent Worker 默认实现。

```text
OpenAIAgentsRuntime
      |
      v
OpenAI Agents SDK
      |
      v
Responses API
```

负责：

```text
Agent creation
Runner.run
tool calls
handoffs
guardrails
approval interruptions
session/state
tracing
```

OpenAI 官方当前将 Agents SDK 定位为应用内 Agent Runtime：SDK Runner 负责模型调用、tool call、handoff 以及最终结果之间的 agent loop，而应用仍然控制部署、工具、存储和审批。

适合：

```text
PlanningWorker
RequirementWorker
ReviewWorker
AnalysisWorker
GenericAgentWorker
```

---

## 9.2 CodexCliRuntime

这是 AI Worker 特别重要的一种 runtime。

```text
CodexCliRuntime
      |
      v
codex process
      |
      v
repository workspace
```

适合：

```text
CodingWorker
RefactoringWorker
BugFixWorker
MigrationWorker
```

因为 Codex CLI 本身已经具备比较完整的软件工程 execution harness。

Runtime 的职责变成：

```text
prepare workspace
build command
inject task
configure sandbox
configure approval
start process
capture events
capture usage
parse result
handle cancellation
```

例如：

```text
AgentRuntime.run()
        |
        v
codex exec ...
        |
        +-- stdout
        +-- stderr
        +-- events
        +-- patches
        +-- usage
```

Worker 不应该自己执行 `ProcessBuilder("codex", ...)`。

必须通过 CodexCliRuntime。

---

# 10. CopilotCliRuntime

与 Codex 类似：

```text
CopilotCliRuntime
      |
      v
Copilot CLI
```

主要目的是：

```text
backend diversity
provider fallback
A/B testing
specialized tasks
future compatibility
```

CodingWorker 可以通过配置选择：

```yaml
runtime: codex-cli
```

或者：

```yaml
runtime: copilot-cli
```

Worker 本身完全不改变。

---

# 11. DirectResponsesRuntime

这个 runtime 很值得保留。

```text
DirectResponsesRuntime
        |
        v
Responses API
```

它不需要 Agents SDK。

适合：

```text
classification
structured extraction
simple planning
summary
single model call
small tool loop
```

例如：

```text
TaskClassifierWorker
IntentWorker
DocumentSummaryWorker
RiskClassifierWorker
```

没有必要所有 Worker 都启动完整 Agent。

原则：

```text
simple LLM task
    -> ResponsesRuntime

agentic task
    -> AgentsRuntime

software coding task
    -> CodexCliRuntime
```

---

# 12. Runtime Resolver

Worker 不直接 new Runtime。

使用 resolver：

```java
interface AgentRuntimeResolver {

    AgentRuntime resolve(
        RuntimeSelector selector
    );
}
```

例如：

```yaml
worker:
  type: coding

agent:
  runtime: codex-cli
```

resolver：

```text
codex-cli
    -> CodexCliRuntime

openai-agents
    -> OpenAIAgentsRuntime

openai-responses
    -> OpenAIResponsesRuntime
```

以后增加：

```text
claude-code
gemini-cli
local-model
agents-api
```

不需要修改 Worker。

---

# 13. RuntimeCapabilities

不是每个 Runtime 能力都一样。

因此不要假定：

```text
所有 Runtime 都支持：
resume
handoff
approval
streaming
sandbox
MCP
```

定义：

```java
record AgentRuntimeCapabilities(
    boolean resume,
    boolean streaming,
    boolean tools,
    boolean mcp,
    boolean approvals,
    boolean handoffs,
    boolean sandbox,
    boolean structuredOutput
) {}
```

例如：

```text
OpenAIAgentsRuntime

tools          YES
resume         YES
handoff        YES
approval       YES
tracing        YES
```

OpenAI Agents SDK 当前确实支持 tool loop、handoff、approval interruption、resumable state 和 tracing。

而：

```text
SimpleLocalRuntime

tools          NO
resume         NO
handoff        NO
approval       NO
```

Control Plane / Worker 可以据此判断是否能执行某个 AgentProfile。

---

# 14. Execution State

一定不要让 Worker 保存 SDK 专用 object。

定义通用：

```text
AgentRunState
```

例如：

```java
record AgentRunState(
    AgentRunId runId,
    String runtimeType,
    String runtimeVersion,
    String opaqueState
) {}
```

其中：

```text
opaqueState
```

由具体 runtime 自己解释。

例如 Agents SDK：

```text
SDK serialized state
conversation ID
previous response ID
```

Codex：

```text
session ID
workspace snapshot
CLI resume ID
```

这样 DB 可以保存：

```text
run_id
runtime_type
runtime_state
status
```

而不知道内部格式。

OpenAI Agents SDK 本身提供多种 continuation strategy，例如应用管理 history、SDK session、Conversation ID 或 previous response ID；对于 approval interruption，也可以保存可恢复 state。

---

# 15. Runtime Event Model

不要仅仅返回 final string。

AgentRuntime 应该产生统一事件：

```text
AgentRunStarted
ModelCallStarted
ModelCallCompleted
ToolCallStarted
ToolCallCompleted
ApprovalRequired
UsageUpdated
AgentMessage
RuntimeWarning
AgentRunCompleted
AgentRunFailed
AgentRunCancelled
```

例如：

```java
sealed interface AgentEvent {}
```

Worker：

```text
AgentRuntime
       |
       | AgentEvent
       v
Worker Event Sink
       |
       +--> DB
       +--> WebSocket
       +--> audit
       +--> metrics
       +--> Web Console
```

这样 Web Console 将来可以实时看到：

```text
CodingWorker #17

> searching repository
> read Foo.java
> modifying Bar.java
> running mvn test
> test failed
> repairing
> tests passed
```

Agents SDK 原生 tracing 已能记录 model calls、tool calls、handoffs、guardrails 等；我们的 SPI Event 模型可以在其上再做平台级归一化，而不是让平台直接依赖 OpenAI 的 tracing schema。

---

# 16. Result Model

统一返回：

```java
record AgentRunResult(
    AgentRunStatus status,
    AgentOutput output,
    AgentUsage usage,
    AgentRunState state,
    List<ArtifactRef> artifacts,
    Failure failure
) {}
```

状态建议：

```text
COMPLETED
PAUSED
FAILED
CANCELLED
BUDGET_EXCEEDED
TIMEOUT
```

其中 `PAUSED` 很重要。

例如：

```text
Agent
   |
   v
rm production.sql
   |
   v
ApprovalRequired
   |
AgentRunResult(PAUSED)
```

然后 Control Plane：

```text
WAITING_APPROVAL
```

人工允许以后：

```text
runtime.resume(...)
```

这比让 Worker 长时间阻塞等待人工批准好得多。

---

# 17. Approval Architecture

审批应该跨 Runtime 统一。

```text
Agent Runtime
      |
      v
ApprovalHandler
      |
      v
Control Plane
      |
      +-- automatic policy
      |
      +-- Web Console
      |
      +-- Telegram
```

例如：

```text
READ_FILE
    automatic allow

WRITE_WORKSPACE
    automatic allow

DELETE_FILE
    configurable

NETWORK
    approval required

PRODUCTION_DB
    always human approval
```

Agents SDK 自身支持在敏感 tool action 前暂停并等待 human review，因此 OpenAIAgentsRuntime 可以自然映射到 SPI 的 `PAUSED / resume`。

---

# 18. Budget

Budget 不应该完全交给 SDK。

定义平台级：

```java
record AgentBudget(
    Duration maxDuration,
    long maxInputTokens,
    long maxOutputTokens,
    long maxTotalTokens,
    int maxTurns,
    int maxToolCalls,
    BigDecimal maxCost
) {}
```

然后：

```text
Control Plane
       |
       v
Fleet Budget
       |
       v
Task Budget
       |
       v
Worker
       |
       v
AgentRun Budget
```

Runtime 必须不断报告：

```text
usage
```

但最终预算 authority 应属于 AI Worker，而不是 LLM SDK。

---

# 19. Permission 与 Sandbox

这里同样需要避免 SDK-specific policy。

统一：

```text
ExecutionPolicy
```

例如：

```yaml
filesystem:
  workspace: write
  outside_workspace: deny

network:
  default: deny
  allow:
    - api.github.com

shell:
  enabled: true

git:
  commit: false
  push: false

secrets:
  allow:
    - OPENAI_API_KEY
```

Runtime 负责映射：

```text
ExecutionPolicy
      |
      +--> Codex sandbox config
      |
      +--> Agents SDK tool guardrails
      |
      +--> local process sandbox
```

注意：

> Runtime 只负责执行 policy，policy 本身应该由 Control Plane / Worker configuration 决定。

---

# 20. Worker 的结构

最终 Worker 应该非常简单。

例如：

```text
CodingWorker
```

逻辑：

```java
Task task = claimTask();

AgentRunRequest request =
    codingRequestFactory.create(task);

AgentRuntime runtime =
    runtimeResolver.resolve(task.runtime());

AgentRunResult result =
    runtime.run(request, context);

publish(result);
```

Worker 不应该存在：

```text
OpenAIClient
Runner
Response
ChatCompletion
ProcessBuilder("codex")
```

这些全部属于 Runtime Adapter。

---

# 21. Worker Profile

Worker 本身建议配置化：

```yaml
worker:
  type: coding

runtime:
  preferred: codex-cli
  fallback:
    - openai-agents

agent:
  profile: coding-agent

budget:
  max_duration: 30m
  max_turns: 50

permissions:
  workspace: write
  shell: true
  network: false
```

这样未来：

```text
CodingWorker
```

可以部署三个实例：

```text
worker-1 -> Codex
worker-2 -> Codex
worker-3 -> Agents SDK
```

Control Plane 不需要知道内部区别。

---

# 22. Runtime 与 Worker 的关系

非常重要：

```text
Worker != Agent
Worker != Runtime
```

三个概念分别是：

```text
Worker
    durable execution process

Agent
    reasoning role / specialist

AgentRuntime
    agent execution engine
```

例如：

```text
CodingWorker

uses

CodingAgentProfile

runs on

CodexCliRuntime
```

也可能：

```text
CodingWorker

uses

CodingAgentProfile

runs on

OpenAIAgentsRuntime
```

---

# 23. Multi-Agent 的边界

这里建议非常保守。

不要让：

```text
Control Plane Worker A
      |
      v
Agent A
      |
      handoff
      |
      v
Control Plane Worker B
```

SDK handoff 不应该直接等于平台 Worker handoff。

这是两个不同层级。

应该区分：

```text
LOCAL AGENT HANDOFF
```

和：

```text
PLATFORM TASK DELEGATION
```

前者：

```text
Review Agent
    -> Security Agent
```

发生于一个 Worker / AgentRuntime 内。

后者：

```text
CodingWorker
    |
    v
new REVIEW task
    |
    v
ReviewWorker
```

由 Control Plane 管理。

这是整个架构里非常重要的一条边界。

---

# 24. 推荐代码模块

如果 Runtime 主要采用 Python：

```text
ai-worker-core/
|
+-- runtime/
|   +-- base.py
|   +-- registry.py
|   +-- capabilities.py
|   +-- events.py
|   +-- result.py
|
+-- runtime/openai/
|   +-- agents_runtime.py
|   +-- responses_runtime.py
|
+-- runtime/codex/
|   +-- codex_runtime.py
|
+-- runtime/copilot/
|   +-- copilot_runtime.py
|
+-- tools/
|   +-- base.py
|   +-- registry.py
|   +-- adapters/
|
+-- agent/
|   +-- profile.py
|   +-- request.py
|   +-- context.py
|
+-- policy/
|   +-- budget.py
|   +-- permission.py
|   +-- approval.py
|
+-- telemetry/
    +-- events.py
    +-- usage.py
```

然后：

```text
workers/
|
+-- coding-worker/
+-- review-worker/
+-- planning-worker/
+-- testing-worker/
```

---

# 25. 是否做成独立 AgentRuntime Service

第一阶段：

```text
NO
```

推荐：

```text
Worker Process
    |
    +-- AgentRuntime library
```

而不是：

```text
Worker
   |
 HTTP
   |
AgentRuntime Service
```

理由：

```text
更简单
低 latency
tool execution local
workspace local
CLI integration natural
failure isolation clearer
```

特别是 Coding Worker：

```text
repo workspace
git
compiler
test
codex
```

本来就在同一台执行节点上。

没有必要把 agent loop 跨网络拆出去。

---

# 26. 什么时候才需要 AgentRuntime Service

以后如果出现：

```text
Java Worker
Python Worker
Go Worker

全部需要同一种 Agent Runtime
```

而且开始重复：

```text
provider integration
cost control
tracing
auth
model routing
```

可以增加：

```text
LLM Gateway
```

但是：

```text
AgentRuntime
```

仍然应该留在 Worker。

即：

```text
Worker
    |
AgentRuntime
    |
LLM Gateway
    |
OpenAI
```

不要变成：

```text
Worker
    |
AgentRuntime Service
    |
tools remotely executed
```

除非以后真的有很强的集中式 Runtime 需求。

---

# 27. 第一阶段实现建议

建议不要一次实现所有 Runtime。

## Phase 1

实现：

```text
AgentRuntime SPI

DirectResponsesRuntime

CodexCliRuntime
```

先验证：

```text
Runtime abstraction 是否合理
```

---

## Phase 2

增加：

```text
OpenAIAgentsRuntime
```

用于：

```text
Planner
Reviewer
Requirement Analyzer
Generic Worker
```

Agents SDK 当前是 Python/TypeScript code-first runtime，适用于由应用自身管理部署、tool implementations、state storage 和 approval decisions 的场景。

---

## Phase 3

增加：

```text
CopilotCliRuntime
```

验证 backend portability。

如果：

```text
CodingWorker
```

只改配置就能从：

```text
Codex
```

切换：

```text
Copilot
```

而不用修改 Worker 逻辑：

那么 SPI 基本成功。

---

# 28. 推荐 MVP SPI

第一版我建议只保留：

```text
AgentRuntime
    run()
    resume()
    cancel()
    capabilities()

AgentRunRequest

AgentRunContext

AgentRunResult

AgentRunState

AgentEvent

AgentTool

AgentBudget

ExecutionPolicy
```

不要一开始制造几十个接口。

目标应该是：

```text
small SPI
rich data model
pluggable adapters
```

而不是：

```text
enterprise framework
```

---

# 29. 推荐的最终职责边界

```text
+--------------------------------------------------+
|                  CONTROL PLANE                   |
|--------------------------------------------------|
| Task                                             |
| Lease                                            |
| Scheduling                                       |
| Worker Registry                                  |
| Fleet Budget                                     |
| Admission                                        |
| Approval                                         |
| Audit                                            |
+--------------------------+-----------------------+
                           |
                           v
+--------------------------------------------------+
|                     WORKER                       |
|--------------------------------------------------|
| Worker Role                                      |
| Task -> AgentRunRequest                          |
| Workspace                                        |
| Result interpretation                            |
+--------------------------+-----------------------+
                           |
                           v
+--------------------------------------------------+
|                AgentRuntime SPI                  |
|--------------------------------------------------|
| run                                              |
| resume                                           |
| cancel                                           |
| tools                                            |
| events                                           |
| state                                            |
| usage                                            |
+-----------+----------------+---------------------+
            |                |
      +-----+-----+    +-----+------+
      |           |    |            |
      v           v    v            v
   Agents      Responses Codex    Copilot
    SDK          API     CLI       CLI
```

---

# 30. 最核心的设计原则

整个方案可以收敛成六条原则：

```text
1. Worker 不依赖具体 LLM backend。

2. AgentRuntime SPI 是 Worker 与 Agent execution engine
   之间唯一稳定边界。

3. AgentRuntime 是 library/runtime abstraction，
   第一阶段不是网络服务。

4. Tool、Budget、Permission、State、Event
   都定义成 backend-neutral model。

5. SDK handoff 是 Worker 内部行为；
   Worker 间协作必须经过 Control Plane Task。

6. Control Plane 管：
      WHO / WHEN / BUDGET / PERMISSION

   AgentRuntime 管：
      HOW TO EXECUTE

   Agent 管：
      HOW TO REASON
```

## 推荐基线

对于 AI Worker，我建议将下面这个结构作为后续 Worker 实现的正式基线：

```text
Java Control Plane
        |
        v
Pull-based Worker
        |
        v
AgentRuntime SPI
        |
        +-- CodexCliRuntime          <- coding 默认
        |
        +-- OpenAIAgentsRuntime      <- agentic 默认
        |
        +-- DirectResponsesRuntime   <- simple LLM 默认
        |
        +-- CopilotCliRuntime        <- alternative backend
```

其中：

```text
Coding Worker
    -> CodexCliRuntime

Planning Worker
    -> OpenAIAgentsRuntime

Review Worker
    -> OpenAIAgentsRuntime / CodexCliRuntime

Classification / Extraction Worker
    -> DirectResponsesRuntime
```

这样既不会把系统绑定到 OpenAI Agents SDK，也不会为了“抽象”过早增加一个中央 LLM 微服务。

这会保留 AI Worker 当前 Worker Pull 架构最重要的特征：

```text
Control Plane 去 Agent 化
Worker 自治
Runtime 可替换
Provider 可替换
Tool 可治理
执行可审计
```