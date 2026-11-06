# Plan 执行位置与 Worker 交接

FD-074 采用全新执行契约，不兼容旧 Worker 的 Workspace assignment 和文件上传交接流程。部署时需同时更新平台与维护中的 Python、TypeScript、Go Worker。

## 执行位置

`WorkflowPlanSpec.location` 可省略。声明时平台仅记录并传递字符串，不检查目录存在、内容、链接或 Git。Worker 负责创建或解析位置；所有 Worker 必须能访问同一个共享文件系统位置。显式相对路径的解释由 Worker 的部署目录约定，建议使用各 Worker 都可访问的绝对路径。

未声明时，平台在 `aidev.plans.temporary-root`（默认 `data/plan-locations`）下创建 Plan 专用临时目录。确认或首次领取时固定位置，所有步骤、评审与重试复用它。平台只管理该临时目录的分配与最终清理，不准备仓库。

## Worker 执行策略

- 单仓库根目录且具有可用提交时，通常创建位于 `.ai/worktrees/<plan-key>/<attempt-key>/` 的专用 detached worktree。它属于该 Plan，多个并发任务各有独立 worktree。
- 原目录有未提交内容、无可用提交、Git 不可用或无法安全创建 worktree 时，保留目录并串行执行；不丢弃原有改动以强行创建隔离。
- 包含多个仓库的目录、非 Git 目录直接执行；不逐仓库自动创建 worktree。
- 各语言通过真实目录下 `.ai/locks/execution.lock` 的原子目录创建协调跨进程访问。独立 worktree 各自持锁，共享原目录没有隔离时串行。陈旧锁不自动抢占，需先确认原执行停止后协调恢复。
- 不同目录的 Plan 可直接并发。独立讨论/规划分析使用每个 attempt 的空沙箱，不接入业务目录内容。
- 受控执行仍遵循工具授权、预算、租约、只读策略和停止确认。任务正文不会自动授予网络、命令或 Git 权限。

Worker 根据任务内容处理 clone、checkout、初始化项目等工作。平台不生成 branchName，不固定 Git HEAD，不自动合并或 reset。

## 固定交接契约

领取响应提供 `planId`、`location`、`handoffProtocol: aiw.handoff.v1`。上游输入包含生产者 `taskId`、`attemptId`、报告摘要和可选的 `handoff` 引用。

Worker 在模型及其执行资源停止后，写入共享位置：

```text
.ai/handoffs/<SHA256(taskId)>/<SHA256(attemptId)>/
  report.md
  manifest.json
```

manifest 至少包含 `protocol`、`taskId`、`attemptId`、`planId`、`workingDirectory`、`completedAt`、`report`、`reportSha256`、`verdict`。`gitResult` 可为空；可扩展声明产物引用。报告以 UTF-8 原始字节独占写入，接收方同时检查 manifest 与报告摘要。修改过的源文件不上传、不自动复制到交接目录。代码留在真实工作目录中。

Worker 完成请求包含：

```json
{
  "workerId": "worker-1",
  "leaseToken": "current-token",
  "report": { "verdict": "PASS", "summary": "Completed the requested work." },
  "gitResult": null,
  "handoff": {
    "manifest": ".ai/handoffs/<task-hash>/<attempt-hash>/manifest.json",
    "sha256": "<manifest-sha256>"
  }
}
```

平台检查租约、身份、路径格式及不可变汇报，在同一个完成事务中登记引用；不打开 manifest，不检验工作区内容，不将自报摘要视为评审通过。依赖任务固定生产者 attempt 的引用，接收 Worker 验证 manifest 摘要、来源和路径，再决定如何使用工作目录及产物。多个分支的结果需由相应 Worker 显式整合，不由平台推断合并。

## 保留与清理

失败、重试、暂停及完成响应不明时保留现场。单个任务结束不删除 worktree 或 Plan 目录；进程终止不明时还要保留锁。用户目录不自动 reset、清空或删除。

取消先走停止与确认流程，平台不通过 Git 删除分支或回退文件。专用 worktree 仍由 Worker 管理，保留现场可供诊断或明确的清理任务使用。

Plan 正常完成或最终取消且执行已停止后，其 owner 可调用 `POST /api/r1/plan-editor/plans/{id}/location/cleanup` 清理平台拥有的临时目录。未确认停止的 attempt、未知状态、占用中的目录锁或未完成的其他位置引用会阻止清理。清理限定配置根目录下由平台分配的直接子目录，拒绝链接子树，先隔离目录再删除；不执行 Git 操作。隔离目录为确定性的 `.cleanup-<SHA256(planId)>`，外部 `.owner` 标记保存所属 Plan 和原始位置；删除失败或事务回滚后，重试继续处理同一隔离目录，实际删除完成后才标记 cleaned。清理不是任务完成的自动副作用。

`.ai/` 交接文件随目录清理删除；数据库保留执行摘要及引用元数据，历史引用不保证清理后仍可读取。需要长期保留的产物由 Worker 按任务要求放到持久位置。

## 验证范围

FD 记录实际编译与独立静态审查证据。无真实 Worker、共享文件系统、进程中断、数据库迁移或模型执行证据时，不声称这些路径已通过运行验证。
