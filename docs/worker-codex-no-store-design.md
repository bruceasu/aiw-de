# Worker Codex 全会话禁存设计

关联：ISSUE-001 / FD-005；设计日期：2026-10-05。

**状态：用户已批准固定原生启动方案与 Python 固定包核对；禁存接入源码已实施，尚未完成独立运行验证。**

用户已明确选择「G2 覆盖全部会话；先补充 Codex 禁存设计」。该决定覆盖全部 process Worker 创建的会话，不把 Codex 自身持久化排除在外。以下补充纳入原 FD 的 1.2、2.4、5.1、5.2，十二个工作项和九个角色的交付范围保持完整。

## 1. 目标与数据边界

所有角色、slot、attempt、重试、压缩、派生线程和内部子 Agent，都必须经过同一会话数据政策。不能通过更换 SDK、后台子进程、诊断日志、插件或崩溃恢复绕过。

| 数据 | 处理规则 |
|---|---|
| task/attempt/role、调用 ID、来源摘要、版本、时间、usage、终态、结果引用 | 必存有界元数据；新增 attempt 日志在工作区外保存 30 天 |
| 完整提示词、任务输入副本、源码读取内容、工具参数/输出正文、完整 diff、模型会话正文、隐藏推理 | 会话处理只在有界内存中进行；默认不落盘、不导出、不缓存为可恢复会话 |
| 密钥、leaseToken、认证头、携带凭据的原始错误 | 不进入会话、元数据、stdout/stderr、插件环境或测试证据 |
| 经任务授权修改的仓库源码和工程工件 | 沿用任务的工作区/发布契约；禁止为了恢复聊天另外复制完整源码或输入 |
| 控制平面最终 report、需求/FD/审查工件 | 沿用现有显式业务工件契约，字段校验后上报；不能借 report 增添完整聊天转储 |

“全部会话”指产品 Worker 创建及派生的会话；本设计不能控制承载本次开发对话的宿主，也不会把删除宿主或其他用户历史当作实现方式。上线前已有 Worker 会话文件的处置另见第 8 节。

必须在持久化发生前阻止。把 `CODEX_HOME` 换到普通临时目录、结束后删除 JSONL、加密保存、缩短保留期或清空 `history.jsonl`，均不能满足正文禁存。内存可跨同一 attempt 的回合使用；进程重启后从控制平面新 attempt 恢复，不用磁盘 session 恢复。

## 2. 已取得的依据与不能推导的结论

| 依据 | 已确认内容 | 尚未确认内容 |
|---|---|---|
| 本机 `codex exec --help`；全局 npm 包声明 0.160.0 | `--ephemeral` 表示不向磁盘保存 session 文件 | 声明版本不等于实际二进制版本/摘要认证，也没有证明全部其他落盘通道关闭 |
| 本地 TypeScript SDK 0.155.1 的 `dist/index.js`、`index.d.ts` | SDK spawn 参数起始为 `exec --experimental-json`；公开配置有 `codexPathOverride`、`env`、`config`，没有 ephemeral 选项；SDK 注释明确会话存于 `~/.codex/sessions` | 不能假设传入未知配置 `ephemeral=true` 有效，不能假设其他版本已支持 |
| 官方 CLI 文档 | `--ephemeral` 关闭 session rollout 文件 | 没有给出整个主机无正文写入的保证。[官方命令参考](https://learn.chatgpt.com/docs/developer-commands?surface=cli) |
| 官方配置文档 | history、OTEL、memory、反馈、SQLite 状态和 hooks 是不同通道 | `history.persistence=none` 只针对 history 文件；配置键及合并优先级须与实际 CLI 版本逐一核对。[官方配置参考](https://learn.chatgpt.com/docs/config-file/config-reference) |
| 官方 App Server 文档 | 有内存型 ephemeral fork，thread 返回 ephemeral 状态；存储线程可 resume/archive | 当前页面没有证明 Python 0.157.0 的 `thread_start` 参数、原始返回对象和子线程传播。[官方 App Server 文档](https://learn.chatgpt.com/docs/app-server) |
| Python 固定 0.157.0 wheel 源码 | `CodexConfig.launch_args_override` 为公开入口；`Codex.thread_start(ephemeral=True)` 传递 generated `ThreadStartParams.ephemeral`；`Thread.read(include_turns=False).thread` 提供 ephemeral/path；METADATA 固定 `openai-codex-cli-bin==0.157.0` | 未安装、导入或执行 SDK/CLI，不能借用全局 CLI 0.160.0 的能力认证。[官方 SDK 文档](https://learn.chatgpt.com/docs/codex-sdk) |

没有运行模型、SDK 会话、线程创建、恢复、插件查询或文件系统禁存测试。官方现行文档用于设计依据，不能替代固定 SDK/CLI 组合的源码和运行证据。

## 3. 公共政策入口

新增公共 `SessionPolicy`，位于运行时启动与会话创建之间；它不成为现有八字段 Profile 的新增必需字段。

```text
SessionPolicy
  mode: metadata-only
  persistedConversation: forbidden
  resumeFromDisk: forbidden
  promptDelivery: stdin-or-sdk-memory
  requiredCapabilities: ephemeral, no-body-log, no-body-trace
  onUnknown: reject-before-register-and-claim
```

启动先核对 SDK、实际 native CLI、适配器、隐私配置和验证证据的固定组合。未知版本、摘要不符、缺少禁存选项、managed config 强制开启冲突项或缺少必要能力，一律报告 `CONFIGURATION` 并停止注册/领取。不得用“出现持久化后再取消”替代准入。

每次会话创建前重新核对入口与配置摘要，避免启动后替换可执行文件。元数据仅记录政策版本、机制、SDK/CLI/适配器摘要和核验状态；不保存完整配置，因为配置可能包含密钥。

独立验证工件绑定明确平台、SDK 版本、CLI 二进制摘要、适配器摘要和政策配置摘要。它是部署能力证据，不是让操作者勾选“安全”的开关。没有证据的运行时不能自称已满足 G2。

## 4. TypeScript SDK 接入方案

### 方案比较

| 方案 | 兼容与维护代价 | 结论 |
|---|---|---|
| SDK 升级到有公开禁存选项的版本 | 需先找到真实版本与源码；依赖和 CLI 配套可能一起变化，现有批准没有覆盖 | 后续有明确来源时另行比较，不自动升级 |
| 修改 node_modules、访问 SDK 私有字段或替换整个执行循环 | 安装可覆盖改动，未公开边界难以稳定复核，可能改变 SDK 行为 | 不采用 |
| 固定原生启动适配器，经公开 `codexPathOverride` 接入 | 保留 SDK 0.155.1、事件循环、线程参数和模型选择；增加一个需发布的原生文件和进程管理责任 | **用户已批准采用；源码已实施，待独立验收** |

推荐适配器只负责启动治理，不承担 Agent、提示词组装、工具循环或模型路由。仍由 SDK 执行 `startThread` / `runStreamed`，最终启动原先解析到的已安装 Codex 原生程序，保留其 companion PATH。

调用链：`Worker → SDK 0.155.1 → 固定适配器 → 原先安装的 native Codex exec --ephemeral`。

适配器要求：

1. Windows 发布为原生 `.exe`；不能将 `.cmd`/`.ps1` 直接传给 SDK spawn，也不启用 shell。其他平台同样提供本平台原生文件。
2. native 目标由宿主解析成固定绝对路径及摘要，不能由模型、任务或工作区选择。适配器与 native 文件不得位于任务可写目录。启动子进程前移除适配器内部变量及全部 CP/lease 凭据。
3. 仅允许首次 `exec`。拒绝 `resume`、`--last`、`--output-last-message`、未知保存选项和正文命令行参数；SDK 提示词由 stdin 转发，不创建 prompt/schema/转录临时文件。
4. 固定注入 `--ephemeral` 及经过版本验证的禁存配置。SDK 仍传入 model、effort、service tier、sandbox、网络和审批政策；隐私字段不能被后续 thread 参数覆盖，冲突则拒绝。
5. stdout 仅以管道交给 SDK 内存处理；stderr 和退出错误以固定错误码呈现，不复制原始文本到服务日志。若协议不兼容，失败前不能发送提示词。
6. 取消管理覆盖适配器和 native 工具进程。Windows 设计采用受控 Job Object 的关闭终止机制；无法纳入 job 时拒绝启动。POSIX 采用受控进程组，并独立核对进程逃逸/父进程终止行为。未验证平台不发布能力声明。
7. 适配器退出前确认活动进程终态；未知时沿用 FD 的资源不确定处理，保留工作区，不回报成功。不把父进程退出视为整个进程树结束。

工程影响：增加原生适配器源码、编译/发布携带规则、版本摘要和独立测试；两个 TS Worker 的 ESM 公共包契约保持现有路径。此方案不要求安装或替换 SDK，但不能只改一行配置就宣称完成。

## 5. Go、Python 和 API 的接入

| 运行时 | 实施要求 | 准入判断 |
|---|---|---|
| Go CLI | 命令数组固定带 `--ephemeral`；提示词由当前 argv 方式改为 stdin；使用相同禁存配置和进程终态政策 | 实际 CLI 版本/摘要及平台能力通过后才接收任务 |
| Python 0.157.0 | 先核对固定包的 `thread_start`、RPC 参数映射、返回的 ephemeral 字段及其固定 CLI runtime；存在公开能力时显式设 true 并在送入提示词前检查结果 | 不根据函数接受 `**kwargs` 推导支持；没有公开能力/回执则阻止此运行时，提出单独兼容决定 |
| API TypeScript | Runner tracing 关闭、Responses `store:false`，不创建文件型 SDK session、Conversation 或正文 exporter；摘要和来源仅在 attempt 内存保留 | 核对实际请求设置和所有调用路径，包括摘要请求 |

用户已允许只下载固定包核对源码。已执行 `python -m pip download --no-deps --only-binary=:all: --no-cache-dir --dest <FD-005 专属临时目录> openai-codex==0.157.0`，只读取 wheel 内的源码，没有安装、导入、下载依赖或启动 SDK。wheel SHA-256 为 `e97c61d4fda9540d3db309e27bcf46cae6e12293c5b9d74e98fe3d285f8477e3`。

公开接口位于 `openai_codex/api.py`：`Codex.__init__(config)`、`Codex.thread_start(... ephemeral: bool | None = None ...)`、`Thread.read(include_turns=False)`、`Thread.turn(input)`；配置位于 `client.py`，线程字段位于 `generated/v2_all.py`。`client.py` 的默认审批 handler 会接受命令/文件审批，公开 `Codex` 构造器未暴露替换 handler 的参数。适配器因此拒绝接受类审批回复，遇到需要宿主审批的操作时终止当前运行，不能访问私有字段或冒充人工授权；native 既有审批政策仍由原参数传递。

远端 Responses 的 `store:false` 约束应用会话存储请求；服务方留存政策不能由 Worker 的本地禁存选项证明。[Responses 数据控制说明](https://developers.openai.com/api/docs/guides/your-data) 的具体部署结论仍需按所用账号/端点另行确认，不把本地 ephemeral 描述为供应商零留存。

## 6. 会话文件以外的通道

下列项目属于同一准入政策，每个固定版本都需要来源与覆盖证据：

- history 文件和 rollout/session 文件分别关闭；禁止 disk resume、持久化 fork 和聊天转储输出。
- 关闭正文 trace、OTEL 导出及 raw prompt 记录；清除子进程中的调试/遥测导出环境。官方键候选包括 `otel.log_user_prompt=false`、各 exporter 的 `none`，不假定老版本识别所有新键。
- 关闭持久化 memory 提取及其输入存储；候选为 `memories.generate_memories=false` / `use_memories=false`。SQLite 状态、job、preview、shell snapshot、临时工具文件和缓存逐项确认，不能只扫描 sessions 目录。
- Codex native 派生 Agent、fork 和压缩必须继承政策，或逐个创建为 ephemeral。无法证明传播的配置不得启用；如果只能关闭该能力，先形成能力差异决定，不能把缺口写成“全会话通过”。API 模型工具并行政策继续为 true。
- hooks、MCP、插件和自定义 provider 的日志/缓存也需要固定配置。未核验的正文消费者不能自动加载；禁存准入不能扩大工具授权。
- SDK 错误、协议错误和 stdout 中可能含正文；服务日志只保留代码、阶段和关联 ID。console、诊断收集和进程管理器 stdout/stderr 捕获都在检查范围内。
- 不复制现有认证文件、不在新目录缓存密钥。认证沿用主机已批准机制，但不能进入会话/新增证据；如路径需要新建凭据文件或刷新落盘，须先核对兼容和政策，不能用禁存配置静默改变登录方式。

`RUST_LOG=off`、禁用 exporter 等只是控制项，仍需对应版本证明。缓存、外部工具或 managed hook 无法满足政策时，在领取前明确拒绝。

## 7. 失败、取消和重启

正常完成、工具失败、长度终止、模型失败、租约丢失、整体/局部超时、显式取消、drain 后强制终止及进程崩溃，都不能留下会话正文。终止后再删除只能用于普通工作区收尾，不能作为禁存保障。

会话 ID 仅作为运行时关联元数据，不作为 disk resume 凭证。取消后停止新增调用，等待受控活动资源终态，再清理工作区。资源未知和远端发布未知分别记录；保留工作区用于 G1 对账不能成为保存完整会话的理由。

关键元数据落盘失败时阻止成功；在非幂等 complete 前先保存必需证据。complete 响应不确定由控制平面恢复，不重放整段 Agent 会话，也不重试整个业务执行。

## 8. 迁移、发布和回滚

1. 补完固定 SDK/CLI 组合的能力证据、原生适配器和 Python 接口核对；编译检查与禁存行为验证分别记录。
2. 新 Worker 先通过启动准入，再按只读角色、可写角色分批接入。旧 Profile 八字段不增加必需字段。
3. 升级前 drain 旧实例；不得让旧实例在新 G2 生效后继续创建可持久化会话。角色名或旧配置不提供政策豁免。
4. 列出本产品先前创建的 sessions、history、logs、memory/cache 归属及容量。默认只形成元数据清单；历史正文清除需要明确范围与授权，不能递归删除共享 `~/.codex` 或其他用户会话。
5. 回滚仅允许回到同样满足 G2 的组合。可回退公共功能或停止派发，不能回退到持久化聊天的旧包来恢复可用性。Git 已发布副作用继续按 G1 单独对账。

改变系统 crash dump、swap/pagefile、主机备份或供应商留存属于部署控制，不由 SDK flag 自动完成。若这些介质也需要满足正文禁存，在对应主机策略取得证据前不能作覆盖声明；本 FD 不擅自修改系统设置。

## 9. 独立验收清单

以下是待执行场景，不是测试结果；由 FD 的独立 Tester 编写，命令另行审查授权。

| 编号 | 场景 | 必须观察到的结果 |
|---|---|---|
| NS01 | TS SDK → 假 native 程序，Go 入口，Python 假 RPC | ephemeral 被强制传递；提示词仅在管道/内存；模型等既有参数保持一致 |
| NS02 | 版本/摘要/配置不符，禁存能力未知，managed policy 冲突 | 注册/领取/发送提示词之前失败 |
| NS03 | resume、旧 session、持久化 fork、输出文件参数 | 拒绝；不降级成普通会话 |
| NS04 | 九角色、两个并发 slot、多 attempt、控制平面重试 | 每次会话均覆盖；只共享批准的元数据，不串话或复用 disk session |
| NS05 | 正常、工具失败、模型失败、超时、取消、租约丢失 | 文件写入监测及磁盘检查均无正文；元数据保持合法终态或明确未知 |
| NS06 | 父进程强制退出、native/tool 子进程存活竞争 | 禁存依靠写入前控制；进程树未知不回报成功、不提前清理工作区 |
| NS07 | memory、SQLite、shell snapshot、hook/MCP、trace、调试 stderr | 无正文写入/导出；缺少覆盖证据时拒绝准入 |
| NS08 | 原生子 Agent、fork、自动压缩 | 全部会话继承禁存，或明确拒绝未核验能力 |
| NS09 | 元数据满额/写入失败与 CP complete 响应丢失 | 不伪造成功；保留可对账元数据，不重跑业务 |
| NS10 | 升级与回滚到不满足 G2 的旧组合 | 拒绝旧组合恢复派发；旧共享用户目录不被自动删除 |

测试用合成标记，不用真实源码、密钥或生产提示词。仅在运行结束后检查文件不足以证明“从未落盘”，必须监测过程中的文件写入和诊断/导出通道。离线假运行时证明适配契约；真实 Codex 禁存证明还需受控本地运行与平台证据，任何模型/网络调用均另需授权。

## 10. 纳入 FD 的实施顺序与当前状态

1. **1.2 / NS-D1**：核对固定版本、Python 0.157.0 源码、全通道清单与认证兼容；形成真实能力矩阵。
2. **2.4 / NS-D2**：实现 SessionPolicy 准入、元数据及配置摘要；所有会话创建都经过该入口。
3. **5.1 / NS-D3**：实现原生启动适配器、TS SDK 接入、跨平台进程治理和发布携带规则。
4. **5.2 / NS-D4**：Go 参数/stdin 接入；按已确认公开接口实施 Python；补齐运维和回滚。
5. **全 FD / NS-D5**：执行一次 compile-only，再进行独立 Tester、PM 和 Reviewer；未通过不发布完整实现声明。

用户于 2026-10-05 回答「采用推荐方案，继续实施」及「允许只下载固定包核对源码」，上述设计与下载 Gate 已解决。该授权没有扩大到 SDK 升级、Python 安装、模型或业务服务测试。

历史实现曾包含 `workers/codex-session-launcher/`，现已移除；TS/Go/Python Worker 直接通过各自 SDK/CLI 启动 Codex。原适配器的参数白名单、统一 RPC gate、输出限制及 launcher 进程控制不再生效。API 在 SDK 导入前关闭正文调试日志与 tracing，并显式设置客户端 logLevel off、Responses store false。

部署清单使用 `aiw.worker.session-policy.v1`，证据使用 `aiw.worker.session-validation.v1`，绑定平台、运行时/SDK、native 摘要、配置来源清单及原始验证工件。配置来源须逐个固定摘要或明确 absent；现有用户配置与任务目录祖先中的 `.codex/config.toml` 未声明或变更时拒绝执行。清单与验证工件均须位于任务可写工作区外，沿用主机权限。样例标为 unverified，不会启用运行时。

当前没有实际 Codex 平台的已验证禁存清单；编译与离线假运行时只能证明接入契约。真实部署仍须十项通道与终止证据，未通过的组合不能注册。FD 继续覆盖全部工作项并保留独立 Tester、PM 和 Reviewer。
