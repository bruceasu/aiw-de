# FD-094: Remove superseded Wish Pool recommendation operations and settings

**Status:** Complete
**Revision:** 5
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

FD-093 已将许愿池切换为普通任务生成统一工作流，但旧角色编辑、分析、应用推荐和准备旧计划的后端仍并存，系统还有独立推荐设置页。

## Options and decision

删除已无新入口调用的许愿池旧 API 和其应用方法，移除独立推荐设置控制器、页面与系统链接。不增加兼容跳转或包装层。其他旧 Plan 详情尚在使用的推荐服务留待其调用方一起清理，不能误删仍需读取执行记录的代码。

## Solution

GoalPlans 只保留快照和统一草稿交接，删除对旧编辑器、推荐服务与工作区服务的依赖。旧许愿池 editor/analyze/analysis/apply/prepare 接口移除；新 workflow/read 接口保留。推荐设置页删除。更新邻近使用说明与功能进展。

## Scope

不修改历史迁移与已有数据库内容，不新增适配旧 API 的代码。旧 Plan 详情和其被使用的推荐服务不在此 FD 删除，避免将清理扩为整个旧引擎重写。

## Work items

- [x] 1.1 删除许愿池旧推荐 API、方法与多余依赖。
- [x] 1.2 删除独立推荐设置页与系统入口，更新邻近文档。

## Acceptance

新许愿池完整保留 formalize/workflow/workflow-read 交接；旧 API 不再提供已确认取消的操作。源码无 GoalPlans 旧推荐调用及被删控制器引用。现有旧 Plan 详情依赖保留，历史数据不删除。

## Verification

- 仅离线编译和独立静态审查；不运行测试、模型、网络或数据库迁移。
- 2026-11-13：离线 Maven compile 通过。初始引用检查仅覆盖 src/main/java 与 src/test/java，未覆盖外置 tests，不能据此声称全部测试无旧引用；未运行测试。新 formalize/workflow/workflow-read 与旧 Plan 查看路由保留。
- 许愿池使用说明更新为直接交接当前工作流；旧历史迁移和表数据未修改。独立静态审查结果为 changes-requested；报告 [review-20261113.md](reviews/review-20261113.md)，覆盖 5055ee7 对 eff01cd，来源事件 FD-094-000002-review-requested，Reviewer codex-review-fd094-20261113。发现两项：中文说明被实际问号覆盖；tests/fd018 的 A07 仍直接引用已删除 RecommendationConsoleController，与上述引用检查记录不符。

- 首轮审查发现中文说明被 PowerShell→Python 管道编码替换为问号，已改用 UTF-8 文件补丁重写，同时修复 goals.js 两处中点字符。外置 tests/fd018 的 A07 仍引用被删设置控制器，已仅删除该退休页面的断言，其他推荐服务验收保留。JS 编译通过，未执行测试；聚焦静态复核已通过。

- 2026-11-13：独立静态复核通过（verification-passed），两项 P2 已解决，无剩余阻塞。报告 [review-20261113-recheck.md](reviews/review-20261113-recheck.md)，覆盖修复提交 66b0744，来源事件 FD-094-000004-implementation-ready，Reviewer codex-review-fd094-20261113。未运行测试、服务、网络、浏览器或模型，不宣称运行验收通过。

## Sources

- docs/FUNCTION-INVENTORY.md 第 02、06 项；FD-093。

**Completed:** 2026-11-12
