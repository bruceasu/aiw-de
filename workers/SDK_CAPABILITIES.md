# Worker SDK 能力基线

FD-020：Python Codex SDK 运行路径与 openai-codex 依赖已删除，改为已安装 CLI 的 codex-app-server；只保留 App Server/openai-agents。以下 Python SDK/wheel 分析是历史证据，不代表现行依赖或生产准入。新适配的源码支持、平台限制与未运行验证见 [Python README](process-python/README.md#codex-app-server-migration) 及 [三语言矩阵](README.md)。

## 当前决定：移除 TypeScript Codex SDK，直接使用系统 App Server

用户要求 Worker 分发完全不带本地 Codex 包。已移除 @openai/codex-sdk 及其传递依赖 @openai/codex，清理旧 SDK adapter。TypeScript Runtime 明确支持 codex-cli 与 codex-app-server 两个目标入口，分别从 AIW_CODEX_PATH/PATH 调用系统 codex exec 或 codex app-server，不自动下载或回退。两个 adapter 已实现独立 Exec 与共享 App Server 本地执行；事件/取消/ephemeral 和结果策略静态接入。生产认证由 FD-015 承接；未运行真实 CLI/模型，不借用 Go 证据宣称 TypeScript 运行通过。

OpenAI API backend 继续使用 @openai/agents 0.20.0、openai 7.32.0、zod 4.6.7。以下 Codex SDK 安装和源码核对记录是此前阶段历史，不代表当前依赖或当前运行能力。

## FD-016 SDK 更新基线（2026-10-13）

经 npm latest 查询并安装，TypeScript 依赖已固定为 @openai/codex-sdk 0.162.1、@openai/agents 0.20.0、openai 7.32.0、zod 4.6.7；package-lock 同步。Agents 0.20.0 要求 Zod ^4 并依赖 OpenAI ^7.2，配套依赖随之升级。安装使用 --ignore-scripts --omit=optional --no-audit --no-fund，没有启动 SDK/CLI 或调用模型。

安装包源码：Codex SDK dist/index.js 的 CodexExec.run 仍以 ["exec", "--experimental-json"] 启动原生 CLI；公开导出未提供 App Server 客户端。TypeScript 可以按官方 App Server 协议通过 stdio/JSON-RPC 接入，但需要独立 Runtime 适配器，不是升级此 SDK 自动获得的能力。[官方 SDK 文档](https://learn.chatgpt.com/docs/codex-sdk)、[官方 App Server 文档](https://learn.chatgpt.com/docs/app-server)。

仅依赖与版本准入常量已更新。新版本的工具参数、流式终态、usage、取消、tracing、store 和禁存契约仍需 FD-016 迁移核对，不声明运行认证完成。下文旧版本的 SDK 源码分析与测试保留为历史，不能用于新版准入认证。

来源：FD-005，2026-10-04/05 的本地安装源码和经授权下载的固定 Python wheel。API 安装命令 `npm install --ignore-scripts --no-audit --no-fund` 已获授权并成功执行。没有调用模型或部署端点。

## API TypeScript

- 实际 Agents SDK / agents-core / agents-openai：0.3.9，package-lock 固定安装结果。
- SDK 使用 OpenAI 6.49.0；Worker 原直接客户端为 5.23.2。用户已明确允许直接依赖对齐到 6.x；本 FD 采用已安装的 6.49.0，保持 Agents SDK 和 Responses 路径。
- 并行：agents-core/dist/runner/toolExecution.mjs 使用 Promise.all 执行工具批次，返回顺序与调用顺序一致。
- 关联：tool 的 execute 第三个参数 details.toolCall 提供调用 ID；SDK 提供 input/output guardrails 与 agent_tool_start/end。
- 工具错误：0.3.9 的公开选项为 errorFunction，null 会传播异常。旧 toolErrorFunction 不是该版本的选项，会落入默认错误文本处理；公共 hosted tool 校验原始参数并在 ToolHost 中保存拒绝，再使用正确的 errorFunction=null。
- 取消：ModelRequest.signal 与 Runner.run 的 signal 可以传入；工具的底层 I/O 和进程生命周期仍由 Worker 负责。
- 上下文：callModelInputFilter 可在每轮模型调用前替换 instructions/input；ModelRequest 也暴露完整请求输入。Worker 的预算与压缩不修改 SDK 的执行循环。
- 完整性：Responses 的非流响应 providerData 保存原始响应；流式 response_done 保留响应 status。SDK 流分支只在 response.completed 时形成终态，不能据此假设任意兼容端点都能安全执行截断调用。
- usage：SDK 提供 Usage；部分缺失原始 usage 的路径会使用零值。Worker 必须区分实际报告与未知，不能把默认零值解释成免费执行。

### Worker 适配决定

保留现有 SDK。包装 ModelProvider/Model，在工具调度前要求 Responses 终态为 completed，并拒绝不完整的工具调用；流结束没有完整终态则失败。模型并行设置继续为 true，不用全局串行弥补能力缺口。

ToolHost 负责参数、授权、资源、取消、输出和证据；SDK 负责 Agent 循环。Model 包装在每轮调用前保护完整冻结指令、执行预算和可关闭的历史压缩。普通工具错误可以返回模型，取消、关键证据失败与不确定资源状态需要终止 attempt。

启动核对实际安装版本；在动态导入 SDK 前设置正文日志关闭、DEBUG 关闭和全局 tracing 关闭，Runner 再明确禁用 tracing，客户端 logLevel 为 off。Responses 与摘要请求都使用 store false。供应商服务留存不由本地配置证明。

部署端点未运行验证。端点必须接受当前 Responses 请求并返回可识别的完整终态；不支持时明确失败，不宣称已经通过兼容验证，也不静默关闭并行。真实端点验证另列授权范围。

## Codex TypeScript

本地 @openai/codex-sdk 为 0.155.1，由现有已安装 Codex CLI 执行工具循环。SDK 的 usage 与 turn/session 事件可用于元数据；内部工具调度不映射成 API Worker 的完整工具证据。复用公共期限、工作区、上下文来源、脱敏与证据机制，保持原注册及模型路径。

SDK 声明 session 存于 `~/.codex/sessions`，0.155.1 的公开选项没有 ephemeral，原 spawn 参数未传 `--ephemeral`。已按用户批准经公开 `codexPathOverride` 接固定 Go 原生适配器，保持已安装 native resolver、companion PATH、SDK 循环及模型政策。适配器只允许首次 exec、强制 ephemeral 和禁存配置、stdin 提示词，拒绝 resume/保存参数与未知覆盖。本机全局 CLI 包声明 0.160.0，仍没有实际二进制和全通道认证；缺少绑定验证工件时注册前拒绝。

## Go 与 Python

共享行为契约与输入样例；各语言维护自己的取消、进程、文件和 SDK 适配。对 Codex 内部行为无法观察的字段明确 unknown/unsupported。旧 codex-* 仍为参考示例，demo-deployer 不默认取得发布授权。

Go 已接入公共期限、证据、进程管理和固定启动适配器。模型指令由 stdin 传递；启动前执行不创建 native 进程的 `--preflight`，每次 exec 再核对清单、摘要和配置。

Python 固定 `openai-codex==0.157.0`，固定 wheel 的 SHA-256 为 `e97c61d4fda9540d3db309e27bcf46cae6e12293c5b9d74e98fe3d285f8477e3`。没有安装或导入此 SDK。

- `client.py`：公开 `CodexConfig.launch_args_override`，设置后跳过默认 CLI 解析与 checkout schema 路径；env 会与父环境合并，实际 native 前由适配器清除 CP/调试变量。
- `api.py`：公开 `Codex.thread_start(ephemeral=True)`、`Thread.read(include_turns=False)`；generated/v2_all.py 的 Thread 有 ephemeral 与可空 path。
- METADATA：固定 `openai-codex-cli-bin==0.157.0`。禁止借用全局 CLI 0.160.0 认证或自动升级。
- `Codex.__init__` 只接收 config；内部默认审批 handler 自动接受命令/文件审批。适配器阻止接受回复，不访问私有 client；需要宿主审批时当前执行失败。
- 新接入通过公开 launch_args_override 启动受控 stdio app-server，显式 ephemeral，先校验 thread/start 回执及公开 read 元数据，再发送提示词；创建、读取与运行均受整体期限约束。无法确认取消终态时保留工作区和排他资源，停止 slot。

三个 Codex 运行时直接使用各自 SDK/CLI 启动原生 Codex，不再经过单独 launcher。部署仍要求 `AIW_CODEX_SESSION_POLICY` 指向工作区外的固定清单，绑定平台、SDK/native 摘要和十项通道验证，并核对用户/工程配置来源；示例故意标为 unverified，不能启用会话。launcher 移除后，原先由其实施的参数白名单、RPC gate 和进程约束不再统一生效。完整方案见 [禁存设计](../docs/worker-codex-no-store-design.md)。

## 后续验证边界

先用假 Model、临时工作区和离线子进程验证确定性契约。独立 Tester 的命令需按 FD 流程审查并授权；真实端点、网络、服务与安装不从自动流程推导授权。源码能力基线不是运行效果或跨平台保证。
