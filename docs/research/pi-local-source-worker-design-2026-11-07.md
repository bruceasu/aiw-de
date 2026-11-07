# Pi 本地源码：Python Worker 可移植机制

日期：2026-11-07。来源是用户提供的 `D:/03_projects/AI-tools/pi` checkout，`pi-agent-core` 与 `pi-ai` 包清单均标记版本 `1.0.2`。本文记录该快照，不能据此推断上游最新版本或实际性能。仅静态阅读源文件；不安装或运行 Pi，不读取凭据，不执行 OAuth、模型、测试或网络请求。[agent 包](D:/03_projects/AI-tools/pi/packages/agent/package.json:3)、[ai 包](D:/03_projects/AI-tools/pi/packages/ai/package.json:3)

## 已确认的执行循环

Pi 的执行核心与交互界面分开。循环先处理一轮完整模型响应，再派发工具并收集结果；`finishTurn` 决定结束或继续，`prepareNextTurn` 与 `prepareRequest` 可在下一请求之前准备上下文。错误或中断响应直接结束；输出达到长度上限时，整轮工具调用均返回错误，避免使用可解析但可能截断的参数。[循环与回合边界](D:/03_projects/AI-tools/pi/packages/agent/src/agent-loop.ts:163)、[截断工具处理](D:/03_projects/AI-tools/pi/packages/agent/src/agent-loop.ts:478)

`transformContext` 先对规范消息生成投影，`convertToLlm` 再转换为模型可接受消息；令牌获取发生在每次模型请求前。该结构适合 Python 移植，认证不必属于 loop，UI 也不必拥有 loop。[请求边界](D:/03_projects/AI-tools/pi/packages/agent/src/agent-loop.ts:381)

默认工具模式是 parallel；全局指定 sequential，或批次中任何工具声明 sequential，会让整个批次串行。并行分支按模型调用顺序输出最终结果，但没有在此循环中看到固定并发上限；不能直接替代我们对文件写入、进程和租约的互斥规则。[默认模式](D:/03_projects/AI-tools/pi/packages/agent/src/agent.ts:253)、[调度决策](D:/03_projects/AI-tools/pi/packages/agent/src/agent-loop.ts:508)、[并行结果排序](D:/03_projects/AI-tools/pi/packages/agent/src/agent-loop.ts:586)

当前源码在完整 assistant 回合及其工具批次完成后取 steering；follow-up 仅在本来会停止时读取。不能把它宣传成任意工具中点干预，或认为每个工具结束便必然消费新输入。自动化 Worker 可将来源换成持久控制队列，但仍需我们自己的 Owner、版本、租约、接收/应用回执和安全边界。[steering 与 follow-up](D:/03_projects/AI-tools/pi/packages/agent/src/agent-loop.ts:286)

## OAuth 路线必须分开

该 checkout 同时存在 `openai-chatgpt` 和 `openai-codex` OAuth，名称相似但注册和调用合同不同。前者使用动态注册入口与直接计划使用 scope；后者使用固定 Codex client 和另一组 OAuth 端点。不能通过复制 token 或替换 URL 将二者互换。[ChatGPT OAuth](D:/03_projects/AI-tools/pi/packages/ai/src/auth/oauth/openai-chatgpt.ts:16)、[Codex OAuth](D:/03_projects/AI-tools/pi/packages/ai/src/auth/oauth/openai-codex.ts:22)

公开 `openai` provider 同时支持 API Key 与独立 ChatGPT OAuth，明确使用 `https://api.openai.com/v1` 和普通 Responses adapter；`openai-codex` provider 的显示名称明确标记 legacy，使用 `https://chatgpt.com/backend-api` 和另一适配器。这进一步支持本项目将认证与执行引擎分开，并保留现有 app-server 路线。[公开 provider](D:/03_projects/AI-tools/pi/packages/ai/src/providers/openai.ts:7)、[legacy provider](D:/03_projects/AI-tools/pi/packages/ai/src/providers/openai-codex.ts:7)

ChatGPT 登录独立执行 PKCE、state、nonce、回调接收和 code exchange；保存回调返回的实际 `clientId` 与授予 scopes。刷新使用该 `clientId`、对应 refresh token 和固定 resource，要求返回新的完整令牌组；每次登录当前都重新走动态注册。它不要求安装 Codex CLI，源码中的 localhost 1455 端口冲突处理也明确提示可能与另一个 Pi/Codex 登录竞争。[登录流程](D:/03_projects/AI-tools/pi/packages/ai/src/auth/oauth/openai-chatgpt.ts:233)、[scope 与令牌转换](D:/03_projects/AI-tools/pi/packages/ai/src/auth/oauth/openai-chatgpt.ts:162)、[刷新](D:/03_projects/AI-tools/pi/packages/ai/src/auth/oauth/openai-chatgpt.ts:208)

需要保留本项目更严格的合同：Pi 此文件只检查 `id_token` 存在，注释说明不以它识别用户；没有在这条实现中验证签名、audience、issuer 或 nonce。我们设计包含账户身份、凭据导入校验和安全展示，因此不能直接移植这项简化。[ID token 处理](D:/03_projects/AI-tools/pi/packages/ai/src/auth/oauth/openai-chatgpt.ts:200)

公开 Responses adapter 已对 ChatGPT 登录省略部分不支持字段，使用 `store=false`、`stream=true`、显式历史；请求前有 payload 钩子，SDK 内置重试关闭，外层另有有界重试。它通过“官方 URL + provider + 令牌不以 sk- 开头”启发式识别 OAuth，适合视作兼容实现线索，不能代替我们的显式 credential 类型和 capability 配置。其通用 adapter 允许 `samplingParams` 最后覆盖字段，移植时应改成最终严格白名单验证，防止配置重新引入不支持参数。[认证判别](D:/03_projects/AI-tools/pi/packages/ai/src/api/openai-responses.ts:36)、[流与重试边界](D:/03_projects/AI-tools/pi/packages/ai/src/api/openai-responses.ts:173)、[请求字段](D:/03_projects/AI-tools/pi/packages/ai/src/api/openai-responses.ts:327)、[参数最终覆盖](D:/03_projects/AI-tools/pi/packages/ai/src/api/openai-responses.ts:382)

工具声明与历史重放不能只替换 Bearer token。共享消息转换器保留调用 ID/namespace，并在支持时将新增工具写成 `additional_tools` developer 项；流处理分别处理 failed 与 completed/incomplete，不能把旧 Codex adapter 的终态归一化当成公开 API 的通用合同。[历史与工具增量](D:/03_projects/AI-tools/pi/packages/ai/src/api/openai-responses-shared.ts:179)、[调用身份](D:/03_projects/AI-tools/pi/packages/ai/src/api/openai-responses-shared.ts:316)、[流终态](D:/03_projects/AI-tools/pi/packages/ai/src/api/openai-responses-shared.ts:743)

## 多进程刷新：可借鉴，也有必须补齐的缺口

Pi 已实现需要的基本模式：先乐观检查有效期；将 refresh 放入 CredentialStore 的 `modify`；获得锁后重新读取真实令牌及有效期，其他进程已经刷新时直接复用；网络刷新限制为 15 秒。成功返回后才写入新凭据并释放锁。刷新失败不会静默改用环境 API Key。[刷新协调](D:/03_projects/AI-tools/pi/packages/ai/src/auth/resolve.ts:102)

实际本地存储使用 `proper-lockfile` 跨进程锁，异步锁等待有期限、可中断，并检查锁被破坏；读改写在锁内完成。目录和文件创建使用 0700/0600 模式。这些是可移植的机制，不需要引入该 JS 依赖。[文件锁](D:/03_projects/AI-tools/pi/packages/coding-agent/src/core/auth-storage.ts:116)、[锁内读改写](D:/03_projects/AI-tools/pi/packages/coding-agent/src/core/auth-storage.ts:157)、[provider 修改](D:/03_projects/AI-tools/pi/packages/coding-agent/src/core/auth-storage.ts:449)

但当前实现按整个 auth 文件加锁，规范存储是每 provider 一条 credential；写回使用 `writeFileSync`，不是临时文件加原子替换，也没有看到持久的 REFRESHING 意图、generation CAS 或未知旋转结果状态。若远端成功轮换而本地写回前崩溃，仅有文件锁不能证明旧 refresh token 仍可用；Windows 也不能将 0600 模式等同于完整凭据保护。我们的多账户、多 Worker 设计继续采用注册/session 标识、跨进程锁、锁内重读、原子持久化和明确的 `REFRESH_OUTCOME_UNKNOWN` 恢复，不能照搬为“刷新已完全解决”。[写回位置](D:/03_projects/AI-tools/pi/packages/coding-agent/src/core/auth-storage.ts:182)、[单 provider 规范存储](D:/03_projects/AI-tools/pi/packages/ai/src/auth/types.ts:36)

## 上下文、压缩与恢复

会话记录与模型输入分开：活动分支的最新 compaction 条目生成摘要，再接上 `firstKeptEntryId` 后保留的消息，已摘要历史不进入当前模型工作集，原始会话仍可追溯。默认压缩按窗口余量触发；切点不能落在孤立工具结果处，保留工具调用与结果配对。[会话投影](D:/03_projects/AI-tools/pi/packages/coding-agent/src/core/session-manager.ts:469)、[触发条件](D:/03_projects/AI-tools/pi/packages/coding-agent/src/core/compaction/compaction.ts:267)、[有效切点](D:/03_projects/AI-tools/pi/packages/coding-agent/src/core/compaction/compaction.ts:430)

摘要是额外模型请求，可能重试；一次性摘要关闭缓存写入，记录 usage。生成达到长度上限的摘要不能持久化成有效 checkpoint；文件读取和修改清单作为摘要详情与文本线索保留。应移植这些正确性约束，不应直接移植固定 token 数字，或承诺摘要总能省钱。[摘要有效性](D:/03_projects/AI-tools/pi/packages/coding-agent/src/core/compaction/compaction.ts:580)、[摘要调用](D:/03_projects/AI-tools/pi/packages/coding-agent/src/core/compaction/compaction.ts:612)、[文件线索](D:/03_projects/AI-tools/pi/packages/coding-agent/src/core/compaction/compaction.ts:1056)

高层重试将失败尝试保留在原始历史、从模型投影中排除，然后在有界、可中断退避后继续；不能把错误消息一遍遍塞回工作集。自动化 Worker 还应区分安全可重试的模型请求、必须等答案的缺失输入、认证/额度故障、以及不能盲目重放的未知副作用。[恢复投影与退避](D:/03_projects/AI-tools/pi/packages/coding-agent/src/core/agent-session.ts:3709)

`agent_end` 仅是底层循环结束事件；高层另有 `agent_settled`。我们的 UI 与资源释放也应区分模型响应结束、工具进程实际安静、等待确认答案、整个 attempt 结束，而不是收到一段最终文本便释放目录/租约。[底层事件](D:/03_projects/AI-tools/pi/packages/agent/src/types.ts:514)、[高层安定事件](D:/03_projects/AI-tools/pi/packages/coding-agent/src/core/agent-session.ts:1050)

## Python 移植建议：推断，不是已交付行为

| Pi 机制 | Python Worker 落点 | 自动化场景补充 |
| --- | --- | --- |
| loop 与 transport 分开 | LoopPolicy、ModelTransport、ToolHost 独立接口 | 同一 attempt 冻结引擎、账户、模型与权限，拒绝静默计费降级 |
| 请求前上下文投影 | 规范消息日志、WorkingSet 投影与组成账目 | 保留约束、授权版本、待解决问题、工具调用/结果身份；不要上传源文件 |
| finishTurn 与 prepareRequest | 完成校验、预算门、控制队列消费 | PASS 需证据；缺失输入转正式 question，而不是继续猜测或无限 repair |
| 批次工具与顺序结果 | 独立读取允许有界并行，修改和进程按资源互斥 | 新动作前核验租约与权限；取消后不派新动作，已开始动作需真实收敛 |
| steering/follow-up | 有版本、持久化的控制输入 | 人工并非必需；安全边界应用，明确 accepted/received/applied，输入不增加授权 |
| 按请求取 OAuth 凭据 | CredentialProvider + Coordinator | 凭据置于工具根目录之外，跨进程刷新互斥、代次、崩溃恢复与账户连续性 |
| 原始历史与恢复投影分离 | 本地完整证据与有界模型历史 | 模型流必须确认终态，不能执行截断或失败响应中的部分工具计划 |

建议先保持现有 FD-079 默认原生 Codex 工厂控制/问答范围，保留 app-server。独立 Python loop + ChatGPT OAuth 是后续清晰的 transport/auth 切面；接入时以已核对的[本项目官方合同设计](../features/archive/FD-079/OAUTH-RESPONSES-DESIGN.md)为准，不把 Pi 的 legacy 路由、固定客户端或简化安全校验当作必须兼容的合同。

## 证据范围与未验证项

已运行的命令只有 `Get-Content`、`rg` 等源代码阅读和文档写入后的静态差异检查；未运行 Pi、依赖安装、测试、编译、基准、OAuth 登录/刷新或真实推理。源码中有针对动态注册、缺失 scope、签发 client ID 与刷新轮换的 mock 测试，但本轮仅读取名称与断言相关位置，未执行，不能报告通过。[已有 OAuth 测试](D:/03_projects/AI-tools/pi/packages/ai/test/openai-chatgpt-oauth.test.ts:69)

该 checkout 无法证明 Pi 比 Codex 更快或更便宜。可测假设是：减少无效模型往返、稳定输入前缀、限制输出和工作集、消除重复工具读取；仍需同模型、同推理强度、同仓库快照、同权限与验收任务的成功率、总耗时、usage 与恢复成本对照。订阅限额使用量不等于 API 账单，未知货币费用不能填零。

没有复制实现代码。仓库根 LICENSE 为 MIT；如果未来实质移植原实现，需要保留其版权与许可声明，依赖与源文件例外另按实际来源确认。[许可文本](D:/03_projects/AI-tools/pi/LICENSE:1)
