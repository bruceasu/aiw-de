# FD-096: Make price collection retry and repair explicit user actions

**Status:** In Progress
**Revision:** 1
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

价格来源结构变化后，定时采集会自动创建修复 Worker，与用户已确认的“失败只报告，由用户决定重做或修复”不符。

## Options and decision

保留现有采集与候选审批流程，只取消隐式派发并增加明确操作。不新增调度系统、数据库或修复框架。

## Solution

采集失败只记录状态与原因。模型调用记录页按服务商提供重新采集、失败时启动修复的按钮。新增现有人类 Owner 边界内的 price-action API。手动重新采集跳过五分钟定时去重，但遵守现有采集租约；后台执行采集，按钮收到排队状态，不是假称采集完成。

## Scope

当前修复准备仍依赖旧流程的历史成功页面、同一脚本与重复结构变化；条件不足明确提示，不假称已派发。去除这些历史前置条件及整体简化修复材料另行实施。管理员审核源码、对应页面并批准运行的规则不变；启动和每日价格采集不变，不触发已有任务的取消。

## Work items

- [x] 1.1 移除结构变化时的自动修复派发，提供显式重做／修复入口。
- [x] 1.2 页面展示失败原因、明确操作回执和中英日文案。
- [x] 1.3 编译并记录静态证据，提交独立审查。

## Acceptance

定时失败不创建修复任务。未认证或代理身份不能调用人类操作入口。连续点击采集不能抢占有效租约。修复条件不足时告知原因；符合现有条件才返回任务 ID。队列受现有租约和固定服务商范围约束。

## Verification

- 跨采集并发与人类操作接口，采用独立静态审查。
- 2026-11-13：离线 Maven compile（FD-081 settings、D:/.m2/repository、skipTests）及 node --check model-usage.js 通过。
- 静态追踪 currentOwner 拒绝未认证／代理身份；claimManual 的同一数据库锁保留有效租约排他性；材料／派发仍使用原校验与任务去重。
- 未运行测试、服务、数据库迁移、浏览器或真实网络采集。未处理旧历史修复前置条件，不将第 22 项标记全面完成。

## Sources

- docs/FUNCTION-INVENTORY.md 第 21、22 项；用户明确失败后自行决定重做或修复。
