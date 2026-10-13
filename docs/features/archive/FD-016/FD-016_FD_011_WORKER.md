# FD-016: 灵活计划 Worker 统一 Profile 与执行契约

**Status:** Complete
**Revision:** 21
**Priority:** Medium
**Evidence policy:** Dual

## Problem

FD-011 将确认的灵活步骤创建为 EXTERNAL_WORK，而 Process Worker 仍按旧 TaskType 选择技能与结果。用户明确不需要兼容旧任务，要求统一 profiles 等定义。本 FD 将三语言配置与执行策略统一到灵活任务模型，删除双轨适配和旧类型分派。

## Options and decision

1. 新旧类型双轨并增加 flexible-work-v1：已被用户否决，不实施。
2. 仅把 allowedTaskTypes 改为 EXTERNAL_WORK：结果逻辑仍按旧类型判断，会丢失报告或错误选择角色，不作为完整交付。
3. 三语言统一 Profile schema，所有任务使用 EXTERNAL_WORK，按启动角色、skillRef 和 resultKind 执行：采用。配置定义先落地，执行实现由后续 Worker 完成。

## Solution

### 统一定义

Profile 固定九个必需字段：name、workerIdPrefix、roleCapability、allowedTaskTypes、readOnly、instruction、protocolSkills、primarySkill、resultKind。allowedTaskTypes 必须为 ["EXTERNAL_WORK"]；resultKind 为 REPORT / VERDICT / WORKSPACE。WORKSPACE 必须 readOnly=false，另外两类必须 readOnly=true；旧八字段配置和旧任务类型不再接受，不提供回退。

| 启动角色 | roleCapability | primarySkill / skillRef | resultKind |
|---|---|---|---|
| requirements | requirement-analysis | software-requirements-analyst | REPORT |
| architect | solution-architecture | solution-architect | REPORT |
| planner | implementation-planning | implementation-planner | REPORT |
| coder | coding | coding-work-package-executor | WORKSPACE |
| integrator | integration | coding-work-package-executor | WORKSPACE |
| repairer | repair | coding-work-package-executor | WORKSPACE |
| reviewer | code-review | software-code-reviewer | VERDICT |
| verifier | verification | software-test-verifier | VERDICT |
| integration-verifier | integration-verification | software-test-verifier | VERDICT |

release-readiness 与 orchestrator 的保留咨询配置也使用同一 schema、EXTERNAL_WORK 和 REPORT，但启动准入继续拒绝这两个角色，本 FD 不把它们变为可执行 Worker。

相同 Skill 被多个角色复用；只靠 skillRef 无法区分整合/编码/修复或两类验证。每个进程按明确启动角色选择 Profile，不再因旧 TaskType 切换 Profile。删除 verifier 对 INTEGRATION_VERIFICATION 的自动映射和额外能力注册，integration-verifier 使用独立角色。

### 技能、能力与输出

- EXTERNAL_WORK 必须显式给出等于本角色 primarySkill 的 skillRef；加载 protocolSkills 和 primarySkill，按 attempt 冻结。拒绝未知、缺失、越界及任意路径引用，不下载技能。
- 仅注册本角色能力、仓库和明确配置的技术能力，不增加 flexible-work-v1。计划 requiredSkills 必须包含目标角色能力，preferredSkills 仍为调度偏好；配置不使用跨角色能力冒充其他角色。
- 控制平面仍按能力匹配，不校验 Skill；Worker 在模型和工作区写入前校验类型、角色与技能。已领取的拒绝沿用有效租约下的 fail 和既有 failureCategory。
- REPORT 返回非空 PASS summary；VERDICT 返回非空 report，末行 AIW_VERDICT: PASS 或 REPAIR_REQUIRED；WORKSPACE 返回有效 resultRevision、changedFiles、适用的 publicationRef 和非空 PASS summary。结果由 resultKind 决定，不再按旧 TaskType 判断或返回空报告。
- 工作区准备、只读检查、Git 收尾和 inputs[] 解析使用有效 Profile。只读角色不得修改工作区；写入角色缺少必要工作区明确失败。审查使用指定上游 revision，不自行选最新 HEAD。
- instruction、Skill 和上游报告不能提升 shell、网络、测试或发布权限。保留整体期限、租约、停止确认、drain、关键证据和禁存准入；不绕过 FD-014 Gate。
- 计划确认、依赖就绪和结果保留由控制平面负责。planner 提议不授予执行权；REPAIR_REQUIRED 不自动创建自主修复任务；整计划收回不等同于强制停止。

### 不兼容切换

本次修改涉及配置契约、角色注册与执行权限边界。用户授权取消旧任务兼容；仅适用于本 FD 的 Process Worker，不删除历史任务数据或重写 Java 自主任务生产器。旧生产器产生的任务不由新版 Process Worker 执行，不保证旧自主流程可用。

升级前停止旧任务生产并 drain/排空旧实例，在无在途任务后整体部署统一配置与执行版本；重新创建或人工处置旧待执行任务，不自动迁移。仅配置变更阶段不能部署。回滚须恢复旧配置和旧运行时代码的完整组合，先排空新任务，不让旧 Worker 领取新任务。部署、排空和数据处置不在本轮执行范围。

### Runtime 与依赖决定（最新用户决策）

用户要求完全移除 Worker 本地的 Codex 包，不接受仅跳过原生二进制而保留 npm 启动包。因此移除 @openai/codex-sdk 和其传递依赖 @openai/codex，TypeScript 不再提供 codex-sdk backend，改为 codex-cli、codex-app-server 与 openai-agents 三个明确入口。用户后续要求保留 Exec；两个 Codex 入口均直接使用系统 CLI，不恢复 SDK 依赖。已卸载依赖、清理旧 SDK adapter 和更新入口定义；定义阶段两个入口曾明确拒绝启动；Auto 阶段已完成本地执行适配和语法/Go 编译，真实运行未验证。

- 通过 AIW_CODEX_PATH 或系统 PATH 找到已安装原生 Codex；继续支持系统 npm launcher 的原生路径解析，但不得从 Worker node_modules 解析二进制。不安装、复制、捆绑或下载 Codex；缺失或不兼容在注册/发送任务正文前拒绝。
- App Server 模式直接使用系统 codex app-server 的 stdio JSON-RPC，不提供远端地址、WebSocket、共享外部服务或旧 SDK fallback。Exec 为独立显式模式，不作为 App Server 失败的自动回退。每个 Worker 进程管理一个服务，多个 slot 使用独立 thread、工作区和事件关联；生命周期归 Worker 管理，shutdown 等待在途终态。
- 初始契约版本以 Go FD-014 已记录的 CLI 0.162.1 为目标。系统安装与升级由部署方负责；固定版本/握手不匹配拒绝。不声称不同语言认证或禁存证据可互相替代。
- 复用系统原生登录环境，不自动登录、注销或复制凭据。复核初始化、account/read、thread/start、turn/start、事件/usage、turn/interrupt、断连与关闭。取消必须绑定对应 thread/turn，未确认终态保留工作区并停止新增副作用，不杀死仍被其他 slot 使用的服务来伪造单任务成功取消。
- thread/start 请求 ephemeral，核验返回和可读取的线程元数据；禁止存储会话恢复。ephemeral 不代表所有日志/SQLite 通道已禁存。本 FD 按用户决定仅交付本地 Runtime；生产禁存准入、隔离及认证由 FD-015 承接，本地不宣称零留存或生产准入通过。固定协议、权限、生命周期和 ephemeral 检查仍须生效。
- 控制平面 runtime 标签与 AI guidance 使用 codex-cli:<role>/codex-cli 或 codex-app-server:<role>/codex-app-server；同步配置/操作样例，不伪装为 codex-sdk。不改变公开协议字段，不自动重写历史 AI profile。
- openai-agents 保留最新稳定依赖 @openai/agents=0.20.0、openai=7.32.0、zod=4.6.7；精确锁定，不在运行时自动升级。该 API backend 不走 Codex App Server。

风险新增服务进程管理、并发 thread 隔离、取消、认证和禁存；复用 Go 契约作为设计输入，TypeScript 自己形成证据。Auto 阶段实施新的 Exec/App Server 执行；本地行为要求保留，生产认证留给 FD-015。

#### Exec 与 App Server 的共同契约与差异

参考 Go FD-014 的 CodexAgentRuntime/runCodex 与 App Server SPI，复用契约和能力声明；TypeScript 自行实现，不依赖 Go 可执行文件。Exec 适用于独立有界单次 attempt，App Server 提供受管 thread/turn 交互；管理者显式选择模式，任务文字不改变 Runtime，不根据任务类型自动切换。

Exec 每 attempt 启动独立系统 codex exec，以 --ephemeral 和结构化 JSON 事件执行，提示词由 stdin 输入；按原生固定版本核对参数。必须传入工作区、权限和批准/网络政策，消费 stdout 事件并限制 stderr，只有明确完成终态和成功退出才回报成功。无 SDK，禁止 resume 或会话持久化选项，不声称支持跨 attempt 恢复。

Exec 取消处理自己的进程树；App Server 取消处理对应 thread/turn。两种模式都共享 Worker 的期限、租约、报告、工作区与 Git 收尾边界，但必须分别记录终态、取消能力和禁存证据。进程退出不等于所有子操作已终止，无法确认时保持 unknown 并保留工作区。两者都不捆绑、复制、下载或修改系统 Codex。

## Scope

包含 TypeScript、Go、Python 的 profiles、加载定义、角色映射、能力注册、技能选择、报告及 workspace 生命周期接入，公共契约、启动配置、发布材料和灵活计划样例。TypeScript 三种 Runtime 共用角色/技能/结果策略。

排除旧任务/旧 Profile 兼容、兼容标签、自动任务迁移、删除历史数据、Java 自主流程重构、demo Worker 改造、无关依赖升级、协议/schema 变更、新规划 Worker、动态 Skill、自动修复、部署和迁移执行。此前定义阶段不实施执行代码；用户调用 Auto 后已授权本 FD 执行实现、独立审查和隔离交付。用户最新决定替代 Codex SDK 升级：移除 SDK，新增 TypeScript 系统 Exec 与 App Server 接入；Agents 与必要的 OpenAI/Zod 依赖升级保留。

## Work items

- [x] 1.1 统一 Profile 定义。小（≤半日），中难度；无依赖；三语言全部 11 份配置统一 EXTERNAL_WORK 与 resultKind，加载定义强制九字段与权限一致；公共契约说明配置先行状态。2026-10-13 已完成定义修改，未接入执行。
- [x] 1.2 TypeScript 统一解析。小（≤半日），中难度；依赖 1.1；只接受 EXTERNAL_WORK，按启动角色与 skillRef 解析，删除旧类型切换，冻结可信技能。
- [x] 1.3 TypeScript 结果与 workspace。小（≤半日），中难度；依赖 1.2；三后端按 resultKind 返回报告和代码结果，接通只读/写入与上游 revision，清除旧 TaskType 分支。
- [x] 1.4 TypeScript 角色与注册。小（≤半日），低难度；依赖 1.2–1.3；角色能力一一对应，删除 verifier 额外能力注册及旧入口，不引入兼容标签，保留禁存与咨询角色拒绝。
- [x] 1.5 Go 统一策略与技能输入。小（≤半日），中难度；依赖 1.1–1.2；补齐 skillRef 输入、可信来源与固定角色，去除旧类型分派。
- [x] 1.6 Go 结果与注册。小（≤半日），中难度；依赖 1.5、1.3–1.4；按 resultKind 接入报告/workspace 和角色能力，保留租约与停止行为。
- [x] 1.7 Python 统一策略与技能。小（≤半日），中难度；依赖 1.1–1.2；固定角色、白名单及冻结输入，去除旧类型映射。
- [x] 1.8 Python 结果与注册。小（≤半日），中难度；依赖 1.7、1.3–1.4；按 result_kind 接入结果和能力，保留 SDK 准入。
- [x] 1.9 文档、配置样例与旧断言更新。小（≤半日），中难度；依赖 1.4、1.6、1.8；统一 README、公共契约、env/启动/发布材料及受影响测试定义，提供单步分析和编码→审查计划，不保留旧任务操作示例；测试执行另行授权。
- [x] 1.10 编译与实现证据。小（≤半日），低难度；依赖 1.1–1.9；预算内静态和 compile-only 检查，Dual 报告记录实际覆盖与残余风险，再交独立 Reviewer。

1.1–1.10 的 ID 保留；内容按用户新决策修订。先前 flexible-work-v1 与旧任务兼容要求全部取消，没有已完成执行历史被删除。

- [x] 1.11 固定最新稳定 SDK 及必要配套依赖。小（≤半日），低难度；依赖 1.1；npm latest 查询、package/lock 同步、版本常量与源码基线更新。仅依赖安装完成，不表示功能认证。
- [x] 1.12 Agents SDK 工具与模型接口迁移。小（≤半日），中难度；依赖 1.11；核对 Zod 4、tool/errorFunction、OpenAIProvider/Runner 与守护包装接口，更新受影响调用；1.2–1.4 接入依赖本项。
- [x] 1.13 新 SDK 事件与取消映射。小（≤半日），中难度；依赖 1.11–1.12；核对 Agents SDK 的终态、usage、AbortSignal、工具终止和结果未知处理；Codex 部分由 1.18 承接，不借用旧版本认证。
- [x] 1.14 新 SDK 禁存与运行时准入。小（≤半日），中难度；依赖 1.11–1.13；核对 Agents tracing、store、日志边界；Codex 本地会话与权限检查由 1.19 承接；生产零留存认证移交 FD-015，静态证据与平台认证分开记录。1.9–1.10 依赖本项。

- [x] 1.15 移除 TypeScript Codex SDK 与本地 Codex 依赖。小（≤半日），低难度；依赖 1.11；package/lock/node_modules 清理、旧 SDK adapter 移除、runtime 和启动定义改为 codex-app-server；迁移期间显式拒绝启动。本项不代表 App Server 已实现。
- [x] 1.16 系统 App Server 进程与握手。小（≤半日），中难度；依赖 1.15；原生路径解析、CLI 版本/initialize/account 校验、受管 stdio、断连与有界关闭；无本地二进制或下载 fallback。
- [x] 1.17 App Server 请求与 thread 隔离。小（≤半日），中难度；依赖 1.16、1.2；请求 ID、thread/turn/attempt 关联，独立工作区和输入，事件分派与终态/usage，接入共享结果策略。
- [x] 1.18 App Server 取消与租约路径。小（≤半日），中难度；依赖 1.17；deadline、停止、lease loss、turn/interrupt 和终态确认，断连/未确认保留 workspace；不影响其他 slot 的线程。
- [x] 1.19 App Server 权限及禁存准入。小（≤半日），中难度；依赖 1.16–1.18；只读/写入配置、ephemeral 请求/回执、独立持久化通道政策、本地证据和未知版本/权限拒绝；生产禁存准入移交 FD-015，不声明生产认证。
- [x] 1.20 App Server 发布与控制平面配置对齐。小（≤半日），中难度；依赖 1.19；更新 runtime/AI guidance 配置样例、启动脚本、测试定义和发布说明，默认安装树及发行清单无 SDK/CLI/平台 Codex 包；1.9–1.10 依赖本项。只读检查发布清单，不自动执行发行构建。

1.11 的 SDK 升级保留为已完成历史，当前 Codex 依赖由 1.15 移除；不擦除之前安装及编译记录。1.13–1.14 的未完成 Codex SDK 验证范围由 1.16–1.20 替代，Agents 验证范围保留。

- [x] 1.21 系统 Exec 启动与输入策略。小（≤半日），中难度；依赖 1.15、1.2；复用系统路径/版本准入，直接 exec --ephemeral 与 JSON 模式，stdin 提示词、固定工作区和角色权限；不依赖 SDK，不自动 fallback。
- [x] 1.22 Exec 事件与结果映射。小（≤半日），中难度；依赖 1.21；事件/session/usage 与 attempt 关联，输出有界，完成事件和退出码共同校验，接入统一 resultKind。
- [x] 1.23 Exec 取消与禁存边界。小（≤半日），中难度；依赖 1.22；deadline、停止和 lease loss 的进程树终止确认，未知终态保留工作区，校验 ephemeral；独立持久化通道生产准入移交 FD-015，不因取消而发布结果。
- [x] 1.24 双 Codex 模式配置与交付对齐。小（≤半日），低难度；依赖 1.20、1.23；启动/发布/AI guidance 样例同时覆盖 codex-cli 和 codex-app-server，参考 Go 对照表标明不同取消/会话能力，默认 ID 不冲突，无 SDK/CLI 本地包。1.9–1.10 依赖本项。

## Acceptance

1. 三语言所有 Profile 使用相同九字段 schema，仅允许 EXTERNAL_WORK，字段遗漏、旧类型、无效 resultKind 或权限冲突被拒绝。
2. 九执行角色按明确角色能力、primarySkill 和 resultKind 运行；咨询角色仍不可启动；verifier 与 integration-verifier 独立，不暗中切换角色。
3. 旧任务在模型调用和写入前拒绝；没有旧 TaskType 分派、兼容标签或旧报告空值例外。
4. 未确认计划不执行，单步任务不补齐流水线，planner 报告不代替管理者确认。
5. Skill 缺失/越界/不匹配、工作区不满足或报告无效必须失败，不产生伪成功；只读角色不能提升权限。
6. REPORT / VERDICT / WORKSPACE 分别产生正确的非空结果；代码→审查以 inputs[] 和指定 revision 交接。
7. 生命周期、停止确认、禁存和关键证据策略保持生效；审查结论不建立新的自主工作流。
8. 文档和启动/发布/测试定义按新模型统一；明确旧自主流程不在支持范围，以及配置先行阶段不可部署。

9. 依赖精确固定为核定稳定版本，版本常量一致；工具、事件、取消和禁存策略按新版重新核对。Codex SDK 已移除；本地运行实现完成后可交付，不将免运行验证当作运行通过，生产认证由 FD-015 承接。

10. TypeScript Worker package、锁文件和默认安装树不包含 @openai/codex-sdk、@openai/codex 或 Codex 平台二进制；使用系统 Codex Exec 或 App Server，旧 codex-sdk 参数拒绝，无下载或 SDK fallback。
11. 多 slot 的请求/事件/工作区不串线；取消、断连与服务关闭有明确终态，权限和禁存保持生效；系统 CLI 不兼容在执行前拒绝。

12. codex-cli 与 codex-app-server 均可显式选择，共用任务/权限/结果契约；不同进程管理、事件和取消策略分别验证，不自动回退，不携带 SDK 或本地 Codex。

## TODO

Auto 已完成执行策略与双 Codex 本地 Runtime 源码。待最终 compile-only、当前 Dual 报告和独立 Reviewer；实际 CLI/模型、类型检查、测试及发布构建未授权运行。生产认证归 FD-015，不把静态或编译描述为真实运行通过。

## Verification

第四次最终整合 Reviewer：verification-passed（本地源码交付/静态范围）；来源 `FD-016-000020-review-requested`，session `fd016-reviewer-20261013-a9e28b61`。报告 [FD-016-review-local-r4](reviews/FD-016-review-local-r4.md)（同名 JSON）。R4/R5 已按实际修复核对，全部非生成发行源存在，R1/R2/R3 保持，无新阻塞。用户授权额外一轮，累计四次 outcome 不重置；未运行 build/release、launcher、CLI/模型、测试或类型检查。待 PM 真实 local-merge 后更新交付反馈并归档，生产认证仍归 FD-015。

第三次最终整合 Reviewer：changes-requested；来源 `FD-016-000017-review-requested`，session `fd016-reviewer-20261013-a9e28b61`。报告 [FD-016-review-local-r3](reviews/FD-016-review-local-r3.md)（同名 JSON）。R4 新 README 过期迁移/发行说明、R5 发布清单要求不存在 bin 目录；R1/R2/R3 修复保留，索引对齐当前来源。旧 pass 不代表整合结果通过；累计三次 outcome（两次请求修改、一次通过）。未运行 launcher/build/release、CLI/模型、测试或类型检查。

独立 Reviewer 第 2 次结果：verification-passed（本地交付/静态证据范围）；来源 `FD-016-000014-implementation-ready`，session `fd016-reviewer-20261013-a9e28b61`。报告 [FD-016-review-local-r2](reviews/FD-016-review-local-r2.md)（同名 JSON）。首轮 R1/R2/R3 已按实际修复核对，无新阻塞；原生 Windows TypeScript Codex 明确拒绝，生产认证归 FD-015，真实 CLI/模型、类型、测试与发布未运行。累计两个 review outcomes，一次请求修改、一次通过。

独立 Reviewer 第 1 次结果：changes-requested；来源 `FD-016-000012-implementation-ready`，session `fd016-reviewer-20261013-a9e28b61`。报告 [FD-016-review-local-r1](reviews/FD-016-review-local-r1.md)（同名 JSON）。R1 App Server sandbox 提升、R2 Windows taskkill 失败误确认、R3 权威 agentMessage 正文未消费须修复；仅静态/固定 schema 审查，未运行测试、CLI/模型、类型检查或编译复跑。生产认证仍归 FD-015。

- 当前配置阶段：一次定向静态检查核对三语言配置一致性、九字段、EXTERNAL_WORK、resultKind/readOnly 和 diff；运行一次 scripts/compile-worker-runtime.mjs，仅语法/编译，不验证 TOML 的实际加载、结果执行或平台。
- 后续实施：静态追踪角色、Skill、注册、报告/workspace 和租约路径；按预算执行适用 compile-only。独立 Reviewer 对当前 FD、diff 和 Dual 报告审查。
- 默认不运行测试、类型检查、格式化、模型、服务、迁移、网络或最终发行构建。可选授权场景为旧类型拒绝、九角色任务、配置错误、跨角色、只读写入、报告缺失、代码交接和租约丢失。

## Definition-stage evidence

- 三语言配置由同一份角色定义同步写入；结果分类与访问权限按上表统一。加载定义已增加九字段和结果/权限校验，执行代码尚未消费 resultKind。
- 已运行 git diff --check 和 git diff --stat；未报告 diff 空白错误，Git 提示 CRLF 会按仓库规则归一化为 LF。统计包含原有用户改动，这些原有文件未被本轮编辑。
- 内联 TOML 静态检查首次使用 python3，因缺少 tomllib 退出 1；更正入口为 python 后仍缺少 tomllib，退出 1。不重复检查，不声明 TOML 一致性程序已通过。
- node scripts/compile-worker-runtime.mjs 退出 1：缺少本地 typescript 模块，未进入源码编译；没有下载依赖或重试。因此三语言编译均未验证。
- 未执行测试、类型检查、模型、网络、服务、迁移或部署。受影响旧测试定义在 1.9 中更新；配置加载及执行效果仍需后续证据。

## SDK 升级证据

- npm view 查询得到 Codex SDK latest 0.162.1、Agents SDK latest 0.20.0；新版 peer/dependencies 确认 Zod 4 和 OpenAI 7 的需求。
- npm install --save-exact @openai/codex-sdk@0.162.1 @openai/agents@0.20.0 openai@latest zod@latest --ignore-scripts --omit=optional --no-audit --no-fund：退出 0，安装 17 个包；最终版本固定为 0.162.1、0.20.0、7.32.0、4.6.7。
- 直接读取已安装 Codex SDK 的 dist/index.js / index.d.ts 核定 Exec 与公开导出；读取 Agents tool 定义确认 errorFunction 仍存在，不代表所有旧包装接口兼容。
- 官方 SDK / App Server 页面确认 TypeScript 直接协议接入与 SDK 使用方式不同；未运行模型、SDK 会话、测试或部署。
- node workers/process-typescript/scripts/compile.mjs：退出 0，仅 TypeScript 语法编译，无发行产物；不包含依赖类型验证或 SDK 运行认证。
- 一次定向静态检查：git diff --check 无空白错误；读取 package/lock JSON 确认四项精确版本一致。历史定义阶段的缺依赖编译失败保留，当前安装后语法通过不代表 Go/Python 或完整执行功能通过。

## Codex 依赖移除证据

- npm uninstall @openai/codex-sdk --offline --ignore-scripts --omit=optional --no-audit --no-fund：退出 0，移除 2 个包，无网络、SDK 或模型执行。
- SDK 实现已移除；App Server 入口当前返回 CODEX_APP_SERVER_MIGRATION_PENDING，未提供实际协议执行。不继承旧 SDK 的运行证据。
- node workers/process-typescript/scripts/compile.mjs：退出 0，语法编译通过，无产物，不包含类型或依赖接口认证。
- 一次定向静态检查：diff 无空白错误；读取 manifest、lock 和本地 node_modules/@openai，确认无 Codex SDK、CLI 或平台包，旧 SDK adapter 已删除。未执行测试、App Server、模型或发行构建。

## Exec 模式保留决策

用户明确保留单次任务的 Exec 选择；runtime/启动定义已增加 codex-cli，默认 Exec Worker ID 为 process-exec-<role>-1，避免与 App Server 实例冲突。新的 Exec adapter 在定义阶段只报告 CODEX_EXEC_MIGRATION_PENDING；Auto 阶段已替换为系统 Exec 执行。App Server 与 Exec 参考 Go 实现的契约，实际 TypeScript 执行已由 Auto Work Items 接入；不宣称真实会话验证通过。

本轮 node workers/process-typescript/scripts/compile.mjs 退出 0，仅语法编译；一次定向静态检查确认三个 Runtime 定义在入口、接口与启动/发布脚本一致，package/lock 未重新引入 Codex 包，diff 无空白错误。未执行测试、CLI、App Server、模型、网络或发行构建。

## Sources

- Issue: none；用户 2026-10-13 创建 FD，随后明确无需兼容旧任务，并选择本轮统一 profiles/定义、执行代码留给 FD 实施。
- FD-011；WorkflowPlanService、WorkflowPlanSpec；TaskExecutionService、WorkerProtocolV1Controller。
- 三语言 profiles；TypeScript profiles.ts/instructions.ts/index.ts；Go main.go；Python process_profiles.py/process_worker.py。
- workers/WORKER_RUNTIME_CONTRACT.md；FD-005、FD-009、FD-010、FD-014 的运行时安全边界。
- 原 Planner session: fd016-planner-20261013-f28c41；原设计来源 FD-016-000002-design-requested。新决策更新原待领取 Worker 交接，不冒充已执行实现。

## Auto 实施记录

2026-10-13：用户调用 Auto，授权实现与正常独立审查、隔离交付流程，取代此前仅定义阶段的执行限制。Worker session `fd016-worker-20261013-0f3443b8`，来源 `FD-016-000011-work-requested`；父分支 master，工作树 feature/FD-016。1.2 已静态接通 EXTERNAL_WORK 和精确 skillRef 校验，任务准备前冻结 Profile，去除 verifier 旧任务映射；编译在实现结束时统一进行。

1.3：共享 attempt 按 resultKind 生成非空报告；校验写角色 workspace/baseRevision 与审查上游 revision；技能文本在 start/工作区准备前读取冻结，失败在副作用前拒绝。只读收尾不再 push。现有结果类型由各后端共享；新后端接入仍未完成。

1.4：注册只包含本角色能力；显式附加能力不能冒充其他执行角色或使用已取消兼容标签；咨询角色继续在启动时拒绝。

1.5：Go 领取输入增加 skillRef；固定启动 Profile，精确技能校验，可信技能在工作区准备前冻结，保留证据摘要和输入预算。编译未运行。

1.6：Go 按 resultKind 生成报告并校验 workspace/上游 revision；只读角色不 stage/commit/push，注册拒绝其他角色能力。保留租约、停止和未知资源处置路径；编译未运行。

1.7：Python 去除 verifier 旧映射，EXTERNAL_WORK 和精确 skillRef 校验在 start 和 workspace 前执行；可信技能内容按 attempt 读取冻结。

1.8：Python 按 result_kind 返回非空报告，检查必需 workspace/上游 revision；取消 verifier 双角色注册，拒绝跨角色能力；只读角色不 push，保留 SDK 准入和 attempt 生命周期。

1.12：静态读取本机已安装 @openai/agents-core / agents-openai 0.20.0 与 Zod 4 定义，确认 Runner/OpenAIProvider/model 包装与 errorFunction:null；将不存在的 AnyZodObject 改为 ZodObject。补齐 TypeScript 角色校验导入。未执行 SDK 或类型检查。

1.13：对照 0.20.0 provider 源码，保持完成事件到 stream closure 后才释放，新增 preserveRawUsage 及 rawUsage 读取；输出按 UTF-8 字节预算拒绝超量；AbortSignal 和工具 settled/unknown 保留。未运行取消/模型行为。

## Auto 范围决定（2026-10-13）

用户确认“按 Go 的本地交付范围，生产准入留给 FD-015”。此决定取代上文历史 G2 生产认证阻塞语义；不免除本地权限、ephemeral、禁止 resume、取消与未知终态行为要求，不宣称运行验证或生产认证通过。本 FD 不自动运行真实 CLI/模型或测试。

1.16：TypeScript 系统 CLI 0.162.1 检查、有界 stdio JSON-RPC、initialize/initialized/account/read 及关闭已编写；系统 npm launcher 解析扩至 Unix，禁止显式路径绕过 Worker node_modules 排除。尚未运行 CLI/服务；生产准入按用户决定留 FD-015。

1.17：每 attempt 独立 ephemeral thread/inbox，任务正文仅 turn/start 发送；请求 ID 与 thread/turn 匹配，终态和 token usage 接入统一结果。只接受当前启动 mode 的 AI guidance，不回退。当前源码尚未最终编译或运行。

1.18：AbortSignal 汇总 deadline/停止/lease loss；已知 turn 发送定向 interrupt 并等实际 terminal，未确认以 PROCESS_TREE_UNCERTAIN 保留 workspace 并停止新增任务，不杀共享服务来伪造取消。Worker 在 slots 结束后关闭服务；注册失败也进入 shutdown。

1.19：按授权查询官方 rust-v0.162.1 schema（ThreadStartResponse/Params、TurnStartParams、ThreadReadParams、ThreadTokenUsageUpdatedNotification、v1 InitializeResponse），核定 ephemeral/CLI/cwd/approval/sandbox 回执与 thread/read 元数据；turn 明确传入权限和网络。协议下载未运行 CLI/模型；production Gate 已移交 FD-015。最初 Thread.json / InitializeResponse 根路径请求不存在（404），更正到嵌入 Thread 定义与 v1 路径后成功，不宣称不存在的 schema 读取通过。

1.21：系统 Exec adapter 已编写，直接调用固定 CLI，--ephemeral/--json、stdin 提示词、固定 cwd、sandbox/network/never approval；共享角色与结果适配，无 SDK 或 fallback。事件及取消收尾在本文件接入，后续条目继续补充证据与边界。

1.22：Exec 显式关联唯一 session，拒绝 session 重用、错误顺序和重复终态；仅成功退出+完成事件+非空有界输出成功。工具失败阻止伪成功；stdout/stderr 不保留正文，关键 usage/终态记录失败则中止。

1.23：每 attempt 发正文前重核系统 CLI；取消销毁本进程树、确认 close/进程组消失，未知保留 workspace，宿主凭据目录不得置于任务工作区。ephemeral/no-resume 本地边界保持；SQLite/供应商认证由 FD-015 承接。真实生命周期未运行。

1.14：核对并检查 agents/core/provider/OpenAI/Zod 精确本地版本；Runner tracingDisabled、provider logLevel off、入模 tracing:false/store:false，禁止 provider continuation/conversation。此为本地 API 配置边界，不代表供应商 ZDR 或生产禁存认证；后者按批准范围交 FD-015。

1.20：release 清单不复制 node_modules，并在打包前拒绝 manifest/lock 中 Codex SDK/CLI/平台包；env 样例明确 App Server mode 与 never/network 默认。发行构建未运行；控制平面历史 AI profiles 未自动改写，管理员须显式授权同名 Runtime profile。

1.24：两个模式均显式选择，共用新 Task/Skill/resultKind；Go App Server guidance 去除旧 codex-cli 别名，不自动 fallback。补充 thread/start 显式网络配置。静态追踪 CP 拒绝路径发现 fail 仅接受 RUNNING，因此三语言均先完成有租约的 start，然后在任何模型/工作区动作前验证任务和冻结技能；配置拒绝可按现有 fail 契约回报，不让 CLAIMED 任务等待过期后循环重领。此前“start 前校验”记录描述中间实现，该顺序以本项为准。

1.9：三语言 README、共享契约/运维、runtime 输入契约与静态样例已统一；单步分析、编码→审查按 WorkflowPlanSpec 提供模板，旧八字段/类型映射样例删除；维护现有 Worker 测试定义，未生成或执行独立测试。补齐 TS integration-verifier env，旧心跳样例改为实际毫秒项。

最终 compile-only 首次通过后，静态检查补充 Codex 执行外围工作区排他协调（内部工具不可观察），防止多个 slot 的同工作区操作与 Git 收尾串扰；相关源修正后按预算重编一次。不把编译结果当作类型、取消或模型运行认证。

## Auto 最终实施证据

实现报告：`reports/FD-016-implementation-local-r1.md` 与同名 JSON；Worker `fd016-worker-20261013-0f3443b8`，来源 `FD-016-000011-work-requested`。

- `node scripts/compile-worker-runtime.mjs` 首轮角色/SDK阶段通过；用户另外授权 Runtime 最终 compile-only，首次通过后源修正补充 Codex 工作区协调，重编通过。三个实际执行均退出 0；TS/JS/Python 仅语法，Go 使用离线本地编译器临时产物并删除。不包含 TS 类型检查、测试、CLI/模型或部署。
- 定向 `git -c core.whitespace=cr-at-eol diff --check master...HEAD` 未发现空白错误；静态追踪 Profile/技能/结果、CP fail 前置 RUNNING、只读收尾与 lease 取消路径。代码修正后再次核对冻结输入和外围工作区协调。
- 依赖只链接父工作区已有安装树用于 compile，未下载或复制依赖；编译后移除该临时链接，不进入发行/提交。`.ai` 仅链接父真实收据，不编辑事件内容；独立 Reviewer 使用同一真实交接链。
- 只读固定协议查询已获授权，核定 App Server 0.162.1 schema；原错误路径更正成功。未执行真实 Codex --version、服务、turn 或凭据操作；源码中启动检查尚无实际运行证据。
- 唯一 blocker `reports/FD-016-blocker-local-scope-20261013-0f3443b8.md` 已按用户范围决定解决；生产禁存归 FD-015，真实多 slot/进程树/取消/SDK 端点行为和类型兼容仍待独立授权验证。
- 尚未独立审查、合并或归档；现在提交当前修订供 Reviewer。

## 第一次审查修复

来源 `FD-016-000013-changes-requested`，原 Worker session 再次领取；第 1 次 review changes-requested 三项可操作，未达到三次 Gate。R1：turn/start 使用同一有效 sandbox，保留管理员显式 read-only。R2：Windows taskkill 非零始终未知，父进程 close 不代替进程树确认；Node 目前无法证明 Windows 正常退出后的子操作终态，TypeScript Codex 两模式在启动/正文前明确拒绝该平台，保留 Windows 路径解析供后续认证；本地 Unix/WSL 与 API 模式不受该平台门禁影响，生产平台能力归 FD-015。此为 Acceptance 11 未知平台拒绝规则的执行，不宣称新增 Windows 运行支持。R3：按 id/phase 消费权威完成正文，优先 final_answer、再 completed 无 phase 的最终消息；commentary/delta 不能独立构成报告，完成正文同样受预算约束。

首轮额外资源风险同步收敛：已核对固定 ThreadUnsubscribeResponse schema，确认终态后请求 thread/unsubscribe 并验证合法回执，删除本地 inbox 不代替服务端释放。释放未知保持 resourceUncertain；服务 EOF 后存在进程组时进行一次有界终止，不直接宣称无子操作。

第一次修复证据：`reports/FD-016-implementation-local-r2.md/json`，来源 `FD-016-000013-changes-requested`；一次 compile-only 退出 0，定向 diff 空白检查通过。未执行真实 CLI/模型、类型检查、测试或构建；等待第二次独立 review。

第三次整合审查（来源 FD-016-000018-changes-requested，三次 outcome 为修改/通过/修改，两次失败）发现 R4/R5。原 Worker 领取后修复：保留父分支的新手/舰队使用说明，但去除过期迁移错误和 start-all 未进清单的断言；按实际 Git 树移除 release/package files 中不存在且未使用的 bin/，其余非生成发行源文件静态核对。新结果尚未经独立复审，不复用先前通过。Auto 已达到累计三次审查上限，下一次 Reviewer 派发需人类确认扩展额度；禁止伪造通过或合并归档。

第三轮修复证据：reports/FD-016-implementation-local-r3.md/json；一次 compile-only 退出 0，全部非生成发行源及 diff 静态检查通过。待人类决定的第四轮 Reviewer 派发见 reports/FD-016-blocker-review-limit-20261013-85baf15.md/json（未解决）；父分支合并反馈也保持未交付，不声称已解决。

## 额外审查授权

用户明确答复“允许额外一轮审查并完成交付”，第四次独立审查可以派发，成功后继续已授权本地合并/清理/归档。累计三次历史 outcome 不重置，上限仅增加到四，不含测试、真实 CLI/模型、类型检查或发行构建。review-limit feedback 已解决；父分支 merge feedback 保持待实际交付后确认。

**Completed:** 2026-10-13
