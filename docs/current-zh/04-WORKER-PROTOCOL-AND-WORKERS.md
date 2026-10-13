# Worker Protocol 与参考 Worker

## 1. 目标

Worker 是外部 AI 员工，不直接访问数据库。只要实现 Worker Protocol v1，就可以使用任意语言或 AI Runtime。

Base path：

```text
/api/worker-protocol/v1
```

## 2. 生命周期

```text
register -> heartbeat -> claim -> start -> heartbeat -> complete/fail
                     shutdown: drain -> offline
```

## 3. 主要 API

注册：

```text
POST /workers/register
```

```json
{
  "workerId":"codex-java-1",
  "runtimeType":"codex-java",
  "capabilities":["java","maven","coding","repo:core"],
  "maxConcurrency":2
}
```

Claim：

```text
POST /tasks/claim
{"workerId":"codex-java-1"}
```

有任务返回 200，无兼容任务返回 204。

Start/Heartbeat：

```text
POST /tasks/{taskId}/start
POST /tasks/{taskId}/heartbeat
```

Complete 会提交 WorkspaceResult 和可选 CompletionReport；Fail 会提交 failureReason。

## 4. Worker Protocol Token

设置：

```text
AIW_WORKER_TOKEN=<shared-secret>
```

以后所有 Worker Protocol 请求需要：

```text
Authorization: Bearer <shared-secret>
```

不配置时本地模式允许无 Token。

## 5. Capability

角色 Capability：

```text
requirement-analysis
solution-architecture
implementation-planning
coding
integration
code-review
verification
integration-verification
repair
```

Repository Task 自动增加：

```text
repo:<repositoryRef>
```

还可以附加 `java`、`maven`、`sql` 等技术 Capability。

## 6. 五种参考 Worker

| 目录 | 语言 | AI Runtime |
|---|---|---|
| `codex-python` | Python | Codex Python SDK |
| `codex-typescript` | TypeScript | `@openai/codex-sdk` |
| `codex-javascript` | JavaScript | `@openai/codex-sdk` |
| `codex-go` | Go | `codex exec --json` |
| `codex-java` | Java 21 | `codex exec --json` |

Java/Go 版本适合最小依赖场景。

## 7. Repository Task 约定

Worker 收到 Workspace Assignment 后应：

1. 校验 repositoryRef；
2. Fetch 上游 publicationRef；
3. 校验 immutable baseRevision；
4. 创建 worktree/branch；
5. 在 worktree 中执行 AI；
6. Commit 未提交修改；
7. 计算 base..result changedFiles；
8. 返回只包含本地 resultRevision 和 changedFiles 的 WorkspaceResult；
9. 清理本地 worktree。

Control Plane 永远不保存 Worker 机器上的实际目录路径。

## 8. 主要 Worker 环境变量

```text
AIW_CONTROL_PLANE_URL
AIW_WORKER_ID
AIW_WORKER_TOKEN
AIW_WORKER_CAPABILITIES
AIW_WORKER_MAX_CONCURRENCY
AIW_REPOSITORY_REF
AIW_REPOSITORY_ROOT
AIW_WORKTREE_ROOT
AIW_CODEX_PATH
AIW_CODEX_MODEL
AIW_CODEX_SANDBOX
AIW_CODEX_APPROVAL_POLICY
AIW_CODEX_REASONING_EFFORT
AIW_CODEX_NETWORK_ACCESS
```

Python SDK Worker 还使用 `AIW_CODEX_APPROVAL_MODE`。轮询/Heartbeat 的时间变量在不同示例中分别使用毫秒或秒，部署时以对应 Worker README/源码为准。
