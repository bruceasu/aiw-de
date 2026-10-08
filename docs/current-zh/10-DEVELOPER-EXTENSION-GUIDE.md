# 开发与扩展指南

## 1. 技术基线

```text
Java 21
Spring Boot 4.0.8
Maven
JDBC only
Flyway
SQLite / PostgreSQL
```

Package root：`me.asu.aidev`。

## 2. 模块

```text
approval
autonomous
development
execution
fleet
work
worker
```

模块内部通常分 domain/application/port/jdbc/protocol。

## 3. 新增 Task Type

一般步骤：

1. 增加 TaskType；
2. 明确谁创建它和 dependency；
3. 定义 required capability；
4. 定义是否需要 workspace；
5. 如完成后需要派生工作，实现 TaskCompletionHandler；
6. 明确 `supportedTypes()`；
7. 增加 readiness/claim/complete/replay integration tests；
8. 更新 current docs。

Completion Handler 只能创建 Task/Dependency，不能直接调用 Worker。

## 4. Completion Handler

正确：

```text
complete -> createIfAbsent downstream task -> persist dependency -> return
```

错误：

```text
complete -> 直接调用 Reviewer Agent
```

Generated Task ID 必须 deterministic/idempotent。一个 TaskType 只能有一个 completion authority，dispatcher 启动时会检查重复 authority。

## 5. 新 Worker

最小要求：register/heartbeat/drain/offline、claim/start/task heartbeat、Lease Lost 处理、Task 执行、Workspace Result、Semantic Completion Report、Fail、Worktree cleanup。

禁止 Worker 直接访问数据库。

## 6. Capability 命名

角色使用稳定小写连字符；Repository affinity 使用 `repo:<repositoryRef>`。技术 Capability 建议保持简单稳定，不要把瞬时 scheduler 状态编码进去。

## 7. 新表规则

只有以下之一成立才优先考虑新表：

- 独立生命周期；
- 独立并发 Ownership；
- 独立索引关系；
- 无法从已有事实恢复的 Audit Retention。

## 8. Spring 规则

Spring-managed class 不要随意 `final`。如果 Bean 有多个 Constructor，Production Constructor 明确加 `@Autowired`。R8-S12 曾因遗漏这一点导致所有 ApplicationContext tests 启动失败。

## 9. SQLite 规则

单连接。不要在 RowMapper/JDBC callback 内嵌套 JdbcTemplate query。R8-S7 曾因此产生 30 秒 connection timeout。

## 10. 验证

```bash
mvn clean verify
```

当前基线 29 个 Failsafe Integration Test methods。
