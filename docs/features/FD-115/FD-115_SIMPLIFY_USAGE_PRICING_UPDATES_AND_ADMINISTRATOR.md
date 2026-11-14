# FD-115: Simplify usage pricing updates and administrator-approved repair

**Status:** In Progress
**Revision:** 1
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

FUNCTION-INVENTORY: 20, 21, 22. 剩余范围尚未按已确认的简化方向完成。原有代码存在不等于本轮优化已完成。

## Options and decision

优先修改现有代码，以函数和直接数据结构完成当前需要；不引入新框架、单实现抽象、重复状态库或无用兼容层。

## Solution

逐次用量按调用时段内部价格计价，缺价补算、套餐和 API 估算分开。Python 抓取价格，Spring 启动及每日调度；修复由用户发起，管理员审阅源码和当前页面后逐次批准，保留价格发布。

Dependencies: none.

附近实现已具备每次用量、分时价格、缺价补算、套餐标记、启动／每日采集以及人工发起修复，不重复实现。

本轮仅处理两个明确重复／耦合点：

1. 删除重复的 ModelPriceBackfillScheduler，保留按价格快照推进游标的 ModelPricingBackfill，并让它遵循现有 enabled 开关。这样少一个线程和一轮重复 DB 扫描；不改计价公式、历史快照与事务边界。
2. ModelPriceObservationPublisher 仍在 Java 内识别 OpenAI 表头、DeepSeek 高低峰字段。把服务商规则转换移入各自 Python 采集模块，runner 统一补齐内部价格规则结构，Java 仅验证、发布内部规则。原页面观察材料保留用于审阅；脚本修复可以同时修页面解析与价格转换，不再要求改 Java。

管理员逐次审核候选源码与当前页面、明确批准才运行的契约保持不变；失败只报告，原价格保留。保留原异步采集线程，避免在本轮顺便改变全局 Spring 调度并发和租约恢复行为。通用调度整理归 FD-118。

验证按用户最新要求：先静态整理和审查，编译待内存恢复后补齐；不运行测试、网络、安装或真实价格采集。

## Scope

仅实施上述已确认要求；未确认选择待澄清，不自动增加权限。保留真实历史和执行结果。

## Work items

- [x] 1.1 核对附近实现，记录最小具体改法与受影响契约。
- [x] 1.2 实施已确认行为，删除因此不再需要的代码。
- [x] 1.3 静态自查差异并提交独立审查；按用户要求延后编译。

## Acceptance

缺失不等于零；旧价格不因失败丢失；分时价可追溯；失败无自动派修；候选未经批准不运行；发布价格与历史补算一致。

## Verification

独立审查：计价数据转换及后台扫描调整；保留原校验与发布事务。

已静态追踪 runner 的正常采集／候选解析两条路径、Observation 与通用价格 Rule、原缺价回填游标、审批候选执行与发布入口。新增内部价格直通／缺价不发布及 Python 标准价转换用例源码；更新原 mock 和 DTO 夹具。删除重复后台线程，既有 ModelPricingBackfill 增加原 enabled 开关。

旧观察数据缺少 priceRules 时仍可读取，但不能据此发布新价格；新采集和候选执行由 Python 模块 prices(observation) 输出内部规则。原 source 摘要、固定官方来源、管理员审核与一次批准仍保留。不运行未批准候选，不自动派发修复。

最终编译待办：用户在 FD-111 遭遇系统内存不足后明确选择“先继续静态整理，稍后再编译”。本 FD 因此未运行 Java/Python 编译、测试、服务、迁移、安装、真实网页／模型／API。静态审查通过也不能代替最终编译；补齐后才归档和交付。

## Sources

- docs/FUNCTION-INVENTORY.md, 20, 21, 22.
- 用户要求：完成剩余优化，逐个使用 fd-workflow auto，并遵守 Small Software。
