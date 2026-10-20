# Worker 环境变量

本文按实际读取配置的代码整理，集中说明 Worker 的设置入口、默认值和运行时差异。
主要运行入口是 [TypeScript Worker](process-typescript/README.md)；Go/Python 的差异列在后面。
变量在 **Worker 进程启动时**读取。修改后需停止并重新启动对应 Worker，不能只刷新页面。

FD-020 更新：Python 仅支持 `codex-app-server`（新默认）与 `openai-agents`，删除 SDK 路径；显式 `codex-sdk`、SDK `auto-review` 审批和 `full-access` 沙箱配置会拒绝启动。App Server 使用 `AIW_CODEX_PATH` 指定已安装 CLI、`AIW_CODEX_APPROVAL_MODE=deny-all`（或 `never`）、只读/workspace-write 沙箱；仅 Unix/WSL 准入，native Windows 使用 Agents 路径。旧环境/AI Profiles/冻结请求不会自动迁移。详见 [Python 迁移](process-python/README.md#codex-app-server-migration) 和 [支持矩阵](README.md)。本节优先于下文历史 SDK 差异记录。

## 设置方法

TypeScript Worker 的完整起点见 [worker.env.example](process-typescript/worker.env.example)。
复制到自己的配置目录，修改后用 Node 的 `--env-file` 加载（需要支持该参数的 Node 版本）：

```sh
cd workers/process-typescript
node --env-file=/absolute/path/worker.env dist/index.js
```

通过管理器启动角色时，同样在启动管理器时加载；子进程会继承配置：

```sh
node --env-file=/absolute/path/worker.env scripts/manage-workers.mjs start all codex-app-server
```

这些命令需要事先生成 `dist/`。Worker 和管理器不会自动读取任意 `.env` 文件。
已导出的进程环境变量优先于 Node `--env-file` 中的同名值。凭据文件应放在仓库外，限制文件读取权限。
Go/Python 可通过 shell、服务管理器或容器注入环境；它们的入口不自动读取本示例文件。

## Step 的模型与强度

Plan 编辑页的每个 Step 可以独立填写 `model`、`effort`，两项都可留空。
它们保存在 Plan 的 JSON 规格中，不需要新增数据库列；旧 Plan 没有这些字段时继续使用原有行为。
配置应用于该 Step 的执行、修复和独立评审，重新评审也会读取；不会应用于单独的恢复授权决策任务。

- 留空的字段由 Worker 自行选择，通常采用该运行时环境变量或 CLI/SDK 的默认值。
- 已配置 Control Plane `aidev.ai` 模型策略时，优先匹配允许的配置；不匹配时回到 Worker 原有选择路径。Step 不扩大模型权限。
- 没有模型策略时，优先使用 Step 填写的字段；格式错误或运行时不支持的强度回退到 Worker 对应字段。
- 模型服务明确拒绝 Step 参数，并且尚未开始工具执行时，回退到 Worker 自身配置再尝试一次。回退也失败则正常报告失败，不循环尝试。
- 代码、工具、权限、网络错误不触发这类模型回退；已经执行工具的失败不会通过重新运行整项任务来回退模型。

TypeScript/Go 记录 `ai-settings` 和 `ai-settings-fallback` 本地 attempt evidence；Python 同名记录包含有效模型与强度。
Control Plane 的 start profile 记录初始选择；回退后的运行选择以本地 evidence 为准。
没有填写 Step 配置时，不额外增加参数失败重试。Python App Server 仅在创建 thread/turn 明确拒绝参数，或终态失败且没有 item 执行证据时允许沿用既有的一次参数回退；工具开始后不回退。

常用 Codex 强度：`none`、`minimal`、`low`、`medium`、`high`、`xhigh`；Agents/API 模式支持的范围更窄，
最终可用性取决于模型及运行时，拒绝时按上面的规则处理。模型名不固定在 UI 下拉列表，也不按 CLI 小版本限制 Worker 启动。

## 通用连接、角色与工作区

下表默认值以 TypeScript Worker 为准。除特别说明外，时间单位为毫秒，数值预算须为正整数。

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| `AIW_AGENT_RUNTIME` | 必填 | `codex-cli`、`codex-app-server`、`openai-agents` |
| `AIW_PROCESS_ROLE` | `requirements` | 单个进程的角色；恢复决策角色为 `recovery-decider` |
| `AIW_CONTROL_PLANE_URL` | `http://127.0.0.1:8080` | Control Plane 地址 |
| `AIW_WORKER_ID` | 按运行时及角色生成 | CLI：`process-exec-<role>-1`；App Server：`process-<role>-1`；Agents：`process-api-<role>-1`；每个进程应唯一 |
| `AIW_WORKER_TOKEN` | 空 | Worker Protocol Bearer token |
| `AIW_WORKER_MAX_CONCURRENCY` | `1` | 同一 Worker 的并发领取槽位 |
| `AIW_WORKER_CAPABILITIES` | 空 | 逗号分隔的技术能力，如 `typescript,node`；角色和仓库能力由 Worker 添加 |
| `AIW_REPOSITORY_REF` | 空 | 已注册的工作区引用；必须与 ROOT 一起设置 |
| `AIW_REPOSITORY_ROOT` | 空 | 对应本机工作区目录；必须与 REF 一起设置 |
| `AIW_WORKSPACE` | ROOT 或当前目录 | 无显式分配时的工作目录 |
| `AIW_WORKTREE_ROOT` | 工作区旁 `.aidev-worktrees` | Git 隔离工作树目录 |
| `AIW_PROCESS_SKILLS_ROOT` | 内置 skills 目录 | 可信角色技能所在根目录 |
| `AIW_WORKER_POLL_MS` | `1000` | 无任务时的轮询间隔 |
| `AIW_WORKER_HEARTBEAT_MS` | `20000` | Worker 与任务心跳间隔 |
| `AIW_WORKER_HTTP_TIMEOUT_MS` | `15000` | Control Plane 请求超时 |
| `AIW_GIT_PUBLISH_REMOTE` | 禁止设置 | 非空会拒绝启动，Worker 不执行 Git push |

TypeScript 管理器额外读取 `AIW_WORKER_ID_PREFIX`（默认 `test-ts`），生成 `<prefix>-<role>-1`；
管理器会覆盖单个 `AIW_WORKER_ID`。技术能力使用 `AIW_WORKER_CAPABILITIES_<ROLE>`，例如
`AIW_WORKER_CAPABILITIES_CODER`、`AIW_WORKER_CAPABILITIES_STAGE_REVIEWER`、
`AIW_WORKER_CAPABILITIES_RECOVERY_DECIDER`（角色中的 `-` 换为 `_`）。
管理器按角色变量设置子进程的 `AIW_WORKER_CAPABILITIES`，未设置时为空。

## TypeScript Codex 运行时

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| `AIW_CODEX_PATH` | 从 PATH 查找系统 CLI | 原生 Codex 可执行文件路径；不使用随 Worker 捆绑的版本 |
| `AIW_CODEX_MODEL` | 空，采用 CLI 自己的配置 | 未被 Step/模型策略选择覆盖时的模型 |
| `AIW_CODEX_REASONING_EFFORT` | `medium` | Worker 默认推理强度 |
| `AIW_CODEX_APPROVAL_POLICY` | `never` | 无人值守；其他值不被支持 |
| `AIW_CODEX_SANDBOX` | `danger-full-access` | 可选 `read-only`、`workspace-write`；只读角色拒绝 workspace-write |
| `AIW_CODEX_NETWORK_ACCESS` | `false` | 控制沙箱模式网络；danger-full-access 使用宿主网络 |
| `AIW_CODEX_MAX_OUTPUT_BYTES` | `4194304` | Codex 单帧和最终输出预算，字节 |
| `CODEX_HOME` | CLI 自身默认 | CLI 登录和配置目录；显式设置时必须在任务工作区之外 |
| `OPENAI_API_KEY` / `CODEX_API_KEY` | 空 | 可传递给 Codex 的 API 凭据；也可使用已有 CLI 登录 |

当前 TypeScript 原生 Windows Codex 模式受进程树终止确认能力限制，使用 Unix/WSL 或 Agents 模式。
这与 CLI 版本无关。

## TypeScript OpenAI Agents 运行时

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| `OPENAI_API_KEY` | 必填 | 模型 API 凭据 |
| `OPENAI_BASE_URL` | SDK 默认 | 可选 API 地址 |
| `AIW_OPENAI_MODEL` | `gpt-4.1` | Worker 默认模型 |
| `AIW_OPENAI_REASONING_EFFORT` | 空 | 不显式指定强度；非空支持 minimal/low/medium/high |
| `AIW_OPENAI_TIMEOUT_MS` | `120000` | 单次 API 请求超时 |
| `AIW_AGENT_MAX_TURNS` | `40` | 每次任务的 Agent 回合上限 |
| `AIW_AGENT_TOOL_TIMEOUT_MS` | `60000` | 单个工具执行超时 |
| `AIW_AGENT_MAX_OUTPUT_CHARS` | `24000` | 名称保留兼容，实际按 UTF-8 字节限制输出 |
| `AIW_AGENT_ALLOW_SHELL` | `false` | 当前宿主 shell 禁止启用 |
| `AIW_AGENT_AIW_TOOL_CATALOG` | 空 | 可选固定外部 aiw 工具目录文件，见 [示例](process-typescript/aiw-tools.example.json) |
| `AIW_AGENT_COMPACT_HISTORY` | `false` | 是否压缩可见历史 |
| `AIW_AGENT_COMPACT_AFTER_BYTES` | 启用压缩时必填 | 压缩前输入字节阈值 |
| `AIW_AGENT_SUMMARY_MAX_TOKENS` | 启用压缩时必填 | 摘要 token 预算 |

运行入口强制关闭模型及工具数据日志和 tracing：`OPENAI_AGENTS_DONT_LOG_MODEL_DATA=1`、
`OPENAI_AGENTS_DONT_LOG_TOOL_DATA=1`、`OPENAI_AGENTS_DISABLE_TRACING=1`，并清空 `DEBUG`。
这些属于进程保护设置，不应依靠外部环境打开。

## TypeScript 时间预算、证据与缓存

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| `AIW_WORKER_ATTEMPT_TIMEOUT_MS` | `900000` | 耗时提示阈值；不会取消执行；未设置时兼容 `AIW_AGENT_TASK_TIMEOUT_MS` |
| `AIW_WORKER_ATTEMPT_HARD_TIMEOUT_MS` | `0` | 硬取消期限，0 表示关闭 |
| `AIW_WORKER_CLEANUP_TIMEOUT_MS` | `10000` | 清理和确认资源终止的时间预算 |
| `AIW_WORKER_GIT_TIMEOUT_MS` | `120000` | Git 命令超时 |
| `AIW_WORKER_MAX_INPUT_BYTES` | `1048576` | 上下文输入预算 |
| `AIW_WORKER_EVIDENCE_MAX_BYTES` | `4194304` | 每次 attempt 本地证据预算 |
| `AIW_WORKER_EVIDENCE_ROOT` | 工作区旁 `.aidev-attempt-evidence` | 持久证据目录，必须位于任务工作区之外 |
| `AIW_WORKER_CACHE_ROOT` | `/mnt/temp/cache` | 按 Worker ID 隔离的缓存及临时文件根目录，必须为绝对路径 |

Worker 自动设置工具缓存变量：`TMPDIR`、`TMP`、`TEMP`、`XDG_CACHE_HOME`、`npm_config_cache`、
`NPM_CONFIG_CACHE`、`GOCACHE`、`GOMODCACHE`、`GOTMPDIR`、`PIP_CACHE_DIR`，并追加
`JAVA_TOOL_OPTIONS` 中的临时目录和 Maven 本地仓库参数。通常只设置 `AIW_WORKER_CACHE_ROOT`。
`AIW_TYPESCRIPT_COMPILER` 是内存语法编译脚本的可选模块路径，并非运行中的 Worker 配置。

## Go / Python 的差异

共用变量名不代表所有默认值和单位相同。下面列出与上表不同或只在对应入口使用的配置。
完整入口说明见 [Go README](process-go/README.md)、[Python README](process-python/README.md)。

| 项目 | Go | Python |
| --- | --- | --- |
| `AIW_AGENT_RUNTIME` | codex-cli / codex-app-server / openai-api | codex-app-server（默认）/ openai-agents |
| 角色入口 | `AIW_PROCESS_PROFILE` 必填，为 TOML 文件路径 | `AIW_PROCESS_ROLE`，默认 requirements |
| `AIW_PROCESS_INCLUDE_REFERENCES` | 默认 true | 技能包读取规则由入口决定 |
| `AIW_CODEX_REASONING_EFFORT` | high | high |
| `AIW_CODEX_SANDBOX` | 按角色 read-only 或 workspace-write | 默认 workspace-write，只读角色由 runtime 限定 |
| Codex 审批 | `AIW_CODEX_APPROVAL_POLICY=never` | `AIW_CODEX_APPROVAL_MODE=auto-review` |
| Codex 网络 | 不允许启用 | 不允许启用 |
| 心跳/轮询/HTTP | 与上表相同的 `_MS` | `AIW_WORKER_HEARTBEAT_SECONDS=20`、`AIW_WORKER_POLL_SECONDS=1`、`AIW_WORKER_HTTP_TIMEOUT_SECONDS=15`，单位秒 |
| API 输出预算 | `AIW_AGENT_MAX_OUTPUT_BYTES=24000` | `AIW_AGENT_MAX_OUTPUT_CHARS=24000`，按字节 |
| API 默认模型 | gpt-4.1 | Agents runtime 默认 gpt-4.1 |

Go Codex 认证支持 `AIW_CODEX_AUTH_MODE=inherit` 和 `AIW_CODEX_AUTH_HOME`；细节见
[CODEX-RUNTIME.md](process-go/CODEX-RUNTIME.md)。`AIW_CODEX_APP_SERVER_VERSION` 保留历史配置兼容，
不再是启动时的精确版本门槛。Python 已删除 Codex SDK，其 App Server 启动/迁移方式见 Python README。
Go/Python 的 `AIW_WORKER_ATTEMPT_TIMEOUT_MS`（默认 900000，兼容 `AIW_AGENT_TASK_TIMEOUT_MS`）仍是硬期限，并非上面 TypeScript 的耗时提示。
Go/Python 的时间预算和 evidence 实现独立于 TypeScript runtime-core；不要将 TypeScript 特有的缓存或历史压缩变量直接照搬。

## 配置源码

- TypeScript：[入口](process-typescript/src/index.ts)、[Codex](process-typescript/src/codex-process.ts)、[Agents](process-typescript/src/api-adapter.ts)、[时间预算](process-typescript/runtime-core/settings.mjs)、[缓存](process-typescript/runtime-core/cache.mjs)。
- Go：[入口](process-go/main.go)。Python：[入口](process-python/process_worker.py)、[API runtime](process-python/openai_api_runtime.py)。
- Plan：[`WorkflowPlanSpec`](../src/main/java/me/asu/aidev/workflow/domain/WorkflowPlanSpec.java)、[Step 配置协议](../src/main/java/me/asu/aidev/workflow/application/WorkflowAiPreferences.java)。
