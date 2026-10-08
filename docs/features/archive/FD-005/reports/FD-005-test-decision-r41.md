# FD-005 PM 测试决策 r41

<!-- aiw-data: FD-005-test-decision-r41.json -->

## 决策

**接受现有测试证据进入独立 Reviewer 阶段，并批准覆盖率例外。** 此决定依据用户于 2026-10-06 的明确选择：不追加测试，保留 API-16 验收要求，将未验证风险交 Reviewer。

独立测试报告 [r39](FD-005-test-report-r39.md) 记录累计 API Worker 场景覆盖 23/33（69.70%），低于 70%；业务代码分支覆盖率未测量。接受这两项作为进入 Reviewer 阶段的例外。r39 唯一授权命令 exit 1，API-16 用例到达断言，但两个保留角色子进程在模块或语法加载阶段退出。该测试未证明 API-16 行为通过；此执行失败和未验证状态均保留，不称为通过。

Acceptance 1 中“保留但不可执行角色继续拒绝注册”的要求保持不变。Reviewer 需检查相关实现和现有原始证据，并明确 API-16 尚未通过运行证据验证。真实服务、远端 Git、平台 G2、非 API Worker、跨运行时行为和完整业务分支覆盖仍未验证。本决策只批准进入审查，不代表 FD 完成。

- Tester 报告：FD-005-test-report-r39.md；来源事件：FD-005-000040-test-report-ready。
- 当前实现报告：FD-005-implementation-r42.md；来源事件：FD-005-000042-implementation-ready。
- 决策时 FD 修订：42；SHA-256：$digest。
- PM：host-pm-fd005-20261006；决策时间：2026-10-05T15:36:00Z。
- 原始证据：	ests/fd005/evidence-r39/diagnostic.json、	ests/fd005/evidence-r39/execution.json。

## 剩余风险

API-16 的生产入口拒绝行为仍未验证；另 9/33 个 API Worker 场景未完整覆盖，业务代码分支未测量。由于 r42 Tester 交接已领取且无结果，本决策不推断该会话是否执行了任何命令；其结果仍待审查时通过可用记录核对。

状态迁移记录：CLI 最初拒绝从 Pending Test 发出 test-accepted。用户随后明确要求 PM 直接接受该决定；本次按该授权记录 `FD-005-000043-test-accepted` 并路由至 Reviewer。r42 Tester 交接的结果仍未知，本决策不推断其执行结果，也未改写其原始事件。

