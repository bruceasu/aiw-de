# FD-008 独立测试报告，第 1 轮

<!-- aiw-data: FD-008-test-report-r1.json -->

## 结论

本轮准备了 2 个黑盒 HTTP 用例，均未运行。36 个适用验收场景中通过 0 项，需求场景覆盖率 **0/36（0%）**；业务代码分支覆盖率未测量。建议 `blocked`，请 PM 明确决定是否接受缺少运行证据的风险。

## 场景与证据

场景由 FD revision 5 的 Acceptance/Verification、公开 Web Console 文档与 Worker 报告推导；Tester 未阅读实现源码。下列场景全部未覆盖，准备的用例不计入通过。

| ID | 可观察行为 | 状态 |
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

准备的 `tests/fd008/workspace-replacement.test.mjs` 包含 Run 表单读取和 READY 任务目录模式替换两个用例。它仅触及 S05、S09、S30、S32 的局部行为；即使运行成功，也不足以判定这些完整场景或其余场景通过。

## 命令与风险

- 已执行静态读取与流程命令：`Get-Content`（FD、模板、公开文档、Worker 报告、现有测试范例）、`rg`（文档与测试位置）、`aiw fd claim FD-008 FD-008-000005-implementation-ready --session fd008-tester-20261007-6de97a`、`git status --short`。未读取 FD-008 实现源码。
- **未运行**测试、覆盖率、服务启动、迁移、编译或网络请求；测试命令清单为空，授权记录为空，原始运行输出不存在。
- 曾向 Planner 提议 `node --test tests/fd008/workspace-replacement.test.mjs`，工作目录为本 FD 工作树，预计少于 10 秒。该命令要求预先设置隔离 loopback 服务、READY 任务与已登记目标；第二个用例会取消该任务并创建替代任务及审计记录。Planner 确认当前没有隔离 fixture，且 Worker 两次离线编译在 JVM 启动阶段因原生内存不足失败，现有 localhost Run 不可作为破坏性测试数据；因此没有授权或执行。
- SQLite、PostgreSQL 的 V20 迁移均未执行，既有数据读取也未验证。Worker 的 `git diff --check` 结果只属于静态补丁证据。

## 追踪信息

- FD：revision 5，SHA-256 `630f1c032f6406ac5cf00afc8b286bf8e97a084eb1195e404bb1ba957a4bc82f`。
- 实现事件：`FD-008-000005-implementation-ready`；Worker 报告：`docs/features/reports/FD-008-implementation-r1.md`。
- Tester session：`fd008-tester-20261007-6de97a`。
- 剩余风险：任务状态、ACK 与自然终态竞态、旧 lease 隔离、依赖重接、权限、审计和双数据库迁移全部缺少运行证据。
