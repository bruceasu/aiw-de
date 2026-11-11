# AI Worker — 中文参考手册

当前统一入口为 [文档导航](../README.md)，建议先读 [系统如何工作](../SYSTEM-OVERVIEW.md)。本目录保留 R8/R9 起形成的成套手册，尚未逐篇完成当前实现核对；下列版本描述原始阶段，不代表整个当前系统。较新的专题由统一入口导航。

**执行平台基线：** R8-S13 Fix2（`me.asu.aidev`）
**当前产品层：** R9 Web Console consolidated  
**原始 Java 基线：** 21；当前根项目声明 Java 25
**Spring Boot：** 4.0.8  
**持久化：** JDBC + Flyway；本地 SQLite / 共享 PostgreSQL  
**Worker 模型：** 外部 Worker、Pull 模式、Worker Protocol v1  
**环境变量前缀：** `AIW_`

历史阶段资料位于 `docs/archive/`。使用本目录的命令与契约前，请按 [文档基线](../DOCUMENTATION-BASELINE.md) 核对对应专题与源码。

## 推荐阅读顺序

| 读者 | 文档 |
|---|---|
| 第一次体验 | [Quick Start](QUICK-START.md) |
| 给 Boss 介绍 | [3～5 分钟介绍稿](BOSS-INTRO-3-5-MIN.md) |
| 产品/技术负责人 | [执行摘要](01-EXECUTIVE-OVERVIEW.md) |
| 架构师 | [系统架构](02-SYSTEM-ARCHITECTURE.md) |
| 工程师 | [自主工程生命周期](03-AUTONOMOUS-ENGINEERING-LIFECYCLE.md) |
| Worker 开发者 | [Worker Protocol 与参考 Worker](04-WORKER-PROTOCOL-AND-WORKERS.md) |
| 用户/操作员 | [API 与用户使用手册](05-API-USER-MANUAL.md) |
| Web Console 用户/操作员 | [Web Console](15-WEB-CONSOLE.md) |
| 运维 | [运维手册](06-OPERATIONS-GUIDE.md) |
| 部署 | [配置参考](07-CONFIGURATION-REFERENCE.md) |
| DBA/数据负责人 | [数据模型与迁移](08-DATA-MODEL-AND-MIGRATIONS.md) |
| 安全评审 | [安全、Authority 与信任边界](09-SECURITY-AUTHORITY-AND-TRUST-BOUNDARIES.md) |
| 维护者 | [开发与扩展指南](10-DEVELOPER-EXTENSION-GUIDE.md) |
| 故障处理 | [故障排查与恢复](11-TROUBLESHOOTING-AND-RECOVERY.md) |
| 发布验证 | [测试、验证与当前限制](12-TESTING-VALIDATION-AND-LIMITATIONS.md) |
| 判断是否继续开发 | [当前能力清单](13-CURRENT-CAPABILITIES.md) |
| 术语 | [术语表](14-GLOSSARY.md) |

## Authority 原则

Java Control Plane 与数据库拥有生命周期、任务归属、调度、Engineering Gate 与 Approval 的权威状态。Worker 不直接修改 Control Plane 数据库。原手册的不可变 Git commit 交付描述适用于对应工程路径，不代表所有 Plan 必须使用 Git；当前交接见 [Plan 位置与交接](../PLAN-LOCATIONS-AND-HANDOFFS.md)。Human Approval 是独立 Authority。

