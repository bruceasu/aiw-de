# FD-128: 整理 SQL 文件并精简新项目 schema

**Status:** Complete
**Revision:** 5
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

当前 `db/migration` 有 51 个 Flyway 版本文件（编号 V1–V53，缺 V11、V20）。历史数据升级和新安装 schema 混在一起，也残留未被运行代码使用的表。

## Decision

不兼容旧 Flyway 历史，不迁移旧数据库数据。整理出一个新的初始 SQL 基线，只包含当前 Java 运行代码实际使用的表、关联约束及必要初始记录。旧数据库不修改、不自动删除；新版本使用一个新建的空数据库。

静态引用盘点得到 172 个迁移建表名称，其中 24 个在主代码和测试中没有引用，属于移除候选。当前 V1 还会删除 `autonomous_run`、`autonomous_run_event`、`web_console_run_archive`，但 Java 代码仍查询它们；新基线必须保留这些表。

## Scope

- 仅整理 `src/main/resources/db/migration` 下的 SQL 文件及必要的数据库启动说明。
- 新基线采用单一 Flyway 初始迁移；不保留旧版本升级链和旧数据回填。
- 不修改或清空现有数据库，不增加自动重置逻辑。
- 表的保留以当前主代码引用及其必要外键为准；无代码引用的历史表从新 schema 移除。

## Work items

- [x] 1.1 静态盘点迁移表名、主代码/测试引用，并识别旧脚本删除的代码依赖表。
- [x] 1.2 以当前代码使用的表和必要初始记录构建新基线，删除未使用表及旧版本 SQL 文件。
- [x] 1.3 更新新数据库使用说明，完成静态核对、Java compile-only 检查和独立审查。

## Acceptance

- 空数据库使用单个新基线建立当前代码所需的表结构和必要默认记录。
- 不创建没有当前代码用途的历史表；保留运行代码实际查询的表。
- 旧数据库不迁移、不清空；使用说明明确需要将应用连接到新建的空数据库。
- Flyway 目录不再含旧版本升级迁移。

## Verification

- 静态对照迁移建表语句与主代码/测试中的 SQL 表名引用；检查外键和初始记录。
- Java compile-only 检查不验证 PostgreSQL SQL 执行；不连接或修改真实数据库。
- 新空库实际迁移验证未授权，暂不运行；独立静态审查通过，见[FD-128 独立审查](reviews/FD-128-review.md)。
- 已将 74 条历史 `ALTER TABLE` 折叠进初始建表语句，纳入原由 V11/V20 Java migration 提供的最终状态约束和工作流表；移除两份 Java migration、无当前代码用途的表、历史数据回填及其余旧版本 migration 文件。
- 已同步更新中英文架构、运维、快速开始及 Schema 说明。
- 静态终检通过：单个 SQL migration，150 张表均有主代码 SQL 操作引用，142 个外键引用按顺序解析，无历史 ALTER、DROP 或回填；差异格式检查通过。
- Java compile-only 通过：`C:\green\apache-maven-3.9.4\bin\mvn.cmd -o '-Dmaven.repo.local=D:\.m2\repository' -DskipTests compile`；365 个主源码。
- 未运行测试或数据库迁移；没有连接或修改任何数据库。

## Sources

- 用户决定：不兼容、不迁移，直接使用新 schema；只保留必要的表。
- 静态检查：Flyway/PostgreSQL；51 个版本文件；24 个无主代码/测试引用的候选表；3 个仍被 Java 查询但被 V1 删除的表。

**Completed:** 2026-11-15
