# FD-128 独立审查

- **FD:** FD-128
- **来源事件:** `FD-128-000004-review-requested`
- **Reviewer session:** `fd128-review-20261115-7c91e4`
- **审查基准:** HEAD `c954e431`；审查 `git diff HEAD`。本次事件后重新核对差异：实现文件未变，只有 `docs/features/FD-128/index.md` 增加 Reviews 导航。
- **结论:** PASS（静态审查）

## Findings

无阻塞问题。

## 审查证据

- 迁移目录仅有 `V1__initial_schema.sql`；`src/main/java/db/migration` 中不再有版本化 Java migration。
- 基线定义 150 个唯一表。独立扫描确认每个表都在主 Java 代码中以 `FROM`、`JOIN`、`INTO`、`UPDATE` 或 `DELETE FROM` 表操作被引用；142 个外键目标均已定义且先于引用表创建。已确认的无引用候选表未进入基线。
- `work_task`、`execution_attempt`、`flexible_workflow_plan` 的最终状态值包含在初始约束中；V20 提供的 `workflow_factory_plan`、`workflow_factory_task` 和索引也已直接建入基线。
- 初始化数据仅包含四个受运行时代码读取/锁定的单例记录：Fleet 并发预算、工作流推荐设置、Authority 锁和初始元数据。
- 基线没有 `ALTER TABLE`、`DROP TABLE` 或历史 `UPDATE` / `DELETE` 回填；旧数据库不会由新基线自动重置或迁移。中英文快速开始、运维、架构和 Schema 文档均说明必须使用新空库。
- 父 Worker 报告的离线 Java compile-only 通过：`C:\green\apache-maven-3.9.4\bin\mvn.cmd -o '-Dmaven.repo.local=D:\.m2\repository' -DskipTests compile`，365 个主源码。

## 执行与未执行的命令

- 本审查执行了只读源码/文档检查和 Python 标准库静态表引用扫描（由 PowerShell 输入 `python -X utf8 -`）；本次交接重新执行 `aiw fd show FD-128`、`aiw fd claim FD-128 FD-128-000004-review-requested --session fd128-review-20261115-7c91e4`、`git diff --name-status HEAD`、`git diff HEAD -- docs/features/FD-128/index.md`、`git rev-parse --short HEAD` 和 `aiw fd emit --help`。
- 未运行测试、数据库迁移、服务、网络或模型命令。Java compile-only 结果来自父 Worker 的本轮实际输出。

## 剩余风险

没有在 SQLite 或 PostgreSQL 空库实际执行 SQL，因此 SQL 运行时兼容性仍未验证；这是本次明确未授权的检查。静态审查不等于数据库迁移通过。
