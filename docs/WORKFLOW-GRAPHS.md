# 从目标到声明式图计划

入口为 `/ui/workflow-graphs`。先绑定当前 owner，保存目标、范围和验收，获得 DRAFT；空目标骨架不可激活。配置同一 plan ID 的执行 policy、时间/成本/任务上限与定价，再连接支持配置/执行许可/正式问答的 Python Worker。两种 runtime 为 `openai-agents` 和 Unix/WSL 的 `codex-app-server`，不限定 SDK/CLI 版本，能力不满足时阻塞。

请求只读规划使用注册 `planner`（implementation-planner）配置，经过同一个 Factory/guard/预算路径。规划任务可以正式提问、挂起和继续；结果从真实完成报告提取 `AIW_GRAPH_V1` 后的一个 JSON。模型失败和不合法候选保留诊断，人工编辑始终可用。候选需要载入编辑框、审阅并保存；不会自动批准或获得新权限。BLOCKED 规划用相同原请求重试 `/recommend`，不可通过改变内容重用请求 ID。

图协议 `aiw.workflow.graph.v1`，人类 API `/api/r1/authority/workflow-graphs/{read,change,decide,recommend}`。请求使用当前会话、CSRF 和 owner；写入需操作角色，审批角色可读取，额度审批仍走正式问题/A18。`change` 需要 UUID、expectedVersion、source、reason、完整 Spec，以及运行期补丁的 region。`decide` 需要 UUID、expectedVersion、action、reason；单执行控制还需要 executionId/expectedExecutionVersion。技术重试/替代必须给当前 expectedTaskVersion、expectedAttemptId。所有外部输入封闭解码，HTTP 图内容有明确大小限制，节点数不暗代任务规模授权。

## 图的结构

Spec 包含 protocol、goal、scope、acceptance、regions、nodes。唯一根 ROOT，Region 包含 key、parent、nodeKeys、entryKeys、joinKey、loopId、loopHeader、imports、carry。child joinKey 必须是直接父范围的节点；条件两子范围共享 joinKey。ROOT 或任意范围的多个 entryKeys 可并行进入自己的节点、或一个直接子范围的完整入口集合；条件子范围只能由它的 IF 进入。

Node 包含 key、region、kind、dependsOn、next 和一个匹配的载荷。TASK/task、IF/branch、GOTO/jump、ASK/ask、END/end。TASK 声明 repositoryRef、完整 baseRevision、可选 baseFrom、runtime、aiProfileId、instruction、failureNext，以及 defaultRole/configRef/candidate 三选一。普通依赖只用本轮节点或 imports 固定的祖先节点；baseFrom 必须是真实成功 task。ASK 回答作为冻结事实进入后续 instruction，不冒充完成 TaskInput。

IF branch 的 predicate 是 sourceNode、field、operator、value；来源必须显式依赖真实 TASK。封闭字段 VERDICT、RESULT_REVISION、CHANGED_FILE_COUNT、SUCCESS；字符串/布尔只支持 EQ/NE，数量支持 EQ/NE/GT/GE/LT/LE。正式 verdict 是 PASS/REPAIR_REQUIRED；缺失和 UNKNOWN 均阻塞。业务 REPAIR_REQUIRED 仍是技术 COMPLETED，可以供条件/修复使用。FAILED 仅从显式 failureNext 传递平台登记的 FailureFact，普通成功边继续等待必要槽。取消和 UNKNOWN 不当 false。

实施审查修正：有 failureNext 被消费时，普通成功出口固定 SKIPPED、失败出口 TAKEN；失败成员本身仍 FAILED_BLOCKED，不变未选。这样失败路径能与真正未选择的正常路径汇合。没有 failureNext 的未消费失败保留待定出口，以便精确技术重试。消费过失败后的替代只接管输出槽，所有已决边保持原历史。

剔除声明回跳后，控制和数据依赖必须无环。每轮预建潜在边；只有全部来源决定且至少一条 TAKEN 才入选，全部 SKIPPED 才为 NOT_SELECTED。失败必要成果不会改成未选。父汇合等待选中子范围的稳定出口槽，冻结实际有效轮和成果；没有猜测“最新任务”。子范围完成的成果作为正式固定输入提供给父 join TASK。

同范围前向 GOTO 是显式控制边，不能跳过未满足的数据输入或进入已启动目标；没有隐式数组位置或取消并行分支。回跳必须声明目标祖先循环的 loopId/loopHeader，旧轮选中任务和问题须终结，实际资源已静止且无 UNKNOWN。循环 activation ID 保持，旧轮 REPEATED，控制路径 TRANSFERRED；父出口 WAITING，外围独立分支不换轮。carry 明确 sourceNode、targetNode、asBase，冻结上轮真实 execution/Task/Attempt/commit/摘要；只有此导入复用旧轮，不把旧成功算成本轮依赖或完成。

初轮之外最多三次回跳（最多四轮），账本为 plan+loopId，全计划历史共享。第四次请求只建立一个具体额度申请，批准后用原转移 ID 继续。时间/成本/规模缺配置等待配置；未知计量等待人工核对；已知不足提出具体追加申请，申请本身无授权。暂停和阈值停止新派发及推进，在途按现有授权收尾并记录真实消耗，可能超出准入阈值。

END 只关闭当前路径。正常范围出口需要全部必要选中成员成功且全部实际任务静止。FAILURE end 可以明确失败结束，不能冒充成功；取消汇总等真实停止，UNKNOWN 不会假报终止。恢复扫描只消费数据库事实，不启动第二种 EXTERNAL_WORK handler，不直接重放 Worker、Git 或命令。

## 修改、问题和恢复

editVersion 是编辑 head；每个 activation 固定 definitionVersion。未激活子树可以整体展开，但保持 parent/entry/join/loop、导入/出口及跨界依赖/base/谓词合同；激活和 patch 共用准入锁。旧回答/完成验证自身冻结来源，不因 head 变化失效。任何已激活（含 NOT_SELECTED）的子树不能原位改写；后续轮使用已绑定子版本。

ASK 使用同一个正式问题 ID，回答消费、图转移及 APPLIED 同事务。执行中提问沿用同一个 continuation 实现，图槽原子指向 successor，真实 lineage 保留。重复回答返回原回执；暂停中事实保存但不消费。控制台、秘书最小投影、绑定 Telegram 私聊的身份/权限合同见 [正式问题](FORMAL-QUESTIONS.md)。两种控制台整图取消入口都同步图的 CANCELLING 状态。

`retry-preparation` 只显式重试原 BLOCKED 意图的当前 generation；扫描不自动重试。`retry` 只允许已知静止且失败未被消费的 FAILED_BLOCKED，原 Task/配置/iteration 不变，新 Attempt 正常计量，不计业务回跳。`replace` 只用于已知失败/确认取消、无已消费出口及无下游已使用该成功输入的槽，新的 execution/Task 保存 replacementFor；失败路径的历史选择不回写。已被激活子范围固定为 ancestor import 的来源不能替代。需要变更已经消费的历史须提交新计划，明确报冲突。

单执行 `cancel` 用当前 task version/Attempt 请求真实 Lease 停止，保留其他独立分支；无物理任务则只关闭图槽。停止确认由原 Worker/可信适配层上报，有疑问由有权限的人类核对，不能让用户直接把旧 Lease 算停止。验证命令、安装、网络、push/部署均不因图计划而开放。

整图取消和 ROOT 正常出口还检查同 plan 的全部 execution_context，包括单独的规划任务及所有 continuation；真实 Task/Attempt、Lease、未决效果或未知计量尚未静止/核对时不宣告全局终态。局部回跳仅等待本循环实际启动的 Task 和已打开 ASK，不要求尚未选择的父汇合先完成；旧轮待定控制路径以 TRANSFERRED 关闭。ASK 的 context/answerRequirements 可省略或为空，与正式创建端口一致，问题正文仍必填。

V15 只新增图版本、持久聚合（保留所有轮次/选路/成员/输入）、事件、幂等请求、物理任务映射及候选表。首期使用新 DB，不负责删除或迁移旧 DB。静态设计和编译不能证明调度并发、SQLite 事务、恢复、两后端执行、Telegram 或浏览器行为已运行通过。
