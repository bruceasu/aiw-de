# FD-014: 本地 Go Runtime 与双认证支持

**Status:** Complete
**Revision:** 23
**Priority:** Medium
**Evidence policy:** Dual

## Problem

Go Worker 通过 `AgentRuntime` 选择 `codex-cli`、`codex-app-server` 或 `openai-api`。`codex-cli` 使用任务级 `codex exec --ephemeral --json`；`codex-app-server` 是固定 CLI 版本的受管 stdio JSON-RPC runtime；`openai-api` 是另一种模型接入方式，不是 Codex App Server。

Java、Go、Python、TypeScript Worker 通过不同语言 SDK/CLI 接入 Codex，运行时请求、事件、取消和能力语义不一致。用户希望评估统一的 Codex Runtime 契约，以 Go 为先行试点，同时保留无需预启动常驻服务的 Exec 使用方式。该试点要回答 App Server 能否安全纳入现有 attempt 生命周期，以及它相对 Exec/现有 SDK 的运维价值；本 FD 不承诺其他语言立即迁移。

## Options and decision

1. **只保留现有 Exec，补齐跨语言文档契约。** 运维简单，但不能验证 App Server 的长连接、多轮事件、取消与恢复能力是否适合 Worker，无法完成 Issue 的 Go 双模式试点；不作为本 FD 的完整结果。
2. **复用 Go `AgentRuntime` SPI，保留 Exec 并新增显式 App Server 实现。** 两种 Codex 模式共用 Worker attempt、工作区和结果发布边界；App Server 能力按可观察事实声明。选择此方案，与 Issue 的试点目标一致。
3. **引入跨语言常驻 Runtime Host，由各 Worker 通过新服务协议接入。** 可能统一进程管理，但新增服务部署、认证、故障域和跨 Worker 隔离问题；Go 试点尚无证据证明需要此边界，暂不采用。
4. **将 Go Worker 包装到 Python/TypeScript SDK。** 可复用 SDK，但会增加语言运行时、打包和进程边界，并偏离 Go 先行试点；不采用。

## Solution

在现有 Go `AgentRuntime` 边界内保留 Exec，并新增显式 Codex App Server runtime。两种模式通过同一 Go Worker 生命周期处理 task/attempt 关联、工作区、权限、整体 deadline、租约丢失、结果校验和发布；Runtime 负责 Codex 会话、请求、事件与模式能力。Control Plane 不接管 Codex 会话，也不复用 Worker 权限。

模式必须由配置明确选择，启动时校验配置和运行时能力；App Server 启动失败、断连、协议不兼容或状态不确定时不得静默切回 Exec。现有 `codex-cli` 配置和 Exec 默认行为保持兼容。部署方负责人工安装/升级 Codex CLI；Worker 校验受支持的 CLI 版本与 App Server 握手能力，不匹配时在注册/领取前拒绝启动。能力分别标记 supported、unsupported 或 unknown；Exec 与 App Server 的差异不能由统一接口隐藏。

每个 Go Worker 进程管理一个 App Server，进程内 slot 复用；必须验证每个 attempt/thread 的会话和工作区隔离。首期契约统一执行、事件、取消、终态和能力声明；审批与恢复标为 unsupported/unknown，不作为跨模式保证。历史上用户选择生产候选目标；当前生产准入、回滚和新增 Linux 运行证据转入 FD-015，当前按用户 G12 决定收敛为本地交付，生产候选目标转入 FD-015；不授权实际发布或部署。

## Scope

**In scope:** Go `process-go` 中 Codex Exec 与 App Server 的对照；统一 Runtime 请求/结果/事件/错误/取消/能力契约；接入现有 attempt、租约、工作区和清理边界；native CLI 版本、人工安装/升级、进程管理和部署成本评估；基于 Go 试点形成其他语言迁移建议。

**本次交付边界：** 本地 Go Runtime、Exec/App Server 显式选择与双认证支持；不声明生产候选就绪。Docker、生产禁存准入和 Linux 新增生命周期证据移至 `../FD-015/FD-015_DOCKER_CODEX.md`。

**Out of scope:** Java、Python、TypeScript 的完整迁移；Control Plane 直接使用 Worker Runtime；修改租约、调度、审批、持久化或 Worker Protocol；以共享 App Server 取代每 attempt 的工作区隔离；未经验证地承诺 SDK、CLI、App Server 的恢复、usage、审批或进程终止行为；自动发布或部署。

## Work items

- [x] 1.1 **记录现有 Go Exec 基线与兼容约束。** Size: S; difficulty: Medium; no dependency. 对照 `agent_runtime.go`、`main.go`、`openai_runtime.go` 和 Go README，记录 runtime 选择、Exec 参数、事件/结果映射、取消/期限、禁存和配置兼容。Evidence: `workers/process-go/CODEX-RUNTIME.md` 的基线表引用源码并区分 supported/unsupported/unknown；明确 `openai-api` 是 Responses API，不是 App Server。
- [x] 1.2 **定义最小跨模式 Runtime 契约和能力矩阵。** Size: S; difficulty: Medium; depends on 1.1. 定义 request、event、terminal result、session correlation、cancel、error 和 capability 语义；区分两种模式的事件流、恢复、审批、usage。Evidence: `workers/process-go/CODEX-RUNTIME.md` 定义的契约映射现有 Worker attempt 与报告，不增加 CP 状态；未观测的 App Server 行为保持 unknown。
- [x] 1.3 **实现每 Worker 进程级 App Server 生命周期管理。** Size: S; difficulty: Medium; depends on 1.1 and 1.2. 由 Go Worker 启动一个 App Server，完成协议握手、运行期健康/断连检测和有界关闭；确认多 slot 会话隔离。Evidence: 本地假服务与真实 CLI 均完成握手和关闭；假服务并发执行两个 thread，session ID 与输出分别关联；断连触发 failure channel，确认 interrupt 后进程仍可供其他 slot 使用。
- [x] 1.4 **实现显式 App Server Runtime 与模式选择。** Size: M; difficulty: Medium; depends on 1.2 and 1.3. 保留现有 Exec，新增 stdio JSON-RPC 请求和事件映射，使用现有 `AgentRuntime` SPI 显式选择；App Server 错误不静默回退。Evidence: 固定版本拒绝、假服务协议/事件及真实 CLI `0.162.1` turn 检查通过；App Server 能力元数据可以记录到 attempt evidence；Exec 仍由独立适配器显式选择。
- [x] 1.5 **接入 attempt 取消与租约丢失。** Size: M; difficulty: Medium; depends on 1.3 and 1.4. 处理 task timeout、lease loss、显式停止、App Server 断连和取消确认。Evidence: 本地测试覆盖 attempt deadline、lease heartbeat 丢失、Control Plane stop request、interrupt acknowledgement、matching terminal、断连通知及不确认时终止受管进程；lease-lost attempt 未 complete/fail，记录 `resource-terminal=unknown` 并保留 workspace。
- **1.6 已取消并拆分替代（原项未完成，不记作通过）：** 验证资源终态、回滚和生产候选准入。原范围与验收由 1.6.1–1.6.6 完整承接；拆分原因是固定版本独立持久化通道、Linux 部署约束及两种认证的准入需要不同证据。保留原 ID，不重用或删除历史结果。
- [x] 1.6.1 **核定固定版本禁存差异及 Linux 部署决策。** Size: S; difficulty: Medium; depends on 1.4 and 1.5. Evidence: `reports/FD-014-no-store-source-review-linux-r1.md` 绑定官方 tag 和具体源码提交，核定独立 SQLite/日志通道与已移除的 sqlite 开关；用户接受内存隔离部署限制，并要求两种认证显式切换。此项只完成源码和设计决策，不表示运行验证通过。
- **1.6.2 已移交 FD-015 1.1（本 FD 取消未完成项，不记作通过）：** **固定 Linux 隔离启动与部署证据契约。** Size: S; difficulty: Pending (security/platform Gate); depends on 1.6.1. Completion: 核定生产部署的隔离启动入口、可写内存边界、swap/core dump/诊断约束、凭据读取及更新机制、工具进程传播和证据来源；在外部隔离机制与部署证据尚未核定前不作为 ready implementation。证据不能只是人工布尔开关。
- **1.6.3 已移交 FD-015 1.2（本 FD 取消未完成项，不记作通过）：** **实现启动前及会话前的准入核验。** Size: S; difficulty: Medium after 1.6.2; depends on 1.6.2. Completion: 绑定平台、CLI/适配器/配置摘要、隔离条件与认证模式对应证据；未知、冲突或变更在注册/领取/发送正文前拒绝。保留 Exec 配置兼容，不静默 fallback。
- **1.6.4 已取消并拆分替代（原项未完成）：** 双认证切换的原范围由 1.6.4.1–1.6.4.3 承接；原因是用户明确先处理本地，Docker 隔离和生产准入暂缓，不再让本地切换代码依赖未处理的 Docker 工作。
- [x] 1.6.4.1 **实现本地 Codex 双认证显式选择与启动检查。** Size: S; difficulty: Medium; depends on 1.4 and 1.6.1. Completion: 两种 Codex 模式支持宿主配置 inherit/api-key/chatgpt，显式模式使用部署方已准备的独立认证目录；在注册与执行前核验原生登录类型，App Server 还核验 account/read；不自动登录/注销，不复制凭据，不静默切换。inherit 保留旧配置兼容。Evidence: `codex_auth.go`、两个 runtime 及 Go README 已实现和记录；本次 `GOTOOLCHAIN=local GOPROXY=off GOSUMDB=off go build -o /dev/null .` 通过。真实凭据及双认证切换未运行；不将本项编译证据当作 1.6.4.2 的运行证据。
- **1.6.4.2 本轮免除运行验证（未运行，不记作通过）：** 用户接受 API Key 接入假设，并确认本地 ChatGPT 已登录可直接使用。默认 inherit 复用原生登录环境，不要求额外认证目录或重新登录。双认证切换、缺失/错误凭据及停止/重启的真实运行证据未产生；运行行为风险保留。后续生产阶段仍须核验实际认证与禁存准入，不以本轮免验证代替。
- **1.6.4.3 已移交 FD-015 1.3（本 FD 取消未完成项，不记作通过）：** **完成 Docker 组合的认证与禁存准入。** Size: S; difficulty: Medium after 1.6.2; depends on 1.6.2, 1.6.3 and 1.6.4.1. Completion: 在 Docker 环境分别验证两种认证上下文、凭据更新和服务端政策绑定，达到原 1.6.4 的生产准入要求；当前按用户要求暂缓。
- **1.6.5 已移交 FD-015 1.4（本 FD 取消未完成项，不记作通过）：** **收集 Linux 生命周期与重启运行证据。** Size: S; difficulty: Medium; depends on 1.6.3 and 1.6.4. Completion: 经明确授权验证并发会话、取消/断连、lease loss、父进程终止和重启；每个场景记录资源终态，未知时保留占用资源且不报告成功。历史 Windows 运行结果不代替 Linux 证据。
- **1.6.6 已移交 FD-015 1.5（本 FD 取消未完成项，不记作通过）：** **收集禁存及同政策 Exec 回滚准入证据。** Size: S; difficulty: Medium after 1.6.2; depends on 1.6.3, 1.6.4 and 1.6.5. Completion: 固定部署组合在过程写入监测和结束扫描中覆盖所有已核定通道；API 与 ChatGPT 分别记录服务端政策证据；显式 Exec 回滚仍符合相同禁存政策。全部证据齐备后才能进入生产候选 Reviewer 验证。
- [x] 1.7 **形成部署与跨语言迁移建议。** Size: S; difficulty: Medium; depends on 1.1 and 1.6. 记录 CLI 人工安装/升级步骤、版本兼容表、运维成本证据，以及 Java/Python/TypeScript 后续接入契约和迁移顺序。Evidence: `workers/process-go/CODEX-RUNTIME.md` 记录唯一支持版本、部署方安装/升级和 Exec 回滚路径；没有量化数据时不声称降低成本；其他语言仅为建议。

- [x] 1.8 **修复独立审查 R1/R2 的本地边界。** Size: S; difficulty: Medium; depends on 1.4, 1.5. 活动 turn 的 transport/非终态错误统一关闭共享 App Server；并发 Close 不以 started=false 伪造终止确认；资源未知保留锁并隔离 slot，不报告 failed/stop-ack；启动显式传递 network_access。Evidence: `FD-014-implementation-review-fixes-r1.md`，修改后离线 compile-only 通过，新增运行验证未运行。

## Acceptance

1. Go Worker 可通过显式配置选择 Codex Exec 或 App Server；Exec 不要求先启动常驻 App Server；没有静默 fallback。
2. 两模式都把会话与正确 task/attempt 关联，并遵守既有超时、租约取消、只读/可写角色、attempt workspace 和结果校验契约。
3. App Server 断连、请求失败、取消和终态不确定有明确处理；未确认活动终止前不释放被占用的 workspace/slot，也不把过期结果报告为成功。
4. Runtime 能力准确表达事件流、恢复、审批、usage 等 supported/unsupported/unknown；不会把一模式的能力推断给另一模式。
5. Exec 与 App Server 的安装、固定版本、人工升级、进程管理和回滚责任有明确说明；运维成本结论由记录的证据支持。
6. 其他语言只得到契约和迁移建议，不声称已实现或验收；无 Control Plane API、租约、审批或持久化边界变化。
7. 本 FD 仅交付本地 Go Runtime 与双认证支持；真实认证切换、Linux 新增生命周期、Docker 和生产禁存验证明确未运行，不视为通过。生产候选准入、回滚和运行证据由 FD-015 承接，不阻塞本地交付的独立静态审查。

## Verification

- 独立 Reviewer r2：`reviews/FD-014-review-local-r2.md`，来源 `FD-014-000022-implementation-ready`，审查提交 `e0bc678`；Verification passed（当前批准本地范围的静态独立审查）。R1/R2 修复闭环；真实认证、新增 Linux 运行、Docker/生产检查仍未运行，不记作通过。

- 首轮独立审查 R1/R2 已修复，原 changes-requested 作为历史保留；当前请求重新独立审查，不自行宣称 passed。见 `reports/FD-014-implementation-review-fixes-r1.md`。修改后运行一次离线 compile-only 通过。

- 独立 Reviewer r1：`reviews/FD-014-review-local-r1.md`，来源 `FD-014-000020-implementation-ready`；Changes requested。发现活动终态未知时释放工作区锁/slot 与 App Server 未传递网络策略两个实现缺陷；未运行真实认证/Linux 检查，按 G11/G12 保留免验证。

- 本轮本地认证验证按用户接受意见跳过，未运行且不记作通过。默认 inherit 可复用已有原生 ChatGPT 登录；不要求为继续设计提交凭据或认证目录。此决定不代表运行行为通过；Linux 生命周期、Docker 隔离及生产认证/禁存证据已由 G12 转至 FD-015。

- 本地双认证实现（本次 Worker 会话 `fd014-worker-linux-20261013-a83c26`，来源 `FD-014-000016-work-requested`）：两个 Codex runtime 共用宿主认证选择与有界登录状态检查；App Server 启动及 thread 前核对 account/read。新增实现后执行一次离线 Go compile-only 通过，输出 `/dev/null`，未保留分发物。未执行测试、真实登录/turn、热切换或 Docker；详见 `reports/FD-014-implementation-linux-r2.md`。

- 2026-10-13 Linux 接续：现有 toml v1.6.0 依赖按授权下载；`GOTOOLCHAIN=local GOPROXY=off GOSUMDB=off go build -o /dev/null .` 重试一次通过，不保留分发产物。固定源码核对见 `reports/FD-014-no-store-source-review-linux-r1.md`；仅为来源及编译证据，不代替 Linux 运行和禁存准入证据。

- 已静态对照 Go `AgentRuntime` SPI、attempt/lease/workspace 生命周期、Exec JSONL 处理、OpenAI Responses 适配器与平台进程终止实现；基线、跨模式契约和能力矩阵见 `workers/process-go/CODEX-RUNTIME.md`。
- 静态审查 Go Worker runtime 选择、Exec 参数、App Server 协议映射及 attempt/lease/workspace 清理调用链。
- `workers/process-go` compile-only：最终 Go 源码修改后执行 `go build -o NUL .` 并通过（GOTOOLCHAIN=local，GOPROXY=off，GOSUMDB=off）；输出写入 Windows NUL 设备，不保留分发物；不把编译通过当作 App Server 行为验证。
- 协议基线固定为 OpenAI Codex CLI `0.162.1`（官方稳定发布 tag `rust-v0.162.1`）及该 tag 中 `codex-rs/app-server-protocol/schema/json/v2` 的 App Server 协议；本次于 2026-10-13 查阅。升级支持版本时需同步审查并更新协议基线及兼容性检查，不跟随浮动 `main`。
- 协议静态依据：官方 App Server 文档规定 stdio 上使用换行分隔 JSON、先成功完成 `initialize` 再发送 `initialized`，随后 `thread/start`、`turn/start`，并以 `turn/completed` 的 `turn.status == completed` 作为成功；v2 schema 定义 `thread/start.ephemeral` 和 `turn/interrupt(threadId, turnId)`。这些字段和通知不证明本 Go 实现的运行行为。
- 假 App Server 与本地 Worker 集成：`go test -run '^Test(CodexAppServer(FakeLifecycle|RejectsUnpinnedVersion|DisconnectSignalsFailure|UnconfirmedInterruptStopsManagedProcess)|ExecuteTask(LeaseLossDoesNotReportSuccess|StopRequestIsAcknowledged))$' -count=1` 通过。覆盖并发 thread/session 关联、事件输出、deadline cancel、interrupt 回执与 matching terminal、stop acknowledgement、lease loss、不报告成功、未知资源终态/workspace 保留、版本拒绝、断连和不确认时受管进程终止。
- 已授权真实 turn：`AIW_RUN_CODEX_APP_SERVER_REAL_TURN=1` 下运行 `go test -run '^TestCodexAppServerRealTurn$' -count=1 -v` 通过；本机官方 CLI `0.162.1` 在临时只读 workspace 完成一次无工具 smoke turn，EOF graceful close 确认。为适配 Windows PowerShell 安装入口，在系统临时目录构建薄 Node launcher，未更改 CLI 安装。
- 一次初始假生命周期运行发现 `exec.Cmd.Cancel` 必须配合 `exec.CommandContext`；随后测试发现 `interrupted` terminal 被误判为未确认取消并关闭共享 App Server。修复后确认取消仍返回 context error，且 App Server 保持运行。Worker 集成运行另发现 runtime capability metadata 不在 evidence allowlist，修复并通过。
- 生产候选仍未准入：`ephemeral` thread 和单次真实 turn 不能证明 Codex CLI、主机及服务端的所有持久化通道满足仓库禁存契约；完整 Worker restart/Exec rollback 行为也未全部覆盖。详见候选准入 blocker pair。
- Reviewer 对照 ISSUE-00004 的六项验收示例、实际 diff、能力矩阵和运行证据；不以文档声明替代运行证据。

## 已确认决策

- **G1 生命周期：** 每个 Go Worker 进程启动并管理一个 App Server，进程内 slot 复用，Worker 退出时关闭；必须验证 attempt/thread 隔离。
- **G2 准入：** 本 FD 目标是具备准入、回滚和运行证据的生产候选；不包含实际发布或部署授权。
- **G3 契约范围：** 首期统一执行、事件、取消、终态和能力声明；审批及会话恢复标为 unsupported/unknown，不作跨模式保证。
- **G4 CLI 分发：** Codex CLI 由部署方人工安装和升级；Worker 不下载或安装 CLI，并须在注册前校验受支持版本与 App Server 协议能力。
- **G5 协议版本（2026-10-13）：** 按用户指示采用当日 OpenAI 官方 GitHub 最新稳定 CLI 发布 `0.162.1`（tag `rust-v0.162.1`），实现基线锁定于该 tag 的 App Server v2 JSON Schema；主来源为该 tag 的协议 schema，OpenAI Developers App Server 文档作为 stdio 流程说明。不得以浮动 `main` 作为实现契约；其他 CLI 版本在能力校验前视为 unsupported/unknown。
- **G6 验证权限：** 用户确认授权本地假 App Server lifecycle/取消/slot 检查，以及使用 CLI `0.162.1` 发起真实 turn；上述检查已执行。该授权不包含发布/部署，也不等于禁存准入认证。

- **G7 Linux 部署准入（2026-10-13，用户已确认）：** 保留生产候选目标，限定为 Codex 状态只能写入不会交换到磁盘的受管内存的 Linux 部署；缺少主机与隔离证据时拒绝启动。该决定没有授权本会话挂载文件系统、修改 swap/系统诊断、安装隔离器或部署。
- **G8 双认证切换（2026-10-13，用户澄清）：** 同时支持 OpenAI API Key 与 ChatGPT/Codex 登录的显式切换，无需在设计阶段二选一。两种认证分别核对实际有效身份和对应服务端政策，不能跨模式复用准入结论；不得自动注销或破坏已有登录缓存。
- **G9 本次来源与编译授权：** 用户 confirm 授权读取固定版本 OpenAI 官方来源、下载已有 Go toml v1.6.0，并重试一次 Linux 编译。下载及编译通过；没有升级依赖，也不表示禁存、运行验证或候选准入通过。

- **G10 本地与 Docker 阶段（2026-10-13，用户指示）：** 后续用 Docker 隔离，当前不处理 Docker，在本地继续实现。生产目标与禁存要求保持后续验收，但不阻塞独立的本地双认证选择代码。新增宿主认证配置默认 inherit，显式模式用已有独立 CODEX_HOME，固定官方登录状态检查和 account/read；不触发登录/注销，不拷贝凭据，也不把本地开发当作生产准入通过。

- **G11 本地认证接受（2026-10-13，用户指示）：** 用户认为 API Key 方式可用，并确认 ChatGPT 已登录后直接使用。本轮不要求额外认证上下文或执行真实认证验证；使用默认 inherit 沿用现有原生认证。1.6.4.2 记录为本轮免验证、未运行，认证行为和安全边界保持原要求，生产认证准入不因此通过。

- **G12 最终交付范围（2026-10-13，用户明确授权）：** 当前结束 FD-014，将交付范围调整为“本地 Go Runtime 与双认证支持”；Docker、生产准入与剩余 Linux 生命周期证据移交后续 FD-015。真实认证切换及新增 Linux 运行验证本轮未运行，按范围决定跳过；不免除实现行为要求、不声称验证通过。完成独立审查后执行本地合并与归档。G12 取代 G2/G7/G10 中要求本 FD 达到生产候选及保留活动状态的范围要求；历史决策和运行结果保留。

## Sources

- Approved Issue and scope: `docs/issues/ISSUE-00004/issue-plan.md`.
- Current Go runtime selection and Codex Exec: `workers/process-go/agent_runtime.go`, `main.go`, `openai_runtime.go`, and `README.md`.
- Cross-runtime authority contract: `workers/WORKER_RUNTIME_CONTRACT.md`.
- Current Codex CLI/App Server capability research: `workers/SDK_CAPABILITIES.md` and `docs/worker-codex-no-store-design.md`.
- Related prior implementation: `docs/features/archive/FD-005/FD-005_WORKER_PI.md` (shared execution foundation; not the App Server pilot).
- Issue: ISSUE-00004.

## TODO

- [x] 本地 Go Runtime 与双认证支持已完成，Linux compile-only 通过。
- 本轮真实认证切换和新增 Linux 生命周期运行验证跳过，未运行、不记作通过。
- [x] 原生产范围已登记至后续 FD-015（Deferred，当前不实施）。原工作项保留 ID 和移交映射，不伪造完成证据。
- [x] 独立 Reviewer 已完成首轮本地范围静态审查，结果 Changes requested；不代表 Verification 通过。
- [x] Worker 修复 R1/R2 后独立复审通过，报告 `reviews/FD-014-review-local-r2.md`；没有补做已免除运行检查。
- [x] 按用户授权本地合并并归档；没有发布、部署或 push。

**Completed:** 2026-10-13

## 本地交付与归档结果

2026-10-13：第二轮独立审查通过事件 `FD-014-000023-verification-passed`，审查次数 2（首轮修复、复审通过）。`aiw git wt local-merge FD-014` 将已审查源码 squash 至 master 提交 `3e2e120`，验证后移除 FD 工作树及分支并保留收据；`aiw fd close FD-014 Complete` 成功。真实认证与新增 Linux 运行验证仍未运行；Docker/生产工作由 Deferred FD-015 承接。本地交付完成，不表示生产认证通过。详见 `reports/FD-014-delivery-local-r1.md`。
