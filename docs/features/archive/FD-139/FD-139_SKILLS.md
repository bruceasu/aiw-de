# FD-139: 按任务提供提示、Skills 和工具

**Status:** Complete
**Revision:** 5
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

Python SDK 仍可能向模型注册当前任务无法调用的文件或 Skill 工具。现有 Skill 加载已能固定版本，提供主 Skill 原文和其他 Skill 的按需目录，这部分继续复用。

## Options and decision

按已有 Broker 策略、工作目录类型、实际冻结资料投影工具。继续由原工具边界核验每次调用。保留来源读取工具，因为本次模型运行稍后可能生成大结果或压缩历史。

## Solution

独立文字目录只提供适用的 Skill 和来源读取，保持正式问答续接。普通文件任务继续提供其可用文件工具。受控任务按初始和当前策略允许范围及已有工作方法约束筛选，保留审批和路径校验。缺少 Skill 包时省去 Skill 读取工具。冻结 Skill 的名称、版本和使用说明保留原语义。

## Scope

Python OpenAI SDK 路径。App Server 备选不改变可见工具。保留原工厂配置、来源内容与调用授权，不新增工具协议。

## Work items

- [x] 1.1 根据实际任务和已有策略筛选 SDK 工具。
- [x] 1.2 核对文字和文件路径，完成 Skill 和来源读取定向检查及编译。

## Acceptance

- 文字任务可读绑定资料；独立目录不出现无关工作区工具。
- 受控 SDK 工具与冻结/当前策略一致，原授权判定仍生效。
- 绑定 Skill 原文和大输出可按原格式继续读取。

## Verification

涉及工具可见性，需独立复审。`node scripts/compile-worker-runtime.mjs` 通过；`python -m unittest test_task_materials` 的 7 条用例通过，覆盖两份策略交集、工作方法限制、Skill 资源是否存在及来源分页。对独立文字目录与其他运行时为静态核对；未调用在线模型。

独立静态审查要求修正 SDK 工具范围和非 Git 目录的 Git 工具可见性。[审查报告](reviews/FD-139-000002-review.md)

对应修正已完成。SDK 按仓库、基线和两份规则的实际可交叠资源筛选。无 Git 元数据的目录即使启用 Shell 也不提供 Git 工具。Worker 编译及 8 条定向用例通过。

独立静态复审通过，确认两处发现已修正。[复审报告](reviews/FD-139-000004-review.md)

## Sources

- [改进清单](../../../AGENT-IMPROVEMENT-BACKLOG.md)

**Completed:** 2026-11-21
