# 配置参考

## 1. Control Plane

| 环境变量 | 默认 | 说明 |
|---|---|---|
| `AIW_BIND_ADDRESS` | `127.0.0.1` | HTTP bind |
| `AIW_PORT` | `8080` | HTTP port |
| `AIW_DB_URL` | SQLite profile 默认为 `jdbc:sqlite:./data/aidev-r3.db` | JDBC URL |
| `AIW_DB_USER` | `aidev` | PostgreSQL user |
| `AIW_DB_PASSWORD` | `aidev` | PostgreSQL password |
| `AIW_WORKER_TOKEN` | 空 | Worker Protocol Bearer Token |
| `AIW_LEASE_RECOVERY_ENABLED` | `true` | Lease recovery 开关 |
| `AIW_LEASE_RECOVERY_INTERVAL_MS` | `5000` | recovery interval |
| `AIW_LEASE_RECOVERY_BATCH_SIZE` | `100` | 每批数量 |
| `AIW_RUN_DEFAULT_MAX_PARALLELISM` | `0` | 新 Run 默认并发，0=unlimited |
| `AIW_FLEET_MAX_PARALLELISM` | `0` | 新 DB 的 Fleet ceiling 初始值 |

注意：`AIW_FLEET_MAX_PARALLELISM` 只作为新数据库 bootstrap default。运行时 API 修改后，重启不会用环境变量覆盖持久化 authority。

## 2. Approval

```text
AIW_APPROVAL_POLICY=MANUAL|REQUIRED
AIW_APPROVAL_REQUESTED_BY
AIW_APPROVAL_CHANNEL=API|TELEGRAM|TEAMS|WEB|CLI
AIW_APPROVAL_CHANNEL_REF
```

## 3. Telegram

```text
AIW_TELEGRAM_ENABLED=false
AIW_TELEGRAM_BOT_TOKEN
AIW_TELEGRAM_API_BASE=https://api.telegram.org
AIW_TELEGRAM_CHAT_ID
AIW_TELEGRAM_ALLOWED_USER_IDS
AIW_TELEGRAM_POLL_TIMEOUT_SECONDS=30
AIW_TELEGRAM_BACKOFF_MS=1000
```

启用 Telegram 时 Bot Token 和 allowed user ids 都是必需的。

## 4. Worker 通用配置

```text
AIW_CONTROL_PLANE_URL
AIW_WORKER_ID
AIW_WORKER_TOKEN
AIW_WORKER_CAPABILITIES
AIW_WORKER_MAX_CONCURRENCY
AIW_REPOSITORY_REF
AIW_REPOSITORY_ROOT
AIW_WORKSPACE
AIW_WORKTREE_ROOT
AIW_CODEX_PATH
AIW_CODEX_MODEL
AIW_CODEX_SANDBOX
AIW_CODEX_APPROVAL_POLICY
AIW_CODEX_REASONING_EFFORT
AIW_CODEX_NETWORK_ACCESS
```

Python Worker 使用 `AIW_CODEX_APPROVAL_MODE`。

Java/JS/TS 常用 `*_MS` 时间变量；Python 使用 `*_SECONDS`。具体以 Worker 实现为准。

## 5. 单 Worker Demo 推荐 Capability

```text
requirement-analysis,
solution-architecture,
implementation-planning,
coding,
integration,
code-review,
verification,
integration-verification,
repair,
java,
maven
```

配置 `AIW_REPOSITORY_REF=core` 后 Worker 会自动加 `repo:core`。

## Web Console

```text
AIW_WEB_CONSOLE_AUTH_ENABLED=false
AIW_WEB_CONSOLE_USERNAME=admin
AIW_WEB_CONSOLE_PASSWORD=
AIW_WEB_CONSOLE_ROLE=ADMIN
AIW_WEB_CONSOLE_SESSION_TIMEOUT=8h
AIW_WEB_CONSOLE_COOKIE_SECURE=false
```

角色：`VIEWER`、`OPERATOR`、`APPROVER`、`ADMIN`。本地模式默认不启用登录；启用时 password 不能为空。
