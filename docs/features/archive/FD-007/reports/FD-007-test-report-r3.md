# FD-007 独立测试报告，第 3 轮

<!-- aiw-data: FD-007-test-report-r3.json -->

## 结论

本轮依据 FD revision 17 清点 22 个适用验收场景；没有运行行为测试、覆盖率、编译、构建、应用或数据库迁移命令，也没有创建测试代码。22/22 场景均为 `uncovered`，需求场景覆盖率为 0%（0/22），业务代码分支覆盖未测量；没有原始覆盖率数据。静态材料不构成运行证据。

PM 决策 `docs/features/reports/FD-007-verification-decision-r1.md` 明确豁免本 FD 生命周期内 S21/S22 的 SQLite/PostgreSQL 迁移执行检查，以接受未验证风险并继续静态审查。该豁免不是迁移成功证据，也不把 S21/S22 记为通过或不适用；两种数据库上的实际迁移仍未验证。Reviewer r2 的静态审查同样指出无法由静态 SQL 检查证明真实 migration runner、数据库配置和既有数据上的结果。

## 场景与证据

场景依据当前 FD 的 Acceptance/Verification，以及之前 Tester r2 对应的 22 个黑盒场景清单保留。未读取实现源码；Worker r3 仅报告同步了 PM 豁免及风险，没有提供新的行为运行证据。本轮没有准备或执行测试用例，因此每项均无测试用例或行为证据。

| ID | 可观察行为 | 状态 | 本轮证据 |
|---|---|---|---|
| S01 | 初始任务创建指令版本；后续不可变修订保留前序版本、作者、原因和时间 | uncovered | 未准备或执行用例；无行为证据 |
| S02 | `BLOCKED` / `READY` 任务使用当前 expected version 修订成功并创建新版本 | uncovered | 未准备或执行用例；无行为证据 |
| S03 | 过期 expected version 修订被拒绝且当前指令版本不被覆盖 | uncovered | 未准备或执行用例；无行为证据 |
| S04 | 未授权身份不能修订、停止或重派任务；控制操作要求填写原因 | uncovered | 未准备或执行用例；无行为证据 |
| S05 | 修订 `CLAIMED` 任务会先撤销旧 lease，再允许领取新版本 | uncovered | 未准备或执行用例；无行为证据 |
| S06 | `RUNNING` 任务收到停止请求后，在控制面接受有效停止确认前仍未停止且不能普通重派 | uncovered | 未准备或执行用例；无行为证据 |
| S07 | 首个匹配 task、worker、lease 的有效停止确认结束旧 attempt 并只执行一次普通重派 | uncovered | 未准备或执行用例；无行为证据 |
| S08 | 已撤销 lease 或过期指令版本不能 start、heartbeat、complete、提交结果或触发下游任务 | uncovered | 未准备或执行用例；无行为证据 |
| S09 | 停止确认前仅 ADMIN 可在确认重复副作用警告并填写原因后强制重派，且动作有审计记录 | uncovered | 未准备或执行用例；无行为证据 |
| S10 | 没有停止确认时 OPERATOR 不能强制重派 | uncovered | 未准备或执行用例；无行为证据 |
| S11 | 已完成任务的指令和结果保持不变，修正任务关联原任务 | uncovered | 未准备或执行用例；无行为证据 |
| S12 | 任务详情展示当前版本、活动 attempt 版本、worker、lease、停止确认和版本历史 | uncovered | 未准备或执行用例；无行为证据 |
| S13 | 空闲、活动、已停止和已完成任务视图及角色操作符合任务状态和权限 | uncovered | 未准备或执行用例；无行为证据 |
| S14 | 未修订任务保持既有 Worker claim/complete 协议行为 | uncovered | 未准备或执行用例；无行为证据 |
| S15 | 停止请求后，旧式/lambda executor 仅正常返回不会被认作 STOPPED，也不会触发普通安全重派 | uncovered | 未准备或执行用例；无行为证据 |
| S16 | 仅在收到停止请求且 stop-aware executor 确实停止后明确返回 `STOPPED`，Worker 才确认停止并允许普通重派 | uncovered | 未准备或执行用例；无行为证据 |
| S17 | 相同 task、worker、lease 的成功停止确认重试成功，且不重复重派、资源释放或确认审计 | uncovered | 未准备或执行用例；无行为证据 |
| S18 | 重复停止确认中的 task 不匹配时仍被拒绝 | uncovered | 未准备或执行用例；无行为证据 |
| S19 | 重复停止确认中的 worker 不匹配时仍被拒绝 | uncovered | 未准备或执行用例；无行为证据 |
| S20 | 重复停止确认中的 lease 不匹配时仍被拒绝 | uncovered | 未准备或执行用例；无行为证据 |
| S21 | 指令与控制状态迁移可在 SQLite 成功应用 | uncovered | PM 豁免本周期执行检查，但迁移未实际运行；无迁移证据，仍未验证 |
| S22 | 指令与控制状态迁移可在 PostgreSQL 成功应用 | uncovered | PM 豁免本周期执行检查，但迁移未实际运行；无迁移证据，仍未验证 |

## 命令与风险

- 本轮测试、覆盖率、编译、构建、应用和 SQLite/PostgreSQL 迁移命令：无。
- 未创建测试文件；无测试授权记录；无原始运行输出。
- 工作流元数据命令：`aiw fd show FD-007`；`aiw fd claim FD-007 FD-007-000017-implementation-ready --session fd007-tester-20261007-9c74e2`。
- 静态材料：当前 FD revision 17、Worker r3 报告、PM 验证豁免 r1、Reviewer r2 报告及 Tester r2 场景清单。
- PM 豁免仅允许流程继续，不表示测试或迁移通过，不构成发布/部署批准。仍无授权/UI、并发与状态转换、旧 attempt 隔离、停止协议、确认幂等、资源计数、Worker 兼容性和两种数据库实际迁移的运行证据。ADMIN 强制重派仍不能保证外部副作用终止。

## 追踪信息

- FD：`FD-007`，revision 17，SHA-256 `df4a7e78d65f81cd230704bc5cd5da9cdefef1eab48a9598f4a92f30e7179a7f`。
- 来源 implementation-ready 事件：`FD-007-000017-implementation-ready`。
- Worker 报告：`docs/features/reports/FD-007-implementation-r3.md`，Worker session `fd007-worker-20261007-r3-63b7ad`。
- Tester session：`fd007-tester-20261007-9c74e2`。
- PM 豁免：`docs/features/reports/FD-007-verification-decision-r1.md`。
- Reviewer r2：`docs/features/reviews/FD-007-review-r2.md`。
- 上轮场景清单：`docs/features/reports/FD-007-test-report-r2.md`（22 个场景均未运行）。
- 报告时间：2026-10-07 07:20 JST。
