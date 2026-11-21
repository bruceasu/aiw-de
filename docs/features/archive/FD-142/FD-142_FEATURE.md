# FD-142: 核对工作流结构与角色分工

**Status:** Complete
**Revision:** 2
**Review policy:** Self
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

工作流已具备预定义判断、人工处理与结构修改。需要核准常用页面和仍在使用的图执行服务各自的职责。

## Options and decision

保留统一工作流页面及仍有调用者的图服务。首期采用 FD-141 展示角色、交接与人工返工，复用现有结构变更确认。

## Solution

常用入口 `/ui/workflows` 由 `WorkflowConsole` 和 `WorkflowRuns` 执行，`/ui/tasks` 复用同一机制处理单步。节点可指定成员、自动分配或选择通用 Worker。模型选择声明过的出口，人工节点等待明确回答。结构建议需要当前执行的版本和有效候选，结构应用要求暂停并由当前操作用户确认。

旧图页面已在 FD-104 退役。图 authority 接口仍开放，`SoftwareDelivery` 使用 `GraphPlans` 和图任务上的产物来源，`GraphRecovery` 继续恢复图执行。现有记录与对外 API 使用情况需结合部署核对，保留该服务。

## Scope

核对入口、角色和变更约束，修正文档入口描述。

## Work items

- [x] 1.1 核对两个执行路径、调用方、存储和结构确认契约。
- [x] 1.2 将现行的用户入口及仍保留的接口写入说明。

## Acceptance

验证正常出口受节点定义约束。应用结构建议有单独人工操作。旧图的实际软件交付调用和恢复链保留。

## Verification

静态核对 `WorkflowRuns.consume/decideProposal`、`WorkflowConsole` 会话检查、`GraphController`、`GraphPlans` 人工角色检查、`GraphRecovery` 调度与 `SoftwareDelivery` 图来源依赖。未查询真实数据库或部署的 API 调用。

## Sources

- [改进清单](../../../AGENT-IMPROVEMENT-BACKLOG.md)
- [图计划说明](../../../WORKFLOW-GRAPHS.md)

**Completed:** 2026-11-21
