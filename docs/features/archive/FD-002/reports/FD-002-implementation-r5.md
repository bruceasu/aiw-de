# FD-002 实施报告（Worker revision 5）

<!-- aiw-data: FD-002-implementation-r5.json -->

## 交接与范围

- FD：`docs/features/FD-002_RUN_MISSION_DETAIL_AND_TIMELINE.md`，Worker 来源 revision 5。
- 来源事件：`FD-002-000005-design-ready`，Session `fd002-worker-20261005-88571fc5`。
- 基线：`7372e13c46779ebe60817872b47348385adbb240`；当前主工作区 master，顺序实施，无隔离 worktree 或合并坐标。
- 会话期间 HEAD 前进到外部本地提交 `81040ba5c591086d4a8639084d3ff46a726702de`（Update），其中已包含本 FD 的早段 Java 投影/控制器及决定/设计记录，也包含其他需求。保留该提交；后续提交仅包含 FD-002 的剩余页面、样式、指南和证据。Reviewer 应以原基线到当前 HEAD 的明确 FD-002 路径集合审查，不能把整个 Update 提交计为本 FD 范围。
- 用户已批准本期最近 300 条时间线，明确总数和截断；决定见 `FD-002-history-decision-r3.md`。
- 2.1–2.6 实现已完成；本报告是 Worker 实施证据，独立测试和 Reviewer 尚未完成。

## 改动与静态证据

| 工作项 | 实现 | 可审阅证据 |
|---|---|---|
| 2.1 | 新增私有 DTO、JDBC 仓库和组合服务 | requirement_task_id 权威关联；任务/attempt 先按 Run 筛选；200/1200/300/20 上限；无 RowMapper 嵌套查询、无逐任务 N+1 |
| 2.2 | Run GET 调用 Mission 服务，详情页重组需求、状态、阶段、计数与动作 | 保留原路由/权限条件/CSRF/输入名称/确认提示；所有保存文本用 th:text 输出 |
| 2.3 | 真实依赖 SVG 和同窗任务表 | 前置 → dependent；Kahn 分层、循环警告；真实 ID、安全锚点、窗口外/跨 subject 标注；无 TaskType 推断边 |
| 2.4 | latest FAILED 单独读取及最近 20 个 FAILED/RECOVERED | actual occurredAt/actor/detail；历史 attempt 分类/原因/时间；EXPIRED 不自动等于自动恢复 |
| 2.5 | 300 条窗口、范围说明、UTC 分组、类别/文本筛选 | 原排序保留；筛选仅返回窗口；事件 key 基于不可变身份，不含变化详情或总数 |
| 2.6 | 局部刷新与中英指南 | console.js 未修改；表单/控件在更新区外；mission.js 重新应用过滤/图开关并保留内部滚动 |

新文件：`WebConsoleMissionDetail.java`、`JdbcWebConsoleMissionReadRepository.java`、`WebConsoleMissionQueryService.java`、`static/ui/mission.js`、两份 `WEB-CONSOLE-RUN-MISSION.md`。修改：`WebConsoleController.java`、`templates/ui/run-detail.html`、`static/ui/console.css`。FD 和决定/报告属于本次流程证据。

## 已执行命令

- `Get-Content`、`rg`：定向静态读取规则、设计、原页面/服务/迁移和附近测试契约。
- `aiw fd --help`、`emit decision-recorded`、`claim`、`emit design-ready`：记录实际用户决定及 Planner/Worker 所有权。
- JDK 21：`C:/green/apache-maven-3.9.9/bin/mvn.cmd -o -DskipTests compile`，**BUILD SUCCESS**，178 个 Java 源文件，9.413 秒。
- 原始编译输出：`.ai/fd/FD-002/compile-r5.txt`。仅生成类和资源，无 package 或最终分发产物。
- 定向暂存后审阅 `git diff --cached`，再提交本 FD 的局部变更，形成供独立 Tester/Reviewer 使用的稳定基线。

## 未执行与剩余风险

- 未执行单元/集成测试、浏览器、Thymeleaf 渲染、JS 执行、覆盖率、网络、依赖下载、格式器或最终构建。运行预算为零；FD-001 授权不移用。
- 编译不能证明 Thymeleaf 表达式、SVG 可访问性、过滤和局部刷新交互成功，须独立黑盒验证。
- 原 TimelineService 全量读取单 Run 投影后排序截断，未优化或测量高数据量成本。
- 任务/边/attempt 上限会产生部分图；页面明确范围，不保证有界窗口保存全部历史。
- 新查询遵循现有只读事务；多查询在数据库既有隔离级别下执行，不宣称跨查询严格原子快照。
- FD-001 尚未 Complete；共享刷新修正已有提交，但其独立精简复验仍待授权。
- 本机此前未找到 Firefox；本 FD 尚未获得 Firefox 延期决定。

## 下一角色

由独立 Tester 在自己的 Session 领取真实 implementation-ready 事件，依据 FD/公共页面契约派生场景，写入 `tests/fd002/` 和报告。先准备可审阅的一条离线、本地、临时数据库/浏览器命令；Planner 审阅后按本仓库规则请求运行授权。当前不得写入测试通过或覆盖率结果。
