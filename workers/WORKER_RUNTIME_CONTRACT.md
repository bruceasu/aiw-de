# Worker 公共执行契约

## FD-030 平台Skill资源

新增`aiw.skill.files.v1`/`skill-files-v1`：三语言SDK/API和AppServer按Claim固定文件集校验完整字节，入口/清单进入提示词，辅助文件通过`read_skill_file`按需读取。来源、版本、摘要、Task/Attempt不匹配须失败，不回退目录或新版本；资源不增加工具权限。独立目录保留，CLI不支持新平台资源任务。完整合同见[说明](../docs/WORKER-SKILL-FILES.md)。

## FD-016 统一执行契约

三语言九字段 Profile：name、workerIdPrefix、roleCapability、allowedTaskTypes、readOnly、instruction、protocolSkills、primarySkill、resultKind。allowedTaskTypes 只能为 [EXTERNAL_WORK]；WORKSPACE 要求 readOnly=false，REPORT/VERDICT 要求 readOnly=true。不兼容旧任务或八字段配置。

| 启动角色 | TaskType | 注册能力 | primarySkill / skillRef | resultKind |
|---|---|---|---|---|
| goal-discussion | EXTERNAL_WORK | goal-discussion | goal-discussion | REPORT |
| requirements | EXTERNAL_WORK | requirement-analysis | software-requirements-analyst | REPORT |
| architect | EXTERNAL_WORK | solution-architecture | solution-architect | REPORT |
| planner | EXTERNAL_WORK | implementation-planning | implementation-planner | REPORT |
| coder | EXTERNAL_WORK | coding | coding-work-package-executor | WORKSPACE |
| integrator | EXTERNAL_WORK | integration | coding-work-package-executor | WORKSPACE |
| repairer | EXTERNAL_WORK | repair | coding-work-package-executor | WORKSPACE |
| reviewer | EXTERNAL_WORK | code-review | software-code-reviewer | VERDICT |
| verifier | EXTERNAL_WORK | verification | software-test-verifier | VERDICT |
| integration-verifier | EXTERNAL_WORK | integration-verification | software-test-verifier | VERDICT |

skillRef 必须精确匹配启动角色的 primarySkill；技能在模型与工作区动作前从可信 skills 根读取冻结。verifier 与 integration-verifier 独立启动、单独能力；附加技术能力不能冒充其他执行角色。release-readiness/orchestrator 仍不可执行。

REPORT 返回非空 PASS summary；VERDICT 返回非空报告且末行 AIW_VERDICT: PASS/REPAIR_REQUIRED；WORKSPACE 返回有效 resultRevision、changedFiles、适用 publicationRef 和非空 PASS summary。写角色必须有固定 Git baseRevision；审查与输入结果 revision 一致，不自行选择新 HEAD。只读角色不 stage/commit/push。

TypeScript 明确选择 codex-cli、codex-app-server 或 openai-agents；两 Codex 模式使用系统已安装 CLI，不限制精确版本，无 SDK/CLI/平台包依赖，无下载或自动回退。Exec 为独立 attempt 进程；App Server 是受管 stdio 服务，每 slot 独立 thread/turn。Go 保留 codex-cli/codex-app-server/openai-api。FD-020 将 Python 替换为 codex-app-server/openai-agents，删除 codex-sdk；Python 每 attempt 独立 App Server 进程与 ephemeral thread，正常及异常返回前核对进程组终态，不明时停止 slot并保留资源。详见 [源码支持矩阵](README.md#角色--语言--后端支持矩阵)。

### 共同 goal-discussion 角色

三包共享 `shared/goal-discussion/profile.toml` 和 `SKILL.md` 规范源，各包的 bundled 副本只用于独立部署。注册角色/capability/skillRef 同名，角色只读、REPORT纯文本；Java 仅接纳有效 PASS/终态及1–16000字符，不将建议当作修改或批准。API模型仅接收冻结资料，不开放workspace/shell/AIW工具；任务拒绝workspace assignment，使用每attempt独立空目录。Codex只读且禁网络，原生沙箱与凭证/宿主权限限制仍适用。

Java `openai` backend允许openai-agents/openai-api，`codex` backend允许codex-cli/codex-app-server；无语言白名单，仍受各包实际实现、角色能力及冻结批准profile约束。Python配置codex-sdk明确拒绝，不替旧冻结任务自动改profile或提供方。新增/删除支持必须同时更新本契约、规范资产、包内副本及主矩阵。

操作授权与资源协调分别处理。只读 API 无写入/host shell；Codex 内部 shell 受原生 sandbox 和主机政策约束。任务、Skill、报告不能提升 shell、网络、测试、发布权限。计划须经理确认；preferredSkills 仅排序；报告不形成第二工作流或自动修复。

## 单次 attempt

1. 校验角色、任务类型和模型指导，调用 start，维持独占租约。
2. 准备工作区、固定输入版本与 Skill 来源，建立整体期限和执行元数据。
3. 执行有界模型与工具，记录调用关联、完整性、取消及副作用。
4. 等待活动工具结束，校验报告及只读工作区，准备结果并按政策发布。
5. 检查所有权并上报 complete。终态不确定时记录未知，由控制平面恢复。
6. 异常时停止新增副作用，等待活动操作终止，在仍拥有有效租约时按原契约上报 fail，然后进行有界清理。

准备、模型、工具、报告和 Git 发布共享整体期限；清理使用单独期限。drain 停止领取并完成在途工作，不等同于中断。取消不等于撤销已经发生的文件或远端修改。

### 控制平面源码基线

TaskExecutionService 的 complete/fail 要求 RUNNING 且租约未过期、属于调用 Worker；complete 的任务更新使用 completeOwned，失败使用 failOwned。现有 complete/fail 不提供已完成请求再次返回成功的幂等接口。响应丢失属于结果未知，Worker 不自行重跑整个任务。

现有 failureCategory 仅有 UNKNOWN、RATE_LIMIT、MODEL_ERROR、OUTPUT_INVALID、TOOL_ERROR、CONFIGURATION。内部取消、权限、证据和资源错误映射到这些值，不新增公开枚举。TS RuntimeFault 的 TIMEOUT 只用于内部取消与工具终态，向控制平面上报时映射为 TOOL_ERROR。

代码任务保持 WorkspaceResult 的 resultRevision、changedFiles、publicationRef。审查/验证必须给出 report；所有 WORKSPACE/REPORT 角色必须给出非空 PASS report。发布与报告校验必须遵循这一既有契约。

## 并行与工具结果

- 独立读取和不同文件操作有界并行，同一文件的读改写全过程排队。
- shell 无可靠文件声明时，与工作区写入协调；Git 收尾必须等待活动工具结束。
- 队列等待可以取消；已经执行的 I/O 没有终止前不释放资源。普通已结束的失败不阻塞后续操作；子进程或 I/O 终止未确认时保留租约，拒绝该协调器的后续操作，主机停止领取并保留工作区待人工对账。
- 路径策略拒绝工作区外路径、Git 内部路径、符号链接及危险硬链接写入；路径检查不等于操作系统沙箱。
- 工具调用与结果按 ID 关联，完成顺序可以不同。嵌套调用经过同一授权、取消与输出边界。
- 参数或响应长度截断造成的调用不完整必须拒绝执行，不因为剩余 JSON 能解析就认为完整。
- UTF-8 行/字节上限包含提示标记。完整结构返回或明确拒绝；搜索部分完成、截断和结果未知分别标记。
- 多区域编辑在原始内容上校验唯一匹配和重叠，再一次写入；格式保留，冲突不产生部分计划修改。

## 上下文与证据

任务约束、角色规则、验收和来源引用必须显式保留。初始输入及历史都设预算；精确 usage、估算与未知区分。预算不足时明确失败，不悄悄删除必需冻结材料。

历史压缩可关闭；启用前核实 SDK 合法历史、模型窗口和预算。摘要是派生材料，不成为人工批准；不拆散工具调用与结果，保留最近细节和可回查来源。

G1：首期接受取消或租约丢失竞争窗口内可能已完成的远端发布，必须记录与对账。发布结果无法确认时标记未知，不承诺自动回滚。

G2：必存执行元数据，访问沿用主机文件权限。新增 attempt 日志保存 30 天，在 Git 工作区外，容量有界；关键证据保存失败不回报成功。用户已明确 G2 覆盖全部 Worker 会话，包括 SDK/CLI 自身及派生线程；完整提示词、源码和会话正文不能因来自既有 SDK 而落盘。既有明确业务 report 和 FD 工件沿用其字段及保留契约，不能增添完整聊天转储。

默认不存完整提示词、源码或隐藏推理；密钥、leaseToken 不进入常规日志或插件环境。关键元数据包括输入版本、调用关联、终态、取消、发布和结果校验。允许字段、拒绝规则与 Runner 前置要求见 [公共输入契约](process-typescript/runtime-core/PUBLIC_CONTRACT.md)。固定版本、权限和本地会话检查在正文送入前生效；未知版本或不安全权限拒绝。用户已批准 FD-016 按 Go 的本地交付范围，生产禁存认证与隔离归 FD-015；这项范围决定不降低生产 G2 要求。Codex 的禁存入口源码已经实现，其他落盘通道及兼容 Gate 见 [完整设计](../docs/worker-codex-no-store-design.md)；真实平台运行效果尚未认证。

## 运行时能力基线

| 运行时 | 当前源码与基线 | 验证边界 |
|---|---|---|
| API TypeScript | Agents 0.20.0 / OpenAI 7.32.0 / Zod 4.6.7；完整终态保护、公共工具与九角色适配；parallelToolCalls=true | 独立离线验收进行中，真实部署端点未运行 |
| Codex TypeScript | 系统已安装 CLI；记录实际版本，不设版本锁；Exec/App Server 直接 stdio 接入，共享任务/结果生命周期 | 内部工具细节明确 unknown；本地交付；生产禁存认证归 FD-015 |
| Go | `AgentRuntime` SPI 显式选择 `codex-cli`、`codex-app-server` 或 `openai-api`；App Server 记录实际 CLI 版本，以真实握手和协议校验判断兼容性，共享期限、证据、workspace 与 Git 生命周期 | App Server lifecycle、隔离、取消及关闭尚未运行验证；OpenAI tool loop 与各 API/CLI 部署行为也需运行验证 |
| Python | SDK 0.157.0 wheel 静态核对；公开 launch_args_override 与 ephemeral/read 回执入口已接入 | 未安装或启动真实 SDK；禁存平台能力未知时拒绝准入 |

这个基线不是端点支持声明。不可观察的能力明确记录 unknown/unsupported，不静默将并行要求改为全局串行。

旧 workers/codex-* 保持参考示例定位，任务契约覆盖三语言 process Worker；demo-deployer 的权限、发布和回滚独立设计。TypeScript Worker 使用包内 runtime-core ESM，Go/Python 按此行为契约适配；TypeScript/Go 使用本地固定 CLI 契约并明确不具有生产禁存认证；Python 保留既有 SDK 会话准入。生产能力须由 FD-015 的实际平台证据认定。

## 输入与拒绝样例

见 [静态输入样例](runtime-contract.examples.json)。旧类型、缺失/越界 skillRef、错误角色能力、缺失必需 workspace、审查 revision 不匹配、空报告或无效末行均拒绝，不调用模型或产生工作区业务修改。计划提议和单步分析不自动补齐流水线，REPAIR_REQUIRED 不自动派生任务。

## 历史 FD-005 验证状态（不代表 FD-016）

静态读取了 API Worker 的 Profile、工具、上下文与生命周期，已安装 SDK 源码、Codex SDK 声明和 TaskExecutionService。首轮源码的一次编译检查通过；首轮独立离线测试 138 个用例执行，78 通过、60 失败。第二轮 261 个用例执行，256 个原始 pass-case、5 个失败；完整场景通过 255/285=89.47%，全 FD 分支尚未测量，两轮均已被 PM 退回。后续源码修复尚待新的独立授权验证，历史编译结果不代表新源码通过。以上样例仍是验收输入，完整 FD 尚未验收，真实端点或模型未调用。SDK 细节见 [SDK 能力基线](SDK_CAPABILITIES.md)，当前证据以 FD 报告与角色交接收据为准。

FD-016 本地平台限制：TypeScript Codex 原生 Windows 进程树能力未核定，启动前拒绝 CODEX_WINDOWS_PROCESS_TREE_UNVERIFIED；系统路径解析保留，Unix/WSL 与 Agents API 按各自准入。App Server 确认终态后调用 thread/unsubscribe 并验证释放回执；未知释放保留工作区，不用删除本地 inbox 冒充服务端清理。

## Optional Step AI preferences

Claim responses may include `aiPreferences: { model: string | null, effort: string | null }`.
These are preferences from the confirmed Plan Step, shared by its execution and review tasks.
Missing or invalid values keep Worker settings. An approved Control Plane profile policy still applies.
A Worker may retry once with its own settings after a clear model/effort rejection before any tool has run.
Do not repeat task side effects, retry ordinary code failures, or loop after the fallback fails.
Record effective settings and fallback reasons in local attempt evidence. Older Workers may ignore this optional field.
See [Worker environment variables](ENVIRONMENT.md) for configuration and runtime-specific limits.
# Versioned per-task configuration

Python Workers may advertise `execution-config-v1` only with the verified execution guard adapter. The optional `executionConfiguration` claim payload contains an immutable reference, its exact UTF-8 JSON content and the selected Worker capability snapshot. New tasks require this version; unsupported Workers cannot claim them. Existing launch-role tasks keep their contracts.

Preparation validates registered Skills/knowledge, output format, exact approved AI profile, backend/resource capability and confirmed permissions/budgets before atomically materializing a claimable task. Each Attempt rechecks the snapshot and constructs a new Agent context. Knowledge remains explicitly scoped; a category, method or Skill never grants permission. Prepared AI parameters do not use legacy Step preference fallback.

OpenAI Agents and Unix/WSL Codex App Server consume the same guarded tools. Versions are recorded as evidence, not allowlisted. Cancellation and UNKNOWN retain the execution guard semantics across configuration switches. See [configuration contract](../docs/EXECUTION-CONFIGURATION.md) and [guard contract](../docs/EXECUTION-GUARD.md). Current FD evidence governs verification; this section is not a runtime certification.
