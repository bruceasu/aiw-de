# FD-074 独立审查 R1

- **结论：CHANGES_REQUESTED**
- Reviewer session：`fd074-reviewer-c22ad21-20261106`
- Source event：`FD-074-000002-review-requested`
- FD revision：2；Review policy：Independent；Evidence policy：Markdown。
- 实现范围：`c22ad21^..c22ad21`。本轮未修改产品代码。

## Findings

### R1 — P1：Python 所有新执行在准备目录前发生 ImportError

位置：`workers/process-python/plan_workspace.py:102`。

`PlanWorkspace.materialize` 执行 `from worker_runtime import current_attempt, runtime_git`，但 `worker_runtime.py:232` 只定义 `git`。`runtime_git` 是 `process_worker.py:20` 导入时创建的局部别名，不是 `worker_runtime` 的导出。因此所有走入新准备路径的 Python 任务都会在模型执行前失败，包括无 Git 编码任务，违反 Acceptance 1/3。

最小修正：从正确模块导入 `git as runtime_git`，保持现有 attempt、进程停止与 Git 权限行为。

### R2 — P1：Go 仍要求 DELIVERY 必须生成 Git 结果

位置：`workers/process-go/main.go:404`。

完成前保留了 `ResultKind == "DELIVERY" && (result == nil || !validResultRevision(...))` 检查。非 Git、无提交、目录包含多个仓库或未能创建 worktree 时，`GitWorkspace.finalize` 合法返回 nil，但上述检查必然抛出 `WORKSPACE_RESULT_INVALID`。因此这些编码任务即使已成功修改文件，也无法汇报完成，违反新契约的 Git 可选规则和 Acceptance 1/3。

最小修正：仅在 Worker 实际提供 Git 结果时校验其格式；报告与交接引用仍须存在。

### R3 — P1：Windows 上 Python 多行报告的摘要与实际文件字节不一致

位置：`workers/process-python/plan_workspace.py:178`、`:182`。

`report.md` 使用默认文本换行写入，而 `reportSha256` 根据原始 `report["summary"].encode("utf-8")` 计算。在本仓库的 Windows 主机上，文本输出将 LF 转换为 CRLF；接收 Worker 却按实际文件字节验证 SHA-256（同文件第 71 行）。包含换行的报告会使后续步骤报 `HANDOFF_REPORT_HASH_MISMATCH`，跨语言接收也同样失败，违反 Acceptance 4。

最小修正：使用显式 UTF-8 字节独占写入，或禁用换行转换，并对实际写入的字节计算摘要。保持交接文件不可覆盖。

### R4 — P2：临时目录清理失败后，重试会错误标记已清理并遗留无法定位的隔离目录

位置：`src/main/java/me/asu/aidev/workflow/application/PlanLocations.java:110`、`:111`、`:122`。

清理先将原目录移动到随机 `.cleanup-<UUID>`，随后递归删除。如果删除因占用或 IO 错误失败，数据库事务回滚，分配记录仍指向旧目录，隔离目录路径没有持久记录。再次调用 cleanup 时旧目录不存在，第 88 行分支跳过删除，并在第 122 行写入 `cleaned_at_epoch_ms`，但先前隔离目录和其中的报告/代码仍遗留。此时 API 与归档门禁均把未完成清理当作成功，且重启后缺少所属 Plan 的恢复坐标。

最小修正：持久保存或确定性定位清理中的隔离目录，确保重试继续处理同一资源；也可以在安全条件下恢复原路径。只有实际完成删除后才标记 cleaned。

### R5 — P2：交付证据未进入 FD，且新中文文档已损坏

位置：`docs/features/FD-074/FD-074_WORKER_OWNED_PLAN_LOCATIONS_AND_SHARED_HANDOFFS.md:35`、`:57`；`docs/current-zh/TASK-OUTPUTS.md:1`；`workers/WORKER_RUNTIME_CONTRACT.md:1`；`workers/README.md:1`；`docs/current-zh/FLEXIBLE-WORKFLOW-PLANS.md:1`。

FD 的 Verification 仍写“编译和独立审查尚未执行”，没有主会话确认已实际执行的编译命令、失败修复/重试和最终通过结果；Work Item 1.5 却已把独立审查与本地交付归档勾为完成。新重写的 TASK-OUTPUTS 正文及多处契约前缀是字面问号，无法传达新行为。主会话已确认编译确实执行通过，本 finding 针对记录遗漏与文档损坏，不将其等同于编译未执行。

最小修正：使用可靠 UTF-8 写入修复文字，补齐 FD 的真实命令/结果/未运行检查；将实现 Work Item 与尚未完成的审查、交付状态准确区分。

## 已审查的静态证据

- Plan location 分配、复用、owner 清理、目录锁及隔离删除路径。
- V27 的 location/handoff 存储、有效租约校验、生产者 attempt 路径绑定，以及领取/完成事务。
- Python、TypeScript、Go 的准备、Git 可选结果、共享锁、报告与 manifest、后继输入读取。
- 计划确认、动态配置/受控执行、原 Git delivery 退役、控制台 location 编辑与保存路径。
- FD、稳定规范、相邻文档和实际提交范围。

执行命令包括 `git -c core.safecrlf=false status --short`、`git -c core.safecrlf=false diff c22ad21^ c22ad21 --stat`、定向 `Get-Content`/`rg`、`aiw fd show FD-074`、`aiw fd claim FD-074 FD-074-000002-review-requested --session fd074-reviewer-c22ad21-20261106` 和 `aiw fd emit --help`。

## 验证范围与剩余风险

本轮只做静态审查，没有运行测试、编译、真实 Worker、模型、浏览器、迁移、共享文件系统并发或进程中断验证。主会话提供的编译结果是语法/编译证据，不能证明上述运行路径通过。未强加额外测试门槛；R1–R4 均由源码可直接确定的执行或恢复缺陷，修复后需针对这些位置与受影响调用链进行一次有限复查。
