# FD-001 独立评审（revision 9）

<!-- aiw-data: FD-001-review-r9.json -->

## 结论与发现

**结论：changes-requested。** 本轮有三项 P2 功能缺陷，需交回 Worker；不是因低覆盖率或 Firefox 延期拒绝评审。

### F1 · P2 · 等待类别总量与示例不属于同一分类

位置：`src/main/java/me/asu/aidev/webconsole/application/WebConsoleQueryService.java:67`；权威分类在 `src/main/java/me/asu/aidev/fleet/jdbc/JdbcFleetDashboardRepository.java:205`。

Fleet 的总量按 `policy → Run admission → capability → Worker capacity → Fleet admission` 的互斥优先级计算，QueryService 却用各个独立条件筛选示例。例如一个处于 PAUSED Run、没有兼容 Worker 的 READY task，Fleet 将它计入 Policy，不增加 unmatchedReady；Overview 又把它列入 No matching capability。界面可能显示该类别总量为 0，同时列出受影响任务，违背 AC02 的准确统计及详情口径。

最小修正：保留 Fleet 权威总量，对齐五组示例的互斥条件：Policy 为 `!policyAllowed`；Run admission 为 `policyAllowed && !admissionAllowed`；无能力为 `policyAllowed && admissionAllowed && compatibleActiveWorkers == 0`；Worker capacity 再要求兼容数量大于 0、可 claim 数量为 0；Fleet admission 再要求可 claim 数量大于 0且 `!fleetAdmissionAllowed`。页面和双语指南应明确类别计数的优先级；若展示额外重叠事实，应单独标注，不能沿用类别总量暗示这是同一集合。不改 Fleet 调度权威或公开契约。

### F2 · P2 · 等待组动态总数变化后丢失展开状态

位置：`src/main/resources/templates/ui/dashboard.html:44`，`src/main/resources/static/ui/console.js:71`、`:102`。

waiting-group 的 details 没有稳定 key；detailKey 回退到 summary.textContent，其中同时含有类别标题和动态 total。用户展开分类、焦点离开该区域后，若下一次快照把数量从 1 改为 2，原 key 与新 key 不同，替换时该 details 会被关闭。AC07 和指南承诺保持展开详情，当前 mission/activity 的稳定 key 不能保护等待组。

最小修正：为 waiting-group details 设置只依赖类别身份的 `data-detail-key`，例如 `g.label`；不要把计数或任务列表纳入 key。

### F3 · P2 · 跨刷新区域文本选择会被局部更新破坏

位置：`src/main/resources/static/ui/console.js:98`。

选区保护只判断 region 是否包含 selection.anchorNode。用户从 Current missions 选到后面的其他面板时，起点区域被保留，但终点及中间与选区相交的区域仍然 replaceWith，导致选区被重设或丢失。跨区域选区的 anchor/focus 还可能都在某个中间区域外，单独检查两个端点也不足以解决。AC07 要求正常局部刷新保持阅读内容，未覆盖的 S078 不能当作豁免。

最小修正：对非折叠 Selection 的各个 Range 使用 `intersectsNode(region)` 判断；任意相交区域都保留。继续沿用原焦点保护、部分快照陈旧提示和最多一个在途请求，不修改浏览器轮询的其他契约。

上述三项均为静态确定的代码路径，Reviewer 没有运行复现脚本；后续若要运行新的定向复验，必须由 Planner 按新的实施事件和 Tester session 记录授权。

## 独立身份和审查范围

Reviewer session：`fd001-reviewer-20261004-9195c0ad`，与 Worker、Tester 不同。已真实领取 `FD-001-000009-test-accepted`；领取时 FD revision 9、SHA-256 `46803d98b1fd2563f5680d6181b5154a158cb1c836dd1b6f91c815a3297742d5` 与 receipt 一致。

审查基线 `d33dbbd12dd8aa906633fe4b518f73d14ee283ee` → 当前提交 `ae19d463436fe9730b07e7106ecbdfacfab98845`（含实现提交 `7723a37`）。范围限本 FD 的私有 webconsole 读取、Java/UI/CSS/JS、两份指南、测试与证据。其他未提交 worker、REQ、FD-002–004 改动未纳入。本轮只新增独立报告并更新 FD Verification/TODO，不实施产品修复。

## Standards 与 Spec 核对

- Controller/QueryService/JDBC 读取分层及原动作服务边界保留；新 DTO 属于私有浏览器投影，没有 schema、公开 REST、依赖或权限改动。
- PENDING 在 SQL 中先过滤再限量；activeRunCount 与 Fleet 的非终态条件一致；展示 Run 批量补充上下文。活动各来源先 LIMIT，再 UNION ALL 排序 LIMIT，没有逐 Run 读取完整 timeline。Run/attempt/approval/control 事件来源及终态时间与现有 timeline 权威规则相符；查询成本未实际测量。
- 模板使用 th:text；Worker 身份由完整 ID 的 SHA-256 决定，状态和 lease 独立显示。原动作路由、CSRF、requireOperator/requireApprover 边界保持。共有样式、焦点、动效和窄屏布局有源码及真实截图支持。
- 刷新对隐藏/编辑、登录跳转、角色变化、非预期 HTML、失败保留快照、10 秒超时及 30 秒陈旧有实际保护代码；阅读保护存在 F2/F3 缺陷，不能据其他场景通过认定全部 AC07 完成。
- M1 与 M2–M4 范围在两份指南及 FD 中区分；不会因本阶段把整个 REQ00001 标记完成。

## Independent Test 和 PM 决定

已核对 [r7 测试报告](../reports/FD-001-test-r7.md)、同名 JSON、原始 `tests/fd001/evidence-r7/results.json`、公开 UI 断言脚本、四角色输出及 1440/390 截图。原始结果有 36 项业务断言，全部 passed，没有 runner/基础设施失败。88 项场景逐项拆分，42 项映射已执行断言、46 项保持 uncovered；没有把标题可读当作五类等待条件正确，也没有把入口可见当作服务端授权通过。42/88 = 47.73%，源码分支未测。

[r7-v1](../reports/FD-001-test-authorization-r7-v1.md) 和 [r7-v2](../reports/FD-001-test-authorization-r7-v2.md) 的命令、cwd、实现事件 7、revision/digest、Tester session 和用户批准引用一致。诊断证据证明新 page target 到达登录页；v2 记录 host 检查条件后放行。诊断不计业务断言，r4 的受阻历史及 r5 拒绝没有回写成通过。

[PM r8 决定](../reports/FD-001-test-decision-r8-v1.md) 明确接受低于 70% 的要求覆盖率和不可用的分支覆盖；本评审尊重这两项例外。用户允许 Firefox 延期，未因未运行 Firefox 要求重跑。Worker 的 4 个 IT、29 个浏览器通过项与后续 2 个焦点通过项作为补充证据，未加入独立计数；历史整套 exit 1 保留。

## 实际命令、未运行与风险

本 Reviewer 实际执行：`aiw fd claim FD-001 FD-001-000009-test-accepted --session fd001-reviewer-20261004-9195c0ad`；`git diff d33dbbd12dd8aa906633fe4b518f73d14ee283ee ae19d463436fe9730b07e7106ecbdfacfab98845 --stat` 及定向指南/Java diff；定向 `rg`、`Get-Content -Encoding UTF8` 和 FD `Get-FileHash`；`aiw fd emit --help`；使用 view_image 读取两个既有截图。写报告后仅执行一次定向静态内容检查，再由真实 `aiw fd emit FD-001 changes-requested --producer reviewer --artifact docs/features/reviews/FD-001-review-r9.md --source-event FD-001-000009-test-accepted` 完成交接。

未运行测试、浏览器、Maven、compile、覆盖率插桩、formatter、lint、typecheck、验证脚本、外部网络、最终构建或部署。Reviewer 没有额外运行授权，也没有改产品源码。

除本轮 F1–F3，残余风险为独立测试列出的 46 个未覆盖场景、源码分支覆盖不可用、大数据量查询成本和 Firefox 兼容性。未来归档移动 Markdown/JSON 时需核对外部图片目录的相对链接；当前 active 路径存在，不将尚未发生的归档当作当前链接损坏，也不扩展本 FD 的 CLI 范围。

Reviewer 失败结果累计：1/3。修复后应针对本轮实际缺陷提供证据，再交下一轮独立评审；不能在此结果上直接关闭或归档。
