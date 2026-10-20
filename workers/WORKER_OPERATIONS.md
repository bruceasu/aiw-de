# Worker 公共基础运维说明

**FD-020 当前变更优先：** Python 已删除 Codex SDK，支持 codex-app-server/openai-agents；App Server 为每 attempt 受管 stdio 进程，仅 Unix/WSL 准入，原生 Windows 需 Agents。它的账号/ephemeral/策略回执和本地终止处理已写入源码，但尚未经过真实 CLI、取消竞争或生产禁存认证。下文 Python wheel/SDK版本/SDK线程与FD-005准入记录为历史设计，不是当前启动步骤。迁移环境、审批模式和 AI Profiles 见 [Python README](process-python/README.md#codex-app-server-migration)；各角色语言/后端源码支持以 [主矩阵](README.md) 为准。

关联：FD-005 / FD-016。FD-016 本地交付优先于下文历史生产预览：TypeScript 使用系统 codex-cli/codex-app-server/openai-agents，生产禁存认证移交 FD-015；Python 保留原准入。**当前为实施预览：公共基础与 Codex 全会话禁存入口源码已接入，独立测试和审查未完成；没有实际平台的禁存认证，不能作为已验收发布包部署。**

## 维护范围与角色

生产对齐范围是单包 `process-typescript`（显式选择 `codex-cli`、`codex-app-server` 或 `openai-agents`）、支持 `codex-cli` / `codex-app-server` / `openai-api` AgentRuntime SPI 的 `process-go`、以及 `process-python`。旧 `process-typescript-with-api` 包及启动路径已删除。旧 `codex-*` 为参考示例，`demo-deployer` 保持独立部署权限契约。

九角色、独立 integration-verifier、九字段 Profile 和 resultKind 报告规则见 [公共契约](WORKER_RUNTIME_CONTRACT.md)。[兼容样例](runtime-contract.examples.json) 提供每个角色的最小语义输入、报告和拒绝样例；它们未执行，不能直接当作 HTTP 请求发送，也不授予命令/网络权限。

## 版本与运行时差异

| 运行时 | 已观察/声明版本 | 当前源码适配 | 仍未证明 |
|---|---|---|---|
| API TypeScript | 固定 Agents SDK 0.20.0、OpenAI 7.32.0、Zod 4.6.7 | 公共 ESM 生命周期/工具/上下文/证据；完整响应保护；并行工具开启 | 类型兼容、行为、部署端点和全部禁存路径 |
| Codex TypeScript | 固定系统 CLI 0.162.1，无 Worker-local Codex 包 | 复用公共 ESM；直接 Exec / App Server 启动系统 native CLI；整个内部 Agent 持排他资源 | native 实际二进制认证、禁存与完整进程树终止效果；内层工具授权不可观察 |
| Go | go.mod 声明 Go 1.23、toml 1.6.0 | 同行为契约的期限/证据/Git/进程组适配；整个 CLI 持排他资源 | Codex 全通道禁存与跨平台行为；内部调度仍由 CLI 负责 |
| Python | 固定 wheel 源码 openai-codex 0.157.0；未安装 | 公开 launch_args_override、ephemeral 与 read 回执；同契约期限/证据/Git/心跳 | 配套 CLI 的实际禁存、内层工具/usage、取消终态；宿主审批接受回复被拒绝 |

精确 metadata 来自实际可观察字段；缺失 usage、窗口、SDK 内部策略记为 unknown，不填虚构零值。TS 启动读取并核对实际安装 SDK 版本；native 版本来自绑定 binary 摘要的已验证清单，不能靠声明版本认证。

API 允许不冲突文件工具有界并行；Codex 适配只协调 Worker 外围工作区，不能宣称复刻其内层 ToolHost。排他工作区不等同于禁用 API 模型并行，也不提供操作系统沙箱。

## 公共环境配置

保留现有控制平面地址、Worker ID/token、角色、能力、仓库、工作区、Profile、Skills 和模型配置。新增公共预算是环境项，不进入严格九字段 Profile。

| 环境变量 | 默认值 | 含义 |
|---|---|---|
| AIW_WORKER_ATTEMPT_TIMEOUT_MS | 900000 | 整体 15 分钟，覆盖 start 后准备、上下文、模型、工具、校验和发布；兼容 AIW_AGENT_TASK_TIMEOUT_MS 别名 |
| AIW_WORKER_CLEANUP_TIMEOUT_MS | 10000 | 独立清理/活动资源等待预算；不能因到期释放尚未结束的 I/O |
| AIW_WORKER_GIT_TIMEOUT_MS | 120000 | 单次 Git 上限，仍受整体期限限制 |
| AIW_WORKER_MAX_INPUT_BYTES | 1048576 | 初始输入 UTF-8 总字节预算；完整必需材料不适配时明确失败 |
| AIW_WORKER_EVIDENCE_MAX_BYTES | 4194304 | 单 attempt 元数据总量，超限不回报成功 |
| AIW_WORKER_EVIDENCE_ROOT | fallback 工作区父目录下 `.aidev-attempt-evidence` | 必须在仓库、fallback 工作区和 worktree 根之外，拒绝危险链接路径 |

API 专用项：`AIW_AGENT_TOOL_TIMEOUT_MS=60000`、`AIW_AGENT_MAX_TURNS=40`；沿用 `AIW_AGENT_MAX_OUTPUT_CHARS` 名称，但新输出上限按 UTF-8 字节、包含结构及截断提示计算，默认 24000、配置最小 512。这是行为差异，中文输出预算应按字节重新核对。

`AIW_AGENT_ALLOW_SHELL` 默认 false，只有可写角色与主机明确许可同时成立才开放。`AIW_AGENT_COMPACT_HISTORY` 默认 false；启用必须同时配置 `AIW_AGENT_COMPACT_AFTER_BYTES` 和 `AIW_AGENT_SUMMARY_MAX_TOKENS`，使用当前批准模型、原整体期限，最多两次摘要；不得把估算字节当精确 token 窗口。

统一的 TS Worker 包在 `process-typescript/runtime-core/` 保存运行时源码；源码入口和发布目录都从包内 `runtime-core/` 加载。构建不再从包外 stage，release 从包内复制该目录；尚未运行最终构建或打包。

## Codex 禁存准入与发布

以下为 FD-005 历史生产准入目标，FD-016 TypeScript/Go 本地交付不以此为已完成认证；实际生产检查由 FD-015 承接。Python 当前仍保留这些准入。三个 Codex Worker 原设计新增必需环境项 `AIW_CODEX_SESSION_POLICY`，指向任务工作区外的 `aiw.worker.session-policy.v1` 清单；缺少或不匹配时注册/领取前失败。清单绑定 runtime（codex-typescript/codex-go/codex-python）、SDK、平台、native/launcher 摘要、用户与工程配置来源及独立验证工件。Python native 必须保持配套 0.157.0。

配置来源固定 SHA-256 或明确 absent。用户 CODEX_HOME/config.toml 及存在的工程祖先 .codex/config.toml 必须列入；变更后重新验证。含工程配置的动态 worktree 未在清单中核验时会拒绝，不能仅复制旧清单授权新路径。清单与程序及原始证据不得放在工作区里，模型不能选择入口。主机权限仍是信任边界，并非操作系统沙箱。

Codex TypeScript、Go 和 Python Worker 均直接通过各自 SDK/CLI 启动已安装的 Codex 原生程序，不再编译、携带或部署单独的 launcher。各 Worker 仍要求外部部署策略清单及独立会话验证证据，未验证的运行时组合继续拒绝启动。

示例 status 为 unverified，controls/rawEvidence 为空，故意不能启用。真实部署须核验 rollout、history、diagnostics、trace、memory、state/cache、派生会话、hooks/plugins、credentials、termination 十项，绑定原始证据。更新 binary、SDK、配置或平台后重做验证，不手工将示例改成 verified 冒充行为证据。

API 在 SDK 导入前关闭正文调试与 tracing，客户端 logLevel off；Responses store false 不证明服务商零留存。Codex stderr 不复制到日志，stdout 只交 SDK 内存处理，部署进程管理器不得转储管道正文。移除 launcher 后，各 Codex SDK/CLI 自身的参数处理与 app-server RPC 行为由对应 Worker 接口决定，不再经过统一的 launcher 白名单。

## aiw 查询工具

Agents 后端默认没有 aiw 工具。启用需 `AIW_AGENT_AIW_TOOL_CATALOG` 指向工作区外固定目录的 catalog；空模板见 [示例](process-typescript/aiw-tools.example.json)。支持 Skills list/discover 和固定 repository 的 GitHub get-issue；不开放任务推进、审批、写入或任意 CLI 参数。

每个 entry 固定 `id`、绝对 `executable`/`pluginPath`、二者 SHA-256、`outputSchema`、`timeoutMs`、`maxOutputBytes`、`networkAllowed`，GitHub 另固定 `repository`。解释器和插件每次查询重新校验；catalog、插件、解释器不能由任务工作区替换。Python 直接执行固定插件入口，避免 aiw 的 PATH/动态发现改变身份。

本机静态盘点如下，未运行查询。解释器摘要仍应由部署者针对实际绝对路径取得，不能填虚构值。

| 安装入口 | 观察到的 SHA-256 |
|---|---|
| C:/green/aiw/aiw.exe | `3b1b53996c5875d4894581a2c44b1282840daf1ec80b0a8442c2ed294ac00e06` |
| C:/green/aiw/plugins/aiw-skills.py | `20d252aad60800d9061e702e5ab822a16ffcfe6d5d582b205b7980c58256b568` |
| C:/green/aiw/plugins/aiw-github.py | `9be9729064d9a86d6f14b7e24e209724af66f62105a71de167cdfb6b2e52c334` |

Skills 输出要求 `ok/action/skills/issues`；GitHub 输出要求 issue number/state/title/html_url 与固定仓库相符。身份不符、缺失、超时、非法/超限 JSON、缺少网络授权均明确失败。GitHub 的 `networkAllowed=true` 必须来自主机既有授权，不能从样例或模型输出推导。插件只收到必要环境；仅已授权 GitHub 查询可获得 GITHUB_TOKEN，不能获得 leaseToken/模型 key。

## 终止、证据与对账

第一次 SIGINT/SIGTERM 停止领取并 drain，在途任务保留原整体期限；第二次强制取消。lease 丢失停止新增副作用。只有实际操作终止后才能释放资源并清理拥有的 worktree。

SDK 取消、逃逸子进程或清理终态无法确认时保留工作区并记录 unknown，不报完成。Python 未确认结束的 SDK 线程会保留排他资源并停止 slot；处理方式是检查实际进程后由操作者恢复，不能凭超时重新派发同一工作区。

新增日志仅记录白名单元数据，终态目录带 `aiw.worker.attempt.v1` 所有权标记，按 30 天清理。不会自动删除不带标记、非终态或其他用户目录。必需证据在非幂等 complete 前保存；complete/fail 响应未知不重跑业务，由控制平面恢复。

push 与取消竞争窗口按 G1 处理：记录目标 publicationRef、resultRevision 和 completed/unknown；据此核对远端引用。回滚包不撤销远端提交，不能自动把 unknown 写成 failed/no-write。

## 升级与回滚

生产升级须先取得 [全会话禁存设计](../docs/worker-codex-no-store-design.md) 的实际版本、平台与全部数据通道证据。源码准入已实现，真实验证未完成，旧包不能自动视为满足新 G2。

完成独立验收后才按新 Worker 身份从只读角色试用，再到编码/集成/修复；升级先 drain，保存已核验包、配置和版本摘要。回滚选择必须同样满足 G2，不能借恢复可用性重新开启会话正文持久化。未完成 attempt、保留工作区及远端副作用分别由控制平面/操作者对账。

## 本次检查范围

`node scripts/compile-worker-runtime.mjs` 是准备好的 compile-only 命令：TS 在内存做语法转译，JS `--check`，Python 在内存 compile，Go 在临时目录 compile 并删除产物，禁用 Go 自动下载/工具链获取。该命令不是 TS 类型检查，不导入 Python SDK，不证明禁存、租约、并发或真实端点行为。实际运行结果记录在 FD Verification，不能把本节当作已通过证据。
