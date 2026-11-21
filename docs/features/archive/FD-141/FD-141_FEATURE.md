# FD-141: 可复用的实现与验证工作流

**Status:** Complete
**Revision:** 2
**Review policy:** Self
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

用户需要一个可复制的两角色流程，直观看到验证依赖哪份交付，以及验收有问题时如何处理。

## Options and decision

复用工作流页面现有的示例、成员选择、正式交接和人工问答。用一份三字段 JSON 作为可独立核对的文字产物。

## Solution

从工作流的「实现与验证」载入示例，保存后可重复执行或另存副本。可分别为生成和验证步骤指定成员。验证读取最近一份生成步骤的正式报告，逐字段核对输入。验收不通过时展示具体原因，请用户选择修正后再运行或结束本次工作。

## Scope

页面预设使用原有 TASK、DECISION、ASK 和 END 节点。继续使用现有工具权限和调度。样例产物走正式文字交接。

## Work items

- [x] 1.1 添加可编辑并保存的示例，预填固定输入。
- [x] 1.2 在验证步骤明确产物、验收项及失败出口。

## Acceptance

- 提交正确标题、语言和来源后，验证可选择 accept。
- 缺少交接或出现任何错项时，验证说明具体问题并进入人工决定。选择重做才回到生成步骤。
- 单 Worker 的对照使用同一输入、模型和推理强度，要求在一次简单任务内先生成再核对。比较正确性、耗时和用量，实测结果独立记录。

## Verification

- 静态核对前序任务 ID、结算后正式交接和 DECISION 的声明出口校验。人工 ASK 会先停在等待用户状态。
- `node --check src/main/resources/static/ui/workflows.js` 与 `git diff --check` 通过。
- 浏览器和实际模型对照待运行。

## Sources

- [改进清单](../../../AGENT-IMPROVEMENT-BACKLOG.md)
- [对照规范](../../../WORKER-QUALITY.md)

**Completed:** 2026-11-21
