# FD-008 独立测试报告，第 2 轮

<!-- aiw-data: FD-008-test-report-r2.json -->

## 结论

用户明确要求“现在内存紧张，就豁免测试”。本轮未运行测试、迁移、服务、编译、覆盖率或网络请求。FD revision 10 有 **38 个适用黑盒场景，0 个通过（0/38，0%）**；业务代码分支覆盖率未测量。建议 `blocked`，PM 可按已记录的用户豁免单独决定是否接受覆盖例外，不能把未运行场景记为通过。

## 场景与证据

沿用 Tester r1 的 S01–S36，并根据 Reviewer r1 的 F-01、F-02 增加 S37、S38。场景来自 FD、公开 Web Console 契约、Worker r2 报告与 Reviewer r1 意见；未阅读实现源码。Worker r2 声称的修复只作为待验证内容。

| ID | 可观察行为 | 本轮状态 |
|---|---|---|
| S01 | 源任务 repositoryRef 与 baseRevision 保持原值，旧任务继续可追溯 | 未覆盖 |
| S02 | 源任务的 attempt、Worker、lease 与结果证据保持原值 | 未覆盖 |
| S03 | 源任务的 instruction revision 历史保持原值 | 未覆盖 |
| S04 | BLOCKED 且无活动 lease 的任务立即取消并创建替代任务 | 未覆盖 |
| S05 | READY 且无活动 lease 的任务立即取消并创建替代任务 | 未覆盖 |
| S06 | FAILED 且无活动 lease 的任务可创建替代任务 | 未覆盖 |
| S07 | CANCELLED 且无活动 lease 的任务可创建替代任务 | 未覆盖 |
| S08 | COMPLETED 任务拒绝 Workspace 替换并保留 linked correction 入口 | 未覆盖 |
| S09 | 目标必须是已登记且启用的 Workspace repository | 未覆盖 |
| S10 | 无效的显式 Git revision 被拒绝且不回退到目录模式 | 未覆盖 |
| S11 | Git revision 留空时在受理时解析并固定目标 HEAD commit | 未覆盖 |
| S12 | 目录模式替代任务的 baseRevision 为空 | 未覆盖 |
| S13 | 替代任务复制类型、subject、指令、skill/profile 与 priority，并有自己的初始指令版本 | 未覆盖 |
| S14 | 替代任务同步 repo capability 并保留非 repository capability | 未覆盖 |
| S15 | 替代任务复制源任务的前置依赖 | 未覆盖 |
| S16 | 未领取的下游任务依赖改接到替代任务 | 未覆盖 |
| S17 | 已推进的下游任务导致整体拒绝且依赖图不发生部分写入 | 未覆盖 |
| S18 | 过期 expectedVersion 被拒绝且不修改任务图 | 未覆盖 |
| S19 | 旧 lease 或不匹配的租约身份不能发起有效替换或停止确认 | 未覆盖 |
| S20 | 重复提交只得到一个替代任务和一个有效关系 | 未覆盖 |
| S21 | CLAIMED 任务在停止确认前只产生待处理请求且替代任务不可领取 | 未覆盖 |
| S22 | RUNNING 任务在停止确认前保持未停止且替代任务不可领取 | 未覆盖 |
| S23 | 正确 Worker/lease ACK 后同一事务终结旧尝试、取消源任务、创建替代任务并重接依赖 | 未覆盖 |
| S24 | 错误 task、Worker 或 lease 的 ACK 被拒绝 | 未覆盖 |
| S25 | 相同身份重复 ACK 不重复创建、释放资源或更改依赖 | 未覆盖 |
| S26 | ACK 前自然完成胜出时请求变为冲突且没有替代任务 | 未覆盖 |
| S27 | ACK 前自然失败胜出时请求变为冲突且没有替代任务 | 未覆盖 |
| S28 | 租约过期、回收、暂停或取消胜出时请求变为冲突且不自动重试 | 未覆盖 |
| S29 | 已取消来源的旧 attempt/lease 不能发布结果、完成任务或触发下游 | 未覆盖 |
| S30 | 成功和待处理替换记录操作者、原因、时间及前后 Workspace/任务关联 | 未覆盖 |
| S31 | 被拒绝的替换请求记录操作者、原因、时间和拒绝原因 | 未覆盖 |
| S32 | Run 页面显示待停止、完成、冲突状态、停止确认与新旧任务链接 | 未覆盖 |
| S33 | 只有 OPERATOR/ADMIN 可提交；其他角色及缺少原因的请求被拒绝 | 未覆盖 |
| S34 | V20 增量迁移在 SQLite 成功应用 | 未覆盖 |
| S35 | V20 增量迁移在 PostgreSQL 成功应用 | 未覆盖 |
| S36 | 两个方言迁移后既有任务与 attempt 数据仍可读取 | 未覆盖 |
| S37 | PENDING_STOP 期间源任务指令修订后，原 Worker ACK 将请求置为 CONFLICT，不创建替代任务 | 未覆盖 |
| S38 | Run 页面能区分待停止、已 ACK、未收到 ACK 和无活动租约无需 ACK，并保留确认时间证据 | 未覆盖 |

现有 `tests/fd008/workspace-replacement.test.mjs` 保留 2 个准备好的黑盒 HTTP 用例，但本轮不运行，也未增添覆盖 S37/S38 的自动用例。未运行用例和静态修复均不计入通过数。

## 命令与风险

- 本 Tester 执行：`Get-Content` 读取 FD、Tester r1、Worker r2、Reviewer r1 与事件；`aiw fd claim FD-008 FD-008-000010-implementation-ready --session fd008-tester-r2-20261007-2bb58e`。
- **未执行**任何测试、迁移、服务、编译、覆盖率或网络命令。测试授权记录、运行原始输出和覆盖率原始证据均不存在。
- Worker r2 报告 F-01 增加受理时 instruction revision，并在 ACK 事务中检测变更；F-02 增加 ACK 时间证据和页面状态区分。这两项仍缺少行为测试与页面渲染证据。
- Worker r1 两次离线 compile-only 均在 JVM 启动阶段因原生内存不足失败，本轮 Worker 未重试；源码没有编译通过证据。SQLite/PostgreSQL 的 V20 迁移以及迁移后旧数据读取均未执行。

## 追踪信息

- FD：revision 10，SHA-256 `28091baa45b02f30b1ed5ce454be199e062508d4c1f514b2ade400f9f6f147bd`。
- 实现来源：`FD-008-000010-implementation-ready`；Worker r2：`docs/features/reports/FD-008-implementation-r2.md`。
- 参考：`docs/features/reports/FD-008-test-report-r1.md`、`docs/features/reviews/FD-008-review-r1.md`。
- Tester session：`fd008-tester-r2-20261007-2bb58e`。
- 剩余风险：ACK 与指令变更的竞争、页面 ACK 证据、旧 lease fencing、依赖图、权限、审计和双数据库迁移仍无运行验证。
