# 旧私人数据迁移规划工具

FD-024 的实际数据迁移、导出、导入、切换、清理和回退执行均保持禁用。工具没有 `--force` 或启用写入的开关。旧平台秘书不再运行；V8/V9 源库和归属不明记录保持封存。

工具仅从用户明确选择的**离线 SQLite 备份快照**读取已知 V8/V9 列。它不猜测源库、owner、当前登录人或文件路径，不连接运行中的服务。存在 WAL/SHM/journal 文件、快照变化、未知列布局、混合权限或归属冲突时拒绝或封存；不使用 immutable 模式忽略活跃 WAL。PostgreSQL 直接读取不在此工具范围：先由未来获准的环境准备流程提供经核验的离线快照，本次不执行转换或下载驱动。

所有 CLI 输出仅含元数据、计数、摘要和阻断清单，不含私人正文、凭证或其他 owner 的行。平台身份、预算、执行许可等不进入私人候选；混合 request/audit/history 快照保持源库封存，不能靠文本猜测拆分。

## 输入

owner-map 必须逐个明确源 actor、目标 owner、目标 Worker 和外部归属证据引用。证据引用是操作者声明，工具不把它当成平台认证；真正迁移仍需单独核验。没有映射的记录默认封存，不自动归给当前人。

```json
{
  "schema": "aiw.secretary.owner-map.v1",
  "mappings": [
    {"sourceActor": "web:EXPLICIT-OLD-USER", "ownerId": "O-EXPLICIT-ID", "workerId": "my-secretary", "evidenceRef": "human-confirmed-inventory-reference"}
  ]
}
```

## 只读命令

从仓库根目录执行；下面是供未来操作者使用的示例，本次实现过程没有读取真实数据库：

```powershell
python -m scripts.secretary_migration preview --source D:/offline/source.sqlite --owner-map D:/offline/owners.json --owner-id O-EXPLICIT-ID --batch-id 80000000-0000-4000-8000-000000000024
python -m scripts.secretary_migration export-plan --source D:/offline/source.sqlite --owner-map D:/offline/owners.json --owner-id O-EXPLICIT-ID --batch-id 80000000-0000-4000-8000-000000000024
python -m scripts.secretary_migration import-plan --package D:/offline/candidate.json --owner-id O-EXPLICIT-ID --worker-id my-secretary
```

`make_export_candidate` 只在调用者内存中形成候选包；CLI 只输出其 manifest，不写出包。候选记录标记 `ARCHIVE_ONLY`，绝不激活历史任务/策略。`validate_package` 严格检查字段、摘要、行数、唯一 ID 和关联。`import-plan` 可选择 `--destination` 指定离线私库快照，核对 `personal_identity` 和 `personal_import_batch`：同批次同摘要为 no-change，不同摘要或目标身份冲突拒绝，不创建目的库或写入账本。普通私人备份与迁移候选不是同一格式。

`export`、`import`、`switch`、`cleanup`、`rollback` 入口明确拒绝执行。规划结果不能证明实时生产者已停写、所有专业任务已结算、外部效果已完成或备份可恢复；实际执行还需源库/备份、可信 owner 映射、在途逐项处置、切换窗口和回退决定。

## English

This is an offline SQLite planning tool. It never writes the source or destination. Use an explicit verified snapshot and an explicit owner map. Unknown ownership and mixed snapshots stay sealed. The tool does not infer an owner from the current login or path.

Preview and export-plan print metadata only. Import-plan validates an existing candidate, checks its owner, Worker, hashes, counts and references, and can compare an offline private import ledger. A repeated batch with the same hash is a no-change plan. A conflicting batch is rejected.

Actual export, import, cutover, cleanup and rollback commands are disabled with no bypass switch. A plan is not proof that live work stopped or external effects were reconciled. No real migration or runtime validation was performed while implementing this tool.
