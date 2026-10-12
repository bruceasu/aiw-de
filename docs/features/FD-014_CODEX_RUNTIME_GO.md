# FD-014: 统一多语言 Codex Runtime：Go 先行试点

**Status:** Open
**Revision:** 5
**Priority:** Medium
**Evidence policy:** Dual

## Problem

Go Worker 当前通过 `AgentRuntime` 选择 `codex-cli` 或 `openai-api`。`codex-cli` 已实现任务级 `codex exec --ephemeral --json`；`openai-api` 是另一种模型接入方式，不是 Codex App Server。仓库中尚未发现 Go App Server runtime、stdio JSON-RPC 会话管理或相应的运行时选择分支。

Java、Go、Python、TypeScript Worker 通过不同语言 SDK/CLI 接入 Codex，运行时请求、事件、取消和能力语义不一致。用户希望评估统一的 Codex Runtime 契约，以 Go 为先行试点，同时保留无需预启动常驻服务的 Exec 使用方式。该试点要回答 App Server 能否安全纳入现有 attempt 生命周期，以及它相对 Exec/现有 SDK 的运维价值；本 FD 不承诺其他语言立即迁移。

## Options and decision

1. **只保留现有 Exec，补齐跨语言文档契约。** 运维简单，但不能验证 App Server 的长连接、多轮事件、取消与恢复能力是否适合 Worker，无法完成 Issue 的 Go 双模式试点；不作为本 FD 的完整结果。
2. **复用 Go `AgentRuntime` SPI，保留 Exec 并新增显式 App Server 实现。** 两种 Codex 模式共用 Worker attempt、工作区和结果发布边界；App Server 能力按可观察事实声明。选择此方案，与 Issue 的试点目标一致。
3. **引入跨语言常驻 Runtime Host，由各 Worker 通过新服务协议接入。** 可能统一进程管理，但新增服务部署、认证、故障域和跨 Worker 隔离问题；Go 试点尚无证据证明需要此边界，暂不采用。
4. **将 Go Worker 包装到 Python/TypeScript SDK。** 可复用 SDK，但会增加语言运行时、打包和进程边界，并偏离 Go 先行试点；不采用。

## Solution

在现有 Go `AgentRuntime` 边界内保留 Exec，并新增显式 Codex App Server runtime。两种模式通过同一 Go Worker 生命周期处理 task/attempt 关联、工作区、权限、整体 deadline、租约丢失、结果校验和发布；Runtime 负责 Codex 会话、请求、事件与模式能力。Control Plane 不接管 Codex 会话，也不复用 Worker 权限。

模式必须由配置明确选择，启动时校验配置和运行时能力；App Server 启动失败、断连、协议不兼容或状态不确定时不得静默切回 Exec。现有 `codex-cli` 配置和 Exec 默认行为保持兼容。部署方负责人工安装/升级 Codex CLI；Worker 校验受支持的 CLI 版本与 App Server 握手能力，不匹配时在注册/领取前拒绝启动。能力分别标记 supported、unsupported 或 unknown；Exec 与 App Server 的差异不能由统一接口隐藏。

每个 Go Worker 进程管理一个 App Server，进程内 slot 复用；必须验证每个 attempt/thread 的会话和工作区隔离。首期契约统一执行、事件、取消、终态和能力声明；审批与恢复标为 unsupported/unknown，不作为跨模式保证。用户选择生产候选目标，因此本 FD 必须完成准入、回滚和运行证据；该决定不授权实际发布或部署。

## Scope

**In scope:** Go `process-go` 中 Codex Exec 与 App Server 的对照；统一 Runtime 请求/结果/事件/错误/取消/能力契约；接入现有 attempt、租约、工作区和清理边界；native CLI 版本、人工安装/升级、进程管理和部署成本评估；基于 Go 试点形成其他语言迁移建议。

**Out of scope:** Java、Python、TypeScript 的完整迁移；Control Plane 直接使用 Worker Runtime；修改租约、调度、审批、持久化或 Worker Protocol；以共享 App Server 取代每 attempt 的工作区隔离；未经验证地承诺 SDK、CLI、App Server 的恢复、usage、审批或进程终止行为；自动发布或部署。

## Work items

- [ ] 1.1 **记录现有 Go Exec 基线与兼容约束。** Size: S; difficulty: Medium; no dependency. 对照 `agent_runtime.go`、`main.go`、`openai_runtime.go` 和 Go README，记录 runtime 选择、Exec 参数、事件/结果映射、取消/期限、禁存和配置兼容。Evidence: 基线表逐项给出源码位置与实际支持状态；明确 `openai-api` 不是 App Server；未观察能力标为 unknown。
- [ ] 1.2 **定义最小跨模式 Runtime 契约和能力矩阵。** Size: S; difficulty: Medium; depends on 1.1. 定义 request、event、terminal result、session correlation、cancel、error 和 capability 语义；区分两种模式的事件流、恢复、审批、usage。Evidence: 契约字段可映射到现有 Worker attempt/report，不增加 CP 状态或伪造能力；Exec 保持兼容。
- [ ] 1.3 **实现每 Worker 进程级 App Server 生命周期管理。** Size: S; difficulty: Medium; depends on 1.1 and 1.2. 由 Go Worker 启动一个 App Server，完成协议握手、运行期健康/断连检测和有界关闭；确认多 slot 会话隔离。Evidence: 进程图、启动/失败/关闭状态可观察；初始化失败不注册 Worker；进程退出时不遗留受管子进程。
- [ ] 1.4 **实现显式 App Server Runtime 与模式选择。** Size: M; difficulty: Medium; depends on 1.2 and 1.3. 保留现有 Exec，新增 stdio JSON-RPC 请求和事件映射，使用现有 `AgentRuntime` SPI 显式选择；App Server 错误不静默回退。Evidence: 两模式均可从现有 Go SPI 到达；错误模式/协议版本会在注册前失败；现有 Exec 配置不变。
- [ ] 1.5 **接入 attempt 取消与租约丢失。** Size: M; difficulty: Medium; depends on 1.3 and 1.4. 处理 task timeout、lease loss、显式停止、App Server 断连和取消确认。Evidence: 运行证据表明过期结果不报告成功；未确认执行终止时不释放仍被使用的 workspace/slot。
- [ ] 1.6 **验证资源终态、回滚和生产候选准入。** Size: M; difficulty: Medium; depends on 1.4 and 1.5. 验证不确定终态的资源保留、Worker 重启/关闭、Exec 回退路径和版本不匹配拒绝。Evidence: 每种失败均有运行证据与恢复步骤；未知状态显式记录；不存在静默模式切换；生产候选门槛全部有证据。
- [ ] 1.7 **形成部署与跨语言迁移建议。** Size: S; difficulty: Medium; depends on 1.1 and 1.6. 记录 CLI 人工安装/升级步骤、版本兼容表、运维成本证据，以及 Java/Python/TypeScript 后续接入契约和迁移顺序。Evidence: 版本和回滚责任明确；没有量化数据时不声称降低成本；其他语言仅作为建议，不冒称已实现。

## Acceptance

1. Go Worker 可通过显式配置选择 Codex Exec 或 App Server；Exec 不要求先启动常驻 App Server；没有静默 fallback。
2. 两模式都把会话与正确 task/attempt 关联，并遵守既有超时、租约取消、只读/可写角色、attempt workspace 和结果校验契约。
3. App Server 断连、请求失败、取消和终态不确定有明确处理；未确认活动终止前不释放被占用的 workspace/slot，也不把过期结果报告为成功。
4. Runtime 能力准确表达事件流、恢复、审批、usage 等 supported/unsupported/unknown；不会把一模式的能力推断给另一模式。
5. Exec 与 App Server 的安装、固定版本、人工升级、进程管理和回滚责任有明确说明；运维成本结论由记录的证据支持。
6. 其他语言只得到契约和迁移建议，不声称已实现或验收；无 Control Plane API、租约、审批或持久化边界变化。
7. 达到生产候选状态前，准入、回滚和运行证据均完整；候选状态不等于授权发布或部署。

## Verification

- 静态审查 Go Worker runtime 选择、Exec 参数、App Server 协议映射及 attempt/lease/workspace 清理调用链。
- compile-only 检查按仓库授权规则执行；不把编译通过当作 App Server 行为验证。
- App Server 握手、事件、并发 slot、取消/断连、禁存准入及回滚属于生产候选的必需运行证据；真实 Codex/网络操作需在执行前取得相应授权。未运行的场景明确列为未验证，不能标记为候选就绪。
- Reviewer 对照 ISSUE-00004 的六项验收示例、实际 diff、能力矩阵和运行证据；不以文档声明替代运行证据。

## 已确认决策

- **G1 生命周期：** 每个 Go Worker 进程启动并管理一个 App Server，进程内 slot 复用，Worker 退出时关闭；必须验证 attempt/thread 隔离。
- **G2 准入：** 本 FD 目标是具备准入、回滚和运行证据的生产候选；不包含实际发布或部署授权。
- **G3 契约范围：** 首期统一执行、事件、取消、终态和能力声明；审批及会话恢复标为 unsupported/unknown，不作跨模式保证。
- **G4 CLI 分发：** Codex CLI 由部署方人工安装和升级；Worker 不下载或安装 CLI，并须在注册前校验受支持版本与 App Server 协议能力。

## Sources

- Approved Issue and scope: `docs/issues/ISSUE-00004/issue-plan.md`.
- Current Go runtime selection and Codex Exec: `workers/process-go/agent_runtime.go`, `main.go`, `openai_runtime.go`, and `README.md`.
- Cross-runtime authority contract: `workers/WORKER_RUNTIME_CONTRACT.md`.
- Current Codex CLI/App Server capability research: `workers/SDK_CAPABILITIES.md` and `docs/worker-codex-no-store-design.md`.
- Related prior implementation: `docs/features/archive/FD-005/FD-005_WORKER_PI.md` (shared execution foundation; not the App Server pilot).
- Issue: ISSUE-00004.
