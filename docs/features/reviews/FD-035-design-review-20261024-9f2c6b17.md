# FD-035 独立设计审阅

<!-- aiw-data: FD-035-design-review-20261024-9f2c6b17.json -->

结论：**ready**。FD Revision 2 的方案可以进入实施；Planner 应在 design-ready 前将下列确定的实施约束纳入 1.3／1.4。没有需要新增人类决策的授权或兼容性问题。本结论只评估设计，不是实现审阅或 verification-passed。

## 发现与实施约束

1. **送达列表有上限，不能用缺失记录证明可以再次发送。** TelegramQuestions.manage 的 read 返回当前 Owner 最近 100 条送达，而绑定也是 Owner 共用。面板须说明绑定及改绑影响当前账号的所有计划，按当前问题 ID 匹配送达。返回数达到 100 时，未显示的旧 SENDING／UNKNOWN 不能被证明不存在；在不改服务接口的本 FD 内，保守禁止新发送并说明“送达记录不完整，无法安全判断”。读取失败同样关闭通知写入口。READY／SENDING／UNKNOWN 和仍有效的 DELIVERED 按 FD 规则阻止重复发送，UNKNOWN 不因过期解锁。此约束不改变后端传输或事务。
2. **授权问题需要专门的人类展示。** FormalQuestions.insertBudget 把 context 生成为包含序列化 Authorization 的机器文本，options 是 APPROVE／REJECT。直接展示这两个原字段会重新出现 JSON 和接口命令。应以 q.authorization 的维度、loopId、amount、cumulativeCeiling 显示中文申请详情，按钮明确批准／拒绝，同时仍向原接口提交精确英文值；机器生成的冗余 context 不原样呈现为日常说明。专业问题原文、回答要求和选项仍按文本保留。
3. **内部版本与整数不能靠推测或舍入转换。** Reply 的 requestVersion 来自选中 Question，expectedAnswerRevision 来自原回答快照；Control／BudgetQuestion 使用读取时的 policy.revision，不能用 source.planVersion 替代。额度输入若以浏览器 Number 提交，应拒绝非整数、非正数与超出安全整数范围的值，或采用保留整数原文的序列化方式；不能将已舍入的值作为精确累计上限。
4. **核对成功与未能证明成功分开。** 回答可用原 messageId、申请可用原 requestId 查见记录来确认；当前读接口不提供完整 inbox 回执查询，原答内容相同也可能仍显示先前回答的 messageId。不能将“未找到同 ID”解释为失败或自动重新发起。按 FD 保留未知状态、仅原幂等 body 显式重试或由人类结束核对。控制、绑定、送达没有同等请求身份，不提供一键重放。

以上为现有契约导出的实施细化，无须开启第二轮设计审阅；实现审阅应逐项核对。

## 事实与证据

- QuestionContract 的 OPEN／ANSWERED／APPLIED／SUPERSEDED、Reply、Answer、Authorization／BudgetQuestion 明确问题版本、回答版本、消息身份及精确授权范围。
- FormalQuestions.answer 在 Owner、请求版本、消息摘要和来源校验后保存回答；只允许人类在未 APPLIED 时携原因及精确 answerRevision 覆盖 delegated 回答。相同原文可返回现有回答，不代表创建了新 Answer 身份。
- FormalQuestions.human／readHuman 保持现有操作、审批角色和当前 Owner 边界。AuthorityPageController.questions 已按当前 actor 的 Owner 筛选 execution_policy，并保留 questionCanOperate／questionCanApprove。
- FormalQuestions.control 的取消提交 stop_requested，progress 分别返回 stop_requested_at_epoch_ms 与 stop_acknowledged_at_epoch_ms，设计正确保留真实确认边界。
- TelegramQuestions.read 是 Owner 级绑定及 LIMIT 100 送达视图；manage(send) 自身不会凭 UI 请求身份去重，因此本页面必须保守防重。
- 当前 questions.html／questions.js 存在 JSON 直出、长页、手输批准值、刷新重建回答表单、跨计划读写未隔离等问题。现有 Console 主题、fragments 导航与 workspace-confirm 对话框可以复用，无需新设计系统或依赖。

## 选项、边界与覆盖

仅美化 JSON 无法解决用户理解成本；重写问题服务会扩展已有多渠道事务风险。采用原写接口之上的收件箱、结构化回答和分区操作，理由充分，改动范围适当。

- 1.1 覆盖问题到交互的决定、并发边界及本次独立设计证据。
- 1.2 覆盖 Owner 计划标题、空态、模式说明及 Console 分区；标题仅取当前 actor 计划，无法取得时回退真实 ID。
- 1.3 覆盖待办筛选、原文和上下文、角色化回答与秘书覆盖、草稿和明确采用新版本。
- 1.4 覆盖任务及停止事实、确认对话框、默认 record-only 非主流程额度、通知、跨计划异步和未知写核对；上述送达上限与账号级绑定说明并入本项。
- 1.5 覆盖点击路径、稳定 UI 说明、范围内编译与后续独立实现审阅。

Acceptance 1—6 均有对应 Work Item 和静态证据路径。各项已注明规模、难度、依赖和完成条件；1.4 的状态协调属于同一页面安全写入结果，规模需保持局部，不能借机改后台服务。

## 兼容、假设与剩余风险

设计保留全部写 DTO、权限检查、CSRF、正式问题消费／覆盖事务、停止确认、计量默认和 Telegram 传输；无新表、迁移、依赖或 API。默认仅记录时隐藏手动额度申请是呈现选择，不将历史正式授权问题伪装成自动批准。

假设：Planner 在交接前记录上述实施约束；问题计划仍由现有执行政策产生；当前页面内草稿不承诺浏览器重启恢复。

未验证：真实 Thymeleaf 渲染、窄屏和键盘交互、数据库权限、异步竞态、Telegram 发送及中断恢复。本次不据此增设运行 Gate，也不将静态证据表述为运行通过。FD-033 的动作授权范围，以及私人秘书、模型和启动脚本均排除。

## 实际检查范围

本次只执行有界本地读取：Get-Content、rg／rg --files、Select-Object、git status。首次问题包路径多写 execution，随后依据实际文件定位纠正；末次 Windows V14 通配检索路径未解析，未据此声称迁移验证，也未继续扩大调查。报告只引用已实际读取的契约与服务。

仅新增本中文报告及同名 JSON。未修改 FD、源码、工作流事件或 Git 提交；未编译、测试、启动浏览器／服务／数据库、访问网络、发送 Telegram 或创建下级 Agent。

来源事件：FD-035-000002-design-requested。Planner／Worker Session：secretary-desktop-20261024-a6529740。独立设计 Reviewer Session：fd035-design-review-20261024-9f2c6b17。

