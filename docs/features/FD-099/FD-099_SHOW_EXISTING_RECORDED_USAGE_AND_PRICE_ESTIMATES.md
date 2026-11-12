# FD-099: Show existing recorded usage and price estimates in unified execution details

**Status:** In Progress
**Revision:** 1
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

统一执行详情没有资源用量，用户必须另找调用记录，且容易把模型调用次数当轮数、未知价格当零费用。

## Options and decision

复用现有 ModelUsageView 账本及估算汇总，增加省略明细的摘要读取方式；不创建第二个账本、计费机制或额外定时任务。

## Solution

WorkflowRuns.view 在原 actor 归属校验后，使用当前 Owner、Plan ID 读取已记录汇总。保留 ModelUsageView 原 Owner/actor、Plan/task 过滤和按币种估算；摘要不查询最近 100 条明细。用量区独立刷新，不重建节点时间线；直达原调用记录页并携带 Plan 过滤。

## Scope

仅展示既有账本，不更改计量、计价、预算或权限。调用数与轮数不同；缓存包含在输入中；已知费用按币种分别显示，不兑换、不把无价格/未知用量当零。运行时和秘书资源接入等其他优化不在本项。按用户最新要求，待本项编译完成后与 FD-098 一起独立审查。

## Work items

- [x] 1.1 原账本新增省略调用明细的汇总方式，并接入执行详情。
- [x] 1.2 显示已知值、缺失计数、币种与估算范围，独立刷新和正确深链接。
- [x] 1.3 编译、静态自检，准备与 FD-098 最终统一审查；审查前不归档。

## Acceptance

无法读取他人的执行及账本。缺失用量显示未记录；部分缺失显示调用计数。不同币种不相加。重试/摘要调用计入已记录汇总；没有记录不声称没有执行。切换两条汇总相同的执行仍更新正确详情链接；语言变化刷新文案。既有权限用例增加摘要断言，不运行测试。

## Verification

- 涉及跨模块只读资源投影，Independent；最后统一审查。计划离线 Java test-compile 与 JS node --check；不执行测试、浏览器、迁移、真实 API 或模型。
- 2026-11-13：离线 Maven `-DskipTests test-compile`（FD-081 settings、D:/.m2/repository）及 `node --check src/main/resources/static/ui/workflows.js` 通过，仅编译生产／测试源。
- `git diff --check` 通过。静态追踪当前人类 Owner、原 ModelUsageView actor/owner/Plan 查询条件及调用记录页实际路由；摘要与原明细使用同一过滤，不新增价格查询或计算。
- 既有用量汇总和他人数据隔离用例增加摘要断言，只编译、不执行。未运行测试、浏览器、迁移、服务或真实 API。

## Sources

- docs/FUNCTION-INVENTORY.md 第 13、20、27 项；FD-088 原用量账本与 ModelUsageView。
