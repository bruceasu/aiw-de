# FD-084: Secretary SDK execution and safe files with knowledge integration

**Status:** Complete
**Revision:** 14
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

**Design date:** 2026-11-08
**Stage limit:** 用户于 2026-11-08 明确 `$fd-workflow FD-084 auto` 并授权先提交设计；在已记录 worktree 实施、独立审查与本地交付。测试、真实账户/服务操作及网络下载没有因此获准。

## Problem

秘书需要处理持续对话、资料查询、知识整理及受控文件修改。当前已有日常工具循环，但 Agents SDK 只承担单次文本调用，模型返回 JSON 动作后由外层执行；运行中的补充指令、上下文处理、暂停和恢复需要明确合同。用户担心文件修改质量，也希望知识库成为设计中的正式能力。

秘书与 Plane 可能不在同一设备，不能要求日常模型连接重用 Plane 的凭据库、进程或在线会话。用户确认以 OpenAI Agents SDK 为主要执行基础，ChatGPT 登录与 API Key 为主要认证入口；Codex 为其他方式不可用时由用户选择的备用入口。参考 Pi 的有用机制，不安装 Pi、不引入 Pi/其 SDK。

### 已核对的代码事实

- `assistant_settings.py` 仍默认 `codex-app-server`；`assistant_model.py` 分流 SDK、本机 Codex、Chat Completions 与高级命令入口。
- `assistant_model_runtime.py` 的 SDK 分支设置 `tools=[]`、`max_turns=1`；`daily_assistant.py` 外层最多执行 12 步。尚未成为由 SDK 执行工具的秘书。
- `assistant_tools.py` 有路径授权、敏感路径限制、覆盖前备份和临时文件替换。读取存在截断，普通写入仍是整文件覆盖；不能把现有保护描述成完整的并发编辑保护。
- `knowledge/` 已有可选配置、搜索/读取、笔记创建与幂等对账；`conversations.py` 有本地历史、摘要和相关记忆。这些是复用基础，不重新建立一套知识库或记忆数据库。
- `private_config.py` 已有 Windows DPAPI 和进程锁实现。具体保护范围、失败清理、令牌轮换与跨进程正确性仍需针对新用途核对。
- 本轮没有读取私人配置、凭据或运行数据库，未确认用户当前 provider、SDK 安装版本、知识库在线状态或真实 OAuth 资格。

## Options and decision

| 选项 | 决定及原因 |
| --- | --- |
| 直接引入 Pi 或 JS SDK | 不采用；用户明确要求 Python 实现并只吸收机制。 |
| 扩大现有 JSON 文本动作循环为主引擎 | 不采用为主路径；保留两套主要工具循环会重复维护协议、错误和上下文。 |
| Agents SDK 主运行器 + 秘书自己的工具宿主、持久状态和控制器 | 采用；SDK 管理模型/工具回合，秘书管理实际权限、副作用、控制和恢复。 |
| 强制秘书通过 Plane 取得模型凭据 | 不采用；独立部署与日常离线于 Plane 的要求不成立。 |
| 模型故障自动切换 Codex/账户/API Key | 不采用；避免更换账户或计费来源，以及重复执行未知副作用。 |

Pi 借鉴范围：steering/follow-up 的不同语义、规范历史与模型输入投影分离、工具生命周期事件、请求前取得有效凭据。不照搬工具权限、无限执行、已有凭据假定或跨主机刷新方案。Pi coding-agent 的会话/压缩与 agent 核心循环区分；不把现有核心循环当作已经提供完整的自动化恢复。

## Solution

### 1. 组件与所有权

| 组件（拟议职责名） | 输入和输出 | 所有权与约束 |
| --- | --- | --- |
| `SecretaryRunController` | 接收当前请求、定时触发、控制指令；输出运行状态与事件 | 秘书 Python；同一会话串行执行副作用，冻结请求版本、模型连接和工具范围。 |
| `SdkRunDriver` | 将规范上下文和已授权工具交给 SDK，返回流事件、暂停状态和终态 | SDK 承担模型/工具回合；不在外层再解析另一套 JSON 动作并驱动同一次 SDK run。 |
| `CredentialProvider` | 按连接 ID/代次提供短期模型认证材料 | 本机凭据管理；模型和工具不能读取令牌、凭据路径或刷新接口。 |
| `ContextProjector` | 从本地规范历史构造本轮模型输入 | 保持工具调用/结果配对、用户约束和来源；裁剪投影不删除原始记录。 |
| `ToolHost` | 校验并执行文件、知识库及现有业务动作 | 每次调用检查当前授权、控制代次和幂等状态；SDK 注册工具不等于授予权限。 |
| `RunJournal` | 保存运行、调用、控制和检查点记录 | 复用 owner-bound 私库；唯一事实来源，不额外创建另一份可独立推进的任务状态。 |
| `CodexFallbackDriver` | 通过现有 model-only Codex 通道请求动作，使用相同工具宿主 | 独立、互斥的备用驱动；不启用原生 Codex 文件/命令工具，不与 SDK 同时驱动同一次运行。 |

主路径：用户/定时触发 → 运行控制器 → SDK Runner → 模型 → Python 工具宿主 → SDK 继续 → 结构化终态。秘书提供 Python 工具，SDK 调用它们；模型不直接持有文件系统权限。

Codex 备用路径可保留最小的 host-action 适配循环，但不得扩展为第二套业务权限、知识库或文件工具实现。SDK 不可用时仍可明确选择 Codex；两种驱动的事件/终态和恢复边界使用同一合同。切换只能在停止原运行并完成副作用对账后创建新的运行尝试，不能跨驱动反序列化私有 SDK 状态。

### 2. 独立认证与连接设置

- 新安装的执行方式默认显示“OpenAI Agents SDK”；认证来源和模型保持未选择，完成明确配置后才能运行。不自动发现或扫描本机其它应用的凭据。
- SDK 下分为“使用 ChatGPT 套餐登录”和“使用 API Key”，随后显示各自的账户/连接状态、模型、必要配置和修复入口。备用方式显示“本机 Codex（备用）”及程序路径、安装/登录状态。可用性不以配置字段非空冒充已连接。
- ChatGPT 登录发生在秘书自身的信任边界，使用独立 host/session 和受支持的 OAuth 流程。登录成功、取得计划调用 scope、模型可调用分别表示，组织批准和订阅资格错误不得转成普通工具重试。
- 首期交互式 OAuth 的明确部署条件：秘书主机能够打开系统浏览器并接收自己的 loopback 回调。浏览器位于另一设备时，不能把浏览器的 `127.0.0.1` 当成秘书主机；不提供未核对的回调转发、手工 token 粘贴或 device-flow 替代方案。远程/无桌面场景可用 API Key 或已就绪的本机 Codex；扩展 OAuth 回调方式前单独核对官方合同。
- API Key 与 OAuth 令牌保存在秘书私密配置边界；Windows 优先复用已核对的 CurrentUser DPAPI 能力。凭据不进入工作区、普通私库导出、事件、模型上下文和工具参数。其它系统没有可用保护机制时明确说明，不能以明文文件冒充系统密钥库；未支持的平台不能启用长期 OAuth 保存。
- 同一令牌组使用跨进程锁与持久代次；锁内重读并保存 REFRESHING 意图，成功后原子替换整组令牌。刷新结果未知时不重放旧旋转令牌；需要重新授权。不同设备优先独立登录。
- “导入”先定位为可选的显式连接接入：校验格式、客户端、resource、scope 和可验证账户。首期可导入连接元数据后重新授权；原始令牌导入只有在验证来源和唯一刷新归属后才开放，不假定 Codex/Pi/Plane 凭据通用。
- 不远程复制 Plane 的 DPAPI 密文或其本机加密密钥。没有受支持的显式导出/交接协议时，只引导独立登录；本 FD 不增加 Plane token 导出 API。
- 运行固定 `connectionId + generation + account + transport + model`；途中不悄悄切换。模型认证与秘书登录、知识库认证、平台 Agent 身份完全分开。

### 3. SDK 请求与工具合同

API Key 与 ChatGPT OAuth 共享业务工具，使用不同的模型请求能力配置。API Key 主路径使用 SDK 的常规模型提供者；OAuth 路径需要受控的 SDK Model/provider 适配，不能仅把 API Key 替换成 bearer token。

OAuth 适配必须核对实际 SDK 版本和官方预览合同：公开 `/v1/responses`、数组形式的 `input`、`store=false`、`stream=true`、显式历史、完整响应终态、函数/自定义工具的有效组织格式及不支持参数。禁用不适用的服务器会话/续接参数，完整保留需要回放的模型输出项与工具对应关系。API Key 和 OAuth 不共用未经区分的参数字典。

工具仅在模型响应完整且被接受后派发。可以展示增量文字，但不能执行尚未完成或最终失败的模型响应中的动作。SDK/适配器必须证明此边界；不满足时阻断 OAuth 工具运行，而不是静默退回旧接口或宣称 SDK 全能力可用。

SDK 中每个函数工具都是 `ToolHost` 的薄适配。调用输入由 Python schema 验证，控制器生成稳定 `runId/callId` 和参数摘要；模型不能凭自报 `approved=true`、requestId 或工具文本取得权限。工具输出包含实际状态、必要内容、来源和裁剪标记；异常与结果未知不伪装成成功。

完成结果至少包含：回答、已核验交付物引用、来源、未完成项和停止原因。最终自然语言不能创建文件/笔记“已成功”的证据；只能引用实际工具回执。订阅用量缺少货币计价时显示未知，不套用 API Key 价格。

### 4. 持续会话、steering 与 follow-up

- 同一会话只有一个活动运行。每条用户输入和控制命令先持久化，带唯一 ID、顺序及目标运行/请求版本；重发同一 ID 不重复入队。
- steering 表示“调整当前工作”：在下一个安全边界合入。模型/工具正在执行时显示“调整待应用”，不宣称已经停止。控制代次变化后禁止开始旧动作批次中尚未派发的副作用。
- follow-up 表示“当前工作结束后再做”：作为新的用户请求排队，不改写当前授权、目标或期限。界面允许用户明确选择，歧义时先作为待确认补充，不自动扩大任务。
- 串行处理同会话控制与副作用。已有在途工具返回后先记录结果；被跳过的调用有明确取消/未执行结果，保持 SDK 历史配对。通过受支持的 SDK 状态接口恢复；若版本不能安全支持在途 steering，先在完整回合结束后应用，并明确显示边界，不修改 SDK 私有字段。
- 摘要保留明确要求、否定约束、待办、当前文件版本、工具结果引用、待确认事项及知识引用。完整历史不被摘要覆盖；文件/网页/知识库内容始终是资料，不成为授权或新指令。
- 每次运行有有限模型回合、工具次数、输入/输出和总期限；沿用现有明确上限作为初值，不默认为无限。对照实际 SDK 的回合含义转换预算，不能简单把旧“12 步”当成等价 SDK turns。
- 暂停与取消是控制动作；预算结束、认证错误、授权等待与工具失败是不同停止原因。定时任务沿用原时区、过期确认与固定计划版本规则，不自动补跑错过的任务。

### 5. 运行状态、事件与恢复

运行状态为 `QUEUED/RUNNING/PAUSE_REQUESTED/PAUSED/WAITING_USER/WAITING_AUTH/RECONCILING/SUCCEEDED/FAILED/CANCELLED`；状态变更持久化后才对外报告。用户输入和权限确认保留原文及原请求关联，不因恢复自动批准。

事件使用 `runId + monotonic sequence`，记录类型、时间、请求/连接代次和安全摘要。必要类型：model start/delta/end、tool queued/start/end、control queued/applied、checkpoint、waiting、terminal。事件序号用于去重和重连读取；私密正文仅通过已认证的秘书界面读取，不流向 Plane 通用日志。展示模型解释和工具行为，不承诺暴露模型内部推理。

每次副作用前持久化调用意图，完成后保存结果和副作用标识。进程中断后：

1. 重新检查当前身份、模型连接、工具权限和控制代次；不因历史记录恢复已撤销权限。
2. 纯读取可按原预算重新进行。文件修改查操作日志、目标摘要与备份；知识库笔记按原幂等键对账；平台业务动作沿用原 outbox/正式回执。
3. 结果未知先进入 RECONCILING，不重复执行。不能确认的保持未知并说明下一步。
4. SDK 可序列化状态只存本地，并记录驱动、SDK 版本和能力配置；版本不兼容时停止私有状态恢复，保留规范历史和待核对副作用。不得使用不可信 pickle 或导入记录激活任务。
5. 暂停不撤销已完成动作；取消不等于在途远端动作被撤销。资源无法确认停止时禁止启动新尝试。

### 6. 文件修改可靠性

普通文档默认生成修订副本，保留原文件；用户明确要求写回原文件或选择应用修订时，使用受控写回。无需用户手工处理补丁，但界面必须说明目标、修改差异、实际结果和恢复入口。继续使用现有具体路径/允许根目录授权，不增加通用 shell 工具。

拟议文件接口：

- `read_file(path, range?)`：返回规范路径、文件身份/版本摘要、编码/换行、读取范围及 `complete/truncated`。部分读取不能生成无依据的整文件替换。
- `create_file(path, content)`：仅新建；发现同名文件返回冲突，不自动覆盖或改名后假称原目标已完成。
- `prepare_edit(path, expectedVersion, edits)`：针对读取过的版本生成修订和可展示差异。局部修改必须精确匹配且位置唯一；不匹配、重叠或匹配多处时停止，不使用猜测性模糊替换。大文件通过有版本依据的局部处理保留未读部分。
- `apply_edit(editId, expectedVersion)`：重查当前路径、文件身份与版本，完成备份和操作日志后按平台能力写回。编码/BOM/换行保持原约定；不默认把所有文件重写为 UTF-8/LF。写入后核对摘要，再返回成功。
- `restore_file(operationId, expectedCurrentVersion)`：核对当前文件再恢复备份，防止回退覆盖用户后续编辑；恢复本身也产生记录。

工具返回 `CREATED/UPDATED/DRAFT_READY/CONFLICT/NOT_EXECUTED/UNKNOWN` 及安全的 before/after 摘要、差异/备份引用。备份和内部日志位于工具不可任意读写的私密边界；模型不能借备份目录读取其它文件。

应用内文件锁只能协调遵守协议的写入者；普通“比较 hash 后 replace”不能承诺排除其它程序在两者之间写入。首期默认修订副本避免覆盖原件；直接写回需要在目标平台验证可靠的文件身份/锁定和替换边界。无法提供所需保护、文件正被编辑、符号链接/路径身份变化或结果不明时保留修订并停止写回，不宣称全平台文件 CAS。

首期支持受控纯文本/Markdown。Word、PDF、表格和二进制文件不经过文本替换；没有对应格式工具时解释限制或委派专业任务，不损坏原格式。复杂代码修改可交给已配置的专业 Worker，由既有 Plan/权限和执行方式决定；不默认绕过用户选择转用 Codex。

### 7. 知识库与记忆

知识库连接独立于模型连接和 Plane；允许与秘书不同主机。沿用 `docs/personal-secretary-knowledge-handoff.md` 的服务身份、HTTPS、独立 Bearer、能力与资料集合范围；不增加 MCP、共享数据库、共享索引目录或本机子进程。

现有 `knowledge_search/knowledge_read/knowledge_save_note/knowledge_note_status` 映射为 SDK 函数工具，复用客户端和持久幂等记录。默认可选关闭，普通聊天不依赖知识库；未授权/未启用的工具不注册，不因模型要求自动开启。

| 内容 | 保存位置与使用规则 |
| --- | --- |
| 原始资料、长期文档和笔记 | 知识库；首期只读资料、创建新笔记，不增加更新/删除原文接口。 |
| 私人聊天、偏好、摘要、任务状态和授权 | 秘书本地；不全量上传、不自动双写知识库。 |
| 搜索结果、按需正文片段 | 当前模型工作集；保存 documentId/revision/location/source/freshness 与截断标记，保留引用到答复/交付物。 |

一次请求沿用至多 1 次知识搜索、3 次正文读取和 30,000 字符总知识上下文的当前预算；不在无关聊天中自动检索。计数绑定原请求，暂停、恢复或进入新 SDK run 不能清零；这些限制不等于知识库服务容量。后续调节需要明确配置，不在本轮借 SDK 改写预算。

读取出现 REVISION_CHANGED 时在剩余预算内重新搜索/读取，不能把旧版本引用附到新内容；预算不足则说明需要新的继续请求，不重置计数。无命中、服务不可用、权限不足、部分结果和资料过期分别报告。资料内指令不能提升工具权限或触发保存；引用 URL 必须经过已有可信 origin 校验。

保存笔记必须来自当前用户明确的保存意图，不能仅由模型动作或资料中的“请保存”触发。主机生成并持久化请求 ID、规范正文、来源版本、serviceId 和幂等键；同一动作在恢复/重试时使用同一记录。SDK 工具 ID 不能替代跨进程幂等键。

超时后先查原创建记录，再按契约决定是否以同键同正文重发；服务身份变化或对账失败时保持未知，不换键重建。只有 succeeded 才报告“已保存”，只有 indexState=ready 才报告“已可搜索”。暂停/取消不等于取消知识库已经受理的写入。

典型闭环：检索资料 → 读取相关片段 → 展示带引用的整理结果 → 按用户要求生成本地文件或保存知识笔记。文件与笔记分别报告结果，不能把一个成功当作整项全部成功。

### 8. 设置、兼容和交付边界

- 沿用秘书现有 Web/桌面界面和视觉风格；增加独立的模型连接、知识库连接、运行过程与文件修改记录。错误给出明确原因和可执行下一步，按钮禁用时解释前置条件。
- 保留现有已保存 provider，不能升级后自动更换账户/执行引擎。Chat Completions 和自定义命令保持高级既有入口，不默认扩展新能力；未配置的新安装采用 SDK 优先展示。
- 本地旧工作、消息、记忆和授权记录原样保留。使用带版本的增量运行记录；不能把旧 DONE 当作新执行证据，不能把旧 RUNNING 无条件重新执行。新记录若需要扩展 Store.KINDS/数据库版本，在实施中给出前向升级和旧版本拒绝写入规则。
- 私有设置中已有模型 key 在用户明确保存/导入连接时迁入新连接结构；不自动扫描或删除旧凭据。迁移失败保留原记录并说明；不得将 token 放入 SQLite 普通记录。
- `openspec/specs/secretary/spec.md` 的私库、通用 Agent、正式问题、授权和远程 mTLS 边界继续适用。模型登录不登记/启用平台 Agent；平台未连接时日常功能仍可使用，专业任务不能绕过平台权限。
- 不修改 FD-079/FD-081 等归档结论，不以秘书设计冒充 Plane 或 Worker 已完成新能力。稳定规格在实施落定后按实际行为更新；本轮不创建 OpenSpec change。

## Scope

范围：秘书 SDK 主路径、独立模型连接、Pi 风格的控制与上下文、纯文本文件保护、现有知识库工具接入、相应 UI 和本地恢复设计。

主要实施位置：`personal-secretary/daily_assistant.py`、`assistant_model*.py`、`assistant_codex_runtime.py`、`assistant_tools.py`、`assistant_settings.py`、`conversations.py`、`store.py`、`private_config.py`、`knowledge/`、`runtime.py` 及 `ui/`。模块按职责逐步拆分，不一次性重写整个秘书。Worker 仅抽取无平台状态的合适辅助逻辑；不能从秘书导入带 Worker 注册/租约/Plane 私密依赖的模块。

不在范围：安装 Pi、依赖 JS、通用终端/任意命令权限、自动扩大平台授权、知识库服务端重构/原文删除更新、完整 Office 编辑器、跨主机共享同一刷新令牌、后台静默故障切换、部署/发布及实际账户登录。未知平台和协议能力有明确受限状态，不以假实现补齐。

## Work items

- [x] 1.1 核对当前实现和已确认需求，记录职责、接口、状态、失败恢复及实施边界；本项只代表设计文档完成。
- [x] 2.1 确定 SDK 模型接口及运行时版本/能力校验策略；实现 API Key 主路径、完整终态检查、有限回合和暂停边界。真实安装环境的集成证据另列，不以编译代替。
- [x] 2.2 实现秘书独立连接与保护存储、OAuth 校验/刷新协调及受限模型适配，明确导入、远程浏览器及不支持平台的边界。
- [x] 3.1 将日常/知识/平台工具接入统一 ToolHost 和 SDK，保留既有授权与专业任务合同；隔离 Codex 备用驱动及显式切换。
- [x] 3.2 实现运行日志、控制队列、规范历史和模型投影，接入 Web/桌面过程展示、steering/follow-up、有限预算及重启对账。
- [x] 4.1 实现文件版本读取、修订副本、精确编辑、写回拒绝、备份恢复副本与结果核对；所有当前平台的原件写回保持禁用。
- [x] 4.2 将知识库引用、请求级预算和笔记幂等恢复贯通 SDK/暂停/重启；保留可选模块隔离。
- [x] 5.1 完成设置迁移与既有调度/业务权限调用路径的静态核对，更新秘书 README/稳定规格；执行 compile-only。此项不代表运行回归通过。
- [x] 5.2 准备定向回归用例和独立审查交接材料，记录编译、自检与未执行项；实际测试执行、独立审查结论及交付/归档由 Verification 门槛单独把关。
- [x] 6.1 修复 R1：同一删除事务清除当前工作的运行图及提交正文，不影响其它工作和既有结果文件/备份。
- [x] 6.2 修复 R2：笔记创建/状态查询绑定原连接及凭据指纹；运行对账检查冻结设置，凭据更换或缺少身份信息时禁止查后重发。
- [x] 6.3 修复 R3：SDK 最终文本只进入完成校验，禁止旧动作分发及外层纠错循环。
- [x] 6.4 修复 R4：普通聊天持久提交去重先于新建对话和入队；页面保留未知请求 ID，内容/目标复用冲突拒绝。
- [x] 6.5 修复 R5：业务终态同步 RunJournal，保护取消/成功终态，未决副作用保持对账；失败/取消的 follow-up 明确不执行。
- [x] 7.1 修复 B1：两类删除入口共用运行/副作用门槛，在删除事务内重查；取消未决操作保留原回执，取消后的核对不恢复运行或重发笔记。
- [x] 7.2 修复 B2：受支持的 LegacyTk 发送稳定提交 ID，未知结果保留原消息/目标/标识，明确展示 UNKNOWN；补齐对应源码回归及编译范围。
- [x] 8.1 修复 C1：按真实回执状态判断未决副作用，安全处理 null/非字典结果；补充两次本地拒绝的真实 invoke 重试路径及 public/cancel/reconcile 回归源码。

5.2 原文同时包含实施与独立审查本身，而 CLI 要求实施项完成后才能申请审查；本次保留 ID，将交接准备与 Verification 门槛分开。没有取消独立审查或验收要求，也没有将未执行的测试记为通过。

顺序：2.1 先建立 SDK 的最小可用纵切；2.2 的独立登录和 OAuth 适配可在接口明确后实施。3.1/3.2 建立统一宿主和恢复基础，再落实 4.1/4.2；未经核对的 OAuth 不能阻塞已明确可实现的 API Key 路径，也不能以 API Key 路径成功代表 OAuth 完成。具体测试执行仍需独立授权。

## Acceptance

1. 秘书与 Plane 不同设备且 Plane 不在线时，已就绪模型连接可支持普通聊天、授权文件和知识库操作；专业任务明确等待平台连接。
2. 新安装明确选择认证/模型后才运行；SDK 成为主要工具循环，Codex 明确为备用。已有设置升级不切账户或 provider。
3. ChatGPT/API Key 状态清楚；缺少 scope、组织批准、额度或模型能力时给出对应错误，不进入文件修复循环或自动改用其它付费来源。
4. 模型输出流中断、终态失败、工具 schema 无效时没有对应副作用。 SDK 与 Codex 备用使用同一权限和结果合同。
5. steering 在安全边界生效，follow-up 在当前请求终结后执行；重复提交、暂停、断线重连和重启不丢请求也不重复副作用，未生效控制保持待处理显示。
6. 受控文本任务可以创建文件/修订副本。截断读取不能覆盖整篇；版本、路径或匹配冲突保留原件并明确报告；备份恢复不能覆盖后续修改。
7. 没有可靠原件写回能力时只交付修订副本，不声称已改原文件；复杂格式不会被当作普通文本改坏。
8. 知识检索有版本和来源，摘要/恢复不丢引用；权限、无命中和服务不可用分开报告。知识库未连接不阻断无关日常任务。
9. 用户明确要求保存笔记时，同键同正文跨重试/重启只创建一次；保存未知、保存成功、索引可搜索分别显示。知识库 serviceId 改变时不重放旧动作。
10. 运行中断后先对账在途写入，再恢复；取消不虚构撤销，暂停不虚构资源已停。旧 SDK 状态不能被不同版本或 Codex 驱动直接加载。
11. 凭据与私密正文不进入通用运行事件/Plane 日志；知识库内容和模型登录不能提升文件或平台权限。
12. 报告分别记录实际工具交付、未完成项和已知用量；只有编译/mock 证据时不宣称真实 OAuth、DPAPI、跨机服务或效率成本已验证。

代表性验收情景：从知识库读取两份有版本的资料，整理成 Markdown 修订副本；执行中用户补充“先保留原文件，再把结果存入知识库”；控制在工具边界应用，笔记保存超时后按原键对账，重启后不重复创建，并准确区分文件成功、笔记未知/已保存和是否可搜索。

## Verification

### 设计轮次证据

- 已静态阅读秘书模型入口、日常循环、文件工具、私有配置/存储、会话摘要、知识库工具/设置及既有跨项目契约；对照当前稳定秘书规格和 FD-079/FD-081/FD-083 的历史范围。
- 已对照本地 Pi checkout 的 `packages/agent/src/agent-loop.ts`、`agent.ts` 及 coding-agent 会话结构；SDK 循环和会话职责已查阅官方文档。这些不是性能测量或运行验证。
- `git status --short` 在创建前没有输出；`aiw fd new --help`、`aiw fd list` 用于核对创建方式和已有记录，随后用 `aiw fd new` 分配 FD-084。未启用 runner、handoff 或子代理。
- 本轮只创建 FD 与其导航；Evidence policy 选择当前工作流推荐的 Markdown，替换生成模板的旧 Dual 默认值，不改变历史 FD。
- 已执行一次新增 FD 的静态检查：`git diff --no-index --check -- NUL docs/features/FD-084/FD-084_SECRETARY_SDK_EXECUTION_AND_SAFE_FILES_WITH_KNOW.md`；无空白错误输出，退出码 1 为 no-index 比较存在新增内容的结果。此检查不代表代码、链接或运行合同验证通过；没有重复运行。
- 未运行编译、测试、模型、服务、浏览器或真实凭据操作；docs-only 无需编译。没有进行 git commit、修改稳定规格、发布或归档。

### 实施选择与实际进展

- 用户明确授权 `$fd-workflow FD-084 auto`，并选择先提交设计再继续；设计基线提交 `abf8fc5`。坐标：parent=`develop`，branch=`feature/FD-084`，worktree=`D:/03_projects/AI-tools/aiw-de/.wt/FD-084`，已核对父工作区 `.ai/fd/FD-084/workspace.json`。实现和后续审查/归档均在该 worktree。
- 新增 `secretary_sdk/secretary_host/secretary_runs/secretary_credentials/secretary_oauth/secretary_files/secretary_completion` 职责模块，SDK 只在执行入口延迟导入；Codex 备用不要求安装 SDK。没有新增 Worker 或 Plane 依赖。
- SDK 使用显式本地历史回放策略，不加载 pickle 或 SDK 私有 RunState；每次安全边界退出都保留工具调用/结果配对及明确的 NOT_EXECUTED/WAITING_USER/UNKNOWN。SDK 内部若转换工具异常，持久阻断标记仍阻止后续模型/工具调用。每回合只接受一个函数调用。
- API Key 适配通过 OpenAI Python SDK 取得原始 Responses 完整终态，再返回 SDK ModelResponse，避免抽象层丢失整体 status；同一次运行复用 HTTP 客户端。OAuth 走公开、有限 SSE 文本通道，将有效文本动作转换为 SDK 函数调用；不发送未经核对的原生 OAuth tools/namespace，不把它描述为完整原生工具支持。
- 模型/知识库连接选择与代次固定，旧环境 API Key 来源保留且绑定指纹；新安装不因已有环境变量就选择认证。新请求明确配置后才调用模型。凭据库有跨进程刷新锁、REFRESHING 意图、未知结果阻断、整体轮换与存储上限；取消/过期登录不能写回旧会话。
- 私库 v1→v2：先取得服务锁、保存有界的受限 SQLite 升级前快照，再事务更新版本；运行中的旧服务不能被另一进程先升级。新增 runtime kind 与事件/来源保存在同一私库，旧版本启动拒绝 v2。检查点不向旧的 personal_history 反复复制完整上下文；界面使用 SQL 元数据投影，原个人记录版本仍保留。
- 文件操作不覆盖原件，保留 BOM/换行与完整版本；发布副本使用已完整写入临时文件的排他创建，冲突不覆盖；版本/备份校验支持差异和恢复副本。原件写回明确返回未执行。未知发布通过原操作日志与目标摘要对账。
- 知识预算按原运行保存；笔记以原 serviceId/正文/幂等键对账，pending 状态暂停协调并提供核对入口。保存意图与保存方法问答、普通文件保存分开，后来的“不保存”撤回原入库意图；没有新增原文更新/删除接口。
- 两次执行 `& ./scripts/compile-secretary-runtime.ps1` 均退出 0：首轮实现后一次，补齐终态/恢复边界及相关用例后一次。仅 Python（含知识库与新增用例源码）、页面脚本及脚本语法，没有 SDK 导入、测试、网络、服务、模型或保留构建物；没有重复运行未变化的失败命令。
- 已编写 `tests/secretary/test_fd084_core.py`、`test_fd084_sdk.py`，覆盖文件、私库升级、凭据刷新、控制、知识库幂等、完整模型响应与 SDK 边界。SDK/HTTP/JWT/密钥保护使用替身；测试执行申请已提出，尚未收到授权，未执行。
- 实现已提交 `825f4d1`。首次审查申请被 CLI 以“all FD Work Items must be completed or explicitly cancelled”拒绝，尚未创建审查事件；将 5.2 的交接准备与实际 Verification 分开后重新申请。尚未归档或本地合并。未修改用户真实凭据/数据库，未安装依赖、登录、启动服务、调用模型、push 或发布。

### 独立审查（2026-11-08）

- Reviewer session：`codex-fd084-reviewer-20261108-a`；精确认领源事件 `FD-084-000004-review-requested`，在本 FD worktree 审查 `abf8fc5..27f29a1` 的 25 文件完整范围。
- 结论：**CHANGES REQUESTED**；[独立报告](reviews/2026-11-08-independent-review-a.md) 记录五项静态可证明缺陷：删除遗漏运行正文、知识库换主体对账可能重发、SDK 最终文本进入旧动作循环、普通请求缺少提交去重、异步回执未同步运行终态/follow-up。
- 未执行测试、编译、模型/服务、网络或真实凭据操作；没有把未授权执行列为硬缺陷。Worker 原编译记录仅按语法证据保留。修正上述缺陷后需要按实际变更进行聚焦复核；本轮没有通过 Verification，也未归档/交付。

### R1–R5 修复交接（2026-11-08）

- Worker session `codex-fd084-worker-20261108` 已认领 `FD-084-000005-changes-requested`，保留首轮失败历史。
- 删除操作在原事务选出 workId 对应运行/提交及所有 runId 子项，同时清除 live/history；其它工作、结果文件和已有备份保留。新增回归检查删除后的私库导出不再包含原运行正文。
- 知识客户端与身份快照一起捕获；身份包含地址、serviceId、credentialRef 和令牌指纹（没有明文令牌）。所有笔记重放及状态查询先比较原身份，旧记录缺失信息时拒绝。运行对账也比较冻结 binding，不能用新账户对原笔记查后重发。
- SDK 最终结果只允许自然语言结果或 finish 信封，任何其它动作均拒绝；专业委派未发生时不会启动外层 SDK 纠错循环。Codex 明确备用适配保留。
- `/api/chat` 明确要求 requestId；服务先持久化原内容、目标、连接与确定的消息 ID，再创建对话/排队。重发返回原消息；接收后中断但尚无消息时返回 UNKNOWN，禁止自动重排。页面跨刷新保留 ID/摘要，未知响应保留原提交；不保存正文到浏览器会话存储。
- DAILY_PLAN 可信终态统一同步 RunJournal；取消/成功终态不可被晚回执或工具退出覆盖。快速回执在工具结算后再同步，UNKNOWN/STARTED 副作用阻止提前完成。失败/取消的 follow-up 明确 NOT_APPLIED。
- 新增 `test_fd084_review.py`，静态覆盖删除/备份、SDK 最终文本旁路、持久提交重发/冲突/接收后中断、平台正式回执、专业晚回执、快速完成和未知副作用；补齐原知识用例的连接替身。
- 修复后执行一次 `& ./scripts/compile-secretary-runtime.ps1`，退出 0（Python、JS、PowerShell 语法，无 SDK 导入/测试/网络/保留构建物）；执行一次 `git diff --check`，退出 0，并检查变更与上述调用路径。
- 定向测试授权仍未收到，没有运行测试、真实 SDK/OAuth/DPAPI/知识库或服务。该限制不记为测试通过；修复等待同一 Reviewer 聚焦复核。

### 独立聚焦复核 B（2026-11-08）

- 同一 Reviewer session `codex-fd084-reviewer-20261108-a` 已认领 `FD-084-000007-implementation-ready`；按真实 receipt 核对 Revision 7，聚焦审查 `27f29a1..f2ee9df`，保留首轮报告。
- 结论：**CHANGES REQUESTED**（累计第二次失败，三次失败上限保留）；[聚焦报告 B](reviews/2026-11-08-independent-review-b.md) 记录两项剩余问题：B1/R1 删除入口未保护暂停/待对账及未结副作用；B2/R4 强制 requestId 后未更新受支持的 LegacyTk 聊天调用方。
- R2/R3、R5 快速/晚到回执保护以及 Web/服务端提交去重的相关静态路径已有支持；接收后中断明确保留 UNKNOWN，不记为执行通过。未运行测试、编译、服务或网络，未修改实现。修复 B1/B2 后仅作相应最终聚焦复核。

### B1/B2 修复交接（2026-11-08）

- Worker 沿用 `codex-fd084-worker-20261108`，读取 pending receipt 后正式认领 `FD-084-000008-changes-requested`。保留前两轮失败结论，下一次仅聚焦 B1/B2 及受影响恢复路径。
- B1：Store 提供两入口共用门槛，拒绝非终态/缺少状态、hasUnknownEffect、hasWaitingTool、工具 STARTED/UNKNOWN/WAITING 或 pending/unknown 远端保存。在实际删除事务及同一 Store 锁内重查，避免入口检查后状态变化。可安全终结的运行仍按原清理范围删除正文。
- 取消时关闭未获准的 WAITING 工具（NOT_EXECUTED），保留未知副作用；取消后的核对只查原文件/原笔记并保持 CANCELLED。知识状态未找到时禁止再 POST；无法验证则继续保留原回执，页面允许取消后未知结果核对。
- B2：LegacyTk/PersonalClient 保存原 payload 和提交 ID；未知结果保留编辑框，轮询自动选中新会话也不会改变原提交目标；连接改变时禁止重发旧正文。已知回复后新消息生成新 ID；明确“核对后另发请求”只结束本机重试记录，不撤销服务端操作。重试记录限当前客户端进程，不宣称跨进程持久。
- 新增两类公开删除入口、事务内复查、取消后未知/笔记 pending、已取消文件核对、未准工具取消及 Tk 丢响应/UNKNOWN/新请求 ID 的回归源码；允许删除样例改为 SUCCEEDED。
- 两次 compile-only 均通过：先覆盖删除/客户端修复，再在补齐 Tk 轮询目标保持与连接隔离后编译最终源码。编译脚本新增 desktop*.py 语法范围；不导入 GUI/SDK，不执行测试或服务。一次 diff-check 在第二次 Tk 小修前退出 0；最终 diff 将由 Reviewer 按其预算核对，不将前次检查冒充最终检查。
- 本轮未获得定向测试执行答复，所有新增回归仍为 not-run；无真实账号、模型、网络、DPAPI、数据库升级或服务操作。

### 独立最终聚焦复核 C（2026-11-08）

- Reviewer session `codex-fd084-reviewer-20261108-a` 已读取并认领 `FD-084-000010-implementation-ready`，真实 Revision 10；仅复核 `f2ee9df..2c94399` 的 B1/B2 和直接恢复/桌面路径。
- 结论：**CHANGES REQUESTED**；[报告 C](reviews/2026-11-08-independent-review-c.md) 记录 C1/P1：新增 unresolved_effects 对宿主重试产生的 result:null 调用 .get，使状态页及取消/对账路径失败。删除门槛和 LegacyTk 提交 ID 的主修复静态已有支持。
- 累计第三次失败，保留 A/B 历史并达到三次失败上限；本 Reviewer 提交结论后停止，不自动开启额外修复/复核循环。没有通过 Verification，未运行测试/编译/服务/网络或修改实现。

### C1 修复与单次额外复核授权（2026-11-08）

- 用户对“再做一次定向修复和静态复核”明确回复 `confirm`，允许在已累计三次失败后增加且仅增加这一轮；没有重置失败历史，也未授权测试、网络、服务或真实账户操作。Worker 沿用原 session，正式认领 `FD-084-000011-changes-requested`。
- `unresolved_effects()` 先排除 NOT_EXECUTED，UNKNOWN/STARTED 仍保持未决；其它回执仅在 action/result 都是字典且包含实际 pending/unknown 笔记结果时识别远端未决保存。null/字符串/列表/数字不会被当成已受理保存，也不会对其调用 `.get()`。
- 新增回归源码通过真实 RunJournal.invoke 连续两次 KnowledgeNotExecuted，检查落盘 NOT_EXECUTED + result:null；随后覆盖 public、暂停后取消与无远端查询的对账。另覆盖非字典结果和实际 UNKNOWN/STARTED、pending/unknown 的状态区别。没有执行这些测试。
- 实际执行一次 `& ./scripts/compile-secretary-runtime.ps1`，退出 0；一次 `git diff --check`，退出 0，并查看 diff stat。均无 SDK/GUI 导入、模型/网络、运行验证或保留构建物。源码静态核对上述 helper 与 public/finish/reconcile 调用链。
- 交回同一 Reviewer 做本次唯一新增聚焦复核；通过后按原 auto 授权继续本地归档和交付，不 push/发布/部署。若仍失败则保存证据并停止，不开启第五轮。

### 独立单次额外聚焦复核 D（2026-11-08）

- Reviewer session `codex-fd084-reviewer-20261108-a` 读取真实 pending receipt 后认领 `FD-084-000013-implementation-ready`，核对 Revision 13 与 `08af2ab..e11a283`。本轮仅复核 C1 和 public/cancel/reconcile 直接路径。
- 结论：**PASS（独立静态实现审查）**；[报告 D](reviews/2026-11-08-independent-review-d.md) 关闭 C1，原 R1–R5/B1/B2 结合此前静态证据无剩余 Reviewer findings。NOT_EXECUTED/result:null 不再误读；真实 UNKNOWN/STARTED 和 pending 保存仍保持未决。
- 原三次失败及 A/B/C 报告保留；本轮依据用户 confirm 对单次额外定向修复和静态复核的例外授权，没有重置失败历史或扩大范围。
- Reviewer 没有执行测试、编译、模型/服务、网络或真实 OAuth/DPAPI/知识库/迁移。新增测试仍为 not-run，实际运行风险继续保留；本 PASS 不表示运行验证或发布批准。后续本地归档交付由父会话按原 auto 授权执行。

### 本地归档与交付记录

- 读取正式 `FD-084-000014-verification-passed` 与报告 D，确认无剩余代码 findings。CLI 拒绝 agent session 认领 PM handoff（PM/human handoff 不支持此认领）；没有修改 receipt 或使用 force。随后正常 `aiw fd close FD-084 Complete` 成功归档本 FD 及四轮报告。
- 归档后的 Sources 相对链接按新目录层级修正，仅文档路径维护，源码与已审查提交一致。记录坐标为 parent=`develop`、branch=`feature/FD-084`、worktree=`.wt/FD-084`；归档提交后使用 `aiw git wt local-merge FD-084` 处理本地 squash 与验证清理，实际结果以工具交付回执为准。
- 本地完成不代表测试或发布批准；没有 push、部署、真实登录/SDK/OAuth/知识库联调或测试执行。

### 能力门槛与剩余风险

- **G1 / 已落实受限合同：** 使用 SDK 公开 Model/Runner/FunctionTool 接口，运行时检查接口能力并记录安装版本；OAuth 不开放原生工具格式，使用已核对的公开文本 Responses 合同和 SDK 内函数转换。完整终态、配对历史和有限预算由适配器控制。真实安装版本/实际账户联调仍未执行，不能宣称 SDK 全能力或真实 OAuth 已验证。
- **G2 / 已落实关闭原件写回：** 不引入未验证的文件系统 CAS，当前平台只发布新文件/修订副本；写回接口明确拒绝，保留原件与恢复依据。将来若增加原件写回，需另行实现和验证平台保护，不在本次以比较 hash 后覆盖替代。
- **G3 / 真实接入：** OAuth 组织许可/订阅资格、真实 DPAPI、知识库跨设备服务身份与幂等语义尚未联调；真实外部操作需要明确授权。文档中已有接口不等于外部服务已经部署。
- 平台专业任务仍受原 Agent/Grant/正式问题合同约束；原 SDK JSON 动作记录与新函数工具状态不直接混读。凭据、运行记录迁移与并发刷新是主要实施风险。
- **Independent 理由：** 实施涉及认证/凭据、私有持久状态、并发控制与文件副作用，完成时需要一次独立实现审查。独立结论必须来自实际审查事件，不由 Worker 自行生成 PASS。

### 计划验证（未执行，执行另行授权）

优先覆盖：无工具副作用的 SDK 契约与流终态、控制代次/队列/重启、OAuth scope/轮换未知、文件截断与版本冲突/备份恢复、知识库服务身份/引用/同键对账、既有授权与过期调度。使用 mock/临时目录覆盖逻辑边界，真实 OS/外部服务证据单独记录；不自动运行全库测试或下载依赖。

## TODO

- 最终源码 compile-only、自检及独立静态实现审查已完成，详见实际实施记录和报告 D；定向测试仍未运行，只有收到明确执行授权后才能执行，不记为通过。
- 评估可重用的纯辅助逻辑，避免秘书直接依赖 Worker 的任务租约或 Plane 的 Java 凭据库。
- 后续用相同模型/任务和工具预算比较调用次数、输入/输出、缓存、耗时、失败/恢复与交付正确性；只有明确授权后执行，不预设 Pi 或 SDK 能带来成本优势。

## Sources

- 用户设计确认：秘书独立于 Plane；允许考虑显式导入；优先 Agents SDK，Codex 为备用；吸收 Pi 的控制/上下文机制；可靠文件修改与知识库一并设计。随后明确 `$fd-workflow FD-084 auto` 与“commit then continue”，授权当前隔离实施/本地交付；没有因此授权测试或真实账户操作。
- [秘书稳定规格](../../../../openspec/specs/secretary/spec.md)、[知识库交接契约](../../../personal-secretary-knowledge-handoff.md)。
- [FD-079](../FD-079/FD-079_OBSERVABLE_AND_CONTROLLABLE_PYTHON_WORKER_AUTOMA.md)、[FD-081](../FD-081/FD-081_CONTROL_PLANE_MANAGED_OPENAI_AUTHENTICATION.md)、[FD-083](../FD-083/FD-083_AUTOMATICALLY_PROTECT_LOCAL_MODEL_CREDENTIAL_KEY.md)；这些不替代秘书新实现证据。
- 本地 Pi 源码：`D:/03_projects/AI-tools/pi/packages/agent/src/agent-loop.ts`、`agent.ts` 和 `packages/coding-agent/src/core/agent-session.ts`；不安装、不运行该 checkout。
- 官方 [Agents SDK](https://developers.openai.com/api/docs/guides/agents/sdk)、[运行与状态](https://developers.openai.com/api/docs/guides/agents/running-agents)、[OAuth 登录](https://developers.openai.com/siwc/token-sharing-open-source/sign-in)、[模型调用](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)、[预览限制](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations)、[刷新与会话](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions)。引用来自此前已核对页面；本轮设计记录没有重新进行网络查询，不据此承诺未来接口保持不变。

**Completed:** 2026-11-08
