# TypeScript Process Worker

## 新手快速上手

实际目录是 `workers/process-typescript`（单数）。下面的命令都在该目录中执行；
Windows 请使用 PowerShell，Linux/macOS 请使用终端。

**交付范围：** FD-016 提供本地 Worker 执行；生产准入与认证由 FD-015 承接。
`codex-cli` / `codex-app-server` 使用系统 CLI 0.162.1，无 Worker-local Codex 包。
原生 Windows 的两种 Codex 模式因进程树能力未核定而在启动前拒绝；可用 Unix/WSL，
或按下例在 Windows 选择 `openai-agents`（需要 `OPENAI_API_KEY`）。
真实 CLI、模型、类型检查及测试未运行，生产禁存认证归 FD-015。

### 先认识四个命令

| 命令 | 作用 | 什么时候执行 |
| --- | --- | --- |
| `npm run build` | 把 TypeScript 源码编译为 `dist/` 中的 JavaScript | 首次从源码运行，或修改 `.ts` 源码后 |
| `npm run release` | 先 build，再整理到 `out/process-typescript-<version>/` | 准备分发给其他机器时；当前发布阻塞见下文 |
| `npm start` | 运行 `dist/index.js`，启动一个角色的 worker | 已 build，并配置好角色和 runtime 后 |
| `npm run start:all -- openai-agents` | 为十个可执行角色各启动一个 worker | 已 build，并配置好共享环境后 |

`start` 和 `start:all` **不会自动 build**。改源码后直接 start 会继续运行旧的编译结果。
`release` 已包含 build，不需要先单独执行一次 build。
`npm run compile` 仅做内存中的语法编译，不生成 `dist/`，不能替代 build。

### 在开发机器上运行

#### 不占用终端的测试管理器（Linux / macOS / WSL）

先按下文安装依赖、执行 `npm run build`，并在**执行 start 的终端**设置 Control Plane、仓库、运行时所需的环境变量。管理器只管理本目录的 TypeScript 进程 Worker，不管理 Go/Python Worker，也不自动启动 Java 服务。每个角色有独立进程、日志和 Worker ID；不要同时运行同 ID 的 `start:all` 或手动进程。

```sh
cd workers/process-typescript
export AIW_CONTROL_PLANE_URL=http://127.0.0.1:8080
export AIW_REPOSITORY_REF=repo-main
export AIW_REPOSITORY_ROOT=/absolute/path/to/my-project
export AIW_WORKER_CAPABILITIES_CODER=typescript,node
export AIW_WORKER_CAPABILITIES_STAGE_REVIEWER=typescript,node
node scripts/manage-workers.mjs start requirements codex-app-server
node scripts/manage-workers.mjs start stage-reviewer codex-app-server
node scripts/manage-workers.mjs start coder codex-app-server
node scripts/manage-workers.mjs status
node scripts/manage-workers.mjs list
node scripts/manage-workers.mjs logs coder
node scripts/manage-workers.mjs stop coder
node scripts/manage-workers.mjs stop all
```

需要全部十个角色时执行 `node scripts/manage-workers.mjs start all codex-app-server`。也可选择 `codex-cli` 或 `openai-agents`；后者须在启动终端配置 `OPENAI_API_KEY`。技术能力使用 `AIW_WORKER_CAPABILITIES_<ROLE>` 独立设置；未设置的角色不会继承共享的 `AIW_WORKER_CAPABILITIES`。默认 ID 是 `test-ts-<role>-1`，通过 `AIW_WORKER_ID_PREFIX` 改前缀以避开其他实例。`logs` 输出日志路径，可用 `tail -f <path>` 跟踪。状态和日志存在已忽略的 `.ai/worker-manager/`，日志文件仅当前用户可读写；退出管理命令不会停止 Worker。

`stop` 发送 SIGTERM，请求 Worker 排空并下线，不强制杀掉进行中的任务；随后用 `status` 确认停止。管理器不自动重启崩溃进程，也不证明 Worker 已成功向 Control Plane 注册；实际注册与匹配仍查看 `/ui/workers`。修改 Worker 源码后先停止对应角色、重新 `npm run build`，再启动。原生 Windows 请使用 WSL 或下文的前台 PowerShell 脚本。

1. 安装 Node.js 22 或更新版本（包含 npm），以及 Git。先启动 Java Control Plane。
2. 在项目根目录进入 worker 目录，安装依赖并编译：

```sh
cd workers/process-typescript
npm ci --omit=optional
npm run build
```

依赖安装通常需要网络。后续只修改源码时，重新执行 `npm run build` 即可。

3. 在同一个终端设置环境变量。替换示例 API key、仓库标识和绝对路径。
   `AIW_REPOSITORY_REF` 必须与 Control Plane 中配置的仓库标识一致；
   `AIW_REPOSITORY_ROOT` 是待处理项目的根目录。

Windows PowerShell：

```powershell
$env:AIW_CONTROL_PLANE_URL = 'http://127.0.0.1:8080'
$env:AIW_REPOSITORY_REF = 'repo-main'
$env:AIW_REPOSITORY_ROOT = 'C:\projects\my-project'
$env:OPENAI_API_KEY = '<你的 API key>'
$env:AIW_AGENT_RUNTIME = 'openai-agents'
$env:AIW_PROCESS_ROLE = 'requirements'
npm start
```

Linux/macOS：

```sh
export AIW_CONTROL_PLANE_URL='http://127.0.0.1:8080'
export AIW_REPOSITORY_REF='repo-main'
export AIW_REPOSITORY_ROOT='/absolute/path/to/my-project'
export OPENAI_API_KEY='<你的 API key>'
export AIW_AGENT_RUNTIME='openai-agents'
export AIW_PROCESS_ROLE='requirements'
npm start
```

这会启动一个 `requirements` worker。换成 `coder`、`reviewer` 等角色即可运行其他角色。
环境变量只对当前终端及其子进程生效，新开终端需要重新设置。

4. 如果要一次启动全部十个角色，先用 Ctrl+C 停止上面的单角色进程，
   然后保留共享环境变量，在同一终端执行：

```sh
npm run start:all -- openai-agents
```

批量启动会自动设置每个子进程的角色和独立 worker ID。
若之前设置过 `AIW_WORKER_ID`，PowerShell 用
`Remove-Item Env:AIW_WORKER_ID -ErrorAction SilentlyContinue` 清除，
Linux/macOS 用 `unset AIW_WORKER_ID` 清除。Ctrl+C 停止批量启动；Windows 的
进程终止可能绕过排空流程，详见下面的批量启动说明。

### 工作区交付物与正式文档

新版 Control Plane 领取响应包含 `outputStore` 时，Worker 自动在完成回报和临时工作区清理前
登记报告和附件。文件保存在 Control Plane 所在机器的持久工作区 `.aiw/plans/.../tasks/.../`，
跨机器输入通过带当前租约校验的接口读取，验证来源 ID、SHA-256 和字节数后加入上下文。
同机可以直接查看共享目录；任务执行使用固定的 Control Plane 输入版本，不扫描最新文件。

最终响应可附一个 `aiw-output-files` JSON 代码块：

````text
完成报告正文。
```aiw-output-files
[{"path":"requirements.md","kind":"DOCUMENT","content":"# Requirements\n..."}]
```
````

或者用 `sourcePath` 声明当前工作区中的已有文件；`content` 与 `sourcePath` 二选一。
每次最多 16 个声明文件，单文件最多 4 MiB，包含自动报告和计划快照的归档总量最多 8 MiB。
只读角色返回内联文档；程序归档不会授予模型写仓库的权限。

TypeScript Worker 自动注册 `task-outputs-v1` 能力，文档入库任务及其独立评审要求此能力。
其他语言的旧 Worker 仍按现有 complete 提交报告，Control Plane 会为有工作区的报告归档；
附件、固定输入下载和入库校验需要实现新协议，不能手动声明此能力来代替实现。
任务详情页提供下载和创建文档入库草稿；草稿仍须通过现有计划确认入口执行。
详见 [任务产出机制](../../docs/current-zh/TASK-OUTPUTS.md)。

### 查看失败诊断

后台管理器将每个角色的标准输出与错误输出保存到 `.ai/worker-manager/<role>.log`。
`logs` 命令只返回文件路径，不显示文件内容；在 Worker 目录中查看需求分析日志：

```sh
node scripts/manage-workers.mjs logs requirements
tail -n 80 .ai/worker-manager/requirements.log
```

前台 `npm start` 或 `start:all` 的输出默认在启动终端，不会自动创建管理器日志文件。
使用后台管理器启动后才能按管理器的路径查日志；不要重复启动同 ID 的实例。

后续轮询与心跳错误会记录时间、Control Plane 地址（不含 URL 凭据）和
`HTTP_401`、`HTTP_403`、`ECONNREFUSED` 等结构化状态码。
Codex 的失败工具事件会记录工具类型、调用 ID、退出码、诊断码及证据目录；
执行证据中的 `sdk-tool-failed` 包含相同字段，可通过 `owner.json` 的执行 ID 关联任务。
默认不会记录完整命令、原始 SDK 错误、命令输出、任务正文或凭据。
只在内存中根据有限输出识别沙箱 namespace、命令不存在、权限、路径及连接错误；
未匹配的失败仍记为 `UNRESOLVED_TOOL_FAILURE`，不推断具体原因。

`SANDBOX_NAMESPACE_UNAVAILABLE` 表示输出中检测到沙箱创建 namespace 被拒绝；
应检查实际宿主机或容器的 Linux sandbox 支持。
`COMMAND_NOT_FOUND`、`COMMAND_PERMISSION_DENIED`、`COMMAND_PATH_NOT_FOUND`、
`COMMAND_CONNECTION_FAILED` 分别指向工具、权限、路径或网络配置排查。
这些诊断是输出模式识别结果，不代表执行环境已经修复。
历史执行没有保留的错误输出无法补回。

无 Git 基线的 DIRECT 工作区允许是空的普通目录。Worker 提示会要求模型先检查
Git 条件，不默认执行 `git status` / `git rev-parse`，也不为报告初始化仓库或编造版本。
搜索探测应在同一命令中处理 `rg` / `grep` 的退出码 1，保留其他错误。
诊断解析支持 `bash`、`sh` 和 `zsh` 的 `-c` / `-lc` 包装；SDK 未提供输出时
仍按未知失败处理，不把缺失输出等同于空输出。实际 Git 非仓库错误会显示
`GIT_REPOSITORY_UNAVAILABLE`。这些提示减少无效探测，但不保证模型一定遵循；
工具失败作为诊断保留，不要求完全相同命令重跑，不单独阻止成功回报。
完成 Gate 检查模型终态、资源终止、证据健康、报告格式、只读/发布约束、产出登记和租约；
普通编码任务不会由 Worker 自动运行统一的构建或测试，业务验收仍由独立评审决定。
模型结束或完成 Gate 通过不等于项目已通过构建/测试。致命工具故障、取消、超时及证据保存失败仍阻止完成。

#### Codex 命令使用 Bash

Codex 两种后端均明确要求模型在每次 `exec_command` 中设置 `shell="/bin/bash"`、
`login=false`，并通过开发者指令及任务提示传递此规则。启动时检查 `/bin/bash`
是带执行位的普通文件，否则报 `WORKER_BASH_UNAVAILABLE`。
同时设置 `allow_login_shell=false`、`features.unified_exec=true`、
`features.unified_exec_zsh_fork=false`、`features.shell_snapshot=false` 和
`shell_environment_policy.experimental_use_profile=false`，覆盖对应个人/项目配置；
模型应从 Worker 继承的 PATH 查找工具，不依赖个人 shell 启动文件。
Worker 不修改系统账户的登录 shell，也不以 `SHELL` 变量冒充默认 shell 覆盖。
Codex 0.162.1 的默认 shell 来自系统账户，Bash 选择依赖工具调用显式传参；
这些指令不是阻止模型使用其他 shell 的操作系统安全边界。
实现依据为 [固定版本的 shell 选择逻辑](https://github.com/openai/codex/blob/rust-v0.162.1/codex-rs/shell-command/src/shell_detect.rs)
及 [exec_command 的 shell/login 参数处理](https://github.com/openai/codex/blob/rust-v0.162.1/codex-rs/core/src/tools/handlers/unified_exec.rs)。

#### 工具缓存与临时目录

Worker 启动时创建 `/mnt/temp/cache/workers/<Worker-ID及摘要>/`，可用
`AIW_WORKER_CACHE_ROOT` 指定其他绝对根目录。缓存按实例持久保存，临时目录为
`tmp/<PID>-<随机后缀>/`，不同进程互不复用临时目录；同一进程内工具自行创建临时文件。
启动日志会显示实际缓存和临时目录。创建失败会在注册、领取任务前终止启动。

| 工具 | 配置 | 实例目录下的位置 |
| --- | --- | --- |
| 通用临时文件 | `TMPDIR`、`TMP`、`TEMP` | `tmp/<PID>-<随机后缀>/` |
| npm | `npm_config_cache`、`NPM_CONFIG_CACHE` | `npm/` |
| Go | `GOMODCACHE`、`GOCACHE`、`GOTMPDIR` | `go/modules/`、`go/build/`、进程临时目录 |
| Python / pip | `PIP_CACHE_DIR` | `python/pip/` |
| Java / Maven | `JAVA_TOOL_OPTIONS` 中的 `java.io.tmpdir`、`maven.repo.local` | 进程临时目录、`maven/repository/` |
| 其他遵循 XDG 的工具 | `XDG_CACHE_HOME` | `xdg/` |

这些变量在 Worker 内设置，并传递给受管子进程及 Codex shell 配置，无需修改 `.bashrc`。
保留已有 `JAVA_TOOL_OPTIONS`，追加本次目录配置。任务提示要求使用这些目录，不得用显式
`--cache` 或类似参数改回项目缓存；直接主机访问模式不能阻止模型主动覆盖环境或参数。
项目依赖安装目录（如 `node_modules`、虚拟环境）和编译产物仍留在项目工作区。
不迁移旧缓存、不自动清理目录，也不改变项目依赖版本、源地址或下载验证设置。
本机制适用于此 TypeScript Worker 启动的工具，不设置全局交互 shell，也不修改 Go/Python Worker 程序。

配置依据：[npm 环境变量](https://docs.npmjs.com/cli/v11/using-npm/config/#environment-variables)、
[Go 环境变量](https://pkg.go.dev/cmd/go#hdr-Environment_variables)、
[pip 缓存](https://pip.pypa.io/en/stable/topics/caching/)、
[Java 工具选项](https://docs.oracle.com/javase/8/docs/technotes/guides/troubleshoot/envvars002.html)。

#### 临时 DEBUG 模式（Codex CLI / App Server）

启动前设置 `export AIW_WORKER_DEBUG=true`（也接受 `1` 或 `yes`），然后启动所需角色。
已运行的 Worker 必须先排空、停止，再用此变量重新启动；变量不会动态更新现有进程。
失败工具额外输出一行 `[AIW DEBUG]` JSON，包含调用 ID、退出码、证据目录、命令、
工作目录、工具输出和错误消息。后台管理器会把这些行保存到对应角色日志中。
每次 attempt 最多记录 8 条工具详情；命令、目录、输出和错误消息分别限制为
4096、1024、8192 和 2048 字符，过长内容保留头尾并标记 `truncated`；
`text: null` 表示该字段未提供，空字符串表示实际输出为空。

DEBUG 尝试遮蔽敏感环境变量值、Bearer 凭据、URL 用户密码及常见凭据字段，
但不能保证识别所有秘密，日志仍可能包含项目正文。只在诊断期间启用，
分享日志前检查内容。该模式不向 `events.jsonl` 写入正文、不改变任务结果判断，
也不打开原始协议、模型提示词或 SDK tracing。
排查结束后执行 `unset AIW_WORKER_DEBUG` 并重新启动 Worker，按本地策略清理诊断日志。

Worker 源码更新后，先停止相关角色并确认排空结束，再执行 `npm run build`，
然后用原运行时重新启动。`npm run compile` 不生成运行文件，不能替代 build。

### 分发给其他机器

**发行定义：** 发布清单已移除不存在且未使用的 `bin/`，包含
`build.mjs`、`start-all.mjs`、`scripts/ai-workers.service`、`scripts/ai-worker@.service`
及现有运行时材料。以下为人工授权后的打包操作说明；
本轮没有执行最终构建或发行脚本，静态清单检查不能替代实际打包验证。

开发机器上的打包命令是：

```sh
npm run release
```

预期输出目录是 `out/process-typescript-<version>/`，当前版本为 `0.1.0`。
分发整个目录，不要只复制 `dist/`。发布脚本拒绝覆盖同版本的已有目录，
重新打包前需要移走或重命名旧输出。

分发包会保留 `package.json` 和 `package-lock.json`：前者声明 JavaScript 模块类型
与运行依赖，后者锁定依赖版本。它们并不是只在开发时才需要的文件。
当前包不包含 `node_modules/`，目标机器安装 Node.js 22+ 和 Git 后，
进入收到的包目录，安装运行依赖：

```sh
npm ci --omit=dev --omit=optional
```

目标机器不需要 TypeScript 编译器，也不需要再次 build。若目标机器离线，
应先在与其系统和架构兼容的环境安装运行依赖，再随包交付。
按上面的示例配置环境变量；Control Plane 在其他机器时，修改其 URL。
然后启动单个 worker：

```sh
npm start
# 等价的直接启动方式：
node dist/index.js
```

在已生成的分发目录安装生产依赖后，启动全部角色：

```sh
npm run start:all -- openai-agents
# 等价的直接启动方式：
node start-all.mjs openai-agents
```

上面每组是两种等价写法，选择一种执行即可。`npm run` 只是命令快捷方式，
运行 JavaScript 的程序是 Node.js。正常流程是：
开发机器 **安装依赖 → build → start**；分发流程 **release → 目标机器安装运行依赖 → start**。

### 用 systemd 管理分发后的 Worker（Linux / 启用 systemd 的 WSL）

发布包中的 `scripts/ai-workers.service` 管理全部十个角色；
`scripts/ai-worker@.service` 按角色管理单个 Worker。两者默认使用
`codex-app-server`，需要目标机器已安装并登录 Codex CLI 0.162.1。
先把 `/opt/aiw/process-typescript` 指向实际发布目录，创建运行用户 `aiw`，
并确认该用户能访问仓库、Control Plane 和 Codex 登录信息。若 Node 不在
`/usr/bin/node`，修改两个 service 文件中的 `ExecStart` 绝对路径。

将这两个 service 文件复制到 `/etc/systemd/system/`。在
`/etc/aiw/process-worker.env` 写入每台机器的共享配置，例如：

```ini
AIW_CONTROL_PLANE_URL=http://127.0.0.1:8080
AIW_REPOSITORY_REF=repo-main
AIW_REPOSITORY_ROOT=/srv/repos/my-project
AIW_CODEX_PATH=/usr/local/bin/codex
AIW_WORKER_ID_PREFIX=host-a
```

将 URL、仓库、CLI 路径和主机前缀替换为实际值。共享文件不要设置
`AIW_WORKER_ID`；整组启动器会用 `AIW_WORKER_ID_PREFIX` 生成每个角色的 ID。
单角色启动时，该前缀不起作用，应在 `/etc/aiw/worker-coder.env` 等
角色文件中设置唯一 ID 和可选模型配置：

```ini
AIW_WORKER_ID=host-a-coder-1
AIW_CODEX_MODEL=your-approved-model
AIW_CODEX_REASONING_EFFORT=high
```

`AIW_CODEX_MODEL` 是 Worker 默认模型；任务配置指定模型时，以任务配置为准。
模型 ID 需由目标环境确认。角色环境文件可省略，但在多机或混合启动场景下
必须为每个单角色实例设置唯一 `AIW_WORKER_ID`。安装模板后执行：

```sh
sudo systemctl daemon-reload
sudo systemctl enable --now ai-workers.service
sudo systemctl status ai-workers.service
journalctl -u ai-workers.service -f
sudo systemctl stop ai-workers.service
```

只启动 `coder` 角色时，先停止会使用相同 Worker ID 的整组服务，再执行：

```sh
sudo systemctl enable --now ai-worker@coder.service
sudo systemctl status ai-worker@coder.service
journalctl -u ai-worker@coder.service -f
sudo systemctl stop ai-worker@coder.service
```

其他角色把 `coder` 换成角色名即可。两个模板使用 `Restart=on-failure`，
意外退出会重启；手动 `stop` 不会重启。停止时 systemd 先向主进程发送
`SIGTERM`，Worker 执行排空；`TimeoutStopSec=infinity` 保留现有不设停止期限的
行为，若任务或进程一直不退出，`systemctl stop` 也会一直等待，需要人工处理。
不要同时运行使用相同 Worker ID 的整组服务与单角色服务。原生 Windows 的
Codex 模式仍不支持此管理方式。

## FD-016 profile transition

Bundled profiles accept only `EXTERNAL_WORK` and require nine fields including `resultKind`: `REPORT`, `VERDICT`, or `WORKSPACE`. Old task types and eight-field profiles are rejected. The launch role fixes the capability, exact `skillRef` and result kind; verifier never switches to integration-verifier. Release-readiness and orchestrator remain non-executable advisory profiles under `profiles/advisory/`. See the [shared contract](../WORKER_RUNTIME_CONTRACT.md). This is local delivery; runtime checks, model calls and production no-store certification are separate evidence.

This is the single TypeScript Worker package for the R9 Worker Pull protocol. Each process runs one R2 role with an explicit `AIW_AGENT_RUNTIME`: `codex-cli`, `codex-app-server`, or `openai-agents`. The Java Control Plane owns task state, leases, retries, scheduling, approvals, and persistence. The Worker owns task instructions and result interpretation; the selected AgentRuntime owns model execution. The former `process-typescript-with-api` package and launcher have been removed.

The Codex backends launch the system-installed CLI 0.162.1: `codex-cli` uses `codex exec` per attempt; `codex-app-server` manages one stdio service with independent ephemeral threads per slot. They use `AIW_CODEX_PATH` or system `PATH`; no Worker-local Codex binary, SDK, download or fallback is included. Native login is inherited. Approval is `never`; the default is direct host access (`danger-full-access`), including host network access. Read-only roles remain constrained by task instructions, without OS-enforced read-only isolation in this mode.

App Server 的 thread 响应须与请求模式一致：直接访问对应 `dangerFullAccess`，该结构不要求
`networkAccess`；`readOnly/workspaceWrite` 必须校验明确返回的网络权限。只读角色的指令约束
不改变所请求的直接访问模式。响应不匹配时仍拒绝执行，并保存 `thread-policy-mismatch` 诊断。

The Agents backend uses OpenAI Agents SDK and Responses API. It requires `OPENAI_API_KEY` before registration and does not need Codex CLI. The SDK version is checked at startup. Agents model and tool body logs and tracing are disabled before SDK import.


The Codex SDK and its transitive Codex CLI package have been removed from package.json, package-lock.json, and local node_modules. Agents SDK 0.20.0, OpenAI 7.32.0, and Zod 4.6.7 remain pinned for the API backend. Exec, App Server and Agents use the unified Profile policy. Production admission belongs to FD-015; ephemeral and store:false do not prove supplier zero retention.

## Roles

Executable role profiles live directly in `profiles/`; unfinished advisory definitions and their reference configuration live in `profiles/advisory/`. The corresponding advisory Skill packages live in `skills/advisory/` and are not loaded by executable roles. Their R2 Skill source version is in `skills/VERSION`. `AIW_PROCESS_PROFILES_ROOT` and `AIW_PROCESS_SKILLS_ROOT` can point to externally supplied copies.

启动参数、`AIW_PROCESS_ROLE`、TOML 文件名及环境配置示例统一使用同一个 role。
例如 `architect` 加载 `profiles/architect.toml`，配置示例为 `profiles/architect.env.example`：

```sh
node scripts/manage-workers.mjs start architect codex-app-server
```

`.env.example` 只提供示例，不会由 Worker 自动加载。TOML 内的 `primarySkill` 是任务协议中的
精确技能引用，例如 `solution-architect`，不用于启动参数。外部 `AIW_PROCESS_PROFILES_ROOT`
也必须提供 `<role>.toml`；不再加载旧文件名。更新源码后先执行 `npm run build`，再重启 Worker。

| Launch role | Task type | Access |
| --- | --- | --- |
| `requirements` | `EXTERNAL_WORK` | read-only |
| `architect` | `EXTERNAL_WORK` | read-only |
| `planner` | `EXTERNAL_WORK` | read-only |
| `coder` | `EXTERNAL_WORK` | writable |
| `integrator` | `EXTERNAL_WORK` | writable |
| `reviewer` | `EXTERNAL_WORK` | read-only |
| `verifier` | `EXTERNAL_WORK` | read-only |
| `integration-verifier` | `EXTERNAL_WORK` | read-only |
| `repairer` | `EXTERNAL_WORK` | writable |
| `stage-reviewer` | `EXTERNAL_WORK` | read-only |

`verifier` and `integration-verifier` register only their own capabilities. The `stage-reviewer` registers `workflow-review` and supplies an independent PASS or REPAIR_REQUIRED verdict for each flexible-plan step. All tasks need the exact primary Skill reference. All runtimes share the profile result policy.

## Install and start

Node.js 22+, npm, and Git are required. Install and build from this directory:

```sh
npm ci --omit=optional
npm run build
```

`node build.mjs` also builds from any working directory. It uses the installed
TypeScript compiler and writes `dist/`; it does not install dependencies or run tests.

To start all ten executable roles with one selected runtime, configure the
shared repository, Control Plane, and runtime environment, then run:

```sh
node start-all.mjs openai-agents
# Equivalent npm command:
npm run start:all -- openai-agents
```

The launcher accepts all three explicit runtimes. Codex modes use system CLI 0.162.1 on Unix/WSL; native Windows is refused before task text because process-tree termination is unverified. It excludes the advisory
`release-readiness` and `orchestrator` profiles. All workers inherit the caller's
working directory and environment; configure `AIW_REPOSITORY_REF` and
`AIW_REPOSITORY_ROOT` together. Each worker receives its own default role ID.
Unset `AIW_WORKER_ID` before launching; use `AIW_WORKER_ID_PREFIX` to give another
fleet distinct IDs, such as `office-coder-1`. Output shares the current terminal.
Keep the launcher in the foreground. Ctrl+C stops the fleet; the launcher forwards
SIGINT and SIGTERM to workers. POSIX workers use separate process groups to avoid
receiving Ctrl+C twice. An unexpected worker exit stops the other workers and
returns a nonzero status. The launcher waits for their existing drain/offline
handling and does not restart workers or impose a shutdown deadline. Windows
process termination may bypass graceful drain; use POSIX for managed shutdown.

Set `AIW_CONTROL_PLANE_URL`, `AIW_REPOSITORY_REF`, `AIW_REPOSITORY_ROOT`, and any additional technical `AIW_WORKER_CAPABILITIES` for each process. The role and `repo:<ref>` capabilities are added automatically. Start one process per role. Examples:

With `start-all.mjs`, use `AIW_WORKER_CAPABILITIES_<ROLE>` to scope technical capabilities to a role; replace hyphens with underscores, such as `AIW_WORKER_CAPABILITIES_CODER=node,typescript`. An unset role has no additional technical capabilities; the launcher does not inherit the shared `AIW_WORKER_CAPABILITIES`. Single-role launches may use that shared variable. The launcher adds the role capability automatically. `*`, `repo:`, role capabilities and `flexible-work-v1` are reserved and rejected in the configured technical list. For a TypeScript workflow, the requirements worker can keep an empty technical set while the coder advertises `node,typescript`; the independent `stage-reviewer` advertises `workflow-review` automatically. See [Worker capabilities](../../docs/current-zh/WORKER-CAPABILITIES.md) for the initial TypeScript, Go, Python, SQL and database capability vocabulary.

```powershell
./scripts/run-role.ps1 requirements codex-app-server
./scripts/run-role.ps1 coder codex-app-server
$env:OPENAI_API_KEY = '<API key>'
./scripts/run-role.ps1 reviewer openai-agents
```

```sh
sh ./scripts/run-role.sh requirements codex-app-server
sh ./scripts/run-role.sh coder codex-app-server
export OPENAI_API_KEY='<API key>'
sh ./scripts/run-role.sh reviewer openai-agents
```

Both scripts require the role and runtime. You may instead set `AIW_PROCESS_ROLE` and `AIW_AGENT_RUNTIME` explicitly, then run `npm start`. Missing or unknown runtime fails before registration; there is no automatic fallback. Use a unique `AIW_WORKER_ID` for each replica. The default is `process-exec-<role>-1` for Exec, `process-<role>-1` for App Server, and `process-api-<role>-1` for Agents, preserving existing Control Plane identities. The runtime registration labels are `codex-cli:<role>`, `codex-app-server:<role>` and `openai-agents:<role>`; task AI profiles must approve the selected runtime.

For a small run without integration work, `integrator` is optional. Check `GET /api/r8/fleet/dashboard` for unmatched READY tasks after starting the fleet.

Select `sh ./scripts/run-role.sh coder codex-cli` for a bounded one-shot process, or `sh ./scripts/run-role.sh coder codex-app-server` for managed thread/turn interaction. Exec cancellation targets its process tree; App Server cancellation targets its current turn and requires a real terminal event. Unknown termination preserves the workspace and drains the Worker.

## Configuration

### 执行循环与次数

所有角色共用领取循环：每个 slot 领取一个任务，执行一次 attempt，上报结果，再领取下一个任务。
空队列或轮询错误后等待 `AIW_WORKER_POLL_MS`（默认 1000ms）；运行至停止、强制取消或资源终态不确定。
并发 slot 数由 `AIW_WORKER_MAX_CONCURRENCY` 决定，默认 1；这不是同一任务的重试次数。

Codex App Server 每个 attempt 创建一个临时 thread，并调用一次 `turn/start`；Codex CLI
每个 attempt 启动一次 `codex exec`。模型内部可以多轮调用工具、修改和验证，Worker 不设
coder 专属轮数，也不在模型结束后自动追加修复 turn。整体耗时提醒由
`AIW_WORKER_ATTEMPT_TIMEOUT_MS` 决定，未设置时使用 `AIW_AGENT_TASK_TIMEOUT_MS`，再默认 900000ms（15分钟）。
到达该时间只向日志和执行证据写入一次 `ATTEMPT_DURATION_WARNING`，继续执行与续租。
默认没有整体硬期限；需要时显式设置 `AIW_WORKER_ATTEMPT_HARD_TIMEOUT_MS`，0 表示关闭。
原 TIMEOUT 配置现为提醒，不能再用它限制总执行时间；此行为适用于所有角色及三种后端。
Codex CLI 等待最终结果不再绑定提醒期限；App Server 单次 RPC 响应仍限制为30秒，
模型 turn 可继续运行。HTTP、API 请求、工具和清理期限、主动停止及租约失效仍按原机制处理。
没有新增断点续跑或自动恢复能力。
任务详情页按执行开始时间显示耗时提醒；Control Plane 的
`aidev.worker.attempt-warning-ms` 可独立配置，默认读取相同 TIMEOUT 环境变量并回落到15分钟。
远端 Worker 自定义提醒值时，需要同步配置 Control Plane；页面提醒是耗时提示，并非 Worker 进度或健康判定。
Agents 后端另有 `AIW_AGENT_MAX_TURNS`（默认 40），它不控制 Codex 两种后端。

灵活计划每步执行完成后由独立 stage-reviewer 评审；`REPAIR_REQUIRED` 由 Control Plane 创建
带评审意见的新执行任务，每步最多三轮（首次加两次返工），仍由原角色执行。
执行任务直接 `FAILED` 时不由 Worker 自行重跑；人工重试保留原任务 ID，新增 execution attempt，
与上述 `~work-1/2/3` 的评审返工轮次是两个计数。

| Variable | Default | Purpose |
| --- | --- | --- |
| `AIW_AGENT_RUNTIME` | required | `codex-cli`, `codex-app-server`, or `openai-agents` |
| `AIW_PROCESS_ROLE` | `requirements` | Role for this process |
| `AIW_WORKER_ID` | runtime dependent | Unique Worker Protocol identity |
| `AIW_WORKER_TOKEN` | unset | Worker Protocol bearer token |
| `AIW_WORKER_MAX_CONCURRENCY` | `1` | Concurrent claim slots |
| `AIW_CONTROL_PLANE_URL` | `http://127.0.0.1:8080` | Control Plane URL |
| `AIW_WORKSPACE` | repository root or cwd | Fallback task workspace |
| `AIW_WORKTREE_ROOT` | sibling `.aidev-worktrees/` | Attempt worktrees |
| `AIW_GIT_PUBLISH_REMOTE` | forbidden | Worker startup fails if set; Git push is not allowed |
| `AIW_WORKER_POLL_MS` | `1000` | Empty-claim retry interval |
| `AIW_WORKER_HEARTBEAT_MS` | `20000` | Worker and task heartbeat interval |
| `AIW_WORKER_HTTP_TIMEOUT_MS` | `15000` | Control Plane request timeout |
| `AIW_WORKER_ATTEMPT_TIMEOUT_MS` | `900000` | Elapsed-time warning only; does not cancel an attempt |
| `AIW_WORKER_ATTEMPT_HARD_TIMEOUT_MS` | `0` | Optional hard cancellation deadline; 0 disables it |
| `AIW_CODEX_PATH` | installed CLI on `PATH` | Existing native Codex executable |
| `AIW_CODEX_MODEL` | CLI default | Codex model |
| `AIW_CODEX_REASONING_EFFORT` | `medium` | Codex reasoning effort |
| `AIW_CODEX_APPROVAL_POLICY` | `never` | Unattended Codex approval policy |
| `AIW_CODEX_SANDBOX` | `danger-full-access` | Direct host access; optional `read-only` or `workspace-write` (the latter is rejected for read-only roles) |
| `AIW_CODEX_NETWORK_ACCESS` | `false` in sandbox modes | Controls sandbox network access; direct host mode always uses host network access |
| `AIW_WORKER_CACHE_ROOT` | `/mnt/temp/cache` | Root directory for tool caches and process temporary files |
| `OPENAI_API_KEY` | required for Agents | OpenAI credential |
| `OPENAI_BASE_URL` | SDK default | Agents API endpoint |
| `AIW_OPENAI_MODEL` | `gpt-4.1` | Agents model without an approved task profile |
| `AIW_OPENAI_REASONING_EFFORT` | unset | Agents reasoning effort |
| `AIW_AGENT_ALLOW_SHELL` | `false` only | Agents host shell is forbidden |
| `AIW_AGENT_MAX_TURNS` | `40` | Agents turn limit |
| `AIW_AGENT_TOOL_TIMEOUT_MS` | `60000` | Agents tool timeout |
| `AIW_AGENT_MAX_OUTPUT_CHARS` | `24000` | Agents output budget, enforced as UTF-8 bytes |
| `AIW_AGENT_AIW_TOOL_CATALOG` | unset | Optional fixed external aiw tool catalog |

`AIW_AGENT_COMPACT_HISTORY` defaults to false. Enabling it requires `AIW_AGENT_COMPACT_AFTER_BYTES` and `AIW_AGENT_SUMMARY_MAX_TOKENS`. The package-local [runtime-core](runtime-core/PUBLIC_CONTRACT.md) controls attempt deadline, cleanup, evidence, workspace isolation, and local result commits. See [Worker operations](../WORKER_OPERATIONS.md) for those budgets and the Codex deployment validation requirement. The optional aiw catalog format is shown in [aiw-tools.example.json](aiw-tools.example.json); it is external to the task workspace and grants no authority by itself.

Agents read-only roles have file inspection tools; writable roles use bounded write/edit tools without a host shell. Codex read-only roles force a read-only sandbox. Workers cannot publish Git branches; code handoff requires shared local repository access. No runtime claims cross-process resume or cancel by run ID; cancellation is scoped to the active attempt's signal.

On SIGINT/SIGTERM the Worker drains, finishes in-flight attempts, then goes offline. A lost lease aborts the active attempt and suppresses stale results. If termination is uncertain, it preserves the workspace and stops claiming work.

## Compile, tests, and release

`npm run compile` performs TypeScript syntax compilation in memory without a release artifact. `npm test` builds and runs the existing Codex role tests; run it only when your validation policy authorizes tests. Agents behavior and online endpoints require separate verification.

`npm run release` creates `out/process-typescript-<version>/` with `dist/`, `profiles/`, `skills/`, package-local `runtime-core/`, `build.mjs`, `start-all.mjs`, scripts, configuration sample, and package metadata. It copies `runtime-core/` from this package, excludes `node_modules/`, and refuses to overwrite an existing release. On a destination, run `npm ci --omit=dev --omit=optional`, then start the selected runtime. An offline destination can receive preinstalled production dependencies from a staging machine. Codex destinations still need their own installed CLI and authentication; Agents destinations need an API key.

## Flexible work contract

All executable roles accept `EXTERNAL_WORK`. `skillRef` must equal the role’s primary Skill. REPORT and VERDICT roles are read-only; WORKSPACE roles require an assigned Git workspace and a 40/64-digit base revision. Reviews with code inputs must use the supplied resultRevision as their base. Tests need host authorization; Git push is forbidden; task text and reports cannot grant it. Plan proposals need manager confirmation; REPAIR_REQUIRED creates no automatic repair task.

## Local platform limit

The two TypeScript Codex modes currently refuse native Windows startup with `CODEX_WINDOWS_PROCESS_TREE_UNVERIFIED`: Node cannot prove that descendants ended after a parent exits. Use the local Unix/WSL Codex runtime or `openai-agents` on Windows. Windows npm native path resolution remains implemented, but it is not a termination certification. Production platform containment and certification belong to FD-015. A failed taskkill never becomes success merely because the parent closed.
