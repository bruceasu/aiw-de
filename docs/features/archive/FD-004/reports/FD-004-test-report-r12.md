# FD-004 独立测试报告（revision 11）

<!-- aiw-data: FD-004-test-report-r12.json -->

## 结论

Tester Session：`fd004-tester-20261005-3e03b1cd`；来源事件：`FD-004-000011-implementation-ready`；FD revision 11；digest：`6218e3ae9566927f6239f302b0405bc2034317920ec191586271aec5e4a6a768`。

本轮修复针对 AC04 的 Run 页审批状态、证据与表单同区域刷新。当前没有针对该刷新行为的已授权、可直接运行黑盒测试或浏览器夹具。本轮**未运行任何测试**。29 个可观察场景执行 0 个、通过 0 个、失败 0 个、未执行 29 个；需求场景覆盖率 **0/29（0.00%）**，业务代码分支覆盖率 **未测量**。

上一轮 `FD-004-test-report-r8` 在 revision 7 的一个既有基本批准集成用例通过；它不检查 Run 页刷新，也不计入本 revision 的已执行覆盖。

## 场景与证据

| ID | 验收 | 可观察行为 | 状态 |
|---|---|---|---|
| pending-history-partition | AC01 | 待决定和已决定记录分区，决定后从待决定区移走 | 未执行 |
| exact-evidence-link | AC01 | 单条审批显示同一 approval ID、Run、source task/attempt、result revision、gate 时间和 review/verification 来源 | 未执行 |
| older-attempt | AC01 | 同一 source task 有多个 attempt 时绑定审批保存的 attempt，不借用更新 attempt | 未执行 |
| repaired-revision | AC01 | repair 后出现新结果时，旧审批仍显示原 revision 和原 gate | 未执行 |
| review-verification-details | AC01 | review 与 verification 分别显示正确 task/attempt、verdict、summary 和来源时间 | 未执行 |
| missing-summary | AC02 | 已存 summary 缺失时明确提示，不补造 PASS | 未执行 |
| missing-source-attempt | AC02 | source attempt 缺失时显示不可核对及原因 | 未执行 |
| gate-mismatch | AC02 | gate 缺失、非 PASSED 或关闭时间不匹配时不显示错误的通过证据 | 未执行 |
| review-mismatch | AC02 | review 或 verification 缺失、版本或依赖不符时显示不可核对 | 未执行 |
| bounded-history | AC02 | 已决定历史最多返回规定窗口，显示返回数和上限，不宣称完整审计 | 未执行 |
| filtered-history-order | AC02 | 已决定状态先过滤，再按时间和 ID 稳定排序并限制数量 | 未执行 |
| pending-count | AC02 | 待决定总数独立准确统计，不用窗口长度冒充总数 | 未执行 |
| approver-role | AC03 | APPROVER/ADMIN 可用动作与服务端权限一致 | 未执行 |
| viewer-role | AC03 | VIEWER 无审批动作，有效 POST 仍被服务端拒绝 | 未执行 |
| csrf | AC03 | 无效或缺失 CSRF 的审批 POST 被拒绝且状态不变 | 未执行 |
| invalid-action | AC03 | 非法或不满足前置条件的审批提交由权威服务拒绝 | 未执行 |
| approve-basic | AC03 | pending 审批可从页面批准，并保存 APPROVED、决定人和备注 | 未执行 |
| reject-basic | AC03 | pending 审批可拒绝，并保存拒绝归属和备注 | 未执行 |
| immutable-decision | AC03 | 已批准/拒绝记录不能改成相反结论 | 未执行 |
| concurrent-decision | AC03 | 两个决定竞争时第二次提交显示实际冲突，不报假成功 | 未执行 |
| grouped-refresh | AC04 | 刷新后证据和操作表单属于同一 approval ID，旧表单不配新证据 | 未执行 |
| stale-submit | AC04 | 过期页面提交后重读实际状态和版本，反馈真实冲突 | 未执行 |
| long-content | AC05 | 长 revision 与 summary 可完整阅读，不破坏页面布局 | 未执行 |
| empty-state | AC05 | 无审批及无工程证据时显示可理解空态 | 未执行 |
| exceptional-state | AC05 | 证据读取异常时显示可理解错误而不误报成功 | 未执行 |
| narrow-window | AC05 | 窄窗口仍能访问证据与动作 | 未执行 |
| evidence-script | AC05 | summary/备注含 HTML 或脚本时按文本显示，不执行脚本 | 未执行 |
| publication-ref | AC06 | publicationRef 仅显示已存标识，不出现全文/下载入口 | 未执行 |
| no-external-read | AC06 | 浏览页面不请求外部文档、Git 全文或文件下载 | 未执行 |

上述场景独立列入 `scenarios`；未运行不能视为通过。AC04 需要具备 pending/已决定审批的受控 Run、审批角色与只读角色、浏览器刷新时序及编辑/焦点交互。现有旧 IT 只覆盖提交 approve，不能代替这些观察。本轮未新增或运行测试代码。

## 命令与风险

本轮只执行只读的 FD/回执/Worker 报告核对、旧场景数据读取，以及 AIW 交接命令；**没有测试命令、授权记录或原始覆盖率输出**。前轮原始测试结果位于 `target/failsafe-reports/`，仅作 revision 7 历史证据。本轮剩余风险集中在 AC04 的实际 DOM 刷新、证据与表单一致性、编辑保护和并发状态反馈；其余 AC01–AC03、AC05–AC06 也没有本 revision 的运行验收。
