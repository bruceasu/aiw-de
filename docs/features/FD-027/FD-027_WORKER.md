# FD-027: 同一 Worker 按任务执行不同专业配置

**Status:** Open
**Revision:** 3
**Priority:** Medium
**Evidence policy:** Dual

## Problem

Python 当前通过 `AIW_PROCESS_ROLE` 固定 Profile、primary Skill 和 resultKind，并在执行中要求 task.skillRef 完全匹配该 Profile。用户希望同一个兼容 Worker 按任务切换专业配置，复用 Lease、心跳、成果及取消，不为每个职业另建常驻进程。

首期是软件开发 Worker 与 Python 执行实现，项目语言不限于 Python。十项公司/AI 职责是配置分类，不是权限来源、管理层或固定进程数量。当前代码存在两条后端路径，不能把其功能差异或操作系统限制隐去。

## Options and decision

| 选项 | 取舍 |
| --- | --- |
| 继续每角色独立进程 | 简单但不能满足同一 Worker 逐任务切换 |
| 从既有 Profile 演进为不可变任务配置 | 选用；复用九字段 Profile、Skills、resultKind 及现有协议生命周期 |
| 自由生成执行脚本、Tools 或长驻职业 Agent | 不采用；超出 Option B，扩大授权及运维边界 |

Factory 是平台正式模块，不是新微服务；确定性目录查找、组合及校验优先。只有现有配置/Skills 不足时才请求声明式内容生成，候选仍须经平台校验。生成的说明不会注册新执行工具。

## Solution

### 配置准备与快照

候选配置记录 configId、revision、内容摘要、职责分类、Skills 及各内容摘要、resultKind、能力要求、候选权限需求、知识引用、输出约定和来源。任务事实置于固定上下文，不写成永久 Skill。知识归 Knowledge Management；引用有版本和访问范围，不读取秘书私库或自动上传私人材料。

先精确复用合适配置，再组合已有 Skills，最后生成声明式说明/清单。平台校验封闭字段、大小、已有工具引用、知识访问和输出协议，保存不可变版本；配置只表达权限需求，实际许可由 FD-029 签发及执行检查。

计划节点可尚未绑定 Skill，但在生成可领取任务前必须完成准备。准备状态建议为 NEEDS_CONFIGURATION、PREPARING、READY、BLOCKED，独立于 WorkTask/Lease；不先创建一个能被领取、然后才发现不兼容的任务。失败显示稳定原因码、缺项和有权限的恢复操作，不自动扩大资源或改用其他模型。

任务领取载荷携带固定专业配置快照及摘要；Worker 校验任务/配置/Runtime/输出格式一致后，获得 Lease 的本次执行才创建 Agent。旧 launch role 仍可服务旧合同任务；支持新配置的 Worker 明确广告配置协议版本和真实 Runtime、工具适配器、资源/工作空间及网络条件。Go/TypeScript 未实现新版时不得领取新版要求，不要求首期同步新能力。

### 首期默认配置建议

以下从现有 Python Profiles/Skills 派生，尚未赋予真实工具或资源权限。具体工具包受 G1/G2 决定约束。

| 默认职责 | 既有方法来源 | 输出 | 权限需求候选 |
| --- | --- | --- | --- |
| 目标讨论/需求分析 | goal-discussion、software-requirements-analyst | REPORT | 明确提交的目标/材料；需求分析可读指定仓库 |
| 架构/计划 | solution-architect、implementation-planner | REPORT | 指定仓库及正式上游成果只读 |
| 开发/修复 | coding-work-package-executor | WORKSPACE | 指定隔离工作区读写、本地成果提交；命令执行另行授权 |
| 代码/阶段审查 | software-code-reviewer、workflow-stage-reviewer | VERDICT | 固定版本差异及上游证据只读 |
| 验证/集成验证 | software-test-verifier | VERDICT | 固定工作区与证据只读，验证命令需独立获准适配器 |
| 集成 | coding-work-package-executor + 集成输出约定 | WORKSPACE | 指定集成工作区与固定结果版本；不含 push/发布 |

需求评审处理需求矛盾，行为审计处理实际行为与授权证据；不把代码审查改名为行为审计。秘书/知识是闭环支持配置或服务，沿用各自身份和知识治理合同。其余市场、安全、财务等分类仅保留目录及明确未实现标识；不创建假能力或默许额外授权。

### 运行后端与取消

事实：`codex-app-server` 是现有默认，Python 文档要求 Unix/WSL，native Windows 不支持其已确认进程树终止；`openai-agents` 支持 Windows，现有工具为受限工作区读取/写入，没有 shell。旧 codex-sdk 已移除，不恢复。

2026-10-23 用户明确选择首期同时支持 Python OpenAI Agents 和 Codex App Server。模型由已批准 AiProfile 指定，不内置具体模型/价格或实际凭证。软件验证命令需 FD-029 的独立许可和隔离执行适配器，未配置时对应任务保持准备阻塞。Codex 纳入同一新合同，必须落实原生工具限制和平台受控工具路径；不能只保留旧能力并宣称满足本次范围。

取消、Lease 丢失及进程退出继续沿用现有终止确认，不在配置切换时复用前一个任务的 Agent 会话、凭证、权限、工作区或未确认子进程。未知终止停止该槽并保留证据。常驻、按需拉起和空闲回收策略继续延期，不借本 FD 决定。

## Scope

拥有 AG01～06 和 AC04；不实现流程图、渠道问答或另一套工具授权。新库不导入旧配置快照/身份/授权；不删除用户 DB。保持 `/api/r1/worker-protocol/v1` 现有身份和 Lease 检查；新增配置载荷与能力版本是明确合同变更，需要独立设计反馈。

### 决策与恢复建议

- G1 已解决（2026-10-23）：当前用户选择两种 Python 后端首期都支持；Codex 仍运行于 Unix/WSL，OpenAI Agents 保留 Windows 支持。实际模型/批准 Profile 由运行配置提供。
- G2 已解决（2026-10-23）：用户确认指定仓库/隔离工作区、本地 Git 成果、验证命令明确许可及隔离边界；本表不授予真实任务权限。
- 准备失败/无兼容 Worker/权限不足建议分别展示 PREPARATION_FAILED、NO_COMPATIBLE_WORKER、AUTHORIZATION_REQUIRED，保留任务意图和版本；允许有权限者修正配置、接入兼容 Worker 或处理对应申请后显式重试，不自动降级 Runtime/模型/权限。
- 上表软件配置及错误恢复为可审阅的设计建议；与用户政策冲突时更新对应 Gate，不伪称已有运行能力。

### D027-02：版本化准备与领取合同

共享合同 `aiw.execution.config.v1` 固定以下对象：PreparedConfigRef={id,revision,digest,protocol,resultKind,skills[{ref,digest}],knowledge[{ref,version,digest}],toolRequirements}；WorkerCapabilities={protocolVersions,runtime,adapterVersions,platform,resourcePolicyDigest}；Preparation={intentId,intentVersion,generation,state,configRef,errorCode}。所有引用必须是平台登记对象，不能包含模型给出的任意下载 URL 或新的可执行 Tool。

Preparation 的 intentId+intentVersion 唯一；变更意图产生新版本，原准备结果不得覆盖。PREPARING→READY 的 CAS 与固定配置引用、业务任务创建在同库事务提交；配置摘要、授权/预算或兼容能力不足不提交可领取任务。claim 在同一准入事务固定所选能力版本；实际执行前复验快照摘要，不能自动换最新配置或 Runtime。准备重试只处理该意图当前版本，不清零业务消耗。

封闭错误码：INVALID_CONFIGURATION、UNSUPPORTED_RUNTIME、PREPARATION_FAILED、NO_COMPATIBLE_WORKER、AUTHORIZATION_REQUIRED、BUDGET_CONFIGURATION_REQUIRED、SOURCE_CHANGED。界面说明缺项和动作：修正候选、重新准备、接入兼容 Worker、处理具体授权/预算申请。禁止静默降级后端/模型/权限。

### D027-01：双后端调用链

两后端只把任务动作交给 FD-029 受控工具服务：Agent 请求 → 参数/配置验证 → 平台 beginOperation → 已登记本地适配器执行 → 平台 finishOperation/正式回执。模型不持平台/秘书凭证；验证命令的项目代码不继承模型传输凭证。

OpenAI Agents 仅注册平台许可目录中的 function tools。Codex App Server 使用实验 dynamicTools/item/tool/call 对接同一工具服务，需显式 experimentalApi；禁止原生工具直接访问项目仓库。具体原生能力关闭、隔离权限 Profile、有效配置检查、用量事件及支持版本由 FD-029 的后端边界合同固定；当前 0.163.0 已授权导出的 schema 是字段证据，不能拿网站新增字段假定当前二进制已支持。

不能满足边界的部署在准备/注册前返回不支持原因，不广告新合同能力；这不把首期 Codex 实现移出范围。两个后端均需实现新配置、正式成果、提问安全退出和取消确认；不自动安装/升级 CLI、登录或修改实际凭证。

独立设计反馈：`../reviews/FD-027-design-review-20261022-08211700.md` 的 D027-02 已补准备合同；D027-01 依赖后端实际边界的固定与有界复查，不以调用示意图代替机制。

## Work items

每项 small、中难度、估算不超过半天；外部执行或安全合同未解决时仍被 Gate 阻塞。

| ID | 依赖 | 独立结果及完成条件 |
| --- | --- | --- |
| 1.1 | G1、G2 | 固定配置快照/能力广告/准备接口及后端支持矩阵 |
| 1.2 | 1.1 | 首期软件职责目录，从现有 Profile 派生、延期分类不可执行 |
| 1.3 | 1.2 | 配置版本持久化及固定知识/Skill 摘要，不能静默覆盖 |
| 2.1 | 1.3 | Factory 精确复用已有配置，解释匹配/缺项 |
| 2.2 | 2.1 | 已有 Skills 组合及候选声明式生成，拒绝新增可执行工具/自授权 |
| 2.3 | 2.2、FD-029 许可合同 | 准备状态与阻塞恢复，未准备完成不创建可领取任务 |
| 3.1 | 1.1、2.3 | 新协议能力匹配/领取载荷，Go/TS 等未支持 Worker 被排除 |
| 3.2 | 3.1 | Python 每任务加载固定配置，保留既有报告/resultKind 与输入校验 |
| 3.3 | 3.2 | 配置切换清理及取消确认，不残留前次权限或会话 |
| 4.1 | 2.3、3.3 | 控制台展示配置版本、原因、恢复操作及真实支持能力 |
| 4.2 | 4.1 | 同步 Runtime 合同/spec/文档，编译及独立实现审查、本地交付 |

- [ ] 1.1 配置与能力合同。
- [ ] 1.2 默认职责目录。
- [ ] 1.3 配置版本持久化。
- [ ] 2.1 精确复用配置。
- [ ] 2.2 组合与声明式候选。
- [ ] 2.3 准备及阻塞恢复。
- [ ] 3.1 能力匹配和领取载荷。
- [ ] 3.2 Python 逐任务配置。
- [ ] 3.3 配置切换及取消。
- [ ] 4.1 控制台展示。
- [ ] 4.2 文档、证据及交付。

## Acceptance

AC04：同一支持新配置协议的 Worker 可先后执行需求/开发等不同配置，各次都保持真实 Lease Ownership、心跳、输出和取消语义（3.1～3.3）。

AG 边界：配置优先复用（2.1）；声明式内容不能增加工具（2.2）；已知不兼容或缺权不进入领取（2.3、3.1）；在途版本不漂移（1.3）；预留分类不冒充已实现（1.2、4.1）。固定路径/工具范围的真实拒绝由 FD-029 提供，需在本 FD 的实际 Runtime 消费链上确认，不以提示词代替。

## Verification

默认静态 Java/协议/Python 调用链核对及实现后的适用离线编译；不生成或执行测试，不调用模型、API、Codex、容器、Worker 或网络。Python 编译应在内存完成，不能以语法编译证明进程终止、真实工具拦截或模型集成已运行。权限/协议/持久化设计需独立设计反馈，实现另需独立 Reviewer。

TODO：固定双后端适配器及准备合同 → 独立设计反馈 → 受管交接 → 隔离实现/审查/交付。G1/G2 已由用户解决；当前尚无新动态配置实现或运行验证。

## 设计就绪记录（2026-10-23）

已取得独立定向复查 [FD-027 design recheck](../reviews/FD-027-design-recheck-20261023-08211700.md) 及同名 JSON；本轮原 findings 全部 resolved，建议 ready。已确认的人类政策及工程合同作为当前设计，本文此前“候选/待复查”的阶段描述以本节为准；尚无实现通过或运行认证。本 FD 当前阶段由 CLI 状态与真实交接记录确定。

## Sources

- Issue: ISSUE-00011
- [批准的需求](../../issues/ISSUE-00011/issue-plan.md)；[父 FD](../FD-025/FD-025_AI_DEV_MANAGER_AI.md)。
- `workers/process-python/README.md`、`agent_runtime.py`、`process_worker.py`、`process_profiles.py`；`workers/WORKER_RUNTIME_CONTRACT.md`。
- 设计来源事件：`FD-027-000002-design-requested`；Planner 会话：`fd025-029-host-20261022-234846-08211700`。
