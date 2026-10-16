# aiw-de Control Plane API

本文档描述仓库当前 Spring MVC 控制器实现的 HTTP 接口，作为独立、按接口和 DTO 查阅的参考，与现有 API 用户手册互补。目前项目未集成 springdoc/Swagger，也没有可供其生成的 OpenAPI 文档。路径以应用根路径为基准，部署时可替换主机和端口。

## 基本信息

- 默认地址：`http://localhost:8080`。服务绑定地址、端口分别由 `AIW_BIND_ADDRESS`、`AIW_PORT` 控制。
- JSON 接口使用 `Content-Type: application/json`；时间值为 ISO-8601 UTC 字符串。
- 下列 GET/POST 路径即实现注册的路径。无请求体的 POST 仍应发送空请求体或按客户端要求发送 `{}`。
- 示例中的 ID、token、时间和返回数据均为示意；省略字段是否为 `null` 取决于实际数据。

## 鉴权与错误

| 接口组 | 鉴权 |
| --- | --- |
| Worker Protocol `/api/worker-protocol/v1/**`（含 Worker outputs） | 当 `AIW_WORKER_TOKEN` 非空时，发送 `Authorization: Bearer <token>`；为空时过滤器不校验 token。任务操作仍会校验 Worker/lease 状态。 |
| External Workflow `/api/workflows/**` | 仅 `aidev.external-workflow.enabled=true` 时注册。使用 `X-Workflow-Token`；intake request 使用单独的 request token，其余调用使用 submission token。 |
| `/api/r6/**`、`/api/r7/**`、`/api/r8/**`、`/api/knowledge/**` | 控制器未声明额外认证；仍应由部署网络边界保护。 |
| Web Console `/ui/**` 的 JSON 管理/输出 API | 已登录的 Web Console session cookie。非 GET 请求还需 `_csrf` 请求参数；操作受当前 session role 限制。认证是否启用由 `AIW_WEB_CONSOLE_AUTH_ENABLED` 控制。 |

Worker 协议和 Worker output 的错误格式：`{"code":"bad_request|state_conflict","message":"...","timestamp":"..."}`，分别对应 400 和 409；无效 token 返回 401。Fleet API 使用相同结构。Workflow Plan 管理 API 错误体是纯文本，常见状态为 400、403、404、409。具体状态也可能受底层 Spring 异常处理影响。

## Worker Protocol v1

基础路径：`/api/worker-protocol/v1`。Worker 注册及任务租约操作使用 POST。

| 方法与路径 | 功能 | 参数/请求体 | 成功返回 |
| --- | --- | --- | --- |
| `POST /workers/register` | 注册/更新 Worker | `workerId`、`runtimeType`、`capabilities` 字符串数组、`maxConcurrency` 整数 | Worker 状态对象 |
| `POST /workers/{workerId}/heartbeat` | 更新 Worker 心跳 | 路径 `workerId`；无业务请求体 | 空响应 |
| `POST /workers/{workerId}/drain` | 停止接收新任务 | 路径 `workerId` | 空响应 |
| `POST /workers/{workerId}/offline` | 将 Worker 标记离线 | 路径 `workerId` | 空响应 |
| `POST /tasks/claim` | 领取一个可执行任务 | `workerId` | 有任务返回领取对象；无任务返回 `204 No Content` |
| `POST /tasks/{taskId}/start` | 开始已领取任务 | `workerId`、`leaseToken`；可选 `externalSessionId`、`profileId`、`profileVersion`、`selectionReason` | 空响应 |
| `POST /tasks/{taskId}/heartbeat` | 更新任务租约并查询停止请求 | `workerId`、`leaseToken`；可选 `externalSessionId` | `stopRequested` 对象 |
| `POST /tasks/{taskId}/stop-acknowledgement` | 确认停止请求 | `workerId`、`leaseToken` | 空响应 |
| `POST /tasks/{taskId}/complete` | 提交任务完成结果 | `workerId`、`leaseToken`；可选 `externalSessionId`、`workspaceResult`、`report` | 完成任务 ID 及新就绪/生成任务 ID 列表 |
| `POST /tasks/{taskId}/fail` | 报告任务失败 | `workerId`、`leaseToken`；可选 `externalSessionId`、`failureReason`、`failureCategory` | 空响应 |

注册示例：

```http
POST /api/worker-protocol/v1/workers/register
Authorization: Bearer <worker-token>
Content-Type: application/json

{"workerId":"worker-east-01","runtimeType":"codex","capabilities":["java","testing"],"maxConcurrency":2}
```

```json
{"workerId":"worker-east-01","status":"ONLINE","activeLeases":0,"maxConcurrency":2}
```

领取示例：

```http
POST /api/worker-protocol/v1/tasks/claim
Authorization: Bearer <worker-token>
Content-Type: application/json

{"workerId":"worker-east-01"}
```

```json
{
  "taskId":"TASK-123",
  "taskType":"IMPLEMENTATION",
  "subjectType":"issue",
  "subjectId":"ISSUE-42",
  "requiredCapabilities":["java"],
  "skillRef":null,
  "instruction":"Implement the requested change.",
  "instructionRevision":1,
  "priority":50,
  "attemptId":"ATT-456",
  "attemptNo":1,
  "leaseToken":"<lease-token>",
  "leaseAcquiredAt":"2026-10-16T01:00:00Z",
  "leaseExpiresAt":"2026-10-16T01:05:00Z",
  "workspace":null,
  "inputs":[],
  "aiGuidance":null,
  "outputStore":null
}
```

`aiGuidance`、`outputStore` 和 `workspace` 内容取决于相应配置/任务数据；上例的空值不表示实现中永远为空。完成请求的嵌套对象字段如下：`workspaceResult={resultRevision, changedFiles, publicationRef?}`；`report={verdict, summary}`。失败上报字段为 `failureReason` 与 `failureCategory`。例如：

```json
{
  "workerId":"worker-east-01",
  "leaseToken":"<lease-token>",
  "externalSessionId":"session-789",
  "workspaceResult":{"resultRevision":"abc1234","changedFiles":["src/A.java"],"publicationRef":null},
  "report":{"verdict":"PASS","summary":"Implementation completed."}
}
```

完成响应示例：`{"taskId":"TASK-123","newlyReadyTaskIds":["TASK-124"],"generatedTaskIds":[]}`。枚举字段须使用服务识别的枚举名；无效状态或不匹配的租约会被拒绝。

## Worker task outputs

基础路径：`/api/worker-protocol/v1/tasks/{taskId}/outputs`，使用 Worker token，并校验对应租约。

| 方法与路径 | 功能 | 请求体 | 成功返回 |
| --- | --- | --- | --- |
| `POST /api/worker-protocol/v1/tasks/{taskId}/outputs` | 注册输出 bundle | `TaskOutputService.Registration`：按当前实现的输出注册模型提供 bundle 元数据和文件 | 已存储的 `TaskOutputBundle` JSON |
| `POST /api/worker-protocol/v1/tasks/{taskId}/outputs/read` | 读取先前登记的文件清单 | `workerId`、`leaseToken`、`attemptId`、`bundleId` | 文件记录数组 |

读取请求示例：`{"workerId":"worker-east-01","leaseToken":"<lease-token>","attemptId":"ATT-456","bundleId":"BUNDLE-9"}`。响应文件结构由 `StoredFile` 定义；文件二进制通过 Web Console 文件下载接口获取。注册体具体字段以 `TaskOutputService.Registration` 当前 DTO 为准。

## External Workflow API

基础路径：`/api/workflows/{workflowId}`。接口仅在 `aidev.external-workflow.enabled=true` 时启用。`workflowId` 格式为小写字母开头、包含小写字母/数字/连字符，并以 `@v<数字>` 结尾（例如 `code-review@v1`）。

| 方法与路径 | 功能 | 参数/请求体 | 成功返回 |
| --- | --- | --- | --- |
| `POST /requests` | 创建 intake 请求 | `X-Workflow-Token` 使用 request token；JSON `capability`、`instruction` | 202，`workflowId`、新生成的 `runId` |
| `POST /runs` | 直接提交一组步骤 | `X-Workflow-Token` 使用 submission token；JSON `steps` | 202，`workflowId`、`runId` |
| `POST /runs/{runId}/steps` | 在 intake 后物化步骤 | submission token；JSON `steps` | 202，原 `workflowId`、`runId` |
| `GET /runs/{runId}` | 查询运行状态 | `X-Workflow-Token` submission token | Workflow 状态及步骤状态 |

步骤数组长度须为 1–32。每个步骤包含 `key`、`capability`、`instruction`、可选 `repositoryRef`、`baseRevision`、`dependsOn`。key/capability 使用小写字母开头的标识；依赖必须指向请求中较早定义的步骤。intake 后的步骤若未指定依赖，会依赖 intake 步骤。

```http
POST /api/workflows/code-review@v1/runs
X-Workflow-Token: <submission-token>
Content-Type: application/json

{"steps":[
  {"key":"inspect","capability":"java","instruction":"Inspect the change."},
  {"key":"report","capability":"writing","instruction":"Write findings.","dependsOn":["inspect"]}
]}
```

```json
{"workflowId":"code-review@v1","runId":"WF-5e96f8ed9f0e4b5ea7f3a4e8fa2c5e99"}
```

状态查询响应示例：`{"workflowId":"code-review@v1","runId":"WF-...","state":"RUNNING","steps":[{"taskId":"WF-...~inspect","state":"READY"}]}`。聚合状态为 `FAILED`、`COMPLETED` 或 `RUNNING`。

## Approval API

前缀：`/api/r7/approvals`。

| 方法与路径 | 功能 | 参数/请求体 | 成功返回 |
| --- | --- | --- | --- |
| `POST /api/r7/approvals` | 为源任务创建审批请求 | `sourceTaskId`、`requestedBy`；可选 `channel`、`channelRef` | 审批记录 |
| `POST /{approvalRequestId}/decision` | 提交审批决定 | `decision`、`decidedBy`；可选 `note` | 更新后的审批记录 |
| `GET /{approvalRequestId}` | 按 ID 查询审批 | 路径 `approvalRequestId` | 审批记录或 404 |
| `GET /by-source/{sourceTaskId}` | 按源任务查询审批 | 路径 `sourceTaskId` | 审批记录或 404 |

`channel` 默认为 `API`；决定值和 channel 按枚举名解析。创建示例：`{"sourceTaskId":"TASK-123","requestedBy":"operator@example.com","channel":"API","channelRef":"ticket-77"}`。决定示例：`{"decision":"APPROVE","decidedBy":"operator@example.com","note":"Reviewed."}`。响应包括 `approvalRequestId`、源任务及 subject 信息、`status`、请求/决定人和时间、决定说明；尚未决定的 `decidedBy`、`decisionNote`、`decidedAt` 可为空。

```json
{
  "approvalRequestId":"APR-123",
  "sourceTaskId":"TASK-123",
  "subjectType":"issue",
  "subjectId":"ISSUE-42",
  "resultRevision":"abc1234",
  "engineeringGateClosedAt":"2026-10-16T01:00:00Z",
  "status":"PENDING",
  "requestedBy":"operator@example.com",
  "channel":"API",
  "channelRef":"ticket-77",
  "requestedAt":"2026-10-16T01:01:00Z",
  "decidedBy":null,
  "decisionNote":null,
  "decidedAt":null
}
```

## 查询与 Fleet API

### Fleet dashboard

`GET /api/r8/fleet/dashboard` 返回 Fleet 当前投影。可传可选整数查询参数 `detailLimit` 控制详情数量。响应类型是 `FleetDashboard`，包含 Worker、租约、就绪任务及汇总等投影字段；具体字段随该 DTO 版本变化。示例：

```http
GET /api/r8/fleet/dashboard?detailLimit=20
```

```json
{"workers":[],"activeLeases":[],"readyTasks":[]}
```

上例只展示响应形态；实际 `FleetDashboard` 还包含其他统计字段。

### Fleet scheduling

| 方法与路径 | 功能 | 参数/请求体 | 返回 |
| --- | --- | --- | --- |
| `GET /api/r8/fleet/scheduling` | 当前并发预算 | 无 | `maxParallelism`、`activeLeases`、`availableSlots`、`overcommittedSlots`、`saturated`、`updatedAt` |
| `POST /api/r8/fleet/scheduling` | 修改并发上限 | `maxParallelism`、`controlledBy`、`reason` | 更新后的预算对象 |
| `GET /api/r8/fleet/scheduling/events?limit=100` | 最近调度变更 | 可选整数 `limit`，默认 100 | 调度事件数组 |

更新示例：`{"maxParallelism":8,"controlledBy":"operator@example.com","reason":"Increase capacity"}`。响应示例：`{"maxParallelism":8,"activeLeases":3,"availableSlots":5,"overcommittedSlots":0,"saturated":false,"updatedAt":"2026-10-16T01:00:00Z"}`。

### Engineering Gate

- `GET /api/r6/engineering-gates/{sourceTaskId}`：按源任务查询 gate；无记录返回 404。
- `GET /api/r6/engineering-gates/latest-passed?subjectType=issue&subjectId=ISSUE-42`：按 subject 查询最近通过项；需要 `subjectType`、`subjectId`，无记录返回 404。

成功响应字段包括 `sourceTaskId`、`sourceTaskType`、`subjectType`、`subjectId`、`status`、repository/revision/publication 信息、`changedFiles`、`review` 与 `verification` 证据（各含 `taskId`、`verdict`、`summary`）及 `closedAt`。

### Knowledge documents

- `GET /api/knowledge/documents`：查询文档。可选查询参数 `repositoryRef`、`runId`、`q`；`limit` 默认 50。
- `GET /api/knowledge/documents/{id}`：读取文档；不存在返回 404。

两者返回 `KnowledgeDocument`（列表或单个对象）。属性由领域 DTO 定义，示意列表响应为 `[ {"id":"DOC-1"} ]`；此处不代表对象只有 `id` 一个属性。

## Web Console 管理 API

这些路径受 Web Console session/role 保护，适用于已登录控制台，不是 Worker token 接口。针对 `/ui/**` 的写请求还需有效 CSRF token（请求参数 `_csrf`）。

### Workflow plans

基础路径：`/ui/workflow-plans`。

| 方法与路径 | 功能 | 参数/请求体 | 成功返回 |
| --- | --- | --- | --- |
| `GET /ui/workflow-plans?view=current` | 列出计划；`view=archived` 列归档 | 可选 `view`，默认 `current` | WorkflowPlan 数组 |
| `GET /ui/workflow-plans/{id}` | 计划详情 | 路径 `id` | `{plan,tasks,controls}` |
| `POST /ui/workflow-plans` | 创建草稿 | WorkflowPlanSpec JSON | WorkflowPlan |
| `POST /{id}/copy` | 复制计划 | 无业务请求体 | WorkflowPlan |
| `POST /{id}/confirm` | 确认计划 | `{"version":<非负整数>}` | WorkflowPlan |
| `POST /{id}/withdraw`、`/stop`、`/complete`、`/cancel` | 改变计划控制状态 | `{"version":<非负整数>,"reason":"..."}` | WorkflowPlan |
| `POST /{id}/jump` | 跳转至步骤 | `version`、非负 `targetStep`、可选 `reason` | WorkflowPlan |
| `POST /{id}/resolve-stop` | 清除过期停止控制 | `{"version":<非负整数>}` | WorkflowPlan |
| `POST /{id}/archive` | 归档计划 | 无业务请求体 | 空响应 |
| `POST /{id}/schedule-delete`、`/undo-delete` | 安排删除/撤销 | 无业务请求体 | 空响应 |

上述写操作要求 operator role；安排删除和撤销要求 admin role。带版本号的变更用于防止基于过期状态更新。WorkflowPlanSpec 的字段以 `WorkflowPlanSpec` DTO 为准。

### 来源问题与已确认答复

基础路径：`/ui/plan-editor/plans/{planId}/questions`。读取要求控制台登录会话；答复要求 operator 权限及 CSRF。这些是控制台接口，不是 Worker token 决策接口。以下例子用于说明契约，尚未实际调用。

| 方法与路径（相对基础路径） | 功能 | 参数/请求体 | 成功返回 |
| --- | --- | --- | --- |
| `GET` | 查看问题、来源文档及答复历史 | 路径 `planId` | Board 对象 |
| `GET /files/{fileId}` | 预览计划当前输出 | 路径 `planId`、`fileId` | `{path,content,truncated}`；内容最多 65,536 字符 |
| `POST /answers` | 显式确认答复并推进计划版本 | 下表 AnswerRequest | Answer 对象 |

AnswerRequest 各字段均必填：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `version` | 整数（Java long） | 面板返回的当前 `planVersion`。 |
| `questionKey` | 字符串 | 面板问题的 `key`，应直接复制。 |
| `sourceFileId` | 字符串 | 问题的 `source.file.id`。 |
| `sourceHash` | 字符串 | 问题的 `source.file.sha256`。 |
| `answer` | 字符串 | 非空，最多 2000 字符；不能包含已确认决定的保留分隔标记。 |
| `confirmed` | 布尔 | 必须为 `true`；草稿答复不具备决定效力。 |

`actor` 从会话取得，不由请求指定。来源必须仍然匹配；计划必须开放，且没有未结束任务、待处理控制或当前版本未过期的待处理修复授权。具体见[数据库流转说明](08-DATA-MODEL-AND-MIGRATIONS.md#4-数据在数据库中的流转)。

Board 字段：`planVersion`、可空的 `blockedReason`、`questions`、`documents`、`history`、`warnings`。问题对象包含 `key`、`id`、`question`、`impact`、`sourceState`、`affects`（字符串数组）、`context`、`source`、可空的 `decision`。来源/文档包含 `taskId`、`attemptId`、`reviewState`、输出 `file` DTO。`history` 和 `decision` 使用下文 Answer 响应结构。问题提取最多 256 个、单个 JSON 文件最多 1 MiB；提取失败或超限会出现在 `warnings`。

读取例子（使用已有登录会话的 cookie 文件）：

```sh
curl -b cookies.txt 'http://localhost:8080/ui/plan-editor/plans/FP-1/questions'
curl -b cookies.txt 'http://localhost:8080/ui/plan-editor/plans/FP-1/questions/files/FILE-1'
```

尚无输出问题的面板响应例子，以及已有输出文件时的预览响应例子：

```json
{"planVersion":1,"blockedReason":null,"questions":[],"documents":[],"history":[],"warnings":[]}
```

```json
{"path":"requirements.json","content":"{\"openQuestions\":[]}","truncated":false}
```

面板已有问题时，将其 key、来源文件 ID/哈希及当前版本填入请求文件。以下为示意值，调用前需替换；`CSRF_TOKEN` 为当前会话 token：

```json
{"version":7,"questionKey":"<board question key>","sourceFileId":"FILE-1","sourceHash":"<source SHA-256>","answer":"Use UTC for all report timestamps.","confirmed":true}
```

保存为 `answer.json` 后调用：

```sh
curl -b cookies.txt -H 'Content-Type: application/json' \
  --data-binary @answer.json \
  "http://localhost:8080/ui/plan-editor/plans/FP-1/questions/answers?_csrf=${CSRF_TOKEN}"
```

响应例子（生成的 ID、哈希及时间为示意）：

```json
{"id":"QD-example","planId":"FP-1","questionKey":"<board question key>","sourceTaskId":"FP-1~requirements~work-1","sourceFileId":"FILE-1","sourceHash":"<source SHA-256>","questionId":"Q-1","questionText":"Which time zone should reports use?","answer":"Use UTC for all report timestamps.","actor":"operator@example.com","planVersion":8,"decidedAt":"2026-10-16T01:00:00Z"}
```

错误码：`400` 答复无效或未确认；`401` 未登录读取；`403` 权限不足；`404` 计划不存在；`409` 版本/来源过期、状态阻塞或上下文超限。控制器处理的参数/冲突错误返回文本，例如 `Plan changed. Refresh before answering.`。答复追加历史，不改写旧报告，不启动任务，也不授予操作权限。重新答复需提交面板的新版本。

### 单轮修复授权

基础路径：`/ui/plan-editor/plans/{id}/repair-authorizations`。两项操作均要求 operator 权限和 CSRF。当前实现固定一轮、有效期一小时；可配置策略模板仍属独立需求方案。

| 方法与路径 | 功能 | 请求 | 返回 |
| --- | --- | --- | --- |
| `POST` 基础路径 | 直接授权修复，或委托指定 Worker 决策是否使用授权 | `planVersion`（long）、`taskId`（来源评审 ID）、`taskVersion`（long）、`reason`（1～2000 字符）、`scope`（1～4000 字符）、可选 `delegateWorkerId`、`warningConfirmed=true` | Authorization 对象 |
| `POST /{authorizationId}/revoke` | 撤销待处理授权 | 路径 ID，无业务请求体 | `{"status":"REVOKED"}` |

`repair.json` 例子：`{"planVersion":8,"taskId":"FP-1~coder~review-3","taskVersion":2,"reason":"Fix review findings","scope":"Correct the timestamp formatting only.","delegateWorkerId":"decision-worker-1","warningConfirmed":true}`。

```sh
curl -b cookies.txt -H 'Content-Type: application/json' --data-binary @repair.json \
  "http://localhost:8080/ui/plan-editor/plans/FP-1/repair-authorizations?_csrf=${CSRF_TOKEN}"
curl -b cookies.txt -X POST \
  "http://localhost:8080/ui/plan-editor/plans/FP-1/repair-authorizations/RA-example/revoke?_csrf=${CSRF_TOKEN}"
```

代理授权响应例子：`{"id":"RA-example","planId":"FP-1","planVersion":9,"sourceReviewId":"FP-1~coder~review-3","sourceTaskVersion":2,"scope":"Correct the timestamp formatting only.","reason":"Fix review findings","grantedBy":"operator@example.com","delegateWorkerId":"decision-worker-1","decisionTaskId":"RA-example~decision","repairTaskId":null,"status":"PENDING","createdAt":"2026-10-16T01:00:00Z","expiresAt":"2026-10-16T02:00:00Z"}`。响应 DTO 不含数据库字段 `round_budget` 和 `decided_at_epoch_ms`。不指定代理时服务立即尝试消费，因此返回状态不一定是 `PENDING`。

代理必须 ACTIVE、心跳有效、具备 `workflow-recovery-decision`，且不能是来源执行者/评审者。创建额外工作前检查版本、适用条件与授权状态；人工填写的范围传入决策和修复指令。当前文本范围尚不是由程序强制校验的结构化权限策略。状态及保留规则见[数据库字典](08-DATA-MODEL-AND-MIGRATIONS.md)。

### Task outputs（Web Console）

| 方法与路径 | 功能 | 参数 | 成功返回 |
| --- | --- | --- | --- |
| `GET /ui/api/task-outputs/tasks/{taskId}` | 列出任务输出 bundle | 路径 `taskId` | `TaskOutputBundle[]` |
| `GET /ui/api/task-outputs/files/{fileId}` | 下载文件 | 路径 `fileId` | 原始二进制；附 `X-Content-SHA256` header |
| `POST /ui/api/task-outputs/files/{fileId}/publication-plans` | 创建发布计划 | `reviewTaskId`、`targetPath`、`reason` | WorkflowPlan JSON |

读接口需登录；创建发布计划需要 operator role。文件响应使用 `application/octet-stream`，下载名由文件路径生成。

## 浏览器页面路由

Web Console 的 `/ui`、`/ui/runs`、`/ui/runs/{runId}`、`/ui/workers`、`/ui/approvals`、`/ui/system` 等为 HTML 页面路由，不属于上面的 JSON API。登录页为 `/ui/login`。传统表单操作包括 Worker drain/offline 和 Fleet scheduling 更新；它们提交表单后重定向回控制台。

## 代码来源与维护

本参考根据 `src/main/java/me/asu/aidev/**/protocol/http` 当前控制器及 DTO 整理。请求/响应 DTO 或路由变化时应同步更新本文档；动态领域 DTO（例如 `FleetDashboard`、`WorkflowPlanSpec`、`KnowledgeDocument`、输出注册模型）的完整字段仍以对应 Java 类型为准。
