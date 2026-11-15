# FD-089 独立审查

- FD revision：2
- Source event：`FD-089-000002-review-requested`
- 审查基线：当前 `develop` HEAD `c2d60b5e25a51d5bbd3e3399ed9cf8c3b2e32a0a`；实现提交 `517795a91787288c8c4bcc56d44434163c67a0c9`（父提交 `81c7165fe0d57cf06cc24403792d8473bbf5b3b0`）
- Reviewer session：`fd089-reviewer-20261115`
- 结论：`changes-requested`

## Findings

### F1：权限主体仍以 Agent 建模，与已确认的 Worker 身份模型冲突

`docs/FUNCTION-INVENTORY.md` 的术语规则（147–149 行）把 Worker 定义为稳定团队成员，把 Agent 定义为模型与工具循环，并明确 Agent 不另设成员、接入或权限体系。但本 FD 的权限页面仍把被授权者称为 Agent（`src/main/resources/templates/ui/authority.html:4,7,10,31,34`）；请求对象使用 `agentId`（`src/main/java/me/asu/aidev/authority/ActionPermissionContract.java:10`）；授权服务还要求匹配 `identity_kind='AGENT'`（`src/main/java/me/asu/aidev/authority/BusinessActionPermissions.java:105-106`）。这使操作者仍会把 Agent 理解为团队成员和独立权限主体。

现有授权记录写入 `worker_id`，因此静态证据不能证明这里创建了第二套权限存储；问题是产品词汇和授权对象边界没有跟已确认方向对齐。请将页面中的授权对象表达为稳定 Worker，并把 Agent 连接/进程信息留在身份诊断中；若 API 内部仍需保留 `agentId`，应明确它映射到同一个 Worker 身份，不形成第二套主体。

### F2：实现提交含有未列入 FD 范围的运行时修改

FD-089 范围是权限页面和批量授权，但实现提交 `517795a9` 还修改了 `ExecutionRuns.java`、`RunContract.java`、`worker-run.js`、`worker-workbench.html`，以及 `execution_configuration.py`、`native_run.py`、`openai_api_runtime.py`、新增 `progress_monitor.py` 和 `run_channel.py`。这些运行时行为未在 FD 的 Work Items、Acceptance 或 Verification 中说明，无法据此将其作为 FD-089 的交付内容审查。

请把这些文件对应到已批准的其他 FD/Issue 并分别审查，或从 FD-089 的变更归属中移出。权限页面所需的 `agents`、`authority`、`fragments` 和文案资源改动属于本 FD 页面范围。

## 静态核对

- 批量请求最多 32 项，要求动作和 request ID 唯一，且共用 Agent、范围、到期时间和每项次数上限。
- `/action-permissions/batch` 与单项路由一样受 human route 限制；服务逐项调用现有 `issue` 校验，并在外层 `@Transactional` 中执行。
- 单项请求按 request ID 与 digest 幂等；UI 确认逐项动作和次数，写入结果不确定时保留原请求，先刷新核对再允许重试。
- 未发现批量接口扩大动作范围或新增默认权限的问题。上述实现行为只作静态判断，未作运行验证。

## 命令与未运行项

- 实际运行：`aiw fd show FD-089`；`aiw fd claim FD-089 FD-089-000002-review-requested --session fd089-reviewer-20261115`；针对权限 DTO、服务、控制器、路由、UI 和术语文件的 `rg -n` 静态检索；`git show --format= --unified=1 517795a9 -- <权限实现与页面文件>`；`git diff-tree --no-commit-id --name-only -r 517795a9`；`git rev-parse HEAD`；`git show -s --format="%H%n%P%n%s" 517795a9`。
- 未运行测试、构建或编译；本次为静态审查，运行验证不在授权范围内。
- 残余风险：事务回滚和 HTTP 不确定结果恢复基于代码路径判断，未用测试或运行证据验证。
