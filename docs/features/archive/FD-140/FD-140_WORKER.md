# FD-140: 固定 Worker 质量对照场景与证据

**Status:** Complete
**Revision:** 2
**Review policy:** Self
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

Worker 的等待、模型表现和产物质量需要用固定输入逐项对照。现有回归用例能验证平台规则，真实模型记录还需要从已有任务详情和账本取得。

## Options and decision

复用三个已有的离线场景，提供一个标准库脚本汇总结果。真实模型对照采用同一组明确的输入与验收，不为基线接入新服务。

## Solution

记录文字任务绕过共享锁、文件批量编辑及必需检查、确认停止后重试。输出场景、结果和本地检查用时。未运行的模型用量及费用用空值，不能与零混淆。文档说明真实对照如何把排队、准备、模型执行和产物验收分开。

## Scope

质量验证与证据，不改产品状态或调用接口。离线脚本只运行固定命令和测试临时数据。真实对照须使用实际可观察数据。

## Work items

- [x] 1.1 固定场景，汇总现有定向用例和实际结果。
- [x] 1.2 记录真实对照的输入、指标与判定边界。

## Acceptance

- 三类场景有可复跑用例，退出失败可辨认，不误作模型失败或性能实测。
- 未观测 Token、模型调用次数、费用及排队用时保留未知。

## Verification

- 固定脚本的文字开工及文件交付场景通过。Windows Maven 启动路径修正后，离线 Maven 报告显示中断恢复用例 1 条通过。
- 核对了退出状态及用量字段。真实 Plane 排队和模型质量、次数、Token、费用仍待实测。

## Sources

- [改进清单](../../../AGENT-IMPROVEMENT-BACKLOG.md)
- [Worker 质量对照](../../../WORKER-QUALITY.md)

**Completed:** 2026-11-21
