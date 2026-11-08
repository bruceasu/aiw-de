# FD-085: DeepSeek model connections for Worker factory and goal discussions

**Status:** Complete
**Revision:** 7
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

用户要求 Worker 工厂与目标讨论同时支持 DeepSeek。现有 Python SDK 路径仅按 OpenAI 服务商选择 Profile，讨论页面仅提供 OpenAI/Codex。

## Options and decision

保留 openai-agents 运行时，明确区分 openai/deepseek 服务商和 responses/chat-completions 协议。DeepSeek 默认 Responses，不自动切换协议、服务商或账户。Codex 与 ChatGPT OAuth 保持原路径。

## Solution

工厂 Settings 增加服务商/协议，旧配置缺省 openai/responses。DeepSeek 使用官方 HTTPS 地址和执行设备的 DEEPSEEK_API_KEY；不复制 OpenAI/ChatGPT 凭据、不新增明文密钥保存。模型目录、Worker 登记、Profile 选择与讨论冻结明确携带服务商；协议纳入工厂兼容键。配置任务的 Binding 增加可选冻结协议，旧构造与历史 JSON 保留；Java 领取检查也拒绝错误服务商/协议，避免先领取再失败。无 schema migration。DeepSeek 两种协议均使用显式客户端历史并提供思考适配；Chat 完整回传推理与工具消息，在流结束后向 SDK 提交完整回合。

## Scope

覆盖 Java 控制平面、工厂与目标讨论 UI、Python Worker 和临近文档。复用秘书已有的持久日志与恢复边界，不修改其私人存储、连接或 UI；不改变其他语言 Worker，不安装依赖、启动服务或调用真实模型。

### 已确认的后续完善（2026-11-08）

用户确认按三个批次继续：第一批补齐能力声明、思考续轮、Schema 与流式异常；第二批完善协议感知的上下文、会话恢复与成本观测；第三批推进图片附件和 Files 生命周期。既有秘书已提供持久运行日志、暂停/继续及在途 UNKNOWN 恢复，不重复实现，也不将私人会话与 Plane 自动共享。Messages 保留为可选协议，非前三批必需项。

第一批使用现有公开 SDK Model/ModelResponse seam。默认工具 Python 环境无 openai-agents 元数据，不安装依赖，源码编译不能证明真实 SDK 兼容性。DeepSeek Chat 通过适配器保存完整助手消息与推理，回合内读取流但仅在完成后向 SDK 提交结果；不依赖 SDK Chat 转换器保留私有字段。标准端点使用非 strict 工具声明，原始 Schema 在本机始终校验，不自动转向 Beta。模型连接能力标注为文档声明，不能伪称已实测。

## Work items

- [x] 1.1 保存/验证服务商与协议，传播至工厂 Profile、兼容键和 Worker 环境。
- [x] 1.2 Python SDK 接入两种协议与 DeepSeek 参数，严格选择实际服务商。
- [x] 1.3 工厂 UI、模型目录和目标讨论增加 DeepSeek 入口。
- [x] 1.4 更新文档并完成编译和静态核对，记录未运行验证。
- [x] 2.1 DeepSeek Chat 原生消息与推理回传适配、严格流结束与工具配对。
- [x] 2.2 协议能力声明、标准端点 Schema 适配及运行过程中的应用层限制。
- [x] 2.3 完整消息组的压缩边界、附件保留和部分已知用量记录。
- [x] 3.1 需求讨论显式选择图片，冻结图片输入并传递到匹配 Worker。
- [x] 3.2 远端文件上传/复用/过期/删除范围和调用的实际归属。
- [x] 4.1 补充本轮编译、静态证据和未执行检查；独立审查仍由主线程完成。
- [x] 5.1 修复独立审查 P1：允许并约束模型终态用量来源字段，补充真实 Evidence 和 SDK Model 接口替身的回归源码。

### 本轮契约与边界

- Goal 只冻结明确勾选的图片，4 张/512 KiB 上限；冻结 JSON 增加 images（缺省空）和 oauth（缺省 false），模型/协议/版本从同一保存快照读取。无需 migration。
- Worker claim 的 modelImages 只在非空时输出，图像任务要求新的 model-images-v1 能力，旧 Worker 不领取；相同 actor 的 Goal 附件权限不变。图片正文不进入观察快照。
- 可配置 inline/files，默认 inline。Files 在本次模型调用范围内使用当前连接、确认 1 小时有效期、请求内复用、仅清理自己创建的 ID。清理未知单独记录、没有自动重试；不把清理未知解释为模型输出失败或文件已删除。未估算文件存储价格，不宣称 Token 费用等于全账单。
- 原有会话与 UNKNOWN 对账机制保留，SDK/认证/权限不跨设备自动共享；本轮没有无条件跨进程模型回放。Messages 仍是可选未来适配器。

## Acceptance

- 工厂能明确选择 DeepSeek、协议和模型，模型目录只使用 DeepSeek 凭据。
- DeepSeek Worker 只选择 DeepSeek Profile，OpenAI Worker 不领取 DeepSeek 讨论；旧 Codex/OpenAI 行为保留。
- 讨论可保存并提交 DeepSeek 后端；无匹配资源时明确失败，不静默回退。
- 缺少 Key、无效服务商/协议、不匹配 OAuth 组合明确失败；页面和日志不显示密钥。

## Verification

### 独立实现审查 A（2026-11-08）

- 独立 Reviewer `codex-fd085-reviewer-20261108` 认领 `FD-085-000003-review-requested`，对本工作区未提交实现作静态审查，结论 **CHANGES REQUESTED**；[报告 A](reviews/2026-11-08-independent-review-a.md) 记录 SDK 回合 Evidence 字段白名单遗漏 `usageSource` 的 P1 阻断。
- Reviewer 没有运行额外测试、编译、依赖安装、服务或网络；原 32 项定向测试仍按原记录范围保留，不能证明真实 SDK 回合通过。修复后需要对真实 Evidence 接口与模型终态调用链进行聚焦复核。

### 独立聚焦复核 B（2026-11-08）

- Reviewer `codex-fd085-reviewer-20261108` 认领 `FD-085-000006-implementation-ready`，对 A 的 P1 修复及真实 Evidence/SDK seam 回归源码进行聚焦复核，结论 **PASS**：[报告 B](reviews/2026-11-08-independent-review-b.md)。`usageSource` 已纳入有界白名单，真实 Evidence 不再拒绝合法完成回合；非法值仍拒绝。
- 本轮未运行额外测试；新增回归源码尚未执行，真实 SDK/API/Files、浏览器与跨进程行为仍未验证。原有 32 项定向测试不因本次静态复核扩展其实际验证范围。

以下保留第一批实现证据；后续实现的最终结果见“本轮实际验证”。

- 已静态阅读工厂 Settings/Provisioner、GoalTaskPolicy、FactoryModelCatalog、Python SDK 和 Profile 选择，以及工厂/讨论页面。
- 2026-11-08 15:15:43 +09:00：串行执行离线 `mvn -o -s .ai/fd/FD-081/offline-maven-settings.xml -Dmaven.repo.local=D:/.m2/repository -DskipTests test-compile`，BUILD SUCCESS；编译 339 个生产源、68 个测试源，11.137 秒，无测试执行。JAVA_HOME=C:/green/jdk-25.0.1+8；Maven 使用现有低内存配置，不下载依赖。
- 随后执行 `node scripts/compile-worker-runtime.mjs`，通过 TypeScript/JavaScript/Python 语法及 Go 编译；无类型检查、模型调用、网络调用、测试或保留分发产物。
- 一次静态核对执行 `git diff --check`、tracked diff/stat，以及新增 API 连接模块和 FD 索引只读检查。diff check 无错误；execution_configuration.py 的既有 CRLF 有 Git 行尾提示，未执行全库格式化。已追踪 Settings → Provisioner → Worker 身份/Profile → SDK 协议，以及讨论冻结和配置任务领取检查。
- 已新增 Java 设置/领取隔离与讨论协议冻结用例、目录凭据路由用例，以及 Python 凭据/同名模型跨服务商隔离用例。Java 测试源通过编译；测试均未执行，不记为通过。Python 新测试文件未由现有 production-only 编译入口覆盖。
- 真实 DeepSeek 模型目录/推理/工具续轮、浏览器操作、服务启动、真实凭据与部署环境均未执行；不能据编译证明真实兼容性。未创建 worktree、提交、发布或改变运行服务。
- 跨模块模型路由需要独立审查。本侧对话禁止子代理及代理交互；不在此调度审查、不伪造 PASS、不标记 Complete。

## TODO

- 独立静态审查和 P1 聚焦复核已完成，详见报告 A/B；按用户授权继续提交与归档。
- 真实 SDK Runner、模型/Files API、浏览器、服务、秘书回归和跨进程恢复均未验证；不能以纯协议用例代替。默认工具 Python 无 openai-agents 元数据，不代表用户指定的其他 Python 环境也缺少 SDK；未安装依赖。

## 本轮实际验证（2026-11-08）

- 用户明确回复“允许这一次定向测试”，授权三组标准库 Python 和四组离线 Java；未授权真实 SDK、模型或服务操作。
- 离线 `mvn -o -s .ai/fd/FD-081/offline-maven-settings.xml -Dmaven.repo.local=D:/.m2/repository -DskipTests test-compile`：341 个生产源、69 个测试源通过，12.213 秒，16:06:46 +09:00。
- 现有 `node scripts/compile-worker-runtime.mjs` 通过 Worker 与页面编译；goals.js 纳入现有入口。客户端清理/流限制和最终协议路由修正后刷新相关编译，没有无变化重复、下载或保留分发产物。四个变更的 Python 测试源另经 compile() 语法编译，不执行模块。
- **首次执行，无重试**：在 workers/process-python 执行 `python -m unittest test_deepseek_protocol test_history_groups test_model_images`，12 项通过，0.151 秒；无 SDK、文件上传、服务或网络。
- **首次执行，无重试**：`mvn -o -s .ai/fd/FD-081/offline-maven-settings.xml -Dmaven.repo.local=D:/.m2/repository -Dtest=DeepSeekFactorySettingsTest,DeepSeekDiscussionTest,GoalImagesTest,FactoryModelCatalogTest test`，20 项通过（5+3+3+9），失败/错误/跳过均 0，15.033 秒，16:14:22 +09:00。包含最终修正后的 341/69 源编译；全部 mock/内存数据，无真实凭据或网络。
- 合计 **32 项定向测试通过**。覆盖推理与工具消息回传、配对、流结束/截断、歧义参数拒绝、响应组压缩边界、图片哈希/观察脱敏、请求内 Files 复用与清理范围，以及配置/目录凭据隔离、单快照冻结、图片归属/大小/历史默认、两种服务商的协议路由。
- 一次静态检查含 tracked `git diff --check`/stat、claim/图片/日志链路、WebConsoleApiRoutes 根路径覆盖与客户端清理核对；无 whitespace error，有原有 CRLF 提示。最后协议修正由后续编译和已授权用例覆盖，没有重复扫描全仓。
- OpenAI Chat 也声明明确协议标签；两种服务商都冻结显式协议，旧工厂快照缺省 Responses。历史未指定协议的外部 Profile 仍由其执行通道决定。图片、推理与工具参数不改变原有执行权限；新日志字段只加入受限元数据白名单。
- 原有 Worker/秘书 UNKNOWN 对账和暂停机制复用，没有新增无条件跨进程回放；私人会话/连接不自动共享。暂不接入 Messages。
- Files 默认不启用；inline 为默认传输。files 只在当前模型调用中处理明确选中的图片，确认 1 小时有效期，请求内复用，只删除本次创建的 ID。清理未知单独记录，不改写为已删除或重放操作；未估算存储价格，不宣称 Token 用量等于全账户账单。
- 32 项本地用例不等于真实模型兼容性通过。尚无独立 Reviewer PASS，本侧不调度代理、不创建审查回执、不提交/归档或标记 Complete。

## 审查修复（2026-11-08）

- Worker session `codex-fd085-worker-20261108` 认领 `FD-085-000004-changes-requested`；审查报告 A 的 P1 指出 SDK 完成回合时用量来源字段未获 Evidence 白名单接受。
- Evidence 允许 `usageSource`，并只接受 `provider-response` 和 `unknown`，其它字段仍按原白名单拒绝。新增 `test_evidence_usage.py` 使用 SDK 接口替身与真实 Evidence，覆盖已知/未知用量及非法来源；按本次用户限制未运行该测试。
- 修复后 `node scripts/compile-worker-runtime.mjs` 退出 0；对新测试源码使用 Python 内置 `compile()` 语法编译成功；`git diff --check` 退出 0（原 CRLF 提示保留）。没有安装依赖、调用 API 或运行新测试。原 32 项测试是修复前的历史结果，不代表本次回归通过。
- 待同一独立 Reviewer 聚焦核查修复。真实 SDK、服务、模型及 UI 风险不因静态检查消失。

## Sources

- 用户本轮明确要求工厂与需求讨论都支持 DeepSeek。
- https://api-docs.deepseek.com/guides/responses_api/
- https://api-docs.deepseek.com/api/create-response/
- https://api-docs.deepseek.com/api/create-chat-completion/
- https://api-docs.deepseek.com/zh-cn/guides/thinking_mode/
- https://api-docs.deepseek.com/zh-cn/guides/tool_calls/
- https://api-docs.deepseek.com/zh-cn/guides/vision/
- https://api-docs.deepseek.com/zh-cn/guides/files_api/
- https://api-docs.deepseek.com/zh-cn/guides/kv_cache/
- https://developers.openai.com/api/docs/guides/agents/running-agents
- https://developers.openai.com/api/docs/guides/prompt-caching
- https://developers.openai.com/api/reference/resources/files/methods/create
