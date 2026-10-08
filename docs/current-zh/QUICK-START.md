# Quick Start — Web Console + SQLite + Java Codex Worker

现在最快的体验路径改为 **浏览器优先**：启动 Control Plane 和一个 Worker 后，直接通过 Web Console 创建 Requirement、观察执行并处理审批。REST API 主要保留给自动化和故障排查。


这是最快体验当前系统的一条路径，建议使用一个可丢弃/测试用的本地 Git Repository。

## 1. 准备

需要：

- JDK 21
- Maven 3.9+
- Git
- 已安装并认证的 Codex CLI
- 一个已经 commit 的本地 Git Repo

## 2. 启动 Control Plane

PowerShell：

```powershell
New-Item -ItemType Directory -Force data | Out-Null
mvn clean verify
mvn spring-boot:run
```

默认使用 SQLite：

```text
127.0.0.1:8080
./data/aidev-r3.db
```

新终端检查：

```powershell
curl.exe http://127.0.0.1:8080/api/r8/fleet/dashboard
```

## 3. 取得 Repository Base Revision

```powershell
git -C C:\work\demo-repo status
git -C C:\work\demo-repo rev-parse HEAD
```

保存完整 SHA。

## 4. 编译并启动 Java Worker

进入：

```text
workers/codex-java
```

PowerShell：

```powershell
Remove-Item -Recurse -Force out -ErrorAction SilentlyContinue
javac --release 21 -d out src/main/java/me/asu/aidev/worker/example/CodexJavaWorker.java

$env:AIW_CONTROL_PLANE_URL="http://127.0.0.1:8080"
$env:AIW_WORKER_ID="local-java-1"
$env:AIW_WORKER_MAX_CONCURRENCY="2"
$env:AIW_WORKER_CAPABILITIES="requirement-analysis,solution-architecture,implementation-planning,coding,integration,code-review,verification,integration-verification,repair,java,maven"
$env:AIW_REPOSITORY_REF="core"
$env:AIW_REPOSITORY_ROOT="C:\work\demo-repo"
$env:AIW_WORKTREE_ROOT="C:\work\.aidev-worktrees"
$env:AIW_CODEX_PATH="codex"

java -cp out me.asu.aidev.worker.example.CodexJavaWorker
```

单机 Demo 可以不设置 `AIW_GIT_PUBLISH_REMOTE`；跨机器 Worker 应配置共同 Git remote。

### 可选：使用 R10 流程专职 Worker

上面的 Java Worker 适合单进程演示。若要让 R2 角色说明在 R9 Pull/Lease 协议下运行，可改用 [TypeScript 流程 Worker](../../workers/process-typescript/README.md)（Node.js 22+）、[Go 流程 Worker](../../workers/process-go/README.md)（Go 1.23+）或 [Python 流程 Worker](../../workers/process-python/README.md)（Python 3.11+）。每个角色使用独立进程，所有进程配置同一个 Control Plane 地址、Repository Ref、Repository Root 和所需技术能力。

Go 版本在 `workers/process-go` 下运行 `go test ./...`、`go build -o process-worker .`，然后为每个角色分别设置 `AIW_PROCESS_PROFILE` 并启动。完整流程至少需要需求分析、架构、规划、编码、代码审查、验证和修复角色；可能产生集成任务时还需集成执行和集成验证角色。Python 版本使用 `scripts/run-role.ps1` 或 `scripts/run-role.sh`，分别启动 `requirements`、`architect`、`planner`、`coder`、`reviewer`、`verifier`、`repairer`，按需增加 `integrator`。具体命令见对应 Worker README。

角色能力和 `repo:<AIW_REPOSITORY_REF>` 会自动注册；`AIW_WORKER_CAPABILITIES` 只填写附加技术能力（例如 `java,maven`）。并行或复制部署时给每个进程不同的 `AIW_WORKER_ID`。不要启动尚无 R9 TaskType 对应的 release-readiness 或 orchestrator 配置。

TypeScript 版本在 `workers/process-typescript` 下运行 `npm ci --omit=optional` 和 `npm run build`，然后为每个角色显式选择后端，例如 `./scripts/run-role.ps1 coder codex-sdk` 或 `sh scripts/run-role.sh reviewer openai-agents`。Codex 后端需要已认证 CLI（从 `PATH` 或 `AIW_CODEX_PATH` 查找）；Agents 后端需要 `OPENAI_API_KEY`。旧 API 包及启动路径已删除。完整配置见 [TypeScript 指南](../../workers/process-typescript/README.md)。

## 5. 提交 Requirement

PowerShell 建议先建立 JSON：

```powershell
$body = @{
  requestId = "quickstart-001"
  requirement = "Add a simple health endpoint and focused tests. Keep the change minimal and do not break existing behavior."
  repositoryRef = "core"
  baseRevision = "<BASE_SHA>"
  technicalCapabilities = @("java", "maven")
  priority = "NORMAL"
  maxParallelism = 2
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri http://127.0.0.1:8080/api/r8/requirements `
  -ContentType "application/json" `
  -Body $body
```

返回：

```json
{
  "runId":"AR-quickstart-001",
  "requirementTaskId":"AR-quickstart-001~requirement"
}
```

## 6. 看进度

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/r8/runs/AR-quickstart-001/dashboard
Invoke-RestMethod http://127.0.0.1:8080/api/r8/runs/AR-quickstart-001/timeline
Invoke-RestMethod http://127.0.0.1:8080/api/r8/fleet/dashboard
```

预期流程：

```text
Requirement -> Architecture -> Planning
-> Implementation(s) -> optional Integration
-> Review + Verification -> optional Repair
-> Engineering Gate -> Approval
```

## 7. 默认 Manual Approval

默认：

```text
AIW_APPROVAL_POLICY=MANUAL
```

所以工程通过后 Run 会停在 `ENGINEERING_PASSED`。

先查询 Run，取 `terminalSourceTaskId`：

```powershell
$run = Invoke-RestMethod http://127.0.0.1:8080/api/r8/runs/AR-quickstart-001
$run.terminalSourceTaskId
```

创建 Approval：

```powershell
$approval = Invoke-RestMethod `
  -Method Post `
  -Uri http://127.0.0.1:8080/api/r7/approvals `
  -ContentType "application/json" `
  -Body (@{
    sourceTaskId = $run.terminalSourceTaskId
    requestedBy = "human:quickstart"
    channel = "API"
  } | ConvertTo-Json)
```

批准：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://127.0.0.1:8080/api/r7/approvals/$($approval.approvalRequestId)/decision" `
  -ContentType "application/json" `
  -Body (@{
    decision = "APPROVE"
    decidedBy = "human:quickstart"
    note = "Quick Start approval"
  } | ConvertTo-Json)
```

Run 最终应进入 `SUCCEEDED`。

如果希望自动产生 ApprovalRequest，需要在 Run 开始前设置：

```text
AIW_APPROVAL_POLICY=REQUIRED
```

## 8. 如果不执行

看 Fleet Dashboard 中的：

```text
unmatchedReady
capacityBlockedReady
admissionBlockedReady
fleetAdmissionBlockedReady
policyBlockedReady
```

详见 [故障排查与恢复](11-TROUBLESHOOTING-AND-RECOVERY.md)。


## 使用 Web Console

打开：

```text
http://127.0.0.1:8080/ui
```

现在可以直接在浏览器中：

1. 选择 **New Run**，填写 Requirement、Repository Ref、不可变 Base SHA、Capabilities、Priority、Run 并发上限；
2. 观察 Requirement → Architecture → Planning → Implementation/Integration → Review/Verification → Gate；
3. Pause / Resume / Recover / Cancel；
4. 修改 Run/Fleet `maxParallelism`；
5. Drain / Offline Worker；
6. 创建并处理 Approval。

默认 `AIW_APPROVAL_POLICY=MANUAL` 时，Engineering Gate PASSED 后可以直接在 Run Detail 中点击 **Request Approval**。

可选启用 Web Console 登录：

```text
AIW_WEB_CONSOLE_AUTH_ENABLED=true
AIW_WEB_CONSOLE_USERNAME=admin
AIW_WEB_CONSOLE_PASSWORD=<strong secret>
AIW_WEB_CONSOLE_ROLE=ADMIN
```

注意：该登录目前只保护 `/ui/**`。Management REST API 仍保持原有信任边界，因此 Control Plane 仍应绑定 localhost 或放在受保护内网。
