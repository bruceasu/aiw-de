# FD-004 独立测试报告（revision 7）

<!-- aiw-data: FD-004-test-report-r8.json -->

## 结论

Tester Session：`fd004-tester-20261005-ac4256a8`；来源事件：`FD-004-000007-implementation-ready`；FD revision 7；digest：`b0a17fd4ee60f1d05ac3933e1a710a85d3f3ce31857ee53e307758dcf0001edb`。

从 FD 验收与公开 Web Console 指南拆出 29 个可观察场景。实际执行既有 R9 审批 HTTP 集成用例 1 个，通过 1 个，失败 0 个；该用例只证明在测试数据中 pending 审批可通过现有页面批准，并保存决定归属和备注。其余 28 个场景未执行。需求场景覆盖率 **1/29（3.45%）**；业务代码分支覆盖率 **未测量**。本次结果不足以证明 FD-004 的精确证据关联、缺失矩阵、历史范围、并发刷新和浏览器可用性。

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
| approve-basic | AC03 | pending 审批可从页面批准，并保存 APPROVED、决定人和备注 | **通过：既有 R9WebConsoleApprovalIT** |
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

这些未执行项只是已准备的黑盒验收场景，不视为通过。本轮未新增测试代码；使用已有测试 `src/test/java/me/asu/aidev/webconsole/R9WebConsoleApprovalIT.java`。`publicationRef` 的无外部读取行为须结合受控网络/文件访问观测才能证实，不能由页面文字推断。

## 命令与风险

1. 授权 `docs/features/reports/FD-004-test-authorization-r7.md` 的首次 PowerShell 命令执行失败：Maven 报 `Unknown lifecycle phase ".test=R9WebConsoleApprovalIT"`，退出码 1，未编译或运行测试。失败原因为未加引号的 `-Dit.test=...` 参数传递。
2. 按修正版授权 `docs/features/reports/FD-004-test-authorization-r7-v2.md` 执行：`$env:JAVA_HOME='C:\green\jdk-21.0.8'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"; C:/green/apache-maven-3.9.9/bin/mvn.cmd -o '-Dit.test=R9WebConsoleApprovalIT' test-compile failsafe:integration-test failsafe:verify`。退出码 0；Failsafe 记录 **1 tests, 0 failures, 0 errors, 0 skipped**，`BUILD SUCCESS`。原始测试结果：`target/failsafe-reports/me.asu.aidev.webconsole.R9WebConsoleApprovalIT.txt` 和 `target/failsafe-reports/TEST-me.asu.aidev.webconsole.R9WebConsoleApprovalIT.xml`（工作树本地生成文件）。

测试以 JDK 21、离线 Maven、随机 loopback 端口和系统临时 SQLite 运行。没有浏览器、真实权限角色矩阵、多版本/多 attempt 样本、并发决定或外部访问监测。测试未测业务分支覆盖率。上述未覆盖行为是后续验收的剩余风险；本报告不作整体通过结论。
