# FD-095: Show existing Worker activity in unified execution details

**Status:** In Progress
**Revision:** 1
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

统一执行详情只有节点级结果，执行中模型与工具活动被既有元数据白名单过滤，用户难以知道 Worker 正在做什么。

## Options and decision

复用已有 execution_run_session/event 和 ExecutionRuns.read 的归属校验，只补足安全元数据事件与统一页面投影。不新建监控系统、日志库或模型循环。

## Solution

转发模型请求／终态、实际模型与推理强度、工具提议与 SDK 活动名称。严格不转发参数、模型正文、路径、凭据或原始异常。统一详情/API 展示最近 64 条活动、最后联系与连接滞后状态；已结束执行可查看最近任务活动。普通节点时间线与输入框不因心跳更新而重建。

## Scope

本次为元数据与当前任务说明的实时可见性，不宣称提供完整模型交互原文或模型内部推理。完整费用汇总、成员团队、知识库与其他功能继续按清单推进。

## Work items

- [x] 1.1 复用原通道转发有界、安全的模型和工具元数据。
- [x] 1.2 执行详情/API 展示最近活动与连接状态，不干扰编辑和用户输入。

## Acceptance

未记录或未连接时明确展示，不从心跳猜测任务已停止。已结束任务仍能查看最近执行活动，权限沿用原归属校验。新增事件不会因字段不匹配导致整个原生通道被拒绝；不泄漏模型正文或工具参数。

## Verification

- 仅离线 Java/Python/JS 编译与独立静态审查，不运行服务、测试或模型。
- 2026-11-13：离线 Maven compile、Python 内置 compile（run_channel.py/openai_api_runtime.py）和 node --check workflows.js 通过。
- 工具提议仅投影工具名称和校验状态，去除原 callId/参数；SDK 活动名称用独立安全字段，模型终态状态枚举与双方白名单同步。现有 metadata 大小、来源及摘要校验继续保留。
- 新详情沿用 ExecutionRuns.read 归属校验，最近 64 条活动独立刷新；已结束记录不因心跳过期误报仍需确认停止。独立静态审查待进行，未运行浏览器或真实 SDK。

## Sources

- docs/FUNCTION-INVENTORY.md 第 13、27 项；FD-079、FD-091、FD-092。
