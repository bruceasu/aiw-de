# FD-007 独立测试报告，第 1 轮

<!-- aiw-data: FD-007-test-report-r1.json -->

## 结论

本轮没有执行行为测试、数据库迁移或覆盖率命令。根据 FD-007 revision 7 的验收标准，列出 14 个彼此独立的外部可观察场景；本轮覆盖 0/14，所有场景均未覆盖。建议 `blocked`：权限边界、旧 attempt 隔离、停止确认与强制重派及数据库兼容性仍无测试证据，不能判断是否满足验收。

## 场景与证据

本报告仅依据 FD 的验收/验证条款、公开 Worker 协议和 Web Console 行为契约，以及 Worker 实现报告中的验证声明制定场景；未读取实现源码，也未把实现报告中的功能描述当作测试通过证据。

| ID | 可观察行为 | 本轮状态 | 用例/证据 |
|---|---|---|---|
| S01 | 新任务具有初始指令版本；修订保留前序版本及作者、原因、时间 | blocked | 未执行；无行为证据 |
| S02 | `BLOCKED`/`READY` 任务以当前 expected version 修订成功并生成新版本 | blocked | 未执行；无行为证据 |
| S03 | 使用过期 expected version 的修订被拒绝，当前版本不被覆盖 | blocked | 未执行；无行为证据 |
| S04 | 未授权身份不能修订、停止或重派任务；操作需提供原因 | blocked | 未执行；无行为证据 |
| S05 | `CLAIMED` 任务修订后旧 lease 被撤销，修订后的任务才能重新领取 | blocked | 未执行；无行为证据 |
| S06 | `RUNNING` 任务收到停止请求后，在 Worker 确认前仍显示未停止且不正常重派 | blocked | 未执行；无行为证据 |
| S07 | Worker 停止确认可重复提交且幂等；确认后任务可按规则回到可领取状态 | blocked | 未执行；无行为证据 |
| S08 | 撤销后的旧 lease/revision 不能 start、heartbeat、complete、提交结果或触发依赖任务 | blocked | 未执行；无行为证据 |
| S09 | 未确认停止时，只有 ADMIN 在确认重复副作用警示并填写原因后可强制重派，且动作留审计 | blocked | 未执行；无行为证据 |
| S10 | OPERATOR 无法绕过停止确认执行强制重派 | blocked | 未执行；无行为证据 |
| S11 | 完成任务的指令和结果保持不变；修正任务关联原完成任务 | blocked | 未执行；无行为证据 |
| S12 | 任务详情可见当前版本、活动 attempt 版本、Worker、lease、停止确认和版本历史 | blocked | 未执行；无行为证据 |
| S13 | 无任务、活动任务、已停止任务、已完成任务及不同角色的 UI 状态/操作呈现符合权限与状态要求 | blocked | 未执行；无行为证据 |
| S14 | 未修订任务保持既有 Worker claim/complete 语义；SQLite 与 PostgreSQL 均能应用迁移 | blocked | 未执行；无协议或迁移证据 |

## 命令与风险

本轮未提出或执行测试命令。仓库执行预算要求测试默认为零，且本次交接明确禁止测试、构建、覆盖率及数据库迁移命令；因此未尝试这些操作。没有测试授权记录、测试文件或原始运行输出。

实现报告提到 Maven 不可用且无构建 wrapper；这不构成测试结果。残余风险包括数据版本并发处理、权限绕过、旧 Worker 对外部副作用、迁移方言兼容和 UI 错误状态。Worker 停止为协作式协议，强制重派仍可能与正在运行的外部操作重叠。

## 追踪信息

- FD：revision 7，SHA-256 `412649fb6c01bf3a875bc346ca7a377766dc7e07306a8fdaa71c9375f66329e5`
- Tester session：`fd007-tester-20261007-b6e91d`
- 来源事件：`FD-007-000007-implementation-ready`
- 实现报告：`docs/features/reports/FD-007-implementation-r1.md`
