# FD-008 Worker 实施报告 r2

<!-- aiw-data: FD-008-implementation-r2.json -->

- 来源事件：`FD-008-000009-changes-requested`；Worker session：`fd008-worker-r2-20261007-9b4d27`。
- Reviewer r1：`docs/features/reviews/FD-008-review-r1.md`，F-01、F-02 均已作静态修复，提交 `cc513c9`。
- F-01：V20 替换记录保存受理时 `source_instruction_revision`。旧 Worker ACK 后，在释放租约的同一事务中比较当前指令版本；若期间修订，记录 CONFLICT，不创建替代任务。没有用普通 task version 做相等比较，因为 Worker 心跳也会增加该版本。
- F-02：V20 记录 `stop_acknowledged_at_epoch_ms`；替换历史查询暴露是否需要 Worker 停止及确认时间。页面分别显示待确认、已确认、未收到确认、无活动租约无需确认。操作文档同步更新。

## 检查与风险

- 本轮 `git diff --check` 通过，只是静态补丁检查。
- 未重复运行 compile-only：r1 两次已在 JVM 启动阶段因原生内存不足失败，用户确认当前内存紧张；没有源码编译通过的证据。
- 用户已豁免本轮测试；独立 Tester r1 的 36 个场景 0 个运行，分支覆盖未测量。修复后尚无新的行为测试。SQLite/PostgreSQL 的 V20 迁移仍未执行。
- 剩余风险：新增迁移列、ACK/指令竞争和模板渲染都只有静态证据；其他 Worker、权限和旧 lease fencing 也缺少运行证据。Reviewer 应继续独立检查实际 diff，不得把本报告当作验证通过。
