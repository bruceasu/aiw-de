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
AIW_WEB_CONSOLE_USERNAME=admin
AIW_WEB_CONSOLE_PASSWORD=
AIW_WEB_CONSOLE_ROLE=ADMIN
AIW_WEB_CONSOLE_SESSION_TIMEOUT=8h
AIW_WEB_CONSOLE_COOKIE_SECURE=false
```

角色：`VIEWER`、`OPERATOR`、`APPROVER`、`ADMIN`。所有 `/ui` 及 `/ui/**` 业务页面统一要求真实登录，只有 `/ui/login` 及其公共 console.css/console.js 资源可匿名。旧 `AIW_WEB_CONSOLE_AUTH_ENABLED` 或对应property已忽略，设置false也不能恢复免登录。真实登录校验用户名和非空密码；未配置密码时，登录页显示配置提示并拒绝全部登录尝试。

门禁按Spring MVC解析后的路径段判定，编码和矩阵参数别名同样受保护。页面别名登录后返回规范编码路径，原查询参数保留；不会把路径中编码的字符变成URL片段或分隔符。

Goal Workshop（`/ui/goals`）同样要求真实登录，使用当前账号的actor及角色。旧local-console数据保留原归属，不删除、不迁移或自动认领；旧匿名operator来源不再有效。会话失效后重新登录；持久人工认证要求保持。

回退到旧可选认证版本前，必须对旧版本显式设置 `AIW_WEB_CONSOLE_AUTH_ENABLED=true`，保留用户名及非空密码，并确认匿名访问被拒绝。不能沿用false或缺省后暴露服务；本次没有执行回退或重启。

`/ui/authority`、`/ui/configurations` 和 `/ui/questions` 始终要求真实登录会话，不接受 `local-console`。未登录时进入登录页，登录成功后返回原目标页面。在根目录 `.env` 中配置 `AIW_WEB_CONSOLE_USERNAME` 和非空的 `AIW_WEB_CONSOLE_PASSWORD`，再通过启动脚本重启；其他启动方式需将这些值设置为环境变量。无需设置登录启用开关。相关 API 继续检查真实登录、CSRF、当前角色及所有者权限，首次代理启用后的持久认证要求仍然有效。

「Agent 权限」分为经理权限和秘书授权。经理是 Control Plane 内置角色，自动故障诊断默认启用。设置 `AIW_MANAGER_AUTOMATIC_DIAGNOSIS=false` 并重启 Control Plane 可停用自动诊断。用户仍可在符合条件的任务详情中手动请求诊断。秘书授权按动作、范围、次数和期限管理。外部 Agent 的配对和当前进程的启停在「Agent 管理」中处理。

FD-152 的工厂运行配置可指定任务默认项与经理默认项。任务和工作流节点可以另选配置，派发后固定配置与连接版本。平台 Skills 可从独立目录保存和查看历史。工厂以 `aidev.agent-factory.max-workers` 和 `aidev.agent-factory.max-workers-per-configuration` 限制 Worker 实例数。新任务使用 Plane 的执行存储标识；需要接入同一共享位置的外部通用 Worker，应配置与 Plane 一致的 `AIW_HANDOFF_STORE_ID` 并验证实际挂载。

在 Agent 工厂的运行 Profile 列表中可以改名或删除。改名更新显示名称，员工和工作流继续按配置标识引用原配置。删除前须解除员工、工作流、未结束的执行和未停止的调度对该配置的引用。删除默认配置时，若仍有其他配置，须先切换对应的默认项。删除最后一个配置会清除两个默认项。已派发任务保留原配置快照。

在工作流执行节点选择「由经理选择」，并指定一个或多个候选运行配置。进入该节点后才开始选型，选定的版本随任务一同派发。经理的本地 API 密钥通过对应服务商的环境变量设置。为任务选择平台 Skill 时，可用当前版本，也可以在版本列表选择一个启用的历史版本。API 使用 `custom-example@2` 表示固定到第二版。

「工具目录」位于 `/ui/tools`。管理员可以登记远程 MCP 工具或 HTTP 操作、维护入参 JSON 以及启停检索。Console API 的列表入口为 `GET /api/r1/tools`。在线 Worker 使用自身 Bearer 凭据查询已启用条目的操作元数据。

配置 `AIW_SHARED_HTTP_ENDPOINTS` 或 `AIW_SHARED_MCP_ENDPOINTS` 后，管理员可为任务或工作流节点授权目录中对应地址的工具。变量接受用逗号分隔的完整地址，默认为空。HTTP 执行限定为 HTTPS GET。MCP 使用 2026-07-28 Streamable HTTP 的 JSON 响应，支持 HTTPS 和 `127.0.0.1` 的 HTTP。任务固定工具版本，Plane 检查租约和授权。可在任务详情撤销授权或核对未知操作。入参支持平面的字符串、整数和布尔值。

FD-152 的样例服务可用 `python tests/fd152/timezone_mcp.py --port 8765` 启动。它使用 Python MCP SDK 2.x 和 IANA 时区数据库。用 `http://127.0.0.1:8765/mcp` 登记操作 `convert_time`，入参为带明确偏移的 ISO 8601 时间 `instant` 和 IANA 时区 `target_zone`。

在团队成员设置中，可以维护平台 Skills、首选运行配置、能力要求和默认验收标准。设定用于派发匹配，工具授权仍由任务单独保存。设置 `AIW_MANAGER_AUTOMATIC_RATE_LIMIT_RETRY=true` 可以让经理在确认纯文本任务首次遇到模型限流且原租约结束后，等待一分钟再自动重试一次。该设置默认关闭。

旧独立图页面 `/ui/workflow-graphs` 和旧软件开发工作台 `/ui/software-development` 已退役，不提供兼容页面或跳转。统一使用 `/ui/workflows` 查看、编辑和执行工作流，任务使用 `/ui/tasks`。旧 authority API 与历史记录的进一步清理单独实施；页面退役不代表已删除历史结果。

在工作流执行详情中，可先暂停后续步骤，再选择尚未派发的节点，填写调整要求并生成建议。建议由一项独立可见的只读任务生成，需要支持任务资料读取的 Python SDK Worker；用量记录在该任务。审阅原说明、建议说明和流程图预览后，点击确认才修改本次执行。目标、资料、成员、Skills、权限和已派发节点不改；执行版本变化后旧建议失效。继续或停止会先请求停止尚在生成的建议，必须等待实际结束证据，不能把请求当作已完成。

侧栏“待处理”（`/ui/questions`）集中显示当前账号可见的流程人工节点、Worker 正式问题与固定操作确认，每类最多 100 条。可以筛选本人可处理或等待确认的记录，并直接打开对应执行、问题或审批。打开列表不会批准、消费授权或执行操作；审批记录沿用现有读取时校验，已过期／失效的请求不作为待决定事项。按计划的详细记录与控制默认收起，从具体问题进入时自动定位。已回答不表示已推进，已批准不表示已执行；最终动作仍校验原版本、权限和 Worker 回执。
