# FD-006 独立审查报告 r1

<!-- aiw-data: FD-006-review-r1.json -->

## 结论

**changes-requested。** AC11 的“同一目标目录串行”尚未实现：锁和活跃任务查询使用 `repositoryRef`，而不同引用可以指向同一实际目录。请 Worker 修复后重新提交；本轮不把 PM 对测试覆盖率的例外当作行为要求的豁免。

## 审查范围与身份

- FD revision 14；来源事件 `FD-006-000014-test-accepted`；Reviewer session `fd006-reviewer-20261007-8d4b7291`。
- 审查 diff：`0312f09d5a68c1afa0a3f1f7e3ec22351b86e50f..85fc3708713f5833ba4f6425f0a221156eca5561`，包括 Worker r1/r2、Tester r2 与 PM r2 决策。
- Worker r2 session `fd006-worker-20261007-r2-6da219`；Tester r2 session `fd006-tester-20261007-r2-da1c0478`；与 Reviewer 不同。

## 发现

### F-01：同一实际目录可绕过目录模式串行锁（阻断，AC11、Work Item 6.14）

`DirectWorkspaceConcurrencyBudget.java:20-30` 仅以 `task.repositoryRef()` 创建锁行并查询活跃任务。`ManagedWorkspaceService.create` 只限制 Workspace ID 唯一，V17 的 `workspace_registry.root_path` 也没有唯一约束；两个不同 ID 可登记相同 rootPath，得到不同的 `a/root`、`b/root`。此外，两个 LOCAL_LINK 可以指向同一源目录。两个目录模式 Run 分别持有不同锁行，活跃任务查询也互不相见，因此可以同时进入 CLAIMED/RUNNING 并写入同一物理目录。该行为与 AC11 的同一目标目录串行冲突。

请在登记/目标解析或调度互斥中建立基于实际目录的共同身份，并处理本地链接、重复根目录与路径别名；至少要拒绝可能重叠的登记或保证它们共享串行锁。修复后需重新评估目录模式的跨 Run 互斥证据。

## 已核查的其他路径与测试证据

- `removeChild` 和 Workspace `delete` 只删除登记行；隐含目录没有删除入口。页面确认文案与实际行为一致。根目录创建、Clone 的文件系统操作与数据库事务不原子，失败可能留下未登记目录，Worker 已如实报告。
- `addChild` 限制一级相对路径；Clone 在根内的应用临时目录进行，失败只清理该目录。Git revision 由目标仓库 `rev-parse` 解析，完整 SHA 也须在该仓库验证；不可解析的非空 revision 不转入目录模式。托管/YAML 同名不同路径的活动目标会拒绝解析。
- Root/Child 目标选择、Python/Go/TypeScript/Java assignment 路径使用和 UI 风格已静态追踪。未执行这些路径的独立行为验证；本报告不宣称其运行通过。
- Tester r2 授权精确绑定 `FD-006-000012-implementation-ready`、FD revision 12、digest `4bdec80bc23b463d175d189a2e6ff844f73b884e21aada26f469b7bbd78b23db`、Tester session 及 `node --test tests/fd006/direct-workspace.test.mjs`。已检查测试只操作自有临时目录。原始输出显示 4/4 通过；场景清单将完整覆盖算作 2/32（6.25%），Git Worker 的两个局部结果没有算入 Control Plane 场景。分支覆盖未测。PM r2 明确记录低于 70% 和未测分支的例外，并保留未验证风险。

## 命令与剩余风险

实际运行：`aiw fd claim FD-006 FD-006-000014-test-accepted --session fd006-reviewer-20261007-8d4b7291`；`git status --short`、`git log --oneline master..HEAD`、`git diff --stat master...HEAD`、`git diff master...HEAD -- <相关实现/UI 路径>`、`git rev-parse HEAD master`、`git merge-base master HEAD`；对 FD、实现、迁移、测试源码/原始输出、授权、决策、模板及回执的 PowerShell 静态读取和 `rg` 定位。未运行测试、编译/构建、数据库迁移、覆盖率、服务或网络调用。

除 F-01 外，UI/API、SQLite/PostgreSQL 迁移、Clone、其他 Worker adapter 和真实并发调度缺少独立运行证据。测试通过只支持 2 个完整场景，不覆盖文件安全及目录锁。外部文件系统操作与数据库事务无法原子提交。
