# FD-021 R1 修复实施报告

<!-- aiw-data: FD-021-implementation-r2.json -->

- Worker：`codex-root-fd021-20261021`。
- 已领取来源：`FD-021-000005-changes-requested`。
- 原实现与范围：[首轮报告](FD-021-implementation.md)，本轮不改变已批准业务范围。
- Reviewer 结果历史：[R1](../reviews/FD-021-review-r1.md)，两项 blocking，未改写为通过。

## 修复

R1-1：`SecretaryPlanGate.check` 不再取 Plan 行锁，旧指令修改、forceRequeue 和完成推进
不会由此产生相反的新锁序。初始 confirm 使用专用 initialConfirmation 锁，与秘书关联
串行化；通用 Task 创建的锁使用 `WHERE state='DRAFT'`，不锁旧 CONFIRMED Plan 行。
关联在 Plan 锁内检查无既有任务。新增准入记录表，只有内部协调创建且属于当前 generation
的专业 Task 才能在 ACTIVE 状态领取；QUEUED 仅可领取协调任务。即使通用任务与关联竞争，
未获得受控准入记录也无法提前执行秘书工作。此方案不为旧 RUNNING 指令修改增加 Plan 锁。

R1-2：源完成报告/attempt 缺失时，专业完成入口直接保存 NEEDS_HUMAN。
协调消费再次显式检查源 task、completed input、report 和 attempt；缺失记录时保存人工
处理并返回空推进，使协调任务的真实终态、资源释放和 consumed 状态一起提交。
这些业务异常不再通过 orElseThrow 回滚，未新增专业动作重试。

## 证据与命令

实际读取 R1 双证据、根共享 receipt 和当前代码；`aiw fd claim` 领取准确 Worker 事件。
apply_patch 修改上述源码、未执行的 V8 增量表、FD 及本报告。

`$env:JAVA_HOME='C:/green/jdk-25.0.1+8'; & 'C:/green/apache-maven-3.9.9/bin/mvn.cmd' -o -DskipTests compile`
退出 0，240 个生产源码编译成功，耗时 5.111 秒；日志 [FD-021-compile-r2.log](FD-021-compile-r2.log)。
这只证明编译通过。首轮脚本语法证据保留，相关 JS/TS 本轮未变，没有重复运行检查。

未运行测试、并发复现、迁移、服务、Worker、模型、网络、浏览器、最终构建或完整 TypeScript
类型检查。没有降低已确认安全规则，没有重做专业动作。新增表尚未迁移到运行数据库。
第二轮独立实现评审仍为交付 Gate；本轮不自行标记通过、不合并或归档。
