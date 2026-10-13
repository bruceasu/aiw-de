# FD-014 本地双认证实施报告（Linux r2）

<!-- aiw-data: FD-014-implementation-linux-r2.json -->

- **角色 / 会话：** Worker / `fd014-worker-linux-20261013-a83c26`。
- **来源事件：** `FD-014-000016-work-requested`，已由本会话领取。
- **范围：** 用户指定本地继续、Docker 后续处理；本次完成 ready 子项 1.6.4.1。完整生产候选 FD 尚未完成，没有发出 implementation-ready。

## 实现

- 添加 `AIW_CODEX_AUTH_MODE=inherit|api-key|chatgpt` 与 `AIW_CODEX_AUTH_HOME`；它们独立于执行模式 `AIW_AGENT_RUNTIME`。默认 inherit 保留现有环境和原生登录行为。`openai-api` 继续使用原配置，不接受 Codex 的显式认证选择。
- 显式模式要求已有绝对认证目录，并核对实际路径不是 repository/worktree/attempt 内的路径，包括已有符号链接解析。不创建目录、不复制凭据，不执行登录/注销或强制登录方式变更。
- 两个 Codex 子进程使用选定 CODEX_HOME，并排除环境中的 OPENAI_API_KEY、CODEX_API_KEY、CODEX_ACCESS_TOKEN，避免覆盖已准备的原生缓存认证。API Key 模式在这个阶段使用官方 CLI 预先登录的 API 凭据，而非 ambient API Key 注入。
- Exec 在注册前和每个 attempt 前以有界 `codex login status` 检查缓存登录类型。该 CLI 的状态文本在 stderr 中，最多捕获 1 MiB，只在内存判定，错误及证据不会包含原始文本或掩码 key。
- App Server 在启动时做同样检查，完成 initialized 后和创建 thread 前再通过 `account/read(refreshToken=false)` 核对 account.type。不记录 email、plan、账户响应或凭据。
- 缺失、未知或不匹配登录状态返回固定错误码，不静默 fallback。切换步骤是停止旧 Worker、等待活动资源终态、修改两个宿主配置并重启；未引入进程内热切换。
- Go README 和 CODEX-RUNTIME.md 记录两种认证上下文准备方式、当前本地限制及后续 Docker 准入；没有修改角色 Profile 八字段、Control Plane API、租约或持久化结构。

## 来源与静态依据

官方固定源码提交 `092d3acd6bec3e3a14bdc7e7a2810ab628ab759d` 中的 [login/status 实现](https://github.com/openai/codex/blob/092d3acd6bec3e3a14bdc7e7a2810ab628ab759d/codex-rs/cli/src/login.rs#L447)明确将 ChatGPT/API 类型写入 stderr；[GetAccountResponse](https://github.com/openai/codex/blob/092d3acd6bec3e3a14bdc7e7a2810ab628ab759d/codex-rs/app-server-protocol/schema/json/v2/GetAccountResponse.json)定义 account.type。[App Server 启动](https://github.com/openai/codex/blob/092d3acd6bec3e3a14bdc7e7a2810ab628ab759d/codex-rs/app-server/src/lib.rs#L584)以关闭 CODEX_API_KEY 环境入口的方式加载认证，故未假设给 App Server 添加该环境变量就能实现切换。

这些核对支持原生已准备凭据上下文与 RPC 类型检查的实现，不证明本机实际登录、配置或服务端政策已经验证。显式模式检查不覆盖自定义 provider、workload identity 或 access-token 登录；这些原有使用方式仍可使用 inherit。

## 实际命令

- `aiw fd refresh-worker FD-014 --reason <用户选择 Docker 后续处理、本地继续的范围决定>`：成功，创建当前源事件。
- `aiw fd claim FD-014 FD-014-000016-work-requested --session fd014-worker-linux-20261013-a83c26`：成功。
- 在 Go Worker 目录运行 `GOTOOLCHAIN=local GOPROXY=off GOSUMDB=off go build -o /dev/null .`：一次通过，不下载依赖、不保留分发产物。
- 静态核对认证选择从 loadConfig、resolveAgentRuntime、注册前 managed.Start、Exec Run 和 App Server thread 前检查的调用链；最终 `git diff --check` 通过；新报告双工件引用与当前 Worker 所有权核对一致。这些仍是静态证据，不是运行验证。

没有运行测试、真实登录状态子进程、真实模型 turn、热切换、Docker、主机配置变更、Reviewer、合并或归档。源码中的登录状态/RPC 调用是运行时行为，本次开发会话只编译，没有执行它们。

## 剩余工作与风险

1.6.4.2 的双认证实际切换和失败场景尚无运行证据；本地状态文本解析仅依据固定 CLI 源码，不表示已在真实凭据上执行。每次 Exec attempt 增加最多 5 秒的原生登录检查，服从整体 context 期限；这项延迟未测量。认证目录、原生配置和凭据刷新由部署方准备，当前没有生产隔离或配置身份认证。

Docker 相关 1.6.2、1.6.3、1.6.4.3、1.6.6 按用户指示暂缓；全通道禁存、主机和服务端政策仍未认证。FD 保持 In Progress，不能把本地编译或选择代码视为生产候选就绪。详见 `FD-014-blocker-local-stage-r1.md/.json`。
