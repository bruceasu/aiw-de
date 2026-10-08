# FD-006 工作树交接收据阻塞反馈

<!-- aiw-data: FD-006-blocker-receipt-worktree-r1.json -->

**阶段 / 角色：** Worker 实施交接 / Worker  
**来源事件：** `FD-006-000008-work-requested`  
**状态：** 已解决，2026-10-07 13:12 UTC  
**需要人类决定：** 无

## 现象与原因

在 FD-006 隔离工作树执行 `aiw fd emit ... implementation-ready --source-event FD-006-000008-work-requested` 返回 `--source-event does not match the latest handoff`。检查发现父工作区 `.ai/fd/FD-006/events` 有已认领的真实事件，但新工作树没有这组本地收据，因此 CLI 在工作树中看不到来源事件。

## 恢复经过

首次 emit 失败，没有产生 Tester 事件。随后把父工作区真实收据复制到工作树；首次复制落在多一层的目录中，已在核对目标位于 FD-006 工作树内后移到正确位置。来源事件文件在两处的哈希一致。再次 emit 成功，产生 `FD-006-000009-implementation-ready`，当前等待 Tester。没有手改事件内容或伪造收据。

## 后续风险与可复用改进

交接事件当前同时存在父工作区和 FD 工作树的本地 `.ai` 目录中。后续 Tester/Reviewer 在工作树继续写收据；合并前需要核对并同步真实事件到父工作区，不能让父工作区使用过期交接链。

建议让 `aiw wt add` 自动继承该 FD 的现有事件收据，或在工作树操作指南明确写出安全同步步骤。仓库中未找到本技能提及的 `BLOCKER_FEEDBACK_TEMPLATE.md` / `.json`，应补齐模板。
