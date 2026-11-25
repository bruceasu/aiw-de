# FD-151: 按经理与秘书整理 Agent 权限

**Status:** Complete
**Revision:** 3
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

现有 Agent 权限页混合配对、过程启停、代理授权、员工执行设置和对账。用户要求按经理与秘书的职责组织页面。

## Options and decision

主页面用「经理权限」与「秘书授权」两个页签。连接和启停由 Agent 管理负责，保留执行设置与审计直达入口。

## Solution

复用现有代理授权、范围、次数和期限。经理是 Control Plane 内置角色。经理区显示自动故障诊断的生效状态与管理入口。部署设置控制自动诊断。既有计划委托和待核对执行的深链接继续可用。

## Scope

当前实现包括页面、外部 Agent 启停入口和自动故障诊断的独立配置。其他管理写入继续由授权用户操作。沿用现有秘书身份、授权和凭据校验。

## Work items

- [x] 1.1 分开显示经理权限与秘书授权，保留计划委托和执行核对入口。
- [x] 1.2 将已配对代理的进程启停移到 Agent 管理。
- [x] 1.3 将经理自动故障诊断设为独立的系统配置。

## Acceptance

默认显示经理页签。秘书页签可使用既有授权和计划委托。Agent 管理负责批准、启停和撤销凭据。既有执行设置、历史及停止核对链接仍能打开。禁用自动诊断后，定时轮询跳过诊断派发，现有人工诊断入口继续按原权限运行。

## Verification

- 离线 Maven `-DskipTests compile` 通过，使用本地依赖缓存并跳过资源复制。
- `node --check` 检查 `authority.js` 和 `agents.js`，均通过。
- `git diff --check` 通过。用 Python HTMLParser 静态核对两个页签、五个视图和片段链接；页面 ID 无重复。
- 浏览器、运行中的诊断开关和 Agent 进程启停接口尚未实测。未变更部署。

独立复审覆盖定时诊断的权限边界、原代理启停接口和授权页面路由。

- [独立静态复审通过](reviews/FD-151-000002-review.md)。

## Sources

- Issue: none

**Completed:** 2026-11-25
**Disposition reason:** 经理与秘书权限入口分离，原有授权校验保留，编译及独立复审通过
