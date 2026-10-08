# FD-004 PM 测试报告决定（r8 v1）

<!-- aiw-data: FD-004-test-decision-r8-v1.json -->

## 决定

**接受独立 Tester 报告，交独立 Reviewer 审查实现和证据；不宣称 FD-004 行为已整体通过。** Tester 建议阻断整体通过，PM 认可其覆盖结论和风险。本次接受仅推进静态独立审查；Reviewer 如不能依据实际代码和证据确认验收，可要求修复。

Tester 报告有 29 个可观察场景：实际执行 1 个既有审批提交用例，通过 1、失败 0、未执行 28。需求场景覆盖率 **1/29（3.45%）**；业务代码分支覆盖率 **未测量**。原始 Failsafe 结果位于工作树 `target/failsafe-reports/me.asu.aidev.webconsole.R9WebConsoleApprovalIT.txt` 与同名 XML，记录 1 tests、0 failures、0 errors、0 skipped。首次命令因 PowerShell 参数解析失败而未进入测试；修正命令按版本绑定授权运行成功。

## 覆盖例外与风险

PM 对低于 70% 的需求场景覆盖和未测量的分支覆盖作**本轮检查例外**。依据：仓库默认不授权大范围测试；已授权并运行的仅是代码检查后确认离线、临时目录副作用的单个旧用例；本 FD 新增的是 Web Console 私有只读投影，决定服务及权限/CSRF 权威未改；Java 21 离线编译成功。该依据足以将报告交给独立 Reviewer 做源码和契约审查，但不足以认定 AC01–AC06 已验证。

残余风险显著：旧用例使用非 Run 的 WORK_ITEM 数据，仅证明审批页面可返回和批准动作，**没有运行验证正向 Run 证据匹配**。多 attempt、repair 后历史 revision、缺失矩阵、最近窗口边界、竞争刷新、窄窗口和外部读取边界均未执行。Reviewer 必须逐项辨别静态可证事实与缺少运行证据的行为；若发现错误关联或页面契约缺陷，应发出 `changes-requested`。本决定不豁免任何验收行为，也不授权额外测试、浏览器、网络或最终构建。

## 真实绑定

- 消费事件：`FD-004-000008-test-report-ready`；FD revision 8；digest `1266f9fb0417f127c4ecbb0b1ac01c6b46a81ed0801a4420356bf6c5c0607da1`。
- [独立 Tester 报告](FD-004-test-report-r8.md)；Tester Session `fd004-tester-20261005-ac4256a8`。
- PM：`fd004-pm-20261005-219a992b`；决定时间：2026-10-05T13:59:30+00:00。
