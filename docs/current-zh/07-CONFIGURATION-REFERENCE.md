# 配置参考

## 1. Control Plane

| 环境变量 | 默认 | 说明 |
|---|---|---|
| `AIW_BIND_ADDRESS` | `127.0.0.1` | HTTP bind |
| `AIW_PORT` | `8080` | HTTP port |
| `AIW_DB_URL` | SQLite profile 默认为 `jdbc:sqlite:d:/temp/data/aidev-r3.db` | JDBC URL；父目录需已存在，按当前系统设置路径 |
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

角色：`VIEWER`、`OPERATOR`、`APPROVER`、`ADMIN`。登录页 `/ui/login` 始终可用，无需启用开关。默认保留普通控制台页面的本地免登录访问；`AIW_WEB_CONSOLE_AUTH_ENABLED=true` 只用于要求这些页面也必须登录。真实登录始终校验配置的用户名和非空密码；未配置密码时，登录页显示凭据配置提示，拒绝所有登录尝试。

Goal workshop（`/ui/goals`）支持既有本地模式：`AIW_WEB_CONSOLE_AUTH_ENABLED=false` 且尚未触发持久人工认证要求时，未登录浏览器共享 `local-console`，创建和编辑草稿仍检查 CSRF。用户主动登录后使用真实用户名和配置的角色，并可退出登录。要求登录的会话失效时会显示重新登录提示及入口。首次代理启用后的持久认证要求不能通过关闭普通页面的登录要求解除。

`/ui/authority`、`/ui/configurations`、`/ui/questions`、`/ui/workflow-graphs` 和 `/ui/software-development` 始终要求真实登录会话，不接受 `local-console`。未登录时进入登录页，登录成功后返回原目标页面。在根目录 `.env` 中配置 `AIW_WEB_CONSOLE_USERNAME` 和非空的 `AIW_WEB_CONSOLE_PASSWORD`，再通过启动脚本重启；其他启动方式需将这些值设置为环境变量。无需设置登录启用开关。相关 API 继续检查真实登录、CSRF、当前角色及所有者权限，首次代理启用后的持久认证要求仍然有效。
