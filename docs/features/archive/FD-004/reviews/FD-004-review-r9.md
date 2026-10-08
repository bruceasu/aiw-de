# FD-004 独立审查（revision 9）

<!-- aiw-data: FD-004-review-r9.json -->

**结论：changes-requested。** Reviewer Session：`fd004-reviewer-20261005-00570883`；来源事件：`FD-004-000009-test-accepted`。审查基点 `00815ac`，Worker 提交 `0453efc`，FD revision 9，来源 digest `310410946926daa174c1c0b48195d0b80f12810bcf196ad471b140254af600ec`。

## 发现

1. **高：Run 页刷新后，审批事实和操作表单不成组更新（AC04）。** `run-detail.html:24` 的 `mission-context` 属于 `data-refresh-region`，显示可刷新的审批状态；`run-detail.html:78-87` 的审批证据和表单在刷新区域外。`console.js:88-106` 只替换带 `data-refresh-region` 的区域。因此另一用户完成决定后，本页会显示新状态，但仍保留旧版证据和 PENDING 操作表单；若当前页最初没有审批，审批请求形成后也不会出现新表单。服务端条件更新能防止反向决定，却不能满足“同一 approval ID 的表单和证据在刷新时成组替换”以及真实的页面反馈。应将审批证据与表单放进同一刷新区域，或将其所在区域整体刷新，并保持焦点/编辑保护。

## 验收与证据

- AC01、AC02、AC03、AC05、AC06：代码静态检查显示审批 ID 到保存版本、gate、review/verification 的受限关联、状态分区与有界历史、原有审批服务和 CSRF、纯文本渲染及无外部读取入口；未发现可确定的额外实现缺陷。上述运行行为尚未被本轮用例证明。
- AC04：上述缺陷使刷新行为不满足当前验收要求。
- 独立 Tester 将 29 个可观察场景拆分记录；仅 `approve-basic` 执行并通过，其余 28 个未执行。需求场景覆盖 1/29（3.45%）；业务分支覆盖未测量。原始 Failsafe 文本和 XML 记录 1 次测试、0 失败、0 错误、0 跳过。首次命令因 PowerShell 参数解析失败，未进入测试；第二次命令与修订授权 `FD-004-test-authorization-r7-v2` 的命令、工作目录、FD revision/digest 和 Tester Session 一致。
- PM 在 `FD-004-test-decision-r8-v1` 接受低于 70% 和分支覆盖未测的检查例外，仅准许独立静态审查；该例外不豁免 AC04。未将 28 个未运行场景计为通过。

## 命令与剩余风险

实际执行：读取 FD、Worker/Tester/PM 报告和授权、Failsafe 原始报告；`aiw fd resume FD-004`；`aiw fd claim FD-004 FD-004-000009-test-accepted --session fd004-reviewer-20261005-00570883`；`git status --short`、`git diff --stat 00815ac..0453efc`、`git diff 00815ac..0453efc -- src/main/java src/main/resources/templates/ui src/main/resources/static/ui/console.css`、定向 `rg` 与 `Get-Content`。

未运行测试、浏览器、SQL 查询、模板渲染、网络访问、覆盖率、编译、格式器或最终构建。精确历史关联、异常矩阵、权限/CSRF、窄窗口与并发交互仍缺少运行证据；修复刷新缺陷后需再次独立审查。
