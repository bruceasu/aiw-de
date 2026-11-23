# FD-148: 简单任务检索与归档删除

**Status:** Complete
**Revision:** 3
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

简单任务历史按时间固定显示，无法快速查找旧任务，也没有完成任务的归档和删除入口。

## Options and decision

在简单任务列表提供搜索、状态筛选和分页。复用计划归档和管理员 30 天延迟删除机制。

## Solution

服务端按当前账号和视图筛选，在分页前按目标、编号和工作位置检索。管理操作核对归属和完成状态，删除同时归档并安排延迟清理，期间可撤销删除。

## Scope

范围为控制台简单任务历史。沿用后台清理规则、权限和现有工作流列表。既有任务和产物通过原有方式读取。

## Work items

- [x] 1.1 接入按归属分页检索和现有归档删除机制。
- [x] 1.2 实现任务列表、视图、筛选和操作反馈。

## Acceptance

- 按关键词和状态在当前、已归档和待删除三个视图查找自己的任务。
- 完成任务可归档。拥有管理员权限的任务所有者可安排删除、在期限内撤销。
- 活跃任务、其他账号任务和定时安排本身不受删除动作影响。

## Verification

- JDK 25、Maven 3.9.9 执行 `-o -Dmaven.repo.local=D:/.m2/repository -Dmaven.resources.skip=true -DskipTests compile` 通过。首轮命令的 PowerShell 参数引用不正确，修正后通过。
- `node --check src/main/resources/static/ui/workflows.js` 通过。
- Maven 同一离线配置执行 `-Dtest=WorkflowTaskHistoryTest test`，两条通过，覆盖查询与分页、三个视图、动作的状态和归属。
- 删除沿用后台计划清理，临时目录须由现有操作释放。未运行实际浏览器、在线服务或到期清理。工作流权限与保留规则需要独立复审。
- [独立静态复审通过](reviews/FD-148-000002-review.md)。到期物理清理仍受已有的临时目录、续接尝试状态及所有者外键约束。

## Sources

- Issue: none

**Completed:** 2026-11-23
**Disposition reason:** 检索和归档删除入口通过定向用例与独立复审，沿用现有后台清理范围
