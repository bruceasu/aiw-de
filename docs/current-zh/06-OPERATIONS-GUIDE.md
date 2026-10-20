# 运维手册

## 1. 运行模式

### 本地模式

默认 `sqlite` profile，适合开发、测试、演示和单 Control Plane。

### 共享模式

使用 PostgreSQL，外部 Worker 可以部署在其他机器。跨机器代码结果交接建议配置共享 Git remote。

## 2. 本地启动

要求：JDK 21、Maven、Git；真实 Codex Worker 还需要 Codex CLI/SDK 及认证。

```bash
mkdir -p data
mvn clean verify
mvn spring-boot:run
```

默认：

```text
127.0.0.1:8080
./data/aidev-r3.db
```

## 3. 构建 Jar

```bash
mvn clean package
java -jar target/ai-dev-manager-r9-9.0.0-SNAPSHOT.jar
```

## 4. PostgreSQL 示例

```bash
export SPRING_PROFILES_ACTIVE=postgresql
export AIW_DB_URL='jdbc:postgresql://db-host:5432/aidev'
export AIW_DB_USER='aidev'
export AIW_DB_PASSWORD='change-me'
export AIW_WORKER_TOKEN='long-random-secret'
java -jar target/ai-dev-manager-r9-9.0.0-SNAPSHOT.jar
```

基础 application.yaml 已是 PostgreSQL 配置；激活非 sqlite profile 后不会加载默认 sqlite profile。

## 5. Flyway

启动自动运行 Migration，当前最新 V14。

升级建议：

1. 备份 DB；
2. 维护窗口中部署新版本；
3. 启动并让 Flyway 迁移；
4. 检查 Fleet Dashboard；
5. 再恢复/启动 Worker。

不要修改已经执行过的 Migration。

## 6. 备份

SQLite 最安全方式：停止 Control Plane 后复制 DB 文件。

PostgreSQL 使用组织标准的 pg_dump/快照/托管备份机制，并定期验证 restore。

## 7. Worker 运维

Graceful maintenance 可先调用：

```text
POST /api/r1/worker-protocol/v1/workers/{workerId}/drain
```

当前任务自然结束后再 offline。

### R10 流程 Worker 运维

[TypeScript](../../workers/process-typescript/README.md)、[Go](../../workers/process-go/README.md) 和 [Python](../../workers/process-python/README.md) 流程 Worker 按角色分别运行。保持需求分析、架构、规划、编码、审查、验证和修复角色在线；可能出现集成任务时也要运行集成执行和集成验证角色。启动或调整 Worker 后检查 `GET /api/r1/fleet/dashboard`，确认 READY 任务没有因角色或技术能力不匹配而积压。

[TypeScript 流程 Worker](../../workers/process-typescript/README.md) 在单一入口显式选择 `codex-sdk` 或 `openai-agents`；旧 API 包及启动路径已删除。选择 Agents 时配置 `OPENAI_API_KEY`，并在 AI 策略中批准相应 `openai-agents` profile。只读角色仅有文件检查工具；写入角色可显式开启主机命令工具，但该工具没有操作系统沙箱，须在隔离主机或容器中运行。配置和验证限制见 Worker 指南。

各进程应指向相同的 Control Plane 和 Repository Ref，并能访问对应 Git 仓库。`AIW_WORKER_CAPABILITIES` 配置额外的技术能力；角色能力及 `repo:<repositoryRef>` 会自动加入。每个并发进程需要独立的 `AIW_WORKER_ID`。Worker 不允许 `git push`；代码任务应共享本地 Git 仓库，跨机器交接需由独立受控发布服务处理。

滚动更新时先 Drain 旧进程，等待活动 Lease 清零后停止，再以相同角色和唯一 Worker ID 启动新进程。进程异常退出后，由 Control Plane 在 Lease 过期时重新排队任务；先查看 Run Timeline，再决定是否人工干预。只读角色强制使用只读 Codex 沙箱；编码、集成、修复角色使用独立可写 Worktree。

## 8. Lease Recovery

默认：

```text
AIW_LEASE_RECOVERY_ENABLED=true
AIW_LEASE_RECOVERY_INTERVAL_MS=5000
AIW_LEASE_RECOVERY_BATCH_SIZE=100
```

Worker 消失后 Lease 过期，Task 会自动 READY；这不等于 Run FAILED。

## 9. 失败 Run

Run=FAILED 时：

1. 查 dashboard/timeline；
2. 看 lastFailedTaskId/failureReason；
3. 修复环境问题；
4. POST `/recover`。

## 10. Run/Fleet 调度

Run 和 Fleet 都支持 runtime maxParallelism。收缩不会 kill 健康 Lease，而是阻止新 Claim 等待自然 drain。

`overcommittedSlots > 0` 在刚收缩后可以是合法状态；`accountingDelta != 0` 才应该视为一致性问题。

## 11. 健康检查

当前没有 Actuator health endpoint。建议：

```text
GET /api/r1/fleet/dashboard
GET /api/r1/fleet/scheduling
```

关注：

- worker lease accounting delta；
- run slot accounting delta；
- FAILED/PAUSED 数量；
- unmatched READY；
- admission/capacity blocked backlog。

## 12. SQLite 重要规则

SQLite Hikari pool 刻意固定为 1。不要通过增加连接数来掩盖 JDBC callback 内嵌套查询。需要的数据应通过 JOIN 一次取回或等第一条 Query 完成后再发第二条 Query。

## 13. Production 上线前

至少补齐：

- PostgreSQL 多实例并发压测；
- Management API Auth/RBAC；
- TLS/Network Policy；
- Secret 管理/轮换；
- Backup/Restore 演练；
- 长时间 Worker crash/recovery；
- 真实 Codex E2E；
- Release/PR policy。

## Web Console 运维操作

日常运维优先使用 `http://127.0.0.1:8080/ui`，无需手工调用 REST。Run Detail 提供 Pause/Resume/Recover/Cancel 和 Run Scheduling；Workers 提供 Drain/Offline；System 提供 Fleet Scheduling 及审计历史；Approvals 提供人工决策。所有浏览器写操作仍调用既有 Application Service。

降低 Run/Fleet 上限后，如果当前健康 Lease 数量高于新上限，会暂时显示 overcommitted；这是正常的 natural-drain 行为，不会强杀现有 Worker。
