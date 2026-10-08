# Web Control Plane 当前能力与差距

检查日期：2026-10-02。HEAD：`d33dbbd12dd8aa906633fe4b518f73d14ee283ee`。

证据来自当前工作区静态读取，未运行应用/浏览器；未证明 HEAD 与 handoff 的 R9 Fix1 接受版本完全对应。“支持”表示存在字段或代码路径，不表示已运行验收。

## 当前架构与入口

Browser `/ui` 经 WebConsoleController / QueryService 读取既有 dashboard/timeline 服务与 JDBC 投影；浏览器动作经 ActionController / ActionService 调用 Run/Worker/Approval/Fleet 应用权威。页面使用 Thymeleaf、共享 CSS 和原生 JavaScript。

| 页面 | 当前数据/操作 |
|---|---|
| `/ui` | Fleet 汇总、积压分类、关注 Run、最近 Run、pending 审批、新建入口 |
| `/ui/runs`、`/ui/runs/new` | Run 列表、创建 |
| `/ui/runs/{runId}` | Run 状态/控制/限额、任务表、timeline、审批 |
| `/ui/workers` | 状态/容量/能力/心跳、drain/offline |
| `/ui/approvals` | 结果版本、决定人、批准/拒绝 |
| `/ui/system` | Fleet 限额、会计差值、最近 50 次调度调整 |

当前导航覆盖上述入口。CSS 使用浅色背景/面板、深色导航，有共享变量和响应式规则。未通过渲染确认视觉或可用性。

## 差距矩阵

M1–M4 对应 [需求计划](plan.md)。后端变更列是设计候选判断，不是已确定方案。

视觉目标已由用户在 2026-10-02 本次会话确认为未来战术游戏整体界面、机器生命体 Worker Fleet/执行状态；本表的视觉目标据此更新，当前实现事实仍以静态检查为准。

| 能力 | 当前后端支持 | 当前 UI | 需要的改进 | 后端变化判断 | 优先级 |
|---|---|---|---|---|---|
| Overview/活跃 Run | activeRuns/byPhase/attentionRuns 与 Run 摘要 | 最近 10 个 Run、运行/暂停/失败指标 | 突出活跃与需处理事项、阶段与入口 | 多数可复用；注意范围及字段关联 | M1 |
| 关注/失败 | attentionRuns、失败计数；单 Run lastFailureReason | 已有关注与 pending 列表 | 原因、影响对象、证据/动作关联 | 最近失败摘要可能需查询补充 | M1 |
| 最近活动 | 单 Run timeline | 仅详情接入 | 首页真实跨 Run 最近活动 | 查询 facade 无全局活动读取，需明确查询/范围 | M1 |
| 积压原因 | ReadyTaskSummary 与能力/容量/Run/Fleet/policy 分类 | 分类数字，无受影响任务详情 | 原因与任务/限制关联 | READY 诊断可复用；历史 claim 理由未在已查投影提供 | M1/M3 |
| 健康/新鲜度 | capturedAt、心跳、容量、会计差值 | 心跳在 Workers、诊断在 System | 显示事实与新鲜度，避免虚假健康结论 | 无完整健康模型，分级/告警规则待定 | M1；分级待定 |
| 需求/详情上下文 | RunSummary 有 requirement，任务有 instruction | 详情主标题为 Run ID；instruction 截断 | 需求、执行、证据在同一上下文 | 详情模型需补需求关联，可复用投影 | M2 |
| 执行图 | lifecycle 描述真实并行/集成/repair | 任务表，无依赖图 | 真实 DAG 与动态路径 | TaskSummary 无 dependency 边，需确认领域读取/投影 | M2 |
| 时间线/恢复 | 总数/返回数；失败/恢复/active execution 字段 | eventType/detail，最多请求 300 条 | 关键事件分组/关联、attempt 区别、范围 | 基础可复用；完整历史/筛选分页范围待定 | M2 |
| Fleet 工作关联 | activeLeases 有 Worker/task/subject/lease expiry | Worker 表无 Run/task/expiry 关联 | 占用/租约与工作上下文关联 | 可组合已有投影；node/health 等不得假定存在 | M3 |
| 调度控制 | limits/available/overcommitted/accounting 与 Fleet audit | 表单、数值和自然排空文案已存在 | 关联瓶颈、调整后果与排空状态 | 多数可复用，历史 claim 回放需另补证据 | M3 |
| 审批 | result revision、归属/时间与工程 gate 语义 | pending/历史混合、approve/reject | 具体版本的证据上下文和分区 | review/verification/gate 展示投影需补；无浏览器 Request Repair | M4 |
| 工程证据 | lifecycle 描述 summary/resultRevision/publicationRef/gate | TaskSummary 无结果/依赖字段 | Run→task/attempt→版本与证据可追溯 | 须盘点权威读取；不等于已有外部文档浏览器 | M4 |
| 刷新 | 浏览器脚本 | 5s/8s 整页重载，编辑暂停，确认提示 | 新鲜度、保留输入、连续阅读 | 片段更新/SSE 由设计选择 | 各期 |
| 战术指挥视觉 | 共享 CSS/fragments/媒体查询 | 浅色内容与深色侧栏 | 指挥区域、任务卡、容量仪表与深色 HUD 组件统一 | CSS/模板为主，无需为视觉修改 schema | M1 |
| 机器生命体 Worker | Worker ID/runtime/capabilities/status 与 lease 数据 | 文本 Worker 表，无个体形象 | 首页 Fleet 区与 Workers 页提供稳定的机械身份和真实状态反馈；后续关联更多执行详情 | 视觉可使用已有投影；角色/状态不能虚构 | M1基础；M3深化 |

## 数据语义和范围

- RunSummary.progressPercent() = completedTasks / totalTasks，不能作为整项工程完成度或 ETA。
- WorkerStatus 仅 ACTIVE、DRAINING、OFFLINE；runtimeType 已有，node/健康等级不在已查 WorkerSummary。
- QueryService.fleet() 的 detailLimit=100；详情限制不等于总数限制。
- runs() 最多最近 200 个，首页再取 10 个；最近 Run 不等于全部活跃 Run。
- pendingApprovals() 先取最近 100 条再筛 PENDING，首页再取 8 条；不是全部待审批数。
- 详情请求最多 300 个 timeline entries，显示返回数/总数，未提供完整历史导航。
- console.js 使用整页 reload，dirty 输入/焦点暂停刷新；未实现 SSE 或完整陈旧状态判定。

## 源码与文档索引

路径相对仓库根目录。

- `docs/web-ui.md`：需求、风格、优先级、接受基线。
- `docs/current/15-WEB-CONSOLE.md`：页面、角色、操作、安全边界。
- `docs/current/03-AUTONOMOUS-ENGINEERING-LIFECYCLE.md`：流程、gate/approval、pause/cancel/recovery。
- `src/main/java/me/asu/aidev/webconsole/protocol/http/WebConsoleController.java`、`WebConsoleActionController.java`：页面数据与浏览器动作。
- `src/main/java/me/asu/aidev/webconsole/application/WebConsoleQueryService.java`：查询能力/范围。
- `src/main/java/me/asu/aidev/webconsole/jdbc/JdbcWebConsoleReadRepository.java`：读取投影。
- `src/main/java/me/asu/aidev/webconsole/domain/WebConsoleRunSummary.java`、`WebConsoleTaskSummary.java`：完成比例/详情字段。
- `src/main/java/me/asu/aidev/fleet/domain/FleetDashboard.java`：队列、容量、Run、Worker、lease 投影。
- `src/main/java/me/asu/aidev/autonomous/domain/AutonomousRunDashboard.java`：执行/失败/调度字段。
- `src/main/java/me/asu/aidev/worker/domain/WorkerStatus.java`：权威状态。
- `src/main/resources/templates/ui/` 中的 dashboard、run-detail、workers、approvals、system、fragments：已展示能力与操作条件。
- `src/main/resources/static/ui/console.css`、`console.js`：视觉规则、响应式、过滤/确认/提交/编辑暂停/刷新。

静态检查足以识别需求缺口，不能证明真实负载性能、视觉质量、可用性或当前全套测试通过。后续设计应核实依赖/证据读取接口以及相关限制。
