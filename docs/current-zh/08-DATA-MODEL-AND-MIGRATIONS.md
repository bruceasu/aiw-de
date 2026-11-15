# 数据模型与初始 Schema

当前项目只保留一个 Flyway migration：`V1__initial_schema.sql`。它创建当前 Java 运行代码引用的表，以及必要的约束、索引和初始记录。该 SQL 文件是 Schema 的唯一准据。

此基线用于全新的空数据库，不会升级、迁移或重置已有数据库。旧数据库保持原样；请将应用配置为使用新建的数据库文件或数据库实例。

## 1. 数据持久化边界

Control Plane 保存任务、工作流、Worker、授权和执行状态。Worker 通过 Worker 协议汇报变化，不直接写入 Control Plane 数据库。工作区文件和 Git worktree 位于关系型 Schema 之外。

## 2. 表结构

精确的表字段、外键、索引和初始记录以 `src/main/resources/db/migration/V1__initial_schema.sql` 为准。全新安装基线不再包含历史升级链。

## 3. 数据库支持

应用支持按配置使用 SQLite 或 PostgreSQL；Flyway 启动时应用同一份初始 Schema。

## 4. 数据在数据库中的流转

Control Plane 负责持久化任务和工作流状态。Worker 通过协议接收工作，并返回状态、结果与交接引用；Control Plane 由应用代码保存这些汇报。文件和 worktree 保留在各自位置，除非某项功能明确将工件记录保存到数据库。

## 5. 后续 Schema 变更

该新项目的初始 migration 只包含当前应用所需结构。后续 Schema 变更使用新的 Flyway migration；不要在启动流程中加入旧库数据回填或重置逻辑。
