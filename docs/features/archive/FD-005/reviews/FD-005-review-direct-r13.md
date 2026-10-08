# FD-005 独立静态审查（用户直接请求，Revision 13）

<!-- aiw-data: FD-005-review-direct-r13.json -->

- Reviewer session：`fd005-reviewer-20261005-4c7d916e`，独立于 Worker `fd005-worker-20261004-2916aea6` 和 Tester `fd005-tester-20261005-7865d383`。
- 来源事件：`FD-005-000013-test-report-ready`；该事件的目标角色是 PM，FD 状态为 Pending Test Acceptance。当前没有 Reviewer 交接。本报告是用户直接请求的预交接审查，**不是正式 Verification 结果**；未领取或发送 Reviewer 事件，未修改 FD。
- 审查基点：`04a0b6205b026c2753bf838ebfbaeec01c9b5945` 加工作区内 FD-005 当前源码。部分首轮实现已包含在基点中，因此结合 r5、r8、r11 Worker 报告审查全范围，当前 `git diff` 仅用于核对后续改动。未将 FD-002 的脏文件计入 FD-005。
- 建议：**需要变更**。用户已取消后续测试，以下建议基于可直接追踪的源码行为；不把未执行测试本身当作修复请求。

## 发现

### P1：工作区清理失败仍可能上报成功

`workers/runtime-core/workspace.mjs:126-140` 中，`cleanup()` 捕获普通 Git worktree remove 失败，只记录 `workspace-cleanup: preserved` 后正常返回。`workers/runtime-core/attempt.mjs:176-194` 将这一正常返回视为 `workspaceCleaned=true`，随后记录 `resource-terminal: settled`、`ready-to-report` 并调用控制平面的 `complete`。例如 worktree remove 因文件占用或 Git 错误返回 `PROCESS_EXIT_FAILED` 时，`resourceUncertain` 保持 false，控制平面可能接受成功，但实际工作区未清理。公共契约 `workers/runtime-core/PUBLIC_CONTRACT.md` 的“新增清理失败……不得生成未经确认的成功上报”与 FD 验收项 3 要求清理终态可追踪。当前日志虽保留了 `preserved`，上报与资源终态却表达相反结论。需要让上层获知清理失败，按失败或明确保留策略处理；不得同时宣称清理已确认并成功完成。

### P2：成功上报后的关键证据故障只能进入对账

`workers/runtime-core/attempt.mjs:188-207` 在 `client.complete` 正常返回后记录 `report-posted: accepted`。若该记录失败，catch 返回 `report-unknown`，不会重发 complete 或改发 fail；这符合非幂等控制平面接口，但控制平面可能已经接受完成。`ready-to-report` 已在请求前保存，故不能据此声称准备阶段关键证据缺失；仍需按公共契约把接受状态的证据故障列为对账风险，不能把 `report-unknown` 当作控制平面失败。此项是残余风险，不作为独立变更要求。

## 证据与覆盖边界

已核对 ISSUE-001 需求、FD 验收与用户已批准的 G1/G2 决策、公共契约和四类 Worker 的实施报告。r11 Tester 报告与 JSON 绑定实现事件 12、FD revision 12 / digest `bf3c8b3e65091a9100ffe90442316b6875384228b03a894c9a7fb014e6f353c4`、独立 Tester session；305 个场景 ID 唯一，全部 `uncovered`，执行/通过/失败均为 0，需求覆盖 0.00%，全 FD 分支未测量。r11 无测试命令、无 Planner 授权、无执行证据，符合用户取消后续测试的决定；未把准备中的 281 个用例算作通过。

r8 的 Planner 授权绑定一次离线命令 `node tests/fd005/r8/run-offline.mjs --authorization docs/features/reports/FD-005-test-authorization-r8.json` 和上述 Tester session。原报告记录 261 执行、256 原始通过、5 失败，完整场景通过 255/285=89.47%；r8 PM 明确退回，未豁免失败。五个失败中，非法参数后的成功上报已到断言，其余若干失败在摘要或 Git 目标断言前中止，不能推断所有目标行为已失败或已修复。r11 源码改动未经过新的编译或执行，历史局部覆盖与首轮 compile-only 结果不证明 r11。

真实 Codex 全会话禁存未获得平台认证，现有实现默认拒绝未认证注册；该保守准入与 G2 一致，不能写成真实环境已证明禁存。API 摘要、Git 发布竞争、跨语言进程治理和回滚仍缺当前版本运行证据。用户停止测试只改变执行安排，不改变 FD 行为要求。PM 尚未出具 r11 决策，本报告不替代其验收，也不触发 Reviewer handoff。

## 实际命令与未运行检查

本次只运行只读的 `Get-Content -Encoding utf8`、`rg`、`git status --short`、`git rev-parse HEAD`、`git diff`/`git diff --stat`，以及 PowerShell `ConvertFrom-Json` 统计现有 r8/r11 报告；使用时钟工具读取报告时间。未运行测试、编译、类型检查、lint、格式化、构建、网络、模型、CLI claim 或 emit。审查文件的写入仅为本报告与同名 JSON。

截至 2026-10-05 12:49 UTC，未获得 PM r11 测试接受、正式 Reviewer 交接或可支持 Verification 通过的当前版本执行证据。用户如维持不测试，后续决策应据实保留未测风险；本报告建议先修正上述静态可确认的收尾问题。
