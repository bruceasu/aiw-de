# FD-054 独立静态审查

- 最终结论：**通过**。三项源码问题在本轮审查中修复并经静态复核关闭；原目标讨论回复及正式版本的 API 回执已读取核对。完整浏览器交互尚未验证。
- Reviewer：`codex-fd054-review-20261027`。
- 来源事件：`FD-054-000002-review-requested`，已由本 Reviewer claim。
- 比较基线：`7c1d51a8c177b69a6be438b3863559d3cb34e300`；审查该基线上的 FD-054 工作区差异。
- 范围：受管讨论 Worker、冻结模型策略、讨论服务、目标工作台页面及附近文档；不包含 Sudoku 功能实现。

## 发现

1. **P1：自动刷新会覆盖正在输入的未发送消息。** `src/main/resources/static/ui/goals.js:184` 在 GET 前保存 `unsent`，在 `await load(id, true)` 后无条件写回。GET 等待期间用户仍可输入，且消息输入不设置 `dirty`，因此新增文字会被旧快照覆盖。`load` 本身没有更改消息输入，无须这次快照恢复；移除该写回或使用输入版本检查。此问题违反本 FD 的未发送消息保护要求。
2. **P2：配置工厂后阻断原有外部讨论后端。** `src/main/java/me/asu/aidev/goal/application/GoalTaskPolicy.java:26` 只要存在工厂设置便排他使用工厂 profile。例如工厂为 Codex，而原目录已批准 OpenAI profile、外部 OpenAI 讨论 Worker 在线时，选择 OpenAI 会在冻结阶段直接失败。应仅在工厂后端匹配时优先工厂 profile，否则沿用原目录与策略校验，保留原有外部 Worker 接入能力。
3. **P2：自动刷新清除附件分享选择。** `src/main/resources/static/ui/goals.js:168` 的自动 `load` 会重建 `renderFiles`；`renderFiles:238` 新建的分享 checkbox 总是未选中，这类选择又未参与 dirty 保护。存在待处理讨论时，用户为下一条消息勾选的附件每三秒可能被清空。自动刷新应保留选择，或避免重建这些尚未提交的控件。

文档附带问题：`docs/current-zh/GOAL-WORKSHOP.md:25` 仍无条件声称选择 Codex 不会自动启动讨论 Worker，与新增的自动启动说明冲突，应限定为未启用工厂自动启动的情况。

## 本轮修复复核

- 已删除自动轮询的 `unsent` 快照及写回；`load` 不再能通过该路径覆盖 GET 等待期间新增的消息。
- `preserveEdits` 更新不再重建来源、附件及版本编辑区域，保留分享选择；GET 返回后仍检查目标和编辑 dirty 状态。
- 工厂 profile 优先仅用于匹配工厂 runtime 的后端，其余后端恢复原目录 profile/策略与版本路径。
- 上述修复在本次尚未出具结论的独立审查中完成；未额外启动 Reviewer 或测试。
- 文档已限定手动启动用于未启用工厂自动启动的情况，冲突说明关闭。

## 已核对

- 受管角色进入 slot key，与 coder 隔离；启动回调在事务提交后执行，回滚移除预留 slot。
- 讨论子进程清除仓库绑定、关闭动态配置与执行 guard；固定 `goal-discussion` 角色、read-only 和禁用 shell。
- 现有 Python 路径为讨论建立独立临时目录、拒绝仓库 assignment；模型纯文字模式禁用并校验工具。没有发现本次改动新增文件工具授权。
- 冻结模型选择的现有 claim/执行专用分支仍在。
- 页面为正式版本操作补充局部反馈、缺失信息确认及版本定位；不把正式快照保存等同于执行计划确认。

## 证据及限制

- 实际执行：只读 `git status`、`git diff`、`git rev-parse HEAD`、相关源码/配置/FD/规则文件的 `Get-Content` 与 `rg`，以及真实 `aiw fd show/claim`。一次使用不存在的 `aiw fd events` 只返回帮助错误，没有修改状态。
- Worker 报告离线编译通过（315 源文件）和两个 JavaScript 语法检查通过；本 Reviewer 未重新运行或将其冒充独立测试。
- 未运行测试、构建、网络/API、模型调用或浏览器；未修改源码、部署或重启服务。
- 已读取根会话运行回执 `C:/Users/suk/AppData/Local/Temp/aiw-goal-execution/fd054-example-request.json`：原目标 `e71f1bc3-84df-4106-b462-2277e00eeeae` 请求 `GC-29125edc-2771-439b-a44f-7a8261fea473` 返回 RECEIVED，554 字符中文回复落入 assistant 消息；自动 Worker 为 RUNNING，待处理请求为 0。
- 同一回执列出正式版本 `3ba0b3d7-e029-409e-90b2-f4f49335c665`，sourceRevision 5、ready=false、planId=null；保留原两个版本、bodyUnchanged=true、servedSourceMatches=true。根会话 FD Verification 还记录备份、最终编译与 HTTP 200 结果；这些为执行者提供证据，不是 Reviewer 再次执行。
- 未完成的验证：浏览器实际点击、输入法、滚动效果，失败启动环境的运行复现及跨后端外部 Worker 的运行复现。相关失败/兼容路径以本轮源码调用链支持，不宣称这些场景已运行通过。
