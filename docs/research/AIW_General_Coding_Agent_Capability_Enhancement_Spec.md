# AIW 通用 Coding Agent 能力增强开发说明书

**版本：** v2（2026-11-18；修正原《AIW AWS Coding Agent 集成与执行环境改造开发说明书》的目标定位）  
**代码基线：** 用户上传 `aiwde-head.zip`；不以 GitHub 线上 HEAD 替代。  
**用途：** 交给 Codex 进行源码复核、分阶段实现与回归验证。  
**文档性质：** 设计／实施要求，不代表代码已经修改或测试通过。

## 0. 核心决定（务必先读）

**AIW 要增强的是现有的通用 Coding Agent，不是新增一个 AWS 专用 Agent。**

AIW 的现有 Python Worker + OpenAI Agents SDK 是共用执行主体；在不同任务下，通过预先登记并授权的 **Skills + Knowledge + Tools** 提升专业程度。AWS 只是首个专业能力包，可与数据库、前端、Java、安全、测试等能力平行扩展。

- 日常 Java/React/SQL 编码任务：保持通用 Coding Agent 的正常工作方式，**不自动加载 AWS 文档或连接 AWS 环境**。
- 涉及 AWS 架构、SDK、ECS、Aurora、IAM、CloudWatch 等任务：根据任务范围发现和加载相关 AWS Skills，按授权访问 AWS 官方知识及必要的只读资源。
- 混合任务（例如 Spring Boot + Aurora + ECS）：按任务选入通用编码能力与若干 AWS 专业 Skills，仍由**同一个 Worker / 同一个 Attempt** 执行；除非工作流本身明确安排独立任务或审查。
- 自由 Shell 和 Sandbox 是**通用 Coding Agent 能力**，不是 AWS 特权；AWS Skill 不授予 Shell、网络、IAM 或生产写权限。
- 不引入 `aws-engineer` 固定 Worker 类型，不强制新增 `aws-engineer` ProcessProfile，不复制第二套 Agent Factory、SkillLoader、Task/Lease 调度或 Git 交付机制。
- 新增配置采用 **TOML**。兼容既有 TOML ProcessProfile、平台已冻结 `aiw.execution.config.v1`、SkillFiles 和审批/审计合同。

## 1. 源码基线与事实／待核实项

本次修正对照了上传代码中的有关模块及以下文档：

| 已存在的模块 / 代码路径 | 确认用途或约束 |
|---|---|
| `docs/COMPOSABLE-ROLES.md` | 已支持组合角色、共享技能、角色内步骤；**角色不等于新的 Worker 类型/进程** |
| `docs/WORKER-OPTIONAL-SKILLS.md` | 已支持向现有 Worker 添加额外 Skill，不新增主角色 |
| `docs/EXECUTION-CONFIGURATION.md` | Candidate、Skills、Knowledge、Tool Requirements 在任务准备时固定版本和摘要 |
| `src/main/resources/static/ui/roles.js` | 组合角色已有 Skills/Tools 编辑入口，包括 `COMMAND` 勾选 |
| `src/main/resources/static/ui/workflows.js`、`workflow-editor.js` | 工作流步骤具有 `requiredSkills`；与配置页 `additionalSkills` 可能是**不同含义** |
| `src/main/java/me/asu/aidev/execution/config/ExecutionConfigurations.java` | 基础方法与 additionalSkills 组合为 Candidate |
| `src/main/java/me/asu/aidev/execution/config/AgentFactory.java` | 任务准备、工具授权、Worker 需求与自动启动 |
| `src/main/java/me/asu/aidev/workflow/application/WorkflowRuns.java` | 按 `requiredSkills` 参与匹配/绑定 |
| `workers/process-python/process_profiles.py` | 从 TOML 加载固定角色，仍被旧任务与兼容路径使用 |
| `workers/process-python/execution_configuration.py` | 严格校验固定配置，消费 Candidate、Skills、Knowledge；非自由结构 |
| `workers/process-python/skill_files.py` | 处理任务固定的 Skill 文件包和按需读取 |
| `workers/process-python/openai_api_runtime.py` | 现有 OpenAI Agents SDK `Agent` + `Runner`，工具接入受 Broker 约束 |
| `workers/process-python/process_worker.py` | 当前 `openai-agents` 明确拒绝 `AIW_AGENT_ALLOW_SHELL=true` |
| `workers/process-python/guarded_openai_tools.py` | SDK 工具由已授权的 Broker FunctionTools 组成 |
| `workers/process-python/execution_guard.py`、`guarded_command.py` | 当前 COMMAND 依赖固定命令/审批及受控执行器，非通用自由 Shell |

**Codex 必须先进行 P0 复核，不可从文件名推断全部运行时行为。** 特别区分三套常被混淆的概念：

1. `ProcessProfile`（Python 的旧角色 TOML / 固定主方法）。
2. `AI Profile`（模型、Runtime、模型参数的已批准配置）。
3. `Skill / Role Composition`（工作方法和专业能力；与 Worker 进程身份不同）。

还必须区分：

- `requiredSkills`：工作流中用于能力匹配/分配的集合；
- `additionalSkills`：动态配置中附加的实际方法／内容资产；
- `primarySkill` / `skillRef`：方法或执行任务的主入口；
- `toolRequirements`：需要哪些可执行工具，**不能从 Skill 文本自动升权**。

不能在未核实端到端语义前，将这些字段合并、重命名或删除。

## 2. 目标架构：一个通用 Agent，多种专业能力

```text
UI / Secretary / Workflow
        |
        | Task + task-related Skills (requested or approved auto-selected)
        v
Control Plane / Existing Agent Factory
        |  freeze Task/Method/Skills/Knowledge/Tools/Model
        |  Worker Pull + Lease + Approval + Audit
        v
Existing Python Worker (process-python)
        |
        v
OpenAI Agents SDK Runtime (general-purpose)
        |-- General Coding tools: Sandbox Shell / Files / Git / Test
        |-- Task-pinned Skill discovery + lazy loading
        |-- Task-pinned Knowledge / company ADR
        |-- Optional professional tools: AWS Docs MCP, AWS Read-only MCP, ...
        |
        v
Existing Result / Review / Verification / Approval / Handoff
```

### 2.1 「自动专业化」的准确含义

**阶段 A：任务准备前选择**

- 根据任务描述、仓库技术栈及用户已选择的 Skills，建议最合适的能力组合。
- 在有明确授权策略的情况下，允许规则化自动选择可用的专业 Skills；其他情况展示候选供用户确认。
- 自动选择**只能在已登记、已授权、版本固定的 Skill 资产集合内**进行，不能从互联网随意下载指令并执行。
- 选择结果必须进入既有准备／冻结配置和审计；不得在领取任务后无记录地更换 Skill、IAM、工具或模型。

**阶段 B：执行中按需加载**

- SDK Agent 可从当前 Attempt **已固定**的 Skills 清单发现相关的 `name / description / triggers`，按任务进展按需读取具体正文和参考资料。
- 即使任务含 AWS，也不要预加载所有 AWS Skills；仅加载必要子集。
- 执行中发现缺失的能力：返回「建议补充某 Skill/工具」或走现有正式重新准备／审批流程；**不能在当前 Attempt 临时扩大授权**。

**示例：**

| 任务 | 自动专业化行为 |
|---|---|
| 修复 React 表单 | `coding` + `diagnosing-bugs`；AWS 相关内容不出现 |
| 优化 PostgreSQL SQL | 通用 SQL/DB 专业知识；若明确使用 Aurora，再增加 Aurora Skill |
| Java SDK 上传 S3 | Java Coding + AWS SDK/S3 Skill + AWS Docs；无需真实 AWS 环境写权限 |
| ECS Fargate 部署设计 | AWS ECS + IAM + Networking Skills；可以只读检查现有 VPC/ECS |
| IAM Policy Review | AWS IAM Skill + 官方文档；不授予 IAM 修改权限 |
| 混合 Java/CloudWatch | 通用 Java Skill + AWS Observability Skill |

## 3. 专业能力模型（可扩展，不是 AWS 特例）

### 3.1 Skill：工作方法

标准 `SKILL.md` 应包含：触发条件、非触发条件、输入、工作流程、输出、验证手段、需要的工具**声明**、禁止事项、引用资料。Skills **不负责直接定义权限**。

先将 AWS 专业能力作为现有平台 Skill 资产扩展：

```text
src/main/resources/worker-skills/
  ... existing skills ...
  aws-architecture/
    SKILL.md
  aws-iam-security/
    SKILL.md
  aws-ecs-fargate/
    SKILL.md
  aws-aurora-postgresql/
    SKILL.md
  aws-java-sdk/
    SKILL.md
  aws-observability/
    SKILL.md
```

以仓库现有规范源、`index.json` / 固定文件包和已有部署副本机制为准；新资产须登记版本与摘要。对于来源于 AWS 官方 Agent Toolkit 的材料，应记录来源与固定版本、许可证和审核信息；不要简单覆盖或重复已有通用 Skill。可优先先实现 **3 个**高价值 Skills（SDK、ECS、IAM），确认效果后扩到 Aurora/Observability。

### 3.2 Knowledge：项目事实

复用现有 `PreparedConfigRef.knowledge` 和知识资产冻结机制；公司基础架构、命名规范、技术决策、网络边界、数据库约束均应走版本化引用。官方 AWS API 事实可按需通过官方 Docs MCP 检索，但检索结果必须标明来源与时间，不当作永久公司政策。

### 3.3 Tools：可执行能力

- 通用 Tools：Shell、文件编辑、Git、编译、测试（由统一 Sandbox 能力控制）。
- 专业 Tools：AWS Docs MCP、只读 AWS Resource MCP；以后可增加 Kubernetes、数据库诊断、浏览器自动化等。
- **Tool Availability 与 Tool Authorization 分开**：一个 Skill 请求某工具不等于任务已被授权。
- 对所有专业能力复用同一个 `Tool Capability Resolver`，不让每种专业域新增一套执行/权限模型。

## 4. 通用 Coding Agent 的 Shell/Sandbox 改造（优先级最高）

### 4.1 目标

让所有有编码权限的通用 Coding Agent，在隔离工作空间内**自动执行**常规 `shell` 命令、管道、重定向、项目脚本、Maven/npm/pytest、Git、静态检查和调试。不要让固定命令白名单和每条命令的审批打断正常自动化。

### 4.2 方案

- 继续使用 OpenAI Agents SDK；为现有 `openai_api_runtime.py` 增加通用 Sandbox 执行模式／适配器，而不是新增 AWS Runtime。
- 采用 SDK 官方 `SandboxAgent`/`Runner`/`SandboxRunConfig`/`Skills` 等（**由 Codex 针对仓库锁定的 SDK 版本验证 API**），不要重新实现文件编辑和 Agent Loop。
- **强隔离是必需条件**：SDK 的本地 Unix 执行仅适用于可信开发场景，不能当成隔离安全边界。若选择 `sandbox-auto`，必须使用已验证的隔离后端（例如 SDK 支持的 Docker-backed sandbox 或其他受控后端）；这仍属于“使用 SDK Sandbox”，不是另写一套编码工具。
- 隔离 Sandbox 可以自由使用 Shell，但没有 Worker Token、Control Plane 凭证、生产 AWS Key、SSH Agent、宿主 Docker Socket、宿主私有目录等。
- 必须限制宿主文件映射、网络、CPU/内存/进程/超时，并核对取消时是否终止整个进程树；失败不得回退到不隔离 Shell。
- `COMMAND` 旧权限及 `guarded-command-v1` 保留供旧任务使用；新增独立的任务级自由执行授权语义（例如 `SANDBOX_EXEC`，**名称仅建议，需经 Java/Python 封闭协议一起确认**），不可偷换既有固定 `argv` COMMAND 语义。
- 对 Git push、生产部署、DB DDL、云资源写入等另设操作权限或独立执行器；不能因为能运行 Shell 就可触及生产。
- 普通开发命令不需要逐次人工批准，但任务级 Sandbox 权限仍须在准备、claim、Worker 能力登记、审计中形成可追溯证据。

### 4.3 通用优先，云能力后加

Codex 首先以**不涉及 AWS 的 Java 编译/测试或 React 修复**验收 Sandbox；确认通过后再挂接 AWS Skills/MCP。这样可以验证我们真正增强了通用 Coding Agent，而不是只为某个 Skill 特例绕过 Guard。

## 5. 任务级 Skills 选择：尽量复用现有机制

1. 不删除 `ProcessProfile`，作为旧任务与默认方法的兼容入口。动态 Candidate/组合角色继续是主要扩展机制；UI 不必强制用户选择固定 Profile。
2. 复用 `ExecutionConfigurations.java`、`SkillFiles.java`、`skill_files.py` 已有的资产登记、冻结、摘要及读取。
3. 在用户明确选择时按现有配置加入新 Skills。**如果要自动推荐/自动选择**，优先扩展**任务准备侧**的推荐逻辑，不能改成 Worker 执行过程中默默注册新 Skill。
4. 仅允许从已登记/当前 Owner 和仓库许可范围内的资产选择；保留组合上限、版本一致性、结果类型/只读冲突检查。
5. 任务运行时可通过已固定的 Skill 元数据进行 `list`/`load`（或复用 SDK `Skills` Capability），但不另建第二个长期 Skill 目录或动态网络安装通道；冻结内容是唯一真实输入。
6. `requiredSkills`（workflow/team matching）和 `additionalSkills`（具体方法附加资产）需要设计一条**明确映射/桥接**，不能仅因为名称相似就认为它们是同一列表。必要时先支持配置 UI 的附加 Skills，再扩展工作流自动路由。

## 6. AWS 专业能力：只作为第一批插件式能力

### 6.1 知识

- **AWS 官方文档 MCP**：当前 API、服务限制、SDK 用法、架构建议，按需检索。
- **AWS Skills**：采用 AWS Agent Toolkit for AWS 的可审核、可版本锁定材料，适配为 AIW Skill 资产。
- **公司知识**：项目架构/ADR、现有 AWS 资源命名、技术栈约束等走 AIW 知识引用。

### 6.2 工具与认证

- AWS Docs MCP 和 AWS Resource MCP 使用不同逻辑授权；对于只需写 Java S3 SDK 代码的任务，**只开文档，不开真实 AWS 资源查询**。
- AWS 资源访问默认关闭；获准后使用受限临时 IAM Role，缩小账户、Region、API 与资源范围；防止“只读”能力读取敏感内容。
- MCP 控制端应运行在受信任 Worker 侧，不将认证材料复制到自由 Shell Sandbox；SDK 的 MCP Tool Filter 不能替代 IAM。
- Sandbox 网络与 MCP 网络隔离。严禁通过 Shell 里的 `aws` CLI 或 `curl` 越过受限工具策略访问真实 AWS 权限。
- 本阶段不自动创建/修改/删除 AWS 资源，不部署生产，不把 AWS 管理权限授予模型。

## 7. TOML 配置设计（兼容旧结构）

保留 `workers/process-python/profiles/*.toml` 原有结构；**不要直接添加未被严格解析器支持的新字段**。建议在已有运行时/Worker 配置中引入专用 TOML 或扩展现有已存在的配置入口。最终字段须由 Codex 与配置快照、Java 验证逻辑一起确定。

以下仅为**目标语义示例，不是现有已实现 Schema**：

```toml
[agent]
runtime = "openai-agents"
execution_mode = "sandbox-auto"  # 通用 Coding Agent 模式

[sandbox]
backend = "sdk-isolated"          # 必须映射到已验证的 SDK 隔离后端
workspace_scope = "task"
shell_auto = true
network_policy = "restricted"

[skills]
selection = "task-scoped"
auto_recommend = true
load = "lazy-from-pinned-assets"

[mcp.aws_docs]
enabled = true                     # 只在任务需要并已配置时启用

[mcp.aws_resources]
enabled = false                    # 默认关闭；按单个任务授权

[security]
cloud_write = "deny"
production_deploy = "deny"
```

注意：TOML 是**配置文件表示方式**；当前平台跨 Java/Worker 的 `aiw.execution.config.v1` JSON 传输和摘要合同可继续保留。用户要求 TOML **不意味着必须修改现有 Worker Protocol 的 JSON**。新增运行时配置要参与 Worker 能力/策略快照；Attempt 期间不得暗中热更新。

## 8. 实施阶段（每阶段独立验证；先通用，再 AWS）

| 阶段 | 内容 | 验收点 |
|---|---|---|
| **P0** | 源码精确复核：Profile、requiredSkills/additionalSkills、Candidate、SkillFiles、OpenAI Runtime、Broker、Guard、Lease | 完整调用链和最小修改清单；现有测试基线 |
| **P1** | 通用 Sandbox Adapter + TOML + 执行后端预检 | **非 AWS** Maven/pytest/React 任务可自动执行 Shell，原 Runtime 不变 |
| **P2** | 新任务级自由 Shell 权限与 Guard 合同 | 不逐命令审批；无权限拒绝；审计、取消、UNKNOWN 与隔离可验证 |
| **P3** | 已冻结 Skills 的按需加载/任务级专业化选择 | 可在配置 UI 选 AWS Skill；非 AWS 不加载；版本/摘要一致 |
| **P4** | 自动推荐能力（准备阶段） | AWS/非 AWS 混合任务匹配准确；不越过配置冻结/Owner/仓库范围 |
| **P5** | AWS Docs MCP 与受限 AWS Resource MCP | 文档可用，真实查询按任务授权；无权不能读取/写入云资源 |
| **P6** | 专业能力验收：SDK/S3、ECS、IAM、Aurora、CloudWatch | 专业输出、验证和引用质量提升；不改变通用 Coding 流程 |
| **P7** | AIW 全链回归/兼容/回滚/文档 | 原 Worker Protocol、Review/Approval、旧 Profile、已有任务都不回退 |

P4 可在 P3 实现后逐步上线。若短期以「用户选择 AWS Skill」为主，P4 不必阻塞 P5；但长期目标应支持在任务准备时推荐专业能力，减少手工维护。

## 9. 验收用例（必须包含负向测试）

### 9.1 通用任务（优先）

1. **Java**：修改现有类 → `mvn test` → 读取失败日志 → 修复 → 测试通过；无逐条命令审批。
2. **React**：修复 UI → npm 测试/构建 → 查看差异；不用 AWS 知识或 MCP。
3. **Python**：改脚本 → `pytest` → 修复；无需新 Worker 角色。
4. **安全**：Sandbox 无法访问宿主 HOME、Worker Token、宿主 Docker Socket、生产凭证、工作区外目录；取消后无残留命令后代。
5. **兼容**：禁用新能力时保留原 Broker/固定命令/旧角色行为。

### 9.2 专业任务（AWS）

6. **S3 SDK**：Java AWS SDK v2 S3 组件和测试；仅需 AWS Docs，不访问真实生产账户。
7. **ECS**：生成 Fargate Task Definition/Terraform plan；可以按授权读取当前 VPC/ECS；禁止 apply。
8. **IAM**：审查过度授权并引用官方依据；禁止修改 Policy。
9. **Aurora**：解释 PostgreSQL 执行计划/锁风险；无生产 DDL。
10. **CloudWatch**：生成日志与告警配置；无生产变更。
11. **专业化正确性**：AWS 任务只加载相关 Skill；普通 React 任务不加载 AWS Skills/不启动 AWS MCP；AWS 资源查询权限不足时明确报告，而不是通过 Shell 绕过。

### 9.3 生命周期与审计

12. Task/Attempt/Lease 过期、取消、进程崩溃、UNKNOWN 副作用处理、恢复/重试去重、Git Worktree 保留/交付、独立 Review 与 Approval 均不得退化。
13. 审计至少记录所用 Skill/Knowledge/Tools 的固定版本与摘要、执行后端、工具授权、命令及结果摘要、文件变更/测试结果；日志/Trace 必须脱敏。

## 10. Codex 具体指令（可直接交给 Codex）

> **需求定位纠正：** 这不是建设 AWS 专用 Coding Agent。请以用户上传的 AIW 当前源码为基线，增强**现有通用 Python Coding Worker（OpenAI Agents SDK）**的通用自主执行能力，并建设可插拔、按任务加载的专业能力体系。AWS 是第一批 Skills + Knowledge + MCP 组合，不得增加新的 AWS 固定 Worker/Runtime/Profile，除非 P0 证据表明完全无法复用且先报告理由。
>
> 先执行 P0：追踪 UI/workflows 中的 `requiredSkills`、配置中的 `additionalSkills`、`primarySkill`、`skillRef`、ProcessProfile、AI Profile、Candidate 固定配置、Python SDK Runner、GuardBroker、Tool approvals、Worker Factory/Lease。输出实际调用链和测试基线；明确区分现有实现与设计建议。然后分阶段实施 P1–P7，每阶段小提交并运行关联测试。
>
> 对所有普通 Coding 任务，支持在**真正隔离的 SDK Sandbox**中自由 Shell、文件编辑、Git、构建、测试、调试，免逐命令审批；不要取消任务级许可、资源边界、Lease、审计、取消及 UNKNOWN 处理。SDK API 必须以当前受支持版本验证；不得将 Unix Local 宿主进程误认为沙箱。若隔离条件不满足，新模式应 fail closed，不得退化为无隔离 Shell。保留旧 Broker/Guard 兼容路径。
>
> 复用当前 `aiw.skill.files.v1` 资产、版本/摘要和按需读取。任务准备前允许推荐或按批准策略选择相关技能，执行时只能在已经固定的技能集合内按需读取；不得通过网络临时安装 Skill 或因为 Skill 声明而授予工具权限。首先提供 AWS SDK/S3、ECS、IAM Skills，并配置官方 Docs MCP；真实 AWS 查询单独授权、默认关闭且只读。任何云写入、生产部署和敏感凭证都不进入自由 Shell Sandbox。
>
> 新增人工配置统一使用 TOML，但不擅自改动现有跨进程 JSON 协议。所有功能默认可关闭并具备回滚方式。最后提交：修改文件、协议版本/迁移、配置示例、测试命令及结果、局限、回滚步骤、通用任务与 AWS 任务的对照评估。

## 11. 明确不做的事

- 不建设第二个 `AwsAgentFactory`、AWS 特有 Worker 调度服务或常驻进程。
- 不要求所有 Coding 任务加载 AWS Skills；不把云知识放进所有 Agent 的系统 Prompt。
- 不将 `ProcessProfile` 删除或重新命名为 AWS Profile；只在已有动态配置／组合角色机制上增量扩展。
- 不为了 TOML 需求改写既有 JSON Worker Protocol。
- 不让 Skill、MCP Tools 或 Agent Prompt 绕过现有执行授权与审计。
- 不把所有专业领域的工具都放进模型上下文；权限和工具均按任务最小化。
- 不预设后续一定扩展到多 Agent。现阶段一个通用 Agent + 按需能力已经满足目标。

## 12. 参考与验证要求

- OpenAI Agents SDK Sandbox Guide：<https://openai.github.io/openai-agents-python/sandbox/guide/>
- OpenAI Agents SDK Sandbox Quickstart：<https://openai.github.io/openai-agents-python/sandbox_agents/>
- AWS Agent Toolkit for AWS：<https://github.com/aws/agent-toolkit-for-aws>

这些官方文档用于 Codex 验证 SDK 的真实 API、Sandbox 隔离与 AWS MCP 接入。SDK 版本/后端、AWS Endpoint/认证方式等必须根据当前实际依赖和官方文档验证，不能把示例视为已通过集成测试。

---

**最重要的验收定义：** 修改完成后，AIW 不需要一个名叫 `aws-engineer` 的特殊 Worker。只要普通 Coding Worker 收到需要 AWS 的任务，就可以通过当前任务已获授权的 Skills、知识和工具表现得更专业；面对不涉及 AWS 的任务，它仍然是原来的通用 Coding Agent。





# 新的内容

我认为你现在的 AIW 已经有了正确的基本方向，缺少的不是另一个 Agent 框架，而是一个**清晰、可实现的 Worker 装配模型**。

我重新对照了你上传项目中的 `AGENT-FACTORY.md`、`COMPOSABLE-ROLES.md` 和 `EXECUTION-CONFIGURATION.md`。

其中已经有几个重要基础：

- Agent Factory 优先复用兼容的在线 Worker，否则启动内置 Python Worker。
- 外部 Worker 通过统一的 Worker Protocol 接入。
- Skills、Knowledge、执行配置都有版本与摘要，可以冻结。
- 组合角色已经支持不同 Skills 和工作步骤。
- 现有 Python Worker 已经支持 OpenAI Agents SDK。

所以我不建议重构这些基础设施。

**我建议将 AIW 的 Agent Factory 明确拆成两个逻辑阶段：能力装配（Assembly）和执行实例化（Instantiation）。** 两个阶段可以由现有模块实现，不一定需要增加两个服务。

## **一、最关键的设计：Worker 不等于 Agent**

我建议在 AIW 内部明确区分四个概念。

| 概念            | 职责                           | 示例                       |
| --------------- | ------------------------------ | -------------------------- |
| Worker Runtime  | 实际执行 Agent 的程序          | Python + OpenAI Agents SDK |
| Worker Template | 定义专业能力的模板             | AWS Engineer               |
| Agent Instance  | 使用模板及任务配置创建的 Agent | 本次 Aurora 优化 Agent     |
| Worker Process  | 运行 Agent 的操作系统进程      | 本机 Python / ECS Task     |

这样就不需要为每个 AWS、数据库、Java 专家复制一套程序。

关键点：**Factory 不必直接生成 Python 源代码。它生成一个确定的 Agent 规格，再由兼容的 Worker Runtime 实例化。**

## **二、Agent Factory 应该装配什么？**

我建议装配五个部分。

01

**Role**

职责、目标、结果类型。例如 Coding Engineer。

02

**Skills**

专业工作方法。例如 aws-ecs、java-development。

03

**Knowledge**

官方文档、公司规范和项目背景。

04

**Tools**

实际可调用的 Shell、MCP、Git、数据库工具。

05

**Execution**

模型、Sandbox、环境、凭证和资源限制。

但这五个部分不应该全部由 Skill 决定。

尤其是 Tools 和 Execution，属于实际执行能力，必须和 Runtime、宿主环境以及授权机制绑定。

我建议一个简单的规则：

> Template 定义默认能力，Skill 声明需要的能力，Factory 选择兼容的执行者，执行政策决定最终授权。

## **三、用 TOML 表达专业 Worker 模板**

例如，AWS 工程师模板：

```
# templates/aws-engineer.toml

[template]
id = "aws-engineer"
version = "1.0"
base = "coding-agent"
description = "AWS software and infrastructure engineer"

[skills]
default = [
  "aws-architecture",
  "aws-security"
]

optional = [
  "aws-ecs",
  "aws-aurora",
  "aws-iam",
  "aws-networking"
]

[knowledge]
sources = [
  "aws-official-docs",
  "company-architecture"
]

[tools]
required = [
  "filesystem",
  "git",
  "shell",
  "aws-docs-mcp"
]

optional = [
  "aws-api-mcp",
  "terraform"
]

[execution]
runtime = "openai-agents"
sandbox = "isolated-auto"

[security]
aws_access = "readonly"
production_mutation = false
```

这里有两个重要设计：

**Template 是默认装配方案，而不是固定 Worker 类。**

例如某个 AWS 开发任务只需 AWS SDK 和 S3 文档，不需要 ECS、Terraform，就不加载这些能力。

**同一个 Template 可以由不同 Worker 执行。**

本机 Python Worker、远程 ECS Worker 只要声明兼容能力，就可以领取这类任务。

上面的 TOML 是我建议新增的模板格式，不是当前 AIW 已经支持的字段。

## **四、不要让 Agent Factory 每次都让 AI 决定全部配置**

我认为这是最容易走错的地方。

如果每次接到任务，都让模型决定：

- 生成什么 Worker。
- 下载什么工具。
- 装什么依赖。
- 需要什么 IAM 权限。
- 加载什么 Skill。

系统会变得不可预测，也很难审计。

更好的方式是 **确定性装配 + AI 辅助推荐**。

例如用户提出：

> 请优化我们运行在 AWS Aurora 上的 Java 应用。

Agent Factory 可以得到一个候选：

```
Base: coding-agent

Skills:
  java-development
  aws-aurora
  postgresql-performance

Knowledge:
  aws-documentation
  company-architecture

Tools:
  shell
  git
  maven
  aws-mcp-readonly

Sandbox:
  isolated-auto
```

AI 可以推荐专业 Skills，但不能凭推荐直接获得更多权限。

最终配置必须经过现有 AIW 的配置校验与授权，然后冻结为一次任务的执行规格。

这与你目前已有的 `PreparedConfigRef` 和 `aiw.execution.config.v1` 思路一致。

## **五、我建议增加一个 Tool Registry**

这是我认为你现有 Agent Factory 最值得增强的部分。

Skills 已经有自己的注册、版本和文件机制，但真正的专业 Worker 还需要处理工具的实际可用性。

建议增加一个轻量级 Tool Registry，记录每个工具：

| 字段               | 用途                 |
| ------------------ | -------------------- |
| `id`               | 唯一工具标识         |
| `kind`             | MCP、SDK、Shell、CLI |
| `runtime`          | 支持的运行时         |
| `capabilities`     | 提供哪些操作         |
| `requirements`     | 依赖、环境、网络     |
| `permission_scope` | 需要的授权范围       |
| `provider`         | 实际工具适配器       |

例如 AWS 工具：

```
# tools/aws-docs.toml

[tool]
id = "aws-docs"
kind = "mcp"
adapter = "aws-mcp"
version = "1"

[capabilities]
operations = [
  "search-documentation",
  "read-documentation"
]

[requirements]
network = true

[security]
credential = "none"
mutating = false
```

Factory 不应根据工具名称猜测实际权限。Registry 的声明需要和 Worker 的真实适配器能力、身份及授权结果进行校验。

第一版甚至可以将 Tool Registry 保存在 TOML 文件里，不需要增加数据库表。

## **六、Worker 如何实例化 Agent？**

我建议让 Python Worker 实现一个很薄的装配器。

```
def create_agent(spec, runtime_context):
    skills = resolve_skills(spec.skills)
    tools = resolve_tools(
        spec.tools,
        runtime_context,
    )

    return Agent(
        name=spec.name,
        instructions=build_instructions(
            spec.role,
            skills,
            spec.knowledge,
        ),
        model=spec.model,
        tools=tools,
    )
```

这是说明接口职责的伪代码。真正实现还需要处理 MCP 生命周期、Sandbox Capability、冻结 Skill 摘要及会话恢复。

重要的是，Worker 不需要识别 `aws-engineer` 这个专业角色。

它只需要知道：

- 如何加载已授权 Skills。
- 如何创建实际可用的 Tools。
- 如何准备 Sandbox。
- 如何构造 OpenAI Agent。
- 如何执行并提交结果。

专业能力主要来自配置，而不是硬编码在 Python Worker 中。

## **七、如何处理独立外部 Worker？**

我建议严格沿用你现有的协议，不让 Factory 接管外部进程。

例如：

**Worker 能力匹配**

| 任务需要          | 实际 Worker 能力 |
| ----------------- | ---------------- |
| OpenAI Agents SDK | 运行时适配器支持 |
| Shell Sandbox     | 隔离执行后端     |
| AWS Docs MCP      | 已连接的工具服务 |
| AWS API ReadOnly  | 相应权限范围     |

Factory 先查找已有在线 Worker。如果存在能力、仓库范围、授权和资源容量均兼容的外部 Worker，就优先复用。

如果没有，再考虑启动平台内置 Python Worker。

这正符合你现有的 Agent Factory 工作方式。

特别注意：Worker 自报 `aws-readonly` 只能表示能力声明，不能证明其 IAM 权限真的符合预期。真实权限仍要依靠 IAM 与执行环境保证。

## **八、最小改造的范围**

我建议分两轮实施，不要一次性重构所有模块。

第一轮只做三件事：

1. **把专业 Worker Template 作为配置对象**，复用当前组合角色和 PreparedConfig 的机制，不另建业务角色枚举。
2. **增加 Tool Registry / Tool Resolver**，让专业工具能够按任务绑定，而不是全部固定在 Python Worker 中。
3. **增加 Sandbox 执行模式**，让 Shell、编译和测试能够自主运行，并保持工作区与凭证隔离。

第二轮再增加自动化：

- 根据任务推荐 Skills 和 Template。
- 让 Agent Factory 自动补齐必要知识来源。
- 支持按需启动具有不同环境的 Worker。
- 建立专业能力评测与质量反馈。

### **我认为暂时不必做的事情**

不必建立新的 Worker Protocol，不必自动生成 Python 项目，不必为每个专业方向增加一个 Worker 进程，也不必建立复杂的 Plugin Marketplace。

## **九、最值得确定的一个架构原则**

我建议 AIW 未来采用这样的关系：

**一个 Worker Runtime 可以实例化多个专业 Agent，但一个 Agent Instance 只能在本次任务已授权的能力范围内执行。**

这样既保留了专业 Worker 的明确身份，也保留了通用 Runtime 的复用性。

还有一点我认为非常重要：

**Agent Factory 不一定要创建 Worker。它应该优先装配一份任务所需的能力规格，再寻找能够执行该规格的 Worker。**

这会让 Factory 从目前偏重启动和兼容匹配的机制，真正发展为你最初希望的「装备不同专业员工的工厂」。

如果要继续深入，我建议下一步就专门设计 **Agent Specification（Agent 装配规格）+ Tool Registry + Worker Capability Matching** 三者之间的接口，而不是马上讨论 AWS Skills 的具体内容。这三个接口确定以后，AWS、Java、数据库、安全等专业能力就都可以使用同一套机制。