# FD-020: 三语言目标讨论角色与后端能力对齐

**Status:** Complete
**Revision:** 7
**Priority:** Medium
**Evidence policy:** Dual

## Problem

FD-019 仅为 TypeScript 增加 goal-discussion，Python/Go 虽有同等任务协议与模型框架却没有该角色；Java GoalTaskPolicy 还按 TS runtime 白名单筛选，Python codex-sdk 和 Go openai-api 被排除。用户要求三语言共同角色、共享角色/Skill 契约和 workers/README.md 的角色×语言×后端矩阵。

## Options and decision

- 保持 TS 专用并标注差异：不能满足用户要求，不采用。
- 给每种语言移植全部 SDK：引入额外依赖与平台维护，不采用。
- 用户随后明确 Python 删除 codex-sdk，只保留 codex-app-server/openai-agents。本 FD 替换 Python SDK 执行路径，不为 Python 新增 codex-cli。OpenAI backend 接受 openai-agents/openai-api；Codex backend 接受 codex-cli/codex-app-server，仍按冻结 approved profiles 约束，未知 backend/runtime 拒绝，不自动跨 backend 回退。

## Solution

1. `workers/shared/goal-discussion/profile.toml` 与 `SKILL.md` 是规范资产；复制同一字节到三语言的 bundled profiles/skills 供独立部署。增加窄同步工具，以 shared 为唯一编辑源，复制 packaged snapshots，提供可选静态一致性检查；本次不执行验证脚本。runtime 不依赖仓库 shared 路径，避免单独打包失效。共同语义同时写入 WORKER_RUNTIME_CONTRACT.md。
2. 同一角色名/capability/skillRef：goal-discussion；TaskType EXTERNAL_WORK、readOnly=true、resultKind REPORT。Java 继续校验 PASS、终态、1–16000 字符，不将报告当目标修改或批准。Python ROLE_FILES 与 launcher 新增角色；Go 从指定 TOML 加载，无需新引擎；补充 reserved role capability，不能用额外技术能力冒充该角色。
3. Python/Go 的该角色拒绝 workspace assignment，每次 attempt 生成独立空目录，不使用 fallback 源码根；TS 也做相同防御检查。空目录沿用 FD-019 保留策略，不将目录当 OS 沙箱。无工具 API 路径：Python 不创建 workspace tools；Go 不发送工具定义且拒绝服务端意外 function_call，不能仅靠 prompt 禁止。
4. goal-discussion 的 Codex 路径固定只读/网络关闭。Go/TS 沿用现有适配；Python 新增标准库子进程 stdio App Server 适配，删除 openai-codex 依赖与 SDK import/上下文。默认 runtime 改为 codex-app-server，显式 codex-sdk 在配置阶段拒绝并提示迁移；不改成其他提供方，不自动登录/下载。其他 Python 角色同时迁移到 App Server，保留 EXTERNAL_WORK/REPORT/VERDICT/WORKSPACE 与模型选择契约。App Server 不接受 fast=true profile，xhigh 在已批准范围内允许。
5. Python 每 attempt 独立 App Server 进程/ephemeral thread，无跨 attempt 会话复用。协议直接参考仓库 TS AppServerTransport 与 Go CodexAppServerRuntime：initialize/initialized、account/read、thread/start+thread/read、turn/start、通知、turn/interrupt。有界 RPC/帧/队列/输出，未知宿主请求拒绝；不执行未知工具或批准提示。检查 ephemeral/path、cwd、approval 和 sandbox 回执，原生 readonly/网络策略在 thread/turn 配置同时固定。
6. Python 进程按 Unix 独立 session/process group 运行；取消、租约丢失、协议错误和正常结束均在返回结果前清理并确认进程组终态，无法确认则保留资源并停止 slot，不报完成。native Windows 在注册前拒绝该 runtime（进程树能力未证明，提供 WSL/Unix 或 openai-agents 路径）；不假装 taskkill 等于已验证全树隔离。凭证沿用已配置本机 CLI 上下文，account/read 必须显示有效 apiKey/chatgpt；不写凭证，不输出原始管道，不声称通过完整生产禁存认证。仅实现已有本地协议，不调用网络查证或真实 CLI。
   Python App Server 固定 approvalPolicy=never，不接受宿主交互审批；默认 AIW_CODEX_APPROVAL_MODE 改为 deny-all，允许显式 never 别名。旧 SDK auto-review 配置拒绝并提示迁移，不把其权限意义偷偷映射到另一种原生审批。沙箱仅支持既有只读/workspace-write，旧 full-access 设置明确拒绝；其他角色仍保留原本有界工作区写入。
7. 可配置个人 backend 为类别而非语言；任务由 matching role Worker 领取，不在 UI 新增语言选择。旧已冻结 codex-sdk 请求不迁移、不改写批准资料；本FD新增的goal请求不接受它。运营者需显式更新 Python启动环境、AI Profiles和排队策略，文档说明旧runtime请求不会自动变更；不擅自修改用户运行配置。
8. Java GoalTaskPolicy 新增 openai-api，现有 Codex runtime 集合不变；frozen profiles、claim/guidance/start 关联、当前 catalog 豁免不变。未配置 profiles 时同 backend 支持的 runtime 可领取；配置 profiles 时仅冻结的批准组合可领取。
9. workers/README.md 修正 TS 过时 runtime，并维护完整执行角色矩阵，标注 TS-only recovery-decider、advisory 不可执行、源码支持与运行认证区别；分别更新 Py/Go/TS 使用说明、环境/运维迁移说明和 FD-019工作台说明。Stable goal spec 增补语言无关兼容规则，不建 OpenSpec change。

涉及调度兼容与工具权限：Python App Server 为新适配，以仓库 TS/Go 协议为依据；其他 runtime 已有实现。保持三语言同样的输入隔离与无工具默认，不通过扩大 shell/网络权限实现一致性。无数据库/持久化迁移、无新增依赖、无真实模型执行。独立设计 r1 因用户变更已明确失效，r2 定向复核 ready；移除旧SDK run_operation不明资源包装、各退出路径终态和审批迁移均为实现必须核对项。

## Scope

交付三语言目标讨论角色等价性、共享 packaged 资产源、Python App Server 替换 SDK及支持矩阵。保留当前聊天/计划/附件的 Java 流程。不新增 Go Agents SDK、不合并三语言运行引擎、不扩大其他角色权限，不新增自动 research/视觉服务，不部署或发布。Python native Windows App Server 暂无进程树准入，不宣称可用。

## Work items

- [x] 1.1 共享契约与资产源；小/低；无依赖；完成规范 profile/Skill、同步工具和三包独立部署副本。
- [x] 1.2.1 Python App Server 传输；小/中；无依赖；有界 JSONRPC、无交互宿主授权、启动/账号回执及故障传播。
- [x] 1.2.2 Python App Server attempt 生命周期；小/中；依赖1.2.1；ephemeral/策略回执、模型输出关联、取消、终态确认与资源不明停止slot。
- [x] 1.2.3 Python SDK移除与启动迁移；小/中；依赖1.2.2；只接受 app-server/agents、依赖/import移除、配置与profile选择一致、nativeWindows明确拒绝。
- [x] 1.2.4 Python讨论角色与权限；小/中；依赖1.1、1.2.3；注册/launcher/预留能力、独立空目录、无工具API与只读Codex。
- [x] 1.3 Go 角色与运行边界；小/中；依赖1.1；profile、预留能力、空目录、API 禁用工具及意外调用拒绝、Codex 策略可追踪。
- [x] 1.4 TS 对齐与 Java runtime 筛选；小/中；依赖1.1–1.3；TS launcher/assignment防御一致，Java接受四种已支持runtime并拒绝未知类别，保留冻结链。
- [x] 1.5 角色×语言×后端文档；小/低；依赖1.1–1.4；主矩阵与各包说明一致，明确未运行认证及现有平台限制。

## TODO

设计 r2 已就绪并完成隔离实现；源代码和支持矩阵均已完成，Worker报告见 reports/FD-020-implementation-r1.md。交独立实现审阅，通过后继续本地合并/清理/归档。

## Acceptance

1. 三种 Worker 均可按各自既有启动方式选择独立 goal-discussion，注册同一 capability/skillRef，bundled规范资产一致且能独立部署（1.1–1.3）。
2. Java OpenAI 接受 TS/Python openai-agents 与 Go openai-api；Codex 接受 TS/Go CLI/App Server 及 Python App Server；未知类别、已删除的PythonSDK路径、不在冻结profile内的runtime仍拒绝，不跨 backend 回退（1.4）。
3. 三语言无 assigned workspace 的讨论只用独立空目录，拒绝意外workspace assignment，API不开放文件/shell/AIW工具；模型意外工具调用不会执行，Codex只读且禁网络（1.2–1.4）。
4. 沿用 REPORT和租约终态、重试、已冻结配置、目标内容归属与旧回复规则，不引入第二套生命周期或持久化（1.2–1.4）。
5. workers/README.md矩阵列明每个执行角色在三语言与本次支持后端的源码支持；平台或运行未验证明确标注，PythonSDK已删除、Go无Agents SDK、Python无CLI均写清（1.5）。
6. Python全部角色仅可选codex-app-server/openai-agents，明确拒绝codex-sdk配置；ephemeral thread及策略回执不符、协议异常或取消终态不明时不回报成功；去除openai-codex依赖且不下载CLI/依赖（1.2.1–3）。

## Verification

- 独立实现审查 R1 通过（Reviewer `fd020-reviewer-20261021-7c2d91`；来源 `FD-020-000006-implementation-ready`；基线 `1625cf5`、受审 HEAD `af42880`）。报告：`reviews/FD-020-review-r1.md` 及同名 JSON。未发现确定性阻断缺陷；仅静态审查，未重跑编译或测试，真实 CLI/进程组/租约取消竞争及生产禁存仍未验证。

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.
- 无测试授权；使用现有 scripts/compile-worker-runtime.mjs 的适用范围，避免无关验证/下载；源码独立worktree缺依赖时复用父工作区已有编译器，不安装。Java离线Maven compile；Go临时编译产物在受检临时路径删除，Python内存compile、TS内存transpile；只编译，不执行模型或Worker。
- 独立设计 r1 为用户范围变更前历史，r2 对 Python 新适配定向复核 ready。源码完成，现有 Worker compile-only（TS/JS/Python语法、Go编译）两次 exit0，Java离线compile exit0；修复后第二次Worker编译覆盖最终Go上下文与TS工具边界。同步工具仅内存语法编译，未执行同步/检查脚本；规范资产和三包副本的SHA256逐项一致。git diff --check通过。未运行测试、CLI、模型、网络、服务、真实协议/取消竞争或生产禁存检查，类型检查未运行。独立实现Reviewer待进行，最多三次失败review，不自审。

## Sources

- Issue: none
- 用户本轮明确指令；FD-019及其已确认产品边界。
- GoalTaskPolicy.java；workers/WORKER_RUNTIME_CONTRACT.md；各语言profiles/运行适配/启动脚本；现有workers/README.md。
- Planner Session fd020-planner-20261021-d95a41，来源FD-020-000002-design-requested。
