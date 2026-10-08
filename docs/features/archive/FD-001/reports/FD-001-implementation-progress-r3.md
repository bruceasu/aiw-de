# FD-001 实施进度（尚未就绪）

<!-- aiw-data: FD-001-implementation-progress-r3.json -->

- Worker：`fd001-worker-20261004-b2aa6c85`。
- 来源：已认领事件 `FD-001-000003-design-ready`。
- 范围：REQ00001 M1；主工作区顺序实施，其他 worker 和 FD 的既有变更保留。
- 状态：源码/指南已修改，定向回归及大部分浏览器证据已取得；焦点检查尚未解决，本报告不是 `implementation-ready` 交接。

## 修改与依据

| 内容 | 实施 | 静态依据 / 关联验收 |
|---|---|---|
| 私有只读投影 | 新增 `WebConsoleOverview` 与 `JdbcWebConsoleOverviewReadRepository`；QueryService 组合单份 Fleet 快照 | AC02/03/09；不改 schema/公开 REST/写入权威；非终态计数条件与 Fleet 的 liveRuns 一致 |
| Run 详情 | 从 Fleet activeRuns 取 12、attentionRuns 取 8，对不同 Run ID 批量取 requirement/已完成任务数/失败原因 | AC02/03；缺失摘要明确 unavailable，动态完成数不显示工程百分比 |
| Pending | SQL `WHERE status='PENDING'` 后 LIMIT 8，独立 COUNT；旧 pending facade 也改为先筛选再限量 | AC02/09；不从近期审批列表推算总量；链接审批 Inbox 支持非 Run 归属 |
| 活动 | Run 开始/终态、Task 创建、Attempt claim/start/finish/gate、Approval request/decision、Run event 10 个分支，每支先限 20 再合并限 20 | AC05/09；与 timeline 权威字段和状态规则对齐，按事件时间/稳定来源键排序；无全 Run timeline 遍历 |
| 首页信息区 | 快照、容量、活跃 Run、关注/审批、Worker、等待、活动，各区标记明细窗口/截断 | AC01/02/05；等待分类与 Fleet 统计匹配，多条件可重叠；长需求可展开 |
| 共享视觉 | 深色 CSS tokens、Overview/Fleet 导航、表单/表格/错误/认证样式、键盘焦点、响应式断点 | AC01/06/08/10；本地 CSS/SVG/系统字体，没有新依赖 |
| Worker 身份 | 完整 ID 的 SHA-256 映射轮廓/色相/标记，首页与 Fleet 共用 SVG fragment | AC04/08；ACTIVE Idle/Executing、DRAINING、OFFLINE 分开；无推测角色/健康；减少动效与本地开关 |
| 局部刷新 | 5/8 秒、单请求、10 秒超时、30 秒陈旧；只替换只读区，保留快照/焦点/选择/展开/滚动；隐藏/编辑暂停并恢复 | AC07；响应跳转/角色变化/异常 HTML 停止；失败下一周期再试；动态控件事件委托 |
| 操作 | 保留原路由/权限/CSRF/application services；Worker 操作表不随只读更新替换，并标明页面初次读取状态/重载入口 | AC06/07/10；提交状态竞争仍由服务端判断 |
| 文档 | 中英文 Web Console 指南同步范围、更新/暂停/失败规则与 M1/M2–M4 边界 | AC10；没有把整个 REQ00001 标记完成 |

## 实际命令与结果

1. 通过 `Get-Content -Encoding UTF8`/`rg` 定向读取 FD、仓库预算、现有 controller/query/Fleet/timeline/测试及指南，使用 `apply_patch` 和一次定向文本替换修改源码与文档。
2. 离线 compile-only：`$env:JAVA_HOME='C:/green/jdk-21.0.8'; & 'C:/green/apache-maven-3.9.9/bin/mvn.cmd' -o -DskipTests compile`。结果 `BUILD SUCCESS`；Java release 21，175 个源文件，7.891 秒。没有生成新发布包，没有依赖下载。
3. 定向读取中有两次错误文件路径：QueryService 实际位于 `application/`，IT 实际位于 `webconsole/`；随后按正确路径读取，未重跑失败的原路径。浏览器工具目录读取没有发现可用工具，未启动浏览器、应用或测试。
4. 用户明确授权后执行 `C:/green/apache-maven-3.9.9/bin/mvn.cmd -o "-Dit.test=R9S1WebConsoleIT,R9WebConsoleSecurityIT,R9WebConsoleOperationsIT,R9WebConsoleApprovalIT" test-compile failsafe:integration-test failsafe:verify`（JDK 21）。4 tests / 0 failures / 0 errors / 0 skipped，14.166 秒；只使用测试临时 DB 和本机端口，没有打包。
5. 已审阅并执行临时验证辅助命令 `C:/green/python312/python.exe .ai/fd/FD-001/visual-runtime.py`。首次及修正后的第二次在 Worker 窗口断言处失败：脚本错误假设管理状态在 100 条 Fleet 明细之外的 Worker 仍应在首页出现；依据既有 Fleet 排序/限量规则修正为截断和四状态两个样例，失败原始记录保存在 `FD-001-visual-evidence/worker-browser-results-attempt1.json` 与 `attempt2.json`。
6. 用户另行授权修正样例后重跑一次；第三次 30 项检查中 29 项成功，最后的程序化聚焦/可见 outline 联合断言失败，进程退出 1。原始结果见 [浏览器结果](FD-001-visual-evidence/worker-browser-results.json)，没有按成功描述。已保存 18 张真实本地数据截图，包括 [1440px 首页](FD-001-visual-evidence/overview-populated-1440.png)、[390px 首页](FD-001-visual-evidence/overview-populated-390.png)、[四状态 Fleet](FD-001-visual-evidence/fleet-states-1440.png)、刷新失败/陈旧/减少动效及旧页面。人工查看了 1440/390 首页截图；未发现整页溢出。
7. 已完成一次定向 `git diff --stat`/`git diff`/`git status --short` 静态检查，只检查 webconsole/UI/指南与 FD 路径；原路由、动作服务、CSRF 和权限保持；未暂存或提交其他任务文件。

## 尚未执行与 Gate

- 未执行覆盖率、格式化/lint/typecheck、全量构建、外部网络访问或发布。Firefox 经用户明确授权延期，未运行，保留兼容风险。
- SQL/Thymeleaf、原操作/VIEWER 安全、四尺寸布局、Worker 状态/身份、刷新保护/失败/单请求/10 秒超时/30 秒陈旧/异常 HTML 和减少动效已有相应证据。完整四角色、真实背景页与编辑暂停/恢复、最终焦点验证仍不能宣称全部通过。
- `fd-workflow` Auto 第 3 步要求 scoped items 与必要证据已解决才能发 `implementation-ready`；仓库 `AGENTS.md` 与 core/validation 限制运行重试。前次授权的重跑已执行且失败，当前提交只核对 New Run 输入/键盘焦点的 `visual-runtime.py focus` 授权请求；未获明确答复前不再运行。
- 建议先离线运行 4 个 Web Console IT，再以本地临时数据库进行浏览器验收。具体测试脚本应先静态审阅；不下载浏览器或依赖，不访问外部服务。独立 Tester/PM/Reviewer 仍需正式回执与 Dual 报告。
- 当前不提交未经验收的交付，不归档；其他任务的未提交变更保持原样。

## 残余风险

编译不能替代浏览器证据。当前表单焦点联合断言失败，须区分 UI 缺陷与程序化聚焦测试条件，不能忽略后交付。独立 Tester/Reviewer 尚未交接，背景/编辑暂停恢复及完整角色矩阵仍需覆盖。共享 CSS 已有旧页面截图和定向操作回归，但 Firefox 尚未验证；活动查询虽然结果有界，高数据量成本未测。部分聚焦区域保留旧快照时提示仍旧，不把部分成功伪装成完整更新。Worker 操作按钮仍是初次加载的可用性快照，用户应重载并依赖提交时的服务端权威检查。
