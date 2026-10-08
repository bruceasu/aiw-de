# FD-007 实施报告，第 1 轮

<!-- aiw-data: FD-007-implementation-r1.json -->

## 结论

已实现任务指令不可变版本、attempt 版本绑定、停止确认与重派控制，以及 Run 详情页的任务修订和历史入口。静态 whitespace 检查通过。编译检查因本机没有 Maven 且仓库没有构建 wrapper 而未能启动；测试和双数据库迁移验证未运行。

## 实施内容

- 新建任务时记录初始指令版本；修订时要求预期任务版本和指令版本匹配，并保存操作者、原因、时间和前序版本。
- Worker claim 响应和 execution attempt 都记录具体指令版本；过期版本不能 start、完成或提交失败结果。
- RUNNING 任务修订时请求 Worker 协作停止。租约过期后，已请求停止且未确认的任务不会自动重派。Worker 确认停止后任务才正常回到 READY。
- ADMIN 可在 Worker 尚未确认停止时显式强制重派，但必须先发出停止请求、填写原因并确认可能产生重复外部副作用；相关操作写入审计表。该操作无法保证终止已在外部运行的进程或撤销既有副作用。
- Run 详情页提供任务指令修订、停止、ADMIN 风险确认强制重派、版本历史和完成任务的关联修正任务。修正任务保留原完成记录并依赖原任务。
- 为避免用户报告的任务详情页面崩溃，在 approval evidence 缺失时显示不可用提示，避免对 null 参数求值。

## 验证

- `git diff --check`：通过，没有 whitespace 错误。
- `mvn -q -DskipTests compile`：未能启动，PowerShell 找不到 `mvn`；仓库没有 Maven 或 Gradle wrapper。
- 单元/集成测试、Worker 协议测试、SQLite 与 PostgreSQL 迁移验证：未运行，等待独立 Tester 阶段及测试授权。

## 剩余风险

- 未完成编译和运行时验证，当前实现不能视为已验证。
- 停止协议是协作式的。ADMIN 强制重派会隔离旧 attempt 对控制面的提交权限，但旧 Worker 可能仍在执行外部副作用。
- 新迁移使用 SQLite/PostgreSQL 共用 SQL，但本次没有运行两种数据库的迁移检查。
