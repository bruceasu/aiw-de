# FD-006 独立审查报告 r2

<!-- aiw-data: FD-006-review-r2.json -->

## 结论

**verification-passed（静态审查范围）。** Reviewer r1 的 F-01 已由共同 admission 锁和活跃目录任务的真实路径比较修复。AC11 的并发行为测试没有运行到业务断言：本轮获准集成命令在 Spring 启动前因 Maven JVM 原生内存不足退出。此结果仍是未验证，不能写成测试通过。

## 身份与范围

- FD-006 revision 18，digest `20726077ae34ba037c81a4c101d82e9b7a7e7bf5ed0ee37a2524c2d20d0edf22`；来源事件 `FD-006-000018-test-accepted`。
- Reviewer session `fd006-reviewer-20261007-r2-9be8407c`，与 Worker r3 `fd006-worker-20261007-r3-f7612a`、Tester r3 `fd006-tester-20261007-r3-805960c9` 不同。
- 完整实现基线为 `0312f09d5a68c1afa0a3f1f7e3ec22351b86e50f`；本轮针对 Reviewer r1 后增量 `ff090b7..a73c74cbc38ce1bcced5680e3ad44d01cdab8a47`，同时参照 r1 对完整 FD 的路径、删除文件保护、revision、UI 和证据审查。

## F-01 修复复核

`DirectWorkspaceConcurrencyBudget.tryReserve` 现在先在 claim 事务中插入或取得唯一 `__direct_workspace_claim__` 行，再做 no-op UPDATE 持有行锁。`TaskClaimService.claimNext` 标有 `@Transactional`，其调用顺序为预算准入、Worker 容量预留、任务 claim 和 attempt 插入；因此两个目录任务的活跃路径查询与后续 CLAIMED 状态写入处于同一事务的串行准入区间。各任务不再凭不同 repositoryRef 获得独立准入锁。

比较对象是候选目标与所有 `base_revision IS NULL`、状态为 `CLAIMED` 或 `RUNNING` 的任务目标。`WorkspaceRegistry.repositoryPath` 解析托管或 YAML 目标，`Path.toRealPath()` 消除已存在路径的 symlink/junction 别名；重复 rootPath 和不同引用的本地链接因真实路径相同而被拒绝。候选或活跃路径无法解析时抛异常并阻止新 claim，属于失败关闭。不同真实路径通过比较后可以分别运行；公共锁只限制 claim 的短暂准入阶段。

静态实现满足 F-01 和 AC11 的代码路径。目录被外部进程在准入之后重定向、真实并发争用和数据库方言行为仍缺运行证据；本轮没有将它们计为验证通过。

## Tester 与 PM 决策核对

- Tester r3 的两份授权均绑定 `FD-006-000016-implementation-ready`、revision 16、digest `fdc97a28f228774cd652e4922886dda98698b856f00184b0ac72df956cb0670c` 与 Tester session。r3 的 `node --test tests/fd006/direct-workspace.test.mjs` 是 Planner 低风险授权；r4 的 `node --test tests/fd006/claim-alias.test.mjs` 记录了对话授权 `approved:conversation:call_7fc9a5953d29450a8f37db403a1ff6dc`。测试源码限制自有临时目录，集成夹具使用本机 SQLite、loopback 和隔离配置。
- 原始 Worker 测试输出为 4/4 通过。集成测试原始输出为 0/1，错误在启动阶段：Maven JVM 无法分配 94736 bytes 原生内存，HTTP claim 断言未执行。这是环境阻断，不是 AC11 通过，也不是业务断言失败。
- Tester 将 32 个独立行为场景拆列，完整覆盖仅 AC09 两项，2/32（6.25%）；AC11 标环境阻断，分支覆盖未测。PM r3 明确记录低于 70%、未测分支与 AC11 环境阻断的例外，并要求静态复核。本报告没有放宽 AC11 的行为要求。

## 命令、跳过项与风险

实际运行：`aiw fd claim FD-006 FD-006-000018-test-accepted --session fd006-reviewer-20261007-r2-9be8407c`；`git status --short`、`git log --oneline ff090b7..HEAD`、`git diff ff090b7..HEAD -- <DirectWorkspaceConcurrencyBudget 与 FD>`、`git rev-parse HEAD`；PowerShell `Get-Content` 和 `rg` 静态阅读 claim 事务、目标解析、Tester 测试源码、授权、原始输出、报告与 PM 决策。

Reviewer 未运行新的测试、编译、构建、覆盖率、SQLite/PostgreSQL 迁移、服务或网络命令。Worker r3 报告 Java 离线 compile-only 成功；Reviewer 未重复执行。实际并发、UI/API、Clone、注册文件保护、数据库迁移及其他 Worker adapter 仍无独立运行证据。文件系统与数据库登记不原子，失败可能保留未登记目录。集成命令产生的被忽略 JVM 崩溃日志据 Tester 报告仍待工作树清理 Gate 处理。
