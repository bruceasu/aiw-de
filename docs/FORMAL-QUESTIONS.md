# 工作问题与人工控制

`aiw.questions.v1` 使用平台中的正式问题 ID，控制台 `/ui/questions`、个人秘书和显式绑定的 Telegram 私聊共用状态。新表由 V14 在新数据库建立；本次没有执行数据库或迁移。旧的已完成成果问题入口仍可用，不因回答取得新执行授权。

同一 owner 的当前操作角色或审批角色可以读取问题和管理自己的私聊绑定。普通专业回答/送达及执行控制要求操作角色；具体授权申请的创建、回答/送达要求审批角色，绑定本身不授予任何业务动作。界面只显示当前角色可提交的回答入口，服务端仍按正式问题种类重新检查。

普通问题状态为 OPEN → ANSWERED → APPLIED；取消或来源被替代后为 SUPERSEDED。每次回答保留原文、actor、channel、messageId 和 answerRevision。消息重复返回原回执；不同回答冲突。当前人类可以在 APPLIED 前填写原因及精确 answerRevision，显式覆盖秘书回答。已生效回答不被晚到消息改写。

## 临时提问与接续

Python 两种后端提供 `ask_question(question, context, options, answerRequirements)`，仅在配置声明 QUESTION 且原始和当前政策都允许时接纳。broker 关闭新工具准入，排空已接纳调用，在已有 Git 许可下提交最终本地 checkpoint，再登记问题和 yield。任务为 YIELDING，保留 Lease/容量；模型、工具及子进程确认静止后 ack，才转 WAITING_INPUT、Attempt YIELDED 并释放容量。未知操作、断线或无停止确认保持占用及 UNKNOWN，不能报告成功。

回答可提前保存。唯一消费者在当前控制、预算和真实 yield 允许时，在同一事务创建续行、绑定原配置及固定上游输入/回答/checkpoint、写 lineage 和 APPLIED。原任务变为 CONTINUED，其真实历史保留；它不等于完成。续行计入 TASKS，不消耗业务回跳额度。软件任务从登记 checkpoint 的固定提交继续，不依赖原模型会话。

checkpoint 保留于指定本机 Git 对象库及隔离工作区。Worker 广告 `checkpoint-store:<主机及规范仓库路径摘要>`；续行仅增加源 checkpoint 的精确存储要求。另一 Worker 或重启进程必须使用同一持久对象库，凭精确 Lease 从 `/tasks/{id}/questions/checkpoints` 取回固定记录并核对实际提交。不同主机/克隆不满足该能力；缺失提交失败关闭，不自动 fetch、下载、上传或复制任意 URL。运维不得在等待问题时清除该对象库、分支或工作区。只读任务不允许通过 checkpoint 增加写入。

LEGACY_PLAN_TASK 是现有受控来源，封闭支持 configured-plan 和 flexible-plan。两者验证真实 policy/context/task/Attempt；configured-plan 验证注册配置，flexible-plan 另验证 WorkflowPlan 与逻辑槽。WORKFLOW_GRAPH_EXECUTION 只通过 FD-026 注册的 resolver；未注册明确拒绝，不接受自由 sourceRef。图 ask 的创建、推进由 FD-026 接入本服务，不创建另一份问题状态。

## 秘书与隐私

秘书的 `/questions?planId=...` 使用专属凭证、当前进程 boot、enablement 和明确 PLAN 读取范围。私人 UI 先显示本次选择的目标、必要上下文及原始回答；第二个明确提交动作才排入私人 outbox。私人聊天、策略、模板、凭证不自动投影。

`/question-replies` 使用 `{envelope, projectionJson}`。UTF-8 原始 projectionJson 的 SHA-256 放在唯一 evidence `question-projection:<digest>`；先无效果地登记，再在既有 action admission 事务中按同一 requestId 读取。登记绑定平台实例、owner、个人 Worker、世代、平台/个人 boot 及原信封；冲突拒绝。A19_ANSWER 的参数为明确的 `kind=PROFESSIONAL` 与 reason，仍必须与用户授予的参数完全相等；快照固定全部问题 ID、source、requestVersion、answerRevision 和当前政策版本。A19 只保存原答，不授予工具、预算或推进。

工作暂停是 execution_policy.paused，允许保存事实。委托禁用、撤销、authority_target 暂停、人类接管、限制或变化的世代仍会拒绝秘书动作。原 outbox、原 UUID 和回执用于对账；UNKNOWN 外部结果不自动重放。

## Telegram

默认关闭。显式设置 `aidev.questions.telegram.enabled=true`，使用现有 `aidev.approval.telegram.token` 和 api-base 配置；不自动采用旧审批的默认 chat/allowed users。控制台当前 owner 明确绑定 user ID 与正数 private-chat ID，重新绑定/解绑递增世代，使旧关联和排队送达失效。

控制台逐问题明确选择发送。outbox READY→SENDING→DELIVERED，未知外部结果或重启遗留 SENDING 为 UNKNOWN，不自动重发。token 15 分钟有效，服务端仅保存摘要。文本必须回复已登记且当前有效的送达 messageId；按钮匹配 token、问题版本、绑定世代、用户及私聊。群聊、无关联文本、错用户、旧 token 均不能回答或授权。超出单消息大小的问题保留控制台显示并将送达 INVALIDATED，不截断授权范围。

启用旧审批时复用同一 poller，否则使用问题 poller。先持久保存最小收件，再提交 offset；消费者独立去重。新问题前缀不会落到旧审批。使用本机文件锁拒绝同一 bot 重复启动；多主机部署必须保证仅一个实例持有该 bot。本期没有跨主机接管协议。

## 控制与授权申请

暂停阻止新领取和推进，在途按已有授权收尾。取消撤销新准入，提交精确停止请求；运行中和 YIELDING 项目保留至真实确认，未知结果由有权限的人类核对。不会调用旧取消服务的 Git 回滚，不自动重试工具或模型。

授权问题固定 requestId、维度、loopId、amount、cumulativeCeiling。控制台人类须具备当前审批角色；Telegram 复验绑定 owner 对应当前角色，必须点击具体申请按钮。秘书必须具有真实有效的 A18_BUDGET 固定授权，并与本申请所有范围及 UUID 匹配，才能提交 APPROVE。ledger 的同一 decisionId 防止多渠道重复追加。A19、普通文本或“可以”不能单独增加额度。初始执行加最多三次回跳仍由执行账本统一计数。

## 证据范围

本次只进行静态调用链检查和适用的离线编译/语法编译，不启动服务、Worker、模型或 Telegram，不连接数据库。实际发送、并发回答、进程中断、跨 Worker 取回和原生协议行为尚无运行证据。首期存储局限和单 bot 实例前置是部署合同，不是运行验证通过声明。
