# API 与用户使用手册

本手册区分三种角色：Requester、Operator、Approver。以下示例默认地址：

```text
http://127.0.0.1:8080
```

## 1. 创建自主 Requirement Run

```text
POST /api/r1/requirements
```

```json
{
  "requestId":"health-endpoint-001",
  "requirement":"Add a /health endpoint and focused tests.",
  "repositoryRef":"core",
  "baseRevision":"<full immutable git SHA>",
  "technicalCapabilities":["java","maven"],
  "priority":"HIGH",
  "maxParallelism":2
}
```

规则：

- requestId 可省略，提供时必须匹配 `[A-Za-z0-9._-]{1,80}`；
- Priority 支持 LOW/NORMAL/HIGH/CRITICAL 或 0～100；
- maxParallelism 0～1024，0=unlimited；
- baseRevision 应使用不可变完整 Git SHA。

## 2. 查看 Run

```text
GET /api/r1/runs/{runId}
```

重点字段：status、controlState、effectiveStatus、terminalSourceTaskId、resultRevision、failure/recovery count、pause/resume 信息以及 Run scheduling slots。

## 3. Dashboard / Timeline

```text
GET /api/r1/runs/{runId}/dashboard
GET /api/r1/runs/{runId}/timeline?limit=200
```

Timeline 默认 500，最大 2000。

## 4. Recover

```text
POST /api/r1/runs/{runId}/recover
{"recoveredBy":"operator:alice"}
```

只适用于 `FAILED + ACTIVE`。

## 5. Pause / Resume / Cancel

```text
POST /api/r1/runs/{runId}/pause
POST /api/r1/runs/{runId}/resume
POST /api/r1/runs/{runId}/cancel
```

请求：

```json
{
  "controlledBy":"operator:alice",
  "reason":"maintenance"
}
```

Cancel 不可逆。

## 6. 修改 Run 并发上限

```text
POST /api/r1/runs/{runId}/scheduling
```

```json
{
  "maxParallelism":1,
  "controlledBy":"operator:alice",
  "reason":"reduce load"
}
```

收缩不会撤销健康 Lease。

## 7. Fleet

```text
GET /api/r1/fleet/dashboard
GET /api/r1/fleet/scheduling
POST /api/r1/fleet/scheduling
GET /api/r1/fleet/scheduling/events?limit=100
```

Fleet Dashboard 将 READY Backlog 分成：

```text
claimableReady
unmatchedReady
capacityBlockedReady
admissionBlockedReady
fleetAdmissionBlockedReady
policyBlockedReady
```

因此可以直接判断为什么 Task 没有执行。

## 8. Engineering Gate

```text
GET /api/r1/engineering-gates/{sourceTaskId}
GET /api/r1/engineering-gates/latest-passed?subjectType=AUTONOMOUS_REQUIREMENT&subjectId={runId}
```

长期 Authority 应记录精确 sourceTaskId，不要使用 `latest-passed` 作为历史批准依据。

## 9. Approval

创建：

```text
POST /api/r1/approvals
```

```json
{
  "sourceTaskId":"...",
  "requestedBy":"operator:alice",
  "channel":"API"
}
```

Decision：

```text
POST /api/r1/approvals/{approvalRequestId}/decision
```

```json
{
  "decision":"APPROVE",
  "decidedBy":"human:alice",
  "note":"Approved"
}
```

还可以 REJECT。

查询：

```text
GET /api/r1/approvals/{approvalRequestId}
GET /api/r1/approvals/by-source/{sourceTaskId}
```

## 10. 常见 HTTP 状态

- 200：成功；
- 204：Worker Claim 当前无任务；
- 400：请求参数错误；
- 404：资源不存在；
- 409：状态/Ownership 冲突；
- 401：启用 Worker Token 后 Token 不匹配。

## 浏览器用户界面

`/ui` 已提供对应的人工操作入口。Web Console 是当前推荐的人类操作界面；REST API 继续用于自动化和外部集成。参见 [Web Console](15-WEB-CONSOLE.md)。
