# FD-006 Worker 实施报告 r3

<!-- aiw-data: FD-006-implementation-r3.json -->

**Worker Session:** `fd006-worker-20261007-r3-f7612a`  
**Source event:** `FD-006-000015-changes-requested`  
**FD revision at claim:** 15

## 修复

响应 Reviewer r1 的 F-01：目录模式 claim 不再按 `repositoryRef` 分开加锁。`DirectWorkspaceConcurrencyBudget` 在同一个 claim 事务中取得全体目录任务共用的 admission 行锁，解析候选目标和现有 CLAIMED/RUNNING 目录任务的真实路径，并拒绝与任一活跃任务指向同一物理目录的 claim。`toRealPath` 解析符号链接或 junction 的路径别名；目标无法解析时失败关闭。不同目录的任务可在各自 claim 完成后同时运行。未修改数据库 schema；旧的按 ref 锁行可留存，新的共同锁行使用现有表。

FD 6.14 的静态证据已更新；先前 Worker r1/r2 的实现及独立 Tester r2 原始结果保持不变。

## 命令与证据

- 阅读 Reviewer r1 报告、ManagedWorkspaceService 的重复 rootPath/LOCAL_LINK 路径、WorkspaceRegistry 解析和 TaskClaimService 的事务/锁顺序。
- `D:\green\apache-maven-3.9.9\bin\mvn.cmd -o -q -DskipTests compile`，`JAVA_HOME=D:\green\jdk-21.0.8`，FD-006 工作树：退出码 0；仅 Java 主源码编译，无发布包。
- Worker 未运行并发行为测试；需独立 Tester 对新版本重新授权并验证。此前 4/4 测试不能证明此修复。

## 残余风险

统一 admission 锁会使目录任务的 claim 短暂串行，执行期不同目录仍可并行。每次 claim 需要解析现有活跃目录任务路径，路径失效时拒绝新 claim 以避免误并发。真实并发调度、UI/API、迁移和其他先前未覆盖场景仍缺少独立运行证据；PM 的低覆盖率例外须重新核对。
