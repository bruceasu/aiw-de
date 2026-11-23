# FD-147: 重写 Python Worker 回合与日常工作流推进

**Status:** Complete
**Revision:** 3
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

用户反馈 Worker 执行和 Control Plane 推进效果差。Python Worker 的模型回合与工具派发由 SDK Runner 管理，宿主在最终回复后另外判断校验、纠正和人工输入。日常工作流从事件历史拼接交接摘录，紧邻上一步的正式交接主要依赖领取时的交付引用。

## Options and decision

参考 Pi 的完整模型回合、工具批次和下一轮准备边界，以及 Hello Agents 的单 Agent 循环与显式节点转移。采用 Python 宿主循环，沿用现有 SDK 模型适配器与工具声明。Control Plane 以正式结果和声明出口推进，优先传递直接前序的任务编号与有界摘要，完整报告沿用既有交付引用。

## Solution

Worker 收到并验证完整响应后才派发工具，按调用顺序保存工具结果。报告校验、人工补充和等待都在回合边界处理。取消时等待已经开始的工具操作收尾。Plane 在事务内冻结下一节点输入，沿用既有任务依赖、交付与租约。

## Scope

已确认首版覆盖 Python SDK Worker 与 WorkflowRuns 日常工作流，沿用 Worker Protocol v1、已有 schema、审批、恢复和手动重试约定。其他 Worker 和工程图调度不在本次范围。

## Work items

- [x] 1.1 实现 Python 宿主回合、批次工具、候选报告及人工输入。
- [x] 1.2 实现日常工作流的有界交接与下一节点派发。

## Acceptance

- 文字、工具、审批和续接继续使用原有入口，工具结果通过原调用 ID 回传。
- 不完整模型响应或租约取消停止进入下一回合。工具收尾完成后才结束执行。
- 下游得到当前目标、人工输入和紧邻上一步的正式结果摘要，能够沿原有引用读取完整结果。
- 非法出口、非正式结果与待决人工输入均不能触发下一节点。

## Verification

- Python 内存编译通过，覆盖 openai_api_runtime.py、guarded_preflight.py、guarded_openai_tools.py。
- JDK 25 执行 Maven 3.9.9 的 `-o -Dmaven.repo.local=D:/.m2/repository -DskipTests compile` 通过。
- 在 workers/process-python 执行 `python -m unittest test_api_connection`。首次暴露 SDK 的 ToolContext 导入位置差异，修复后 11 条通过，覆盖完整响应、取消等待及工具结果排序。
- 跨模块改动需要独立复审，重点为模型协议、取消、人工续接和任务事务。Java 的断言、真实模型、实际 Plane/Worker 联调和耗时对照尚未运行。
- [独立静态复审通过](reviews/FD-147-000002-review.md)。已核对工具超时和取消幂等修正。

## Sources

- Issue: none
- [Pi agent-loop.ts](../../../../../pi/packages/agent/src/agent-loop.ts)
- [HelloAgents 1.0.1 SimpleAgent](../../../../../HelloAgents-main/hello_agents/agents/simple_agent.py)
- [HelloAgents 1.0.1 回合执行](../../../../../HelloAgents-main/hello_agents/core/runtime.py)

**Completed:** 2026-11-23
**Disposition reason:** 独立静态复审通过，Python 与 Java 编译及前版离线回归已记录
