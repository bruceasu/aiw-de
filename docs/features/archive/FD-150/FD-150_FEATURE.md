# FD-150: 简单任务全宽列表与结束任务删除

**Status:** Complete
**Revision:** 6
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

简单任务历史挤在侧栏。失败和已停止任务被完成态限制挡在归档、删除入口之外。本机记录中有两条失败、两条已停止，且当前账本已有八条进入待删除。

## Options and decision

使用全宽列表、URL 过滤条件和独立的新建、详情页面。保留原 URL 的详情入口。既有归档和 30 天延迟删除用于所有安全结束的简单任务。

用户确认，到期后从简单任务列表和详情移除。后台保留关联记录，包括审计、模型用量和交付。可复用工作流和普通 Plan 沿用各自的清理规则。

## Solution

管理动作由后端核对归属、权限和是否还有未结束任务或租约。结束失败计划后归档，历史结果保持失败。归档后禁止重试。列表和详情显示一致的管理动作。

独立复审范围涵盖任务终态、归档权限、续接与旧详情入口。

## Scope

范围为简单任务界面、管理入口和到期可见性。沿用浏览与管理权限，保留后台记录。

追加范围包括工作流定义和执行记录的宽列表，以及 Worker 页遇到续接状态时报错的修复。

## Work items

- [x] 1.1 放开安全结束的失败和停止任务管理。
- [x] 1.2 实现任务列表、筛选、分页、独立详情与返回。
- [x] 1.3 工作流定义和执行记录使用全宽表格，保留编辑、运行和定时入口。
- [x] 1.4 修复 Worker 页的状态统计。已有四条 `CONTINUED` 进入统计的兜底异常分支。
- [x] 1.5 实现简单任务的到期移除，后台保留执行及关联记录。

## Acceptance

- 已完成、失败及已停止的任务可管理。活动任务和持有租约的任务拒绝管理。
- 管理员可在期限内撤销延迟删除，归档失败任务的重试被拒绝。
- 到期后列表和详情均不可见，旧详情地址和 API 返回 404，撤销被拒绝。简单任务不进入物理清理队列，普通 Plan 清理规则继续适用。
- 从列表打开详情再返回，保留筛选条件和页码。
- 列表和详情的删除入口共用系统确认对话框，显示所选任务和 30 天撤销说明。确认后才提交。
- 工作流定义和执行记录可从全宽表格打开。Worker 统计跳过旧续接记录，等候输入计入阻塞，交还租约中计入活跃。

## Verification

- 本地只读查库确认失败和停止任务被原完成态条件排除。
- Maven 离线执行 `-DskipTests compile` 成功，使用本地缓存并跳过资源复制。
- Maven 离线执行 `-Dtest=WorkflowTaskHistoryTest test` 通过，共 2 项。
- `node --check src/main/resources/static/ui/workflows.js` 与 `git diff --check` 通过。
- 追加 Worker 修复后的 Maven 离线 Java 编译通过。用只读 SQLite 执行更新后的 Fleet 状态查询，原四条续接记录已排除，失败任务仍保留在统计输入中。
- 更换删除确认对话框后，`node --check src/main/resources/static/ui/workflows.js` 和相关前端文件的 `git diff --check` 通过。
- 未运行浏览器验收、生产库写入、到期物理清理和服务部署。
- 原物理清理路径受临时目录、续接状态和归属外键限制。本地只读核对时待删记录尚未到期。
- 独立复审要求修复到期路径后再交付，见 [FD-150 复审报告](reviews/FD-150_review_000003.md)。
- 按用户确认实现到期移除后，Maven 离线 `-DskipTests compile` 与 `-Dtest=WorkflowTaskHistoryTest test` 通过，共 3 项。定向用例覆盖到期边界、两种详情入口、旧列表 API、撤销和物理清理排除。
- 更新删除提示后，`node --check src/main/resources/static/ui/workflows.js` 和 `git diff --check` 通过。
- 到期移除修复通过独立复审，见 [FD-150 到期移除复审报告](reviews/FD-150_review_000005.md)。

## Sources

- Issue: none

**Completed:** 2026-11-25
**Disposition reason:** 30 天到期移除简单任务，保留后台关联记录，定向测试及独立复审通过
