# FD-021 R2 修复实施报告

<!-- aiw-data: FD-021-implementation-r3.json -->

- Worker：`codex-root-fd021-20261021`。
- 已领取来源：`FD-021-000007-changes-requested`。
- 本轮仅修复 [R2](../reviews/FD-021-review-r2.md) 的 R2-1 及同一锁/清理边界；业务范围不变。
- R1/R2 失败历史保留，未改写通过；后续为本实现周期第三次独立评审。

## 改动

托管 Plan 的原收紧/终止操作现在先取得同一 secretary_lock，再获取任何专业任务或 Plan 写锁：

- WorkflowPlanService.control（stop/complete）、withdraw、clearStaleControl。
- WorkflowCancellationService.cancel，在原 tasks.lock 和 Git 清理前串行化；秘书页面本身仍不调用 Git 清理。
- WorkflowQuestionService.answer，在锁任务及 Plan CAS 前串行化。
- WorkflowPlanArchiveService 的归档/删除排期/撤销及 JdbcWorkflowPlanArchiveRepository.purge。

TaskExecutionService.requestStop/acknowledgeStop 在 tasks.lock 前调用只串行化的 serializeTask。
此 hook 不验证当前执行授权，因此取消后的 CLAIMED/RUNNING 租约仍可停止确认和释放资源。
afterStopAcknowledged 的 Plan 控制应用在已持秘书锁的同一事务中继续，不再由 task 锁之后
首次请求秘书锁。complete/fail/heartbeat 已有 execution policy 在任务写入前取得该锁。
被 gate 拒绝的指令改写/forceRequeue 仍在 task 锁前拒绝；旧未托管 Plan 不新增秘书写锁。

核对实际 DependencyResolver.refreshAfterCreation：协调持有的 Plan→source task 顺序，与
旧控制的 source task→Plan 顺序现在由秘书锁串行化，不存在两条托管路径并行持锁。

同时核对 purge 的新依赖边：秘书协调任务不属于 flexible-plan，原 purge 只删除专业任务自身
的依赖边，不能删除秘书指向它的边。新增清理仅删除“本秘书协调请求→本待清理 Plan 专业任务”
的边，保留协调任务、报告、请求和秘书工作，其他外部依赖与原安全检查不变。归档 purge
在秘书全局锁内，防止清理与协调消费竞争；Plan 已清理时协调仍按既有修复转人工。

## 实际证据

静态读取 R2 报告、control/withdraw/stop acknowledgement、DependencyResolver、QuestionService
及 archive/purge 真实 SQL 与外键，按上述入口逐项追踪。未声称运行并发复现。

离线命令：`$env:JAVA_HOME='C:/green/jdk-25.0.1+8'; & 'C:/green/apache-maven-3.9.9/bin/mvn.cmd' -o -DskipTests compile`
退出 0，240 个生产 Java 源文件通过，耗时 5.119 秒，见 [compile-r3 日志](FD-021-compile-r3.log)。
相关 JS/TS 本轮未改，未重复语法检查。

未执行测试、数据库迁移、清理、服务、Worker、模型、浏览器、网络、最终构建或完整类型检查。
实际 PostgreSQL/SQLite 并发和归档清理仍未运行验证，保留该限制。独立 R3 结论前不合并、不归档。
