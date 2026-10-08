# FD-004 Worker 修复报告（r10）

<!-- aiw-data: FD-004-implementation-r10.json -->

- 来源事件：`FD-004-000010-changes-requested`；Worker Session：`fd004-worker-20261005-40492535`；FD revision 10。
- 针对首轮 Reviewer 的 AC04 发现：Run 页原先的 `mission-context` 会局部刷新审批状态，而证据与表单在刷新区外。现将有审批权限者的状态、证据和操作表单放进独立的 `mission-approval-actions` 刷新区域；该区域在刷新时整体替换，聚焦或编辑时整体保留。只读角色的审批状态与证据都留在 `mission-context`，同样整体刷新。
- 表单和证据使用同一审批 ID；刷新后新增审批请求会在同一区域出现，已决定记录的表单会一起消失。决定服务、角色校验、CSRF 与不可逆记录未变。中英文 Web Console 指南已同步修正。
- 仅修改 `run-detail.html`、两份指南与 FD/报告。通过模板与 `console.js` 的区域替换/焦点保护路径做静态核对；`git diff --check` 无空白错误。未运行新测试、模板渲染、浏览器或第二次编译。先前 Java 21 离线编译及一个基本审批 IT 的历史结果仍有效，但不能证明本次刷新修复在浏览器中已验证。
- 剩余风险：28 个场景仍未运行，尤其是焦点/编辑期间的实际 DOM 替换；请独立 Reviewer 按修复后的 FD 与实际差异重新审查。
