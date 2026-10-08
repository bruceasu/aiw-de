# FD-009: TypeScript Worker AgentRuntime SPI 与单入口合并

**Status:** Complete
**Revision:** 15
**Priority:** Medium
**Test policy:** Independent
**Evidence policy:** Dual

## Problem

`workers/process-typescript` 和 `workers/process-typescript-with-api` 分别以 Codex SDK 和 OpenAI Agents SDK 执行同一组 Pull Worker 角色。两者各自维护启动配置、角色映射、指令、模型选择、依赖和发布说明；`workers/runtime-core` 已共享领取、租约和 attempt 生命周期，但执行后端仍写在各自的 Worker 入口。新增后端或修改执行策略时容易出现两套行为分歧。

影响 Worker 维护者与部署者：同一角色需要选择不同包和脚本。用户要求依据 `docs/AgentRuntime SPI.md` 用 TypeScript 或 JavaScript 落地，并建议把两个 TypeScript Worker 合成一个。

## Options and decision

1. **维持两个完整 Worker 包，只抽取共用函数。** 入口、配置、角色和发布仍分叉；拒绝。
2. **统一成一个 TypeScript Worker 包和启动入口，以进程内 AgentRuntime SPI 选择现有 Codex 与 Agents 后端。** 复用 `runtime-core` 的控制平面协议和 attempt 生命周期；选择。保留 `workers/process-typescript`，删除 `workers/process-typescript-with-api` 包和启动路径。
3. **新增独立 AgentRuntime 服务。** 引入网络边界及远程工具执行，与草案的本地 workspace 设计不符；拒绝。

本 FD 先统一两个现有后端。草案中的 DirectResponsesRuntime、CopilotCliRuntime 和跨语言复用留待后续 FD；未实现能力不得宣称支持。

## Solution

- 在正式 TypeScript 包内定义小型、与后端无关的 `AgentRuntime` 契约：运行输入、上下文、结果、事件、能力声明与取消信号。类型不得暴露 Codex 或 Agents SDK 对象。结果映射到现有 `AttemptAdapter` 的输出、故障类别和证据。当前实现 `run`；仅在真实支持时声明 `resume` 或按 run ID 取消，现有取消继续使用 attempt 的 `AbortSignal`。
- 一个 Worker 入口加载角色、仓库和租约配置，通过确定性 resolver 选择 `codex-sdk` 或 `openai-agents` 适配器。每个进程选择一个后端，以既有 role profile 注册。未知后端、缺失凭据或不满足能力要求时，在注册或领取前失败；不自动跨后端回退。
- Codex 和 Agents 各自的 SDK 交互移入适配器；共享请求构造、角色约束和结果映射各有单一所有者。保留 `runtime-core` 的 Pull/lease、attempt、证据、清理与不确定终止处理。SDK 版本校验、Codex session launcher、Agents 工具边界、预算和模型选项留在对应适配器。
- 保持 Control Plane 现有 runtime 标识、任务字段、lease 和证据协议；不新增数据库迁移或公共 API。统一入口必须显式选择 `codex-sdk` 或 `openai-agents`；未指定或选值冲突时，在注册前报错。沿用两个后端现有环境变量、`AIW_WORKER_ID` 默认值和 runtime 标签，避免部署时身份或 policy 静默变化。
- 删除 `workers/process-typescript-with-api` 包和其启动路径；把仍需使用的 Agents 代码、配置和依赖迁入正式包。更新锁文件、构建/打包脚本、编译入口、角色启动脚本、README、Quick Start、运维文档及仍在使用的测试/引用。历史归档证据可保留原路径文字。实施时单独检查新增依赖和离线构建影响。

## Scope

Included:

- TypeScript/JavaScript 进程内 SPI 与现有 Codex SDK、OpenAI Agents SDK 适配器。
- 两个 TypeScript Worker 的单入口合并、旧 API 包/启动路径删除、配置/角色/profile 收敛和相关文档。
- 保持现有 Control Plane 协议、策略约束、工具权限、证据及终止行为。

Excluded:

- DirectResponsesRuntime、Copilot CLI、其他语言 Worker、独立 Runtime 服务和自动后端回退。
- 新持久化、跨进程 resume、审批 UI、数据库或 Control Plane 公共 API 变更。
- 本次设计阶段的代码实施、SDK 升级或部署。

## Work items

- [x] 1.1 定义 TypeScript SPI 与状态映射。**Size:** Small. **Difficulty:** Medium. **Dependencies:** none. **Completion:** 导出中立的 request/context/result/event/capabilities 与 runtime 接口；标明当前支持和不支持的能力；与 `AttemptAdapter` 的映射可审查。
- [x] 1.2 抽取单一 Worker 入口与 resolver。**Size:** Medium. **Difficulty:** Medium. **Dependencies:** 1.1. **Completion:** 一个入口处理配置、注册和 slot 循环；必须显式选择后端，保留两个 runtime 名称及各自身份默认值；无效选择/凭据在领取前失败。
- [x] 1.3 接入 Codex Runtime 适配器。**Size:** Small. **Difficulty:** Medium. **Dependencies:** 1.1, 1.2. **Completion:** 现有 Codex 执行经 SPI 运行；session launcher、sandbox/approval、事件、usage、取消及资源不确定处理保持约束。
- [x] 1.4 接入 Agents Runtime 适配器。**Size:** Medium. **Difficulty:** Medium. **Dependencies:** 1.1, 1.2. **Completion:** 现有 API 执行经 SPI 运行；模型/工具/profile、只读角色、shell 开关、预算和故障归一化保持约束。
- [x] 1.5 合并共用角色与指令配置。**Size:** Small. **Difficulty:** Medium. **Dependencies:** 1.3, 1.4. **Completion:** 共用 profile、任务指令和后端无关模型选择只有一个维护位置；后端专用字段留在适配器；原有角色与任务类型继续可用。
- [x] 1.6 收敛包并删除旧 API 启动路径。**Size:** Medium. **Difficulty:** Medium. **Dependencies:** 1.2–1.5. **Completion:** 正式包加载两后端；删除旧 API 包、入口与启动脚本；打包、编译、仍在使用的测试和 role 脚本指向唯一入口；环境变量、Worker ID 和 runtime 标签保留。
- [x] 1.7 更新使用与迁移文档。**Size:** Small. **Difficulty:** Low. **Dependencies:** 1.6. **Completion:** Worker README、Quick Start、运维说明及 SPI 草案只指向唯一启动入口，说明显式后端选择、旧路径删除和不支持能力。

## Acceptance

- 单一正式入口按显式选择运行既有 `codex-sdk` 与 `openai-agents` Worker；角色、任务类型、注册标签和已批准 AI policy 匹配逻辑与现状一致。
- `workers/process-typescript-with-api` 包和启动路径已删除。唯一入口必须显式选择后端；未指定、未知或冲突的 runtime，以及缺少 API key 或 Codex launcher，均在注册/领取前明确失败。
- SDK 依赖只在 Runtime 适配器；SPI 请求/结果/事件和角色编排没有 SDK 对象；`runtime-core` 的 Pull/lease/attempt 边界不变。
- 两后端的只读/写入权限、工具范围、sandbox/审批、预算、超时、取消、不确定终止时 drain、证据及失败类别不放宽。缺少可验证能力时拒绝运行，不伪造 resume、approval 或 usage。
- 成功、后端错误、无效终态、超时和取消继续产出兼容的 attempt 结果与证据；代码库中只有一个 TypeScript Worker 包和入口。
- 文档说明唯一入口、两后端前置条件、旧路径已删除、身份默认值和未实现能力。

## Verification

- 独立 Reviewer 第 2 轮报告：`docs/features/reviews/FD-009-review-r2.md`；Verification 通过。脚本冲突拒绝缺口已修复；Tester 观察到两向冲突诊断与非零退出，工具退出码 1 与脚本静态 `exit 2` 预期不同。PM 已明确接受 2/26（7.69%）场景覆盖和分支覆盖未测量的例外；其余运行时风险及退出码限制保留。
- 独立 Reviewer 第 1 轮报告：`docs/features/reviews/FD-009-review-r1.md`；结论为请求修改。已在两个角色启动脚本的参数校验后、环境改写前加入冲突拒绝：非空 `AIW_AGENT_RUNTIME` 与参数不同时向 stderr 报错并以 2 退出。此修复只有静态证据，尚无成功执行的行为测试。
- Worker 静态检查：SPI 仅引用共享 Attempt 类型；SDK 导入位于各自适配器。统一入口显式选择后端，保留旧 runtime 标签和按后端区分的默认 Worker ID；旧 API 包已删除，当前启动和运维文档已改为单入口。
- 已运行一次 compile-only：在 `workers/process-typescript` 执行 `$env:AIW_TYPESCRIPT_COMPILER='D:\03_projects\AI-tools\aiw-dev\workers\process-typescript\node_modules\typescript'; node scripts/compile.mjs`，结果为 TypeScript 语法编译通过；不生成分发产物。此命令不检查类型、依赖或运行时行为。
- 独立 Tester 第 1 轮报告 `docs/features/reports/FD-009-test-report-r1.md`：授权命令运行一次，但 Node 测试运行器在载入用例前报 `spawn EPERM`，行为断言执行 0 个；需求场景覆盖率 0/25，分支覆盖率未测量。PM 在 `docs/features/reports/FD-009-test-decision-r1.md` 明确记录覆盖例外与风险。新增冲突拒绝场景须在下轮 Tester 报告中列为未运行或取得新的独立证据，不得援引本轮阻断结果为通过。
- 未完成行为测试、类型检查、最终构建、真实 Codex CLI/OpenAI API 在线端到端运行或部署演练。历史 `tests/fd005` 证据含旧包路径，不作为 FD-009 的可执行验证结果。

## TODO

- Worker 范围的七项 Work Item 已完成；第 1 轮 Reviewer 的脚本冲突发现已修复，等待重新独立测试记录、PM 决策与 Reviewer。
- DirectResponsesRuntime、Copilot CLI 与跨进程 resume 属于后续 FD，不计入本 FD 完成条件。

## Sources

- 用户请求：根据 `docs/AgentRuntime SPI.md` 为 TypeScript/JavaScript Worker 建 FD，并合并两个 TypeScript Worker。
- 用户后续决定：不保留旧 API 启动路径；本修订删除先前的兼容入口方案。
- `docs/AgentRuntime SPI.md`（Worker/AgentRuntime/Control Plane 边界与分阶段建议；当前工作区已有用户改动）
- `workers/process-typescript/src/index.ts`、`workers/process-typescript-with-api/src/index.ts`、`workers/process-typescript-with-api/src/api-adapter.ts`
- `workers/runtime-core/attempt.mjs`、`workers/runtime-core/index.d.mts`
- 两个 Worker 的 `package.json`、README、`workers/WORKER_OPERATIONS.md` 与 `scripts/compile-worker-runtime.mjs`
- Planner session `fd009-planner-20261007-6f2b9c`；source event `FD-009-000002-design-requested`。

**Completed:** 2026-10-07
