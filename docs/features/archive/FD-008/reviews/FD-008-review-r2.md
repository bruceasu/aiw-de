# FD-008 独立审查报告 r2

<!-- aiw-data: FD-008-review-r2.json -->

## 结论

**verification-passed（仅限独立静态审查）。** 本轮未发现新的、可由代码静态证实且需要退回 Worker 的缺陷。Reviewer r1 的 F-01、F-02 均有对应修复。用户豁免测试，PM 接受 0/38 场景覆盖例外；本结论不表示任何行为测试、编译、页面渲染或数据库迁移通过。

## 修复复核

- **F-01：已静态修复。** 替换请求在 V20 记录 `source_instruction_revision`；`afterStopAcknowledged` 在任务锁及同一 ACK 事务内，于租约释放后比较当前指令修订号。RUNNING 任务若在 PENDING_STOP 期间修订指令，当前修订号增加，请求转 CONFLICT，`complete` 不执行，也不创建替代任务。普通 Worker 心跳在 `JdbcWorkTaskRepository.heartbeatLease` 增加 task version，但不改变 instruction revision，因此正常心跳不会误判为指令修改。原有 `source_version` 仍保留为受理时审计字段。
- **F-02：已静态修复。** 活动租约请求在替换表保存 lease token，ACK 时在同一事务写入 `stop_acknowledged_at_epoch_ms`；`forRun` 暴露是否需要 Worker 停止及确认时间。Run 模板分别展示待确认、已确认、未收到确认和无活动租约无需确认，记录保留源/替代任务链接与冲突原因。V20 新增的 `INTEGER NOT NULL`、`BIGINT` 与原有部分唯一索引、外键、CHECK 均属 SQLite/PostgreSQL 可接受的 SQL 结构；这只是静态方言判断，未验证迁移执行。

## 审查身份与证据

- FD revision 13，digest `9e1026cd3a149519b29a221d93bfbd4ce7d13b3abb231bf46dc1e1f12cf5c27c`；来源事件 `FD-008-000013-review-requested`；Reviewer session `fd008-reviewer-r2-20261007-1a7c43`。
- 审查基线 `master` (`9869374`)，提交 `7890b65`；重点修复提交 `cc513c9`，并查看从 r1 审查提交 `c7f95f9` 到当前 HEAD 的变更。阅读 FD、Worker r2、Tester r2、PM r2、上一轮报告、替换服务、任务和 attempt 仓储、Worker ACK 路径、V20 迁移和 Run 模板。
- Tester r2 清点 38 个适用场景：执行 0、通过 0、失败 0、未执行 38；需求覆盖率 0/38（0%），分支覆盖未测量，无授权记录或原始运行输出。PM 已明确接受本轮覆盖例外，未豁免行为要求。Worker r1 的两次 compile-only 都在 JVM 启动时因内存不足失败；r2 未重试。SQLite/PostgreSQL 迁移均未运行。

实际运行的命令：`Get-Content`、`rg`、`git log -6 --oneline`、`git status --short`、`git show cc513c9 -- <修复路径>`、`git diff --stat c7f95f9..HEAD`、`git rev-parse HEAD`、`git rev-parse master`、`aiw fd claim FD-008 FD-008-000013-review-requested --session fd008-reviewer-r2-20261007-1a7c43`。未运行测试、编译、构建、迁移、覆盖率、服务启动、格式化或网络命令。

剩余风险：38 个行为场景均缺少运行证据；Java 源码尚未成功编译；ACK 与指令变更竞争、页面渲染、Worker 清理、旧 lease fencing、权限、SQLite/PostgreSQL V20 迁移及既有数据读取仍需在内存恢复后单独验证。本通过仅允许 FD 工作流继续，不代表可发布。
