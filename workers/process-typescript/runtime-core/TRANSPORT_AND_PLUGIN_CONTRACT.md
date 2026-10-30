# 控制平面与固定插件公开契约

这是 FD-005 独立测试准备所需的公共传输说明，整理已有 Worker Protocol v1 与查询接口。它不改变运行代码、FD 验收或测试授权。参考 [协议文档](../../../docs/current/04-WORKER-PROTOCOL-AND-WORKERS.md) 与 [运维约束](../../WORKER_OPERATIONS.md)。

## ControlPlaneClient

构造为 `new ControlPlaneClient(config, runtimeType)`。config 字段为 baseUrl（服务根 URL，不包含下方前缀）、workerId、token（可为空）、capabilities（字符串数组）、maxConcurrency、httpTimeoutMs（毫秒）。内部传输固定前缀 `/api/r1/worker-protocol/v1`；全部 POST、Accept: application/json，有 payload 时 Content-Type: application/json。非空 token 用 Bearer 头，任务 leaseToken 只进入协议请求，不落常规日志。

| 公开方法 | 路径 | 请求 JSON |
|---|---|---|
| register() | /workers/register | workerId, runtimeType, capabilities, maxConcurrency |
| workerHeartbeat() | /workers/{workerId}/heartbeat | 无正文 |
| drain() | /workers/{workerId}/drain | 无正文 |
| offline() | /workers/{workerId}/offline | 无正文 |
| claim() | /tasks/claim | workerId |
| start(task, selection?, signal?) | /tasks/{taskId}/start | workerId, leaseToken；有 selection 时附 profileId=selection.id、profileVersion=task.aiGuidance.version、selectionReason=selection.reason |
| heartbeat(task, externalSessionId) | /tasks/{taskId}/heartbeat | workerId, leaseToken, externalSessionId |
| fail(task, externalSessionId, reason?, category?) | /tasks/{taskId}/fail | workerId, leaseToken, externalSessionId, failureReason, failureCategory |
| complete(task, externalSessionId, workspaceResult, report, signal?) | /tasks/{taskId}/complete | workerId, leaseToken, externalSessionId, workspaceResult, report |

workerId/taskId 路径段作 URL 编码。claim 的 204 返回 null；其余成功响应的非空正文为 JSON，空正文返回 null。非成功状态抛出 ProtocolError，status 为 HTTP 状态，不能把错误正文当作授权或结果。请求有独立 httpTimeoutMs，传入 signal 时同时受 attempt 取消约束。客户端不自动重试。

ProtocolError 的公开构造为 `new ProtocolError(status: number)`，错误内容只含固定协议说明与状态；合成客户端可使用 `new ProtocolError(409)` 表示拒绝，不传原始服务错误正文。

register 的服务响应为 workerId、status、activeLeases、maxConcurrency；complete 为 taskId、newlyReadyTaskIds、generatedTaskIds。领取任务的公开响应：

```json
{
  "taskId": "synthetic-task", "taskType": "IMPLEMENTATION",
  "subjectType": "CHANGE", "subjectId": "synthetic-subject",
  "requiredCapabilities": ["coding"], "skillRef": null,
  "instruction": "Synthetic bounded instruction", "priority": 0,
  "attemptId": "synthetic-attempt", "attemptNo": 1,
  "leaseToken": "synthetic-lease",
  "leaseAcquiredAt": "2026-10-05T00:00:00Z",
  "leaseExpiresAt": "2026-10-05T01:00:00Z",
  "workspace": null, "inputs": [], "aiGuidance": null
}
```

workspace 非空时为 repositoryRef/baseRevision/workspaceKey/branchName；上报 workspaceResult 为 resultRevision/changedFiles/publicationRef。report 为 null 或 verdict/summary。服务端只接受当前 RUNNING 且有效所属租约的完成/失败，未承诺这些非幂等接口的重发成功。

## AiwAdapter catalog

`AiwAdapter.load(catalogPath, cwd)` 的空路径表示没有启用查询；非空路径指向工作区外的有界普通 JSON 文件（最大 64 KiB）。schema 为 aiw.worker.tool-catalog.v1，entries 最多三项，id 不重复。如下仅展示字段，不提供可部署的摘要：

```json
{
  "schema": "aiw.worker.tool-catalog.v1",
  "entries": [{
    "id": "skills-list",
    "executable": "<absolute pinned interpreter executable>",
    "executableSha256": "<64 hex SHA-256>",
    "pluginPath": "<absolute pinned plugin entry>",
    "pluginSha256": "<64 hex SHA-256>",
    "outputSchema": "aiw.skills.v1",
    "timeoutMs": 1000, "maxOutputBytes": 1024,
    "networkAllowed": false
  }]
}
```

部署者以绝对 Python 解释器和固定插件入口配置；接口没有另外的 interpreter、args 或任意 shell 字段。可执行文件和插件需是普通非链接文件，各自不超过 128 MiB；路径不能在任务 cwd 内。加载时及每次查询前重查 SHA-256。timeoutMs 是 1..60000 的整数，maxOutputBytes 是 512..24000 的整数，networkAllowed 必须为布尔。

| id | outputSchema | 固定 argv（在 executable 之后） | 额外限制 |
|---|---|---|---|
| skills-list | aiw.skills.v1 | pluginPath, list, --json | 无网络查询 |
| skills-discover | aiw.skills.v1 | pluginPath, discover, --json | 无网络查询 |
| github-get-issue | github.issue.v1 | pluginPath, --json, get-issue, repository, number | repository 固定 owner/repo；number 为正整数；networkAllowed=true 必须有主机既有授权 |

`query(id, input, signal)` 不接受任意参数扩展。缺失操作、身份变化、未授权网络、非法参数、超时、超限、非法 JSON 或 schema 不符均明确失败。解释器固定 argv 直接启动，cwd 为任务工作区。仅必要环境被保留；没有 Worker leaseToken、控制平面密钥或模型密钥，GITHUB_TOKEN 只用于已获授权的 GitHub 查询。合成测试不得调用真实 GitHub。

## 插件结果

Skills 返回对象必须包含 ok=true、action=list 或 discover、issues（字符串数组）和 skills。list 中每个 skill 包含字符串 name/description/source；discover 中每项包含字符串 name/status。issues 非空时结果 complete=false。

GitHub 返回对象要求 number 匹配请求、title 为字符串、state=open 或 closed、html_url 位于固定仓库的 https://github.com/owner/repo/issues/ 路径。

AiwAdapter 返回 `{ content, complete, data }`，data 的 schema 为 aiw.worker.query.v1，包含 operation、pluginDigest 与经验证的 result。结果是数据，不能授予网络、写入或生命周期权限。测试里的假插件仅验证实际适配边界，不能代替真实已安装插件或外部服务证据。
