# 统一多语言 Codex Runtime：Go 先行试点

- 创建日期：2026-10-09
- 请求者与需求决策者：当前会话用户
- 整理者：Codex，使用 issue-management 技能
- 当前阶段：DISCOVERY；正式 Issue 创建后仍需用户决策，未批准、未交接 FD
- 准备度：INCOMPLETE；核心方向已确认，运行模式边界及试点验收决策留待 Issue/FD 设计解决

## 事实与来源

| 来源 | 已知内容 | 证据性质 |
|---|---|---|
| 当前会话用户 | 希望统一多语言 Runtime，Go 作为接入试点；希望保留无需预启动常驻 App Server 的 Exec 使用方式，并考虑 App Server 长连接方式 | 用户确认的方向与约束 |
| 当前会话用户 | TypeScript/Python SDK 可能带来 Codex 程序下载、版本管理和发布负担 | 用户观察，尚无成本量化 |
| `workers/process-go/README.md`、`workers/process-go/main.go` | Go 已有 `AgentRuntime` 选择；Codex 使用 `codex exec --ephemeral --json`，另有 `openai-api` runtime | 当前实现静态证据 |
| `workers/process-python/README.md`、`workers/SDK_CAPABILITIES.md` | Python 有 `AgentRuntime`；Codex SDK 路径已通过公开启动参数探索 stdio app-server，且涉及 ephemeral、读取回执及取消边界 | 当前实现与能力研究静态证据；不代表端点已认证 |
| `workers/process-typescript/src/codex-adapter.ts`、`workers/WORKER_OPERATIONS.md` | TypeScript Codex 路径使用 `@openai/codex-sdk`，由 SDK 启动原生 CLI；存在版本与 native 准入/验证约束 | 当前实现静态证据 |
| `workers/WORKER_RUNTIME_CONTRACT.md` | Worker 自己负责租约、期限、工作区、证据和 Git 生命周期；Runtime 负责模型执行；不透明能力应报告 unknown | 现行跨运行时契约 |

## 问题与使用者

Java、Go、Python、TypeScript Worker 需要通过不同语言 SDK 或 CLI 接入 Codex，导致 Runtime API、事件和能力呈现不一致，并增加依赖安装、CLI 版本管理及发布维护成本。Go 已有可承载多种执行适配的 SPI，适合作为第一阶段验证点。

主要使用者是 Worker 维护者与部署运维者；后续语言实现者可复用试点形成的协议与行为契约。当前不将 Control Plane 的 AI 调用并入本 Issue，以免把任务执行权限/生命周期与控制面辅助能力混成一个安全边界。

## 目标

定义可由 Java、Go、Python、TypeScript 以一致方式使用的 Codex Runtime 接入契约，先在 Go Worker 验证两种运行模式的边界：

- Exec：任务级启动 `codex exec --json`，适合短任务和无需常驻服务的部署。
- App Server：通过 stdio JSON-RPC 管理 Codex 会话，评估长连接、多轮交互、事件、取消、审批及恢复能力。

统一的是协议语义和 Worker 可依赖的行为；各语言客户端不要求同一个语言 SDK，也不把 App Server 内部能力未经验证地视为跨模式保证。

## 范围与非目标

**范围**

- 为跨语言 Runtime 定义统一请求、事件/结果、取消、会话标识、能力声明和错误语义的目标契约。
- 在 Go `process-go` 以现有 Exec runtime 为基线，验证 Exec 与 App Server 接入能否纳入现有 Worker attempt 生命周期。
- 记录 Codex native CLI 的固定版本/安装/升级与进程管理责任，评估减少语言 SDK 及重复 native 分发的实际收益。
- 根据 Go 试点证据形成其他语言适配建议与迁移顺序，不在本 Issue 默认承诺完成所有语言迁移。

**非目标**

- 本 Issue 不直接实现 Java、Python、TypeScript 全量迁移。
- 不让 Control Plane 直接复用 Worker 会话或权限配置；CP 的 AI 能力另行需求评估。
- 不替换控制平面的租约、调度、审批或持久化权威。
- 不以共享 App Server 进程替代每个 attempt 的工作区隔离或主机权限边界。
- 不将 SDK、CLI 或 App Server 的未观测内部行为宣称为已验证。

## 业务规则与约束

- 每个 attempt 必须关联自己的任务、会话、权限策略与工作目录；多会话服务进程不意味着可以共享工作目录。
- Worker 仍负责租约、整体期限、证据、工作区和结果发布；Runtime 管理 Codex 执行接口及其会话/事件。
- App Server 断连、取消终态不确定、租约丢失时，工作区与排他资源的释放须服从现有不确定状态处理规则。
- 权限、审批、网络、sandbox 和禁存准入必须由宿主策略约束；Runtime 协议本身不构成操作系统沙箱。
- 只有实际可观察的能力才可声明支持；其余标为 unknown/unsupported，并保留 Exec 回退路径。

## 验收示例

1. Go Worker 可通过显式配置选择 Exec 或 App Server，不发生静默回退；Exec 不要求先运行常驻 App Server。
2. 两种模式均能把运行结果关联到正确的 task/attempt，并遵守已有整体超时、租约取消、只读/可写角色、隔离工作区和结果校验规则。
3. App Server 断连、请求失败、取消与终态不确定均有明确且可审计的处理；不能确认执行停止时不会错误释放仍被使用的工作区或 slot。
4. Runtime 能力报告准确区分事件流、恢复、审批、usage 等支持/不支持/未知状态；Exec 与 App Server 能力不同不会被统一接口掩盖。
5. Go 试点记录 native CLI 安装/版本/升级和 Worker 部署方式，比较其与现有 SDK 路径的运维成本及未解决风险；没有量化证据时不声称降低了成本。
6. 明确列出其他语言接入所需的契约和未决兼容点，但不以文档建议冒充其实现或验收完成。

## 规模评估与阶段

这是跨运行时契约加两个 Codex 传输模式的中等至大型工作，具有进程生命周期、安全隔离、取消与禁存准入等高风险不确定性。保留一个 Issue 是因为统一契约与 Go 试点共同构成一个可决策结果；Go 试点完成后再按证据决定是否为各语言迁移建立后续 Issue/FD，不预先复制四份独立实施范围。

建议阶段：
1. 设计阶段：明确契约、Go 试点验收、Exec/App Server 生命周期、共享进程模型和权限/数据边界。
2. Go 试点：保留 Exec 并引入或原型验证 App Server，对照同一 Worker 场景收集实际证据。
3. 试点决策：基于证据决定 App Server 的适用范围、部署方式及其他语言迁移次序。

## 未决 Gate

- %% NEEDS_INPUT: App Server 的初始生命周期应限定为每个 Go Worker 进程一个，还是允许独立 runtime host 跨 Worker 共享？这会改变故障影响面与隔离要求；交 FD 设计确定。
- %% NEEDS_INPUT: Go 试点的 App Server 是否作为正式生产候选，还是先作为受控原型；需定义能支撑准入的取消、会话/禁存及主机策略证据。
- %% NEEDS_INPUT: 统一契约的第一阶段是否必须覆盖审批与会话恢复，还是先覆盖任务执行、事件、取消和结果；需由 FD 按控制平面实际消费场景确定。
- %% NEEDS_INPUT: 是否需要为 Codex native CLI 安装/版本升级建设统一部署制品或运行时宿主；当前只有发布维护负担的定性反馈，没有运维成本数据。

## 推荐下一步

先批准 Issue 的目标和边界，再通过 FD 设计决定 Go 试点的 App Server 生命周期、契约范围和准入证据。批准 Issue 不等于 App Server 可直接部署或已获实施/发布授权。
