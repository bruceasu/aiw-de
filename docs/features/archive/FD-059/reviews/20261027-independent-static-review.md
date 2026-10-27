# FD-059 独立静态审查

- Reviewer：`codex-fd059-review-20261027`。
- 来源事件：`FD-059-000002-review-requested`，已 claim。
- 比较基线：`2b97f53ee089ea797e662f43f55de298be7e89b0` 上本 FD 增量，不包含更早已交付功能。
- 最终结论：**通过**。本轮加载覆盖问题修复并经静态复核关闭；已读取限定只读 API/页面证据，用户原草案未修改、未启动。完整浏览器交互仍未验收。

## 发现

**P2：现有草案加载完成前可输入，随后被无条件覆盖。** `workflow-editor.js` 新 `?edit=` 路径先等待 catalog，再等待 GET 原 draft；仅 Save 被禁用，目标、模板、步骤控件仍可操作，最终无条件写回 `goal.value` 和 `steps`。慢响应时用户先输入的文字会丢失。原草案尚未取得前应禁用编辑区域，或检查输入版本并拒绝异步覆盖；能力帮助和取消入口可以保持可用。

## 已核对

- 本轮修复：edit 模式在 catalog/原草案读取前禁用目标、模板、套用模板、增加步骤和保存控件；成功读取并同步完成步骤渲染后才解锁，失败保持禁用。帮助和取消仍可操作，消除读取过程中覆盖用户新输入的路径。

- 草案读取和保存的控制台 operator、会话/CSRF 路径保持；服务核对原 actor、非代理、DRAFT、无任务、未归档、非托管。保存与既有确认在相同 Authority 锁下串行，SQL 同时比较 DRAFT/version，冲突不覆盖。
- 保存保留原 ID/source/createdBy，更新 spec/version/reason/updatedBy；不调用创建任务或确认执行。规范、图、角色、仓库检查复用既有契约；基线校验来自既有 Step/WorkTask 约束。retarget 共享相同未开始草案保护。
- 页面以 `?edit=` GET 原草案并向同 ID POST；新建和复制仍走原 context 路径，不被编辑模式误导到创建新计划。失败保留输入，显式显示保存错误。
- Goal 文案区分目标快照、执行草案、确认并开始执行，已有计划链接到详情顶部编辑/启动入口。没有把保存目标版本改为自动开始任务，也未修改已保存目标快照。
- 能力说明使用现有 dialog/console-confirm/workspace-confirm 样式和关闭按钮，原生 dialog 支持 Escape；内容更新工厂、外部 Worker、独立审查与目录/工具权限边界，新增中日文条目对应实际 UI 文案。

## 证据与限制

- 实际只执行 `git diff/status/rev-parse`、`rg`、`Get-Content` 及真实 `aiw fd show/claim`。未运行测试、构建、API、模型或用户计划，也未修改产品源码。
- 已读取 WorkflowDraftEditingTest 八项断言：同 ID/source/DRAFT、不创建任务、其他所有者、旧版本、确认/归档/托管/已有任务和并发拒绝。执行者报告加上 ReviewedCompletion 十二项共二十项通过、compile-only 319 源及三份 JS 语法检查通过；不是 Reviewer 重新执行。
- 完整浏览器编辑/弹窗/滚动/输入、数据库并发及真实保存 API 未由 Reviewer 运行。本轮按只读验证约束保留用户原草案，不以测试名义启动计划或改用户内容。

## 只读运行证据核对

- 已读取 `%TEMP%/aiw-goal-execution/fd059-readonly-receipt.json`：原计划 `FP-26e5cf21-3e6c-46fe-985d-d26fcee8ad18` 的新 draft GET 返回 DRAFT/v0、tasks=0、mutations=0。
- 回执检查 Goal 保存标签、详情顶部草案工具栏、编辑器 dialog、同 ID 保存脚本及翻译目录均存在；FD Verification 另记录加载保护脚本 HTTP 读取成功。页面标记读取证明服务加载了本次内容，不证明键盘、视觉或实际浏览器保存已运行成功。
- FD Verification 记录备份 `D:/temp/data/aidev-r3.before-FD059-20261027-235325.db`、integrity ok、无活动任务时重启加载。未调用模型或确认执行用户计划；Reviewer 只读取实证，没有再次访问服务或修改草案。
