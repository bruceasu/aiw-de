# 首期软件开发闭环

统一入口 `/ui/software-development`，提供原始目标提案、图进展、配置准备缺口、同一正式问题、预算和成果证据。它不另建调度器、授权或交付账本。图计划、配置、问答/控制和授权核对仍有各自原入口。

## 从目标开始

可以直接在 `/ui/workflow-graphs` 保存目标、范围和验收；也可以在个人秘书的“选定软件目标”表单只选要提交的目标/必要上下文/验收，先本地预览原文及摘要，再明确确认。预览不调用平台或模型，不读取未选私聊。平台仅收这三个封闭字段与原请求 ID/raw digest；私人 policy、模板、凭证和私聊全文留在本地。

个人服务和平台身份均需按原流程明确启用。目标提案是惰性数据，不需要一个尚不存在的 Plan read_scope，不借 A01，不授予执行或批准能力。POST `/api/r1/authority/goal-proposals` 验证当前独立身份、worker boot、generation 和 enable boot，原文 UTF-8 SHA-256 不符或 UUID 重用异内容则拒绝。重启后的历史回执由当前启用的同 owner+worker GET `/goal-proposals/{originalUuid}` 查询；来源 boot 保留历史，不伪造仍在运行的旧进程。

私人 `decision` 记录 PREVIEWED→SENDING，发送前 CAS 只允许一次 POST，丢响应/解析不符为 UNKNOWN，重启遗留 SENDING 也先核对。UNKNOWN 只能查询原 ID，404 或超时不自动转为可重发，也不自动换 UUID；平台 instance 改变保持来源冲突。已确认 RECORDED 的历史回执不会因后来网络失败丢失。该逻辑不复用或修改原 action outbox 的重发策略。

当前人类在软件开发工作台读取自己的提案，检查投影/digest，填写新 plan ID 后采纳。操作角色才可采纳，审批角色只读。采纳先查不可变回执，同一事务调用原 GraphPlans.change 再 CAS 绑定；重放返回原 plan/version，不因后来编辑或重启重新创建。采纳得到空 DRAFT，缺范围/验收明确待澄清，不能直接激活。之后到图计划页请求只读规划，载入候选审阅、保存，配置实际授权和预算，再批准执行。

图与 execution policy 使用同一 owner。原服务双向核对同名 plan ID，阻止用自己的空草稿读取另一个 owner 的预算或 Task。新接口沿用当前人类会话、CSRF、角色和所有权校验，最小投影 envelope 上限 32768 UTF-8 字节。

已有旧式计划即使还没有 policy/target，也按其真实 created_by 核对归属，其他 owner 不能通过空图占用该 ID。交付文件按原成果拥有者的集合合同比较，顺序不同不构成冲突；多/少文件及原文摘要/版本/来源不一致仍报冲突，历史记录不重写。

## 执行和人工参与

首期只有 Python `openai-agents` 和 Unix/WSL `codex-app-server` 两种受控后端；不限定 SDK/CLI 版本，按实际能力检查并记录适配版本，不满足边界时阻塞。同一兼容进程可领取不同固定专业配置；职责模板不是常驻 Worker，也不授予工具权限。知识归知识管理，需求评审审需求，行为审计审行为；市场监测/安全处置是不同授权，首期不实现其业务系统。Worker 常驻/按需/回收策略仍预留。

所需时间/成本/任务规模上限及定价未配置先等待；业务循环是初轮加最多三次回跳，共最多四轮。具体追加由同一正式问题/账本申请并获当前人类或有效限定秘书委托批准，不能靠改模板追加。暂停和到限阻止新派发及推进，在途可在既有授权收尾，真实消耗可能超出阈值。技术 Attempt 不计业务回跳。

专业问题和临时提问经控制台、个人秘书、明确绑定的 Telegram 私聊共享同一正式问题。原始回答去重，暂停时事实保留；临时工作先真实静止挂起，再用原 continuation 和固定 checkpoint 继续。取消是停止请求，规划 Task、执行 Task、续行和 UNKNOWN 都纳入真实终态核对；不自动回滚或重放。未知外部结果由有权限的人类到授权页核对，秘书只转达。

## 成果记录如何解释

交付视图只取最终成功 ROOT 的当前冻结出口，并重新核对出口/Fact/TaskInput 摘要、graph execution 映射、实际 COMPLETED Task/Attempt、仓库/base/result/变更/verdict、context 原文摘要、配置绑定/来源、Delivery 原文摘要及其 baseline/result/changes/sources。按精确 Task+Attempt 去重；旧轮、失败、被替代或 CONTINUED 源任务不冒充最终成果。

WAITING_FINAL_EXIT 表示尚无最终出口；NO_SOFTWARE_DELIVERY 表示没有软件 Task 成果，不会利用空集合宣布交付齐全；MISSING/EVIDENCE_INCOMPLETE 表示缺版本、Attempt 或 Delivery；CONFLICT 表示摘要/映射/来源不一致；UNKNOWN 表示未决效果/计量；EVIDENCE_RECORDED 仅表示原记录可核对，不是业务验收、测试通过或生产发布。REPAIR_REQUIRED、未解决项及检查未运行仍显示原内容。commandsRun/checksNotRun 是 Worker 的原声明；没有记录就写未记录，不从命令文本推断独立验证通过。

没有默认开放网络、任意 shell、安装、push 或部署；验证命令需实际许可和隔离条件。交付视图不创建第二份 Delivery，也不发布成果。此仓库本次仅静态和离线编译，没有运行下列集成场景。

## 静态场景覆盖与限制

| 场景 | 唯一能力所有者与静态接合 |
| --- | --- |
| 目标→候选→审阅 | SelectedGoals→GoalProposals→GraphPlans.change→GraphRecommendations/Factory；无候选自动执行 |
| 动态软件配置 | AgentFactory→ExecutionConfigurations 的固定 binding/capability→Python execution_configuration；不改 Lease |
| 条件/并行/必要汇合 | GraphValidator/GraphRunner；未选路径有明确选择，失败必要槽保留 |
| 初轮+三回跳/追加 | GraphRunner 的稳定 activation/transition/carry→ExecutionLedger→FormalQuestions/A18 |
| 三渠道重复回答 | PersonalQuestions、Console、Telegram→FormalQuestions；GraphQuestions 单消费者更新槽/APPLIED |
| 暂停中完成/重启 | Guard 先留真实事实；纯 DB Recovery 未消费事件恢复，不重放模型或工具 |
| 取消未确认/UNKNOWN | FormalQuestions.control→真实 stopRequested/Worker quiescence/guard reconciliation→全 plan 终态 |
| 越权工具/不兼容 | GuardBroker、受控模型/本地 Git/隔离命令、Factory/metadata；拒绝或具体阻塞 |
| 正式交付 | SoftwareDelivery 对 ROOT/物理 Task/Attempt/context/配置/Delivery 逐项只读核对 |

V11～V16 使用用户决定的新 DB，不迁移旧运行数据；不删除用户 DB。Go/TypeScript 保留原协议，不声明支持本期动态配置及执行边界。市场业务、安全处置、长期记忆、Worker Leader、常驻回收、远程发布等仍延期。

实际并发、断电/重启、Flyway、两后端、Telegram、真实停止、cgroup 和浏览器尚未运行验证；静态阅读与编译不等于运行通过。详见 [图计划](WORKFLOW-GRAPHS.md)、[专业配置](EXECUTION-CONFIGURATION.md)、[正式问题](FORMAL-QUESTIONS.md) 和 [执行许可](EXECUTION-GUARD.md)。
