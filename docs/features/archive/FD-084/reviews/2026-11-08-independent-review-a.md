# FD-084 独立实现审查

- 结论：**CHANGES REQUESTED**。以下五项为代码/合同缺陷；没有把尚未授权的测试或真实服务联调列为硬缺陷。
- Reviewer session：`codex-fd084-reviewer-20261108-a`；与实施 Worker 分离，未派生其他 Reviewer。
- 源事件：`FD-084-000004-review-requested`，读取 pending 回执后由本 session 精确认领。
- 工作区：`D:/03_projects/AI-tools/aiw-de/.wt/FD-084`；parent=`develop`，branch=`feature/FD-084`。
- 差异：`abf8fc5..27f29a13a651930435316fec54fac18f8d240beb`，含实现 `825f4d1` 和交接修正 `27f29a1`；25 文件。审查开始时仅 FD 文档有申请审查造成的未提交修改，没有实现漂移。
- FD revision：4；Evidence policy：Markdown；Review policy：Independent。

## Findings

### R1 / P1：删除对话与永久删除工作遗漏新增的私人运行正文

位置：`personal-secretary/store.py:49`、`:232`、`:351`；数据写入点 `secretary_sdk.py:239`、`:261`、`:380`。

事实：新增 `runtime` kind 保存完整输入历史、工具参数/正文、OAuth 原始响应及控制文本。`delete_conversation()` 的删除集合仍只覆盖 message、原 DAILY_PLAN/DAILY_CONFIRMATION 等 decision 及可选 memory/todo，没有收集该 work 的 SECRETARY_RUN 和通过 runId 关联的 SOURCE/WIRE/TOOL/CONTROL/EVENT。`backup()` 又遍历全部 KINDS，因此删除成功后的当前数据库及新导出仍包含这些私人文本。

触发：完成一次含私人正文的 SDK 会话，再删除对话；或将工作移除后永久删除。旧消息/处理记录被删除，但新增运行副本保留。这是 live 记录残留，不能用 README 对已有备份、WAL 和磁盘残留的排除项解释。

违反：保留原有契约；README“私人会话、历史消息与记忆”及第 85 行的删除范围承诺。影响是用户认为已经删除的聊天/工具正文仍可从随后生成的私库备份恢复。

最小修正：在原删除事务中收集并删除该 work 对应的完整 runtime 关联图；对 PAUSED/RECONCILING 等尚有恢复依赖的运行先明确阻断删除。继续保留合同明确要求保留的独立实际文件、专业平台回执和既有备份。

### R2 / P1：对账使用当前知识库凭据，可能跨服务主体重复创建旧笔记

位置：`personal-secretary/secretary_runs.py:309`、`:339`；`knowledge/tools.py:39`、`:47`。

事实：普通执行的 `check()` 和 `resume_run()` 比较冻结 binding（包括 knowledgeCredentialRef），但 `reconcile()` 只检查状态和记录 revision，随后用 `self.runtime.settings` 创建 DailyTools。`knowledge_note_status` 只比 serviceId；发现 CREATION_NOT_FOUND 时会用当前凭据 POST 原正文。KNOWLEDGE_NOTE 也没有记录原凭据引用/服务主体。

触发：主体 A 的笔记创建已受理但回包丢失，运行进入 RECONCILING；用户配置同一个 serviceId 下主体 B 的 token，然后点“核对原操作结果”。B 只能查自己的创建记录，得到 CREATION_NOT_FOUND 后，同一个 key 会在 B 的幂等作用域内再次创建，且上传 A 的旧正文。手动/新请求查询原 pending note 也经过同一只校验 serviceId 的入口。

违反：FD 固定连接和恢复前重查身份/授权的合同、Acceptance 9/10；知识库交接契约第 133–146 行明确幂等作用域是“稳定服务主体 + key”，查询只能读当前主体自己的记录。相同 serviceId 和 key 不能证明主体相同。

最小修正：保存原知识连接身份，并在所有可能查后重发的入口校验；无法证明主体不变时保持 UNKNOWN，禁止以替换后的连接重发。至少让运行对账遵守原 binding，同时让跨请求 note-status 也遵守原保存记录的身份约束。

### R3 / P2：SDK 最终文字仍能被旧外层 JSON 循环当工具动作执行

位置：`personal-secretary/daily_assistant.py:318`、`:327`、`:337`、`:383`。

事实：`host.sdk(context)` 的 final_output 与 Codex 文本走相同的 json.loads 和动作分发。只要 SDK 的最终 assistant message 是 `{"action":"create_file",...}`，Runner 已经结束后外层仍会调用 `host.perform()`；没有检查其是否是已注册函数调用，也不经过 SDK 的 `matches()`。连 SDK 未注册的 delete_file 也能进入旧确认路径。下一次外层循环还会再次启动 SDK。

违反：FD 组件合同明确 SDK 路径不由外层再解析 JSON 动作驱动同一次工作；所有 SDK 工具输入经过声明 schema；Acceptance 2/4。完整 Responses 终态检查本身没有解决该旁路。文件现有授权检查仍然存在，本 finding 不宣称它能直接绕过人类删除确认。

最小修正：SDK 分支的最终输出只能进入完成结果校验；非 finish 的结构化动作应拒绝，不能派发宿主工具或启动旧动作循环。旧 JSON 动作分发限定在显式文本备用驱动。

### R4 / P2：普通用户请求没有稳定提交 ID，重复提交会创建独立副作用

位置：`personal-secretary/daily_assistant.py:178`、`:252`；入口 `main.py:343`，UI `app.js:1266`。

事实：请求去重只实现于 `/api/run/control`。聊天入口只传 workId/text，`chat()` 每次生成新的 message UUID，`run()` 再生成新的 DAILY_PLAN UUID；没有接收、持久化或核对客户端原请求 ID。工具去重和知识笔记幂等键都以这次新 job/run 为范围。

触发：首次聊天 POST 已开始或完成保存，但响应丢失；忙碌状态结束后重发原请求。服务创建新 message/job/run；同一知识笔记得到新幂等键，可能再次创建，修订副本也可以再次发布。暂时的 busy 拒绝无法处理完成后或重启后的重发。

违反：FD 持续会话首条要求“每条用户输入和控制命令先持久化，带唯一 ID……重发同一 ID 不重复入队”，Acceptance 5。没有请求级去重，当前工具级去重不能支持该验收。

最小修正：为普通输入建立持久、可重用的原提交 ID，UI 在响应不明时保留该 ID；服务器在排队/创建会话前绑定并核对 payload，原请求重放返回原消息/运行，不新建 job。保留用户明确再次提出同文请求的能力。

### R5 / P2：异步专业任务/平台动作终结后，运行仍停在 WAITING_USER

位置：`personal-secretary/secretary_host.py:163`；`daily_assistant.py:460`；关联 `professional_tasks.py:243`、`platform_agent_actions.py:87`。

事实：`after_tool()` 在 delegate_task 或平台 pending 回执后 yield，`stopped()` 将 SECRETARY_RUN 设为 WAITING_USER。现有专业任务 `_receive()`、平台动作 `poll()` 之后只更新原 DAILY_PLAN/work/message，不更新 RunJournal。`followups()` 则仅在原 run.state 为 SUCCEEDED 时发送后续请求。

触发：委派期间或等待平台回执时提交 follow-up；远端随后给出明确成功结果。原消息已经完成，运行卡片仍“等待确认或专业任务结果”，follow-up 永远留在 QUEUED。失败终态同样没有对齐；当前代码也只对 SUCCEEDED 释放排队输入。

违反：Acceptance 5 的当前请求终结后处理 follow-up、Acceptance 10 的真实状态/回执恢复；同一请求的旧 job 与新运行事实不一致。

最小修正：让异步正式回执同步完成对应 RunJournal，按实际成功/失败/取消保留终态与事件；为当前请求失败/取消时排队 follow-up 的处理落实明确规则，避免永远排队或误记已应用。

## 审查范围、证据与限制

已审查完整变更文件集合，重点追踪 Runner/Model/FunctionTool → Host → Journal → 文件/知识/平台工具、确认后继续、控制和重启对账；核对设置/凭据刷新、私库升级锁和备份、UI 请求/状态、完成声明校验、编译脚本、两份新增测试源码、README/稳定规格及知识库交接合同。为判断兼容性额外读取现有 conversations、professional_tasks、platform_agent_actions、authorization_requests、private_config 的相关路径。

静态支持的事实：API Key 原始 Responses 在整体 completed 后才交给 SDK，OAuth 文本动作在完整响应后转换；函数调用前有本机权限/回执边界；原件写回明确关闭，新文件/副本采用排他发布；刷新有跨进程锁和 REFRESHING 意图。以上不等于真实服务运行通过。

测试源码中的文件版本/副本、刷新轮换、控制、知识幂等、流完整性及 SDK 替身断言具有对应局部目的；没有普通 chat 提交重放、删除 runtime 图、实际异步回执到 RunJournal、换主体对账和 SDK final-output 旧循环旁路的覆盖。SDK 替身只模拟一轮调用并给定 final_output，不能证明真实 SDK 多轮协议、OAuth 资格或 DPAPI。

Worker 在 FD 中记录两次 `& ./scripts/compile-secretary-runtime.ps1` 成功及 staged whitespace 检查成功。Reviewer 检查了脚本范围和源码，不曾重跑这些命令；记录的编译仅证明语法边界，测试仍为 not-run。

假设/未验证：真实 SDK 版本与公开接口的运行兼容性、OAuth 组织/订阅资格及实际令牌声明、Windows DPAPI/锁/硬链接行为、真实知识库稳定主体和恢复语义、真实升级/回滚均未执行。R1–R5 的判断来自本地可追踪代码及既有合同，不依赖假设它们已经运行失败。

## 实际命令

均在上述 FD worktree 执行；共享规则从父工作区确切安装路径只读加载。

- `git status --short`；`git diff --stat abf8fc5..HEAD`；`git diff abf8fc5..HEAD -- <本报告涉及的变更路径>`；`git diff abf8fc5..HEAD --name-only`；`git rev-parse HEAD`。
- `Get-Content [-Encoding utf8] <根 AGENTS、四项 shared core、work-management、fd-review SKILL/共享合同、code-review task prompt、monorepo prompt、FD、事件/工作区 JSON、上述代码/文档/测试路径>`，配合 `Select-Object -Skip/-First`；`rg --files`、`rg -n [-A/-B]`、`Get-ChildItem` 对这些路径作定向静态发现/调用链定位。没有执行导入或源码逻辑。
- `aiw fd show FD-084`；`aiw fd claim --help`；`aiw fd claim FD-084 FD-084-000004-review-requested --session codex-fd084-reviewer-20261108-a`（成功）；`aiw fd emit --help`。
- 初始读取发现 worktree 的共享规则目录未安装，改读父工作区；两次共享合同/事件文件名定位有路径错误；`aiw fd events FD-084` 因子命令不存在而失败，随后直接只读真实事件 JSON；一次包含双引号的 rg 模式被 PowerShell 拆分而失败，改用单引号模式定位。没有权限失败或运行验证失败，也没有用备用方式执行受限动作。
- 报告与 FD Verification 通过 `apply_patch` 写入；完成后仅执行一次静态 `git diff --check`。随后正式命令：`aiw fd emit FD-084 changes-requested --producer reviewer --artifact docs/features/FD-084/reviews/2026-11-08-independent-review-a.md --source-event FD-084-000004-review-requested`。

未运行：测试、编译、服务/浏览器、模型、网络、依赖下载、格式化/lint/type-check、真实 OAuth/DPAPI/知识库/迁移、额外子代理。没有修改实现代码，没有 commit/merge/push/归档/发布。测试未授权本身不构成 finding；具体代码问题修正后需聚焦复核。
