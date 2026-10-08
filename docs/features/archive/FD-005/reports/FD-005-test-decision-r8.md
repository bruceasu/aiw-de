# FD-005 PM 测试决策，第二轮（r8）

<!-- aiw-data: FD-005-test-decision-r8.json -->

## 决策

**rejected，退回 Worker。** 已读取独立 [Tester 报告 r8](FD-005-test-report-r8.md)、同名 JSON、execution、五项原始失败事件及受控请求元数据。261 个实际用例中 256 通过、5 失败，按 fd-workflow Auto 3b 不接受已执行失败。REPORT-manifest 的 pass-case 仍只属于部分行为，完整需求覆盖为 255/285=89.47%。

- 来源事件：FD-005-000010-test-report-ready；当前 FD revision：10。
- 当前 FD digest：5b5a4a5d5b36f86cec6af83087d29035ffe58cf41fb93cb5468fa78d3cfedabd。
- Tester 测试绑定来源 event 9 / tested revision 9；当前 revision 是 Tester 提交后的交接版本，不混用。
- PM：fd005-pm-20261005-b698602d；时间：2026-10-04T23:37:17+00:00。
- 当前 CLI 不允许 PM 事件被 agent claim；PM 按 Auto 3b 直接绑定最新 test-report-ready 提交决策，不借用 Worker 或 Tester 的所有权。

## 实际证据与修复范围

| 失败 | 观察结果 | 后续要求 |
| --- | --- | --- |
| SDK-tool-parameters | 带未声明字段的 write 参数未阻止 complete=1；文件后置断言未执行 | 修复固定 SDK 工具错误处理与原始参数拒绝，验证无副作用及无成功上报 |
| TOOL-failure-then-text | 普通 Error 后第二工具被取消，未到最终目标断言 | 独立建立可修正与致命错误前提，允许符合契约的终止，保持失败不能被文本抹去 |
| SDK-summary-privacy / API-store-false | 各三个普通流式请求，complete=0，未观察到概要请求 | 保存元数据终态和 fake CP fail 诊断，确保预算、完整调用对及摘要前置成立后验证禁存/模型/期限 |
| WORKSPACE-git-finalize | push 阶段 PROCESS_EXIT_FAILED；缺少 Git argv/stderr/远端状态持久证据 | 新命令保留受控诊断，区分测试 guard/配置与产品失败；不推定零写入或回滚 |

源码自查另发现 SDK 0.3.9 接受 errorFunction，而产品使用旧 toolErrorFunction；成功路径可能误记 PROCESS_TREE_UNCERTAIN；适配器资源等待和未确认资源后继续领取需要收紧。Worker 处理这些实际职责范围内的问题，并补齐公共工厂角色拒绝与类型声明。以上属于 Worker 的源码证据，不能替代独立 Tester 或 Reviewer 的结论。

## 覆盖与例外

需求覆盖 **89.47%**；全 FD 业务分支 **not measured**。公共核心分支 629/777=80.95%，实际 API 转译 JavaScript 分支 94/116=81.03%，均为局部测量。25 个不完整场景包括 24 未执行及 REPORT-manifest 部分验证。本轮**无验收豁免**，局部覆盖率不能抵消失败。

Tester Markdown 的逐场景 E1 显示标签有 undefined；同名 JSON 的 raw_evidence 与 test_cases 保留实际 suite 路径，PM 按这些路径核对原始结果。原报告作为历史证据保留，后续报告需修正显示索引。

## 命令、资源与残留风险

唯一获批命令为 node tests/fd005/r8/run-offline.mjs --authorization docs/features/reports/FD-005-test-authorization-r8.json，运行 30.334 秒、exit 1；三个临时 Go 编译 exit 0，没有重跑。execution.resource_uncertain=false，拥有的临时根已在路径与 owner 核对后删除；未保存的合成 attempt 诊断和 Git 状态不能恢复。

真实四类 Worker 主循环、Go/Python SDK、真实端点、Codex 全会话禁存平台、发布竞态及升级回滚仍未运行。按已批准设计，缺少验证清单的 Codex Worker 在注册前拒绝；本轮不启用真实部署。首轮与本轮失败、授权、用例和证据保留。修复后产生新的 Worker 报告、Tester 命令提案和对应授权，再交独立 Reviewer；目前 Reviewer outcome 为 0，未重置既有计数。
