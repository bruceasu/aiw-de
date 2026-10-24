# Configuration Reference

## 1. Control Plane

| Environment variable | Default | Purpose |
|---|---:|---|
| `AIW_BIND_ADDRESS` | `127.0.0.1` | HTTP bind address |
| `AIW_PORT` | `8080` | HTTP port |
| `AIW_DB_URL` | SQLite profile: `jdbc:sqlite:./data/aidev-r3.db`; base: PostgreSQL URL | JDBC URL |
| `AIW_DB_USER` | `aidev` | PostgreSQL username |
| `AIW_DB_PASSWORD` | `aidev` | PostgreSQL password |
| `AIW_WORKER_TOKEN` | empty | Optional shared bearer token for Worker Protocol |
| `AIW_LEASE_RECOVERY_ENABLED` | `true` | Enable expired-lease recovery scheduler |
| `AIW_LEASE_RECOVERY_INTERVAL_MS` | `5000` | Recovery scan interval |
| `AIW_LEASE_RECOVERY_BATCH_SIZE` | `100` | Maximum expired leases processed per scan |
| `AIW_RUN_DEFAULT_MAX_PARALLELISM` | `0` | Default new-run concurrency ceiling; 0 = unlimited |
| `AIW_FLEET_MAX_PARALLELISM` | `0` | Bootstrap value for the global fleet ceiling; 0 = unlimited |

### Important Fleet bootstrap behavior

`AIW_FLEET_MAX_PARALLELISM` initializes a new database’s singleton fleet budget. Runtime updates through `/api/r1/fleet/scheduling` persist and are not overwritten by the environment value after restart.

## 2. Approval policy

| Variable | Default | Purpose |
|---|---|---|
| `AIW_APPROVAL_POLICY` | `MANUAL` | `MANUAL` or `REQUIRED` |
| `AIW_APPROVAL_REQUESTED_BY` | `control-plane:approval-policy` | Actor recorded for auto-generated requests |
| `AIW_APPROVAL_CHANNEL` | `API` | `API`, `TELEGRAM`, `TEAMS`, `WEB`, `CLI` |
| `AIW_APPROVAL_CHANNEL_REF` | empty | Channel-specific reference, e.g. `chat:<telegram-id>` |

When policy is `REQUIRED`, a PASSED engineering gate automatically materializes an approval request.

## 3. Telegram approval

| Variable | Default | Purpose |
|---|---|---|
| `AIW_TELEGRAM_ENABLED` | `false` | Enable Telegram adapter |
| `AIW_TELEGRAM_BOT_TOKEN` | empty | Telegram Bot token; required when enabled |
| `AIW_TELEGRAM_API_BASE` | `https://api.telegram.org` | Bot API base |
| `AIW_TELEGRAM_CHAT_ID` | empty | Default target chat |
| `AIW_TELEGRAM_ALLOWED_USER_IDS` | empty | Required allow-list when Telegram is enabled |
| `AIW_TELEGRAM_POLL_TIMEOUT_SECONDS` | `30` | Long-poll timeout |
| `AIW_TELEGRAM_BACKOFF_MS` | `1000` | Retry delay after polling failure |

The adapter uses Bot API `getUpdates` long polling and inline Approve/Reject buttons. It validates allowed user IDs and, when possible, the expected chat.

## 4. Common worker configuration

The exact set varies slightly by language implementation.

| Variable | Typical default | Purpose |
|---|---:|---|
| `AIW_CONTROL_PLANE_URL` | `http://127.0.0.1:8080` | Control Plane base URL |
| `AIW_WORKER_ID` | implementation-specific | Stable worker identity |
| `AIW_WORKER_TOKEN` | empty | Worker Protocol bearer token |
| `AIW_WORKER_CAPABILITIES` | `coding` | Comma-separated capabilities |
| `AIW_WORKER_MAX_CONCURRENCY` | `1` | Parallel slots in one worker process |
| `AIW_REPOSITORY_REF` | empty | Optional legacy YAML repository identity; UI-managed targets arrive in the claim response |
| `AIW_REPOSITORY_ROOT` | empty | Optional legacy local repository path; UI-managed targets arrive in the claim response |
| `aidev.workspaces.<workspace>.repositories.<repositoryRef>` | unset | Optional legacy local target; new Workspaces are stored through `/ui/workspaces`. Legacy refs remain usable without import; conflicting managed refs at another path are rejected. |
| `AIW_WORKSPACE` | repository root/current directory | Fallback workspace for non-repository tasks |
| `AIW_WORKTREE_ROOT` | sibling `.aidev-worktrees` | Attempt worktree parent |
| `AIW_GIT_PUBLISH_REMOTE` | empty | Optional remote used to publish result branches |
| `AIW_CODEX_PATH` | `codex` | CLI path for Java/Go workers |
| `AIW_CODEX_MODEL` | runtime default | Explicit model override |
| `AIW_CODEX_SANDBOX` | `workspace-write` | Codex sandbox mode |
| `AIW_CODEX_APPROVAL_POLICY` | usually `never` in CLI workers | Codex approval policy for Java/Go/JS/TS examples |
| `AIW_CODEX_APPROVAL_MODE` | `auto-review` in Python example | Python Codex SDK approval-mode setting |
| `AIW_CODEX_REASONING_EFFORT` | `high` | Model reasoning effort where supported |
| `AIW_CODEX_NETWORK_ACCESS` | `false` | Whether Codex network access is enabled where supported |

Polling/heartbeat time variables:

- Java/TS/JS examples primarily use `AIW_WORKER_POLL_MS`, `AIW_WORKER_HEARTBEAT_MS`, `AIW_WORKER_HTTP_TIMEOUT_MS`.
- Python uses `AIW_WORKER_POLL_SECONDS`, `AIW_WORKER_HEARTBEAT_SECONDS`, `AIW_WORKER_HTTP_TIMEOUT_SECONDS`.

## 5. Suggested single-worker demo capabilities

For a single Java worker that can execute the entire current R8 lifecycle in a Java/Maven repository:

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

When `AIW_REPOSITORY_REF=core` is set, the reference worker automatically adds `repo:core`.

## Web Console

```text
AIW_WEB_CONSOLE_USERNAME=admin
AIW_WEB_CONSOLE_PASSWORD=
AIW_WEB_CONSOLE_ROLE=ADMIN
AIW_WEB_CONSOLE_SESSION_TIMEOUT=8h
AIW_WEB_CONSOLE_COOKIE_SECURE=false
```

Roles: `VIEWER`, `OPERATOR`, `APPROVER`, `ADMIN`. All console business pages require a real signed-in session. The login page and its public resources remain available; a nonblank password is required to sign in. The former enabled property/environment flag is ignored, including false. Existing local-console data retains its original ownership and is not automatically assigned to the configured account.

Before rolling back to an older optional-auth version, explicitly set that version's `AIW_WEB_CONSOLE_AUTH_ENABLED=true`, retain nonblank credentials, and verify anonymous access is rejected. This change does not perform a deployment or rollback.
