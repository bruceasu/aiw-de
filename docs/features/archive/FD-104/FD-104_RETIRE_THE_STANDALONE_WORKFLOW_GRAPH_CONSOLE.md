# FD-104: Retire the standalone workflow graph console

**Status:** Complete
**Revision:** 5
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

FUNCTION-INVENTORY: 07, 28. 剩余范围尚未按已确认的简化方向完成。原有代码存在不等于本轮优化已完成。

## Options and decision

优先修改现有代码，以函数和直接数据结构完成当前需要；不引入新框架、单实现抽象、重复状态库或无用兼容层。

## Solution

移除旧独立图页面及导航，工作流图统一在工作流编辑与执行页面查看。唯一直接依赖旧图页面的旧软件开发工作台同时移除，避免遗留半条失效流程。保留仍被任务执行实际使用的数据和内部 API，不新增兼容页面。

已核对 GraphController、SoftwareDevelopmentController、模板／脚本、统一 workflows 页面、现有安全路由与测试调用。删除两处 GET 映射、四个旧页面资源、仅用于页面的注入依赖，以及安全配置中不再存在的页面路径。已有 authority API 的认证、业务操作和数据保持原有规则，由 FD-105 继续退役重复后端。

## Scope

仅实施上述已确认要求；未确认选择待澄清，不自动增加权限。保留真实历史和执行结果。

## Work items

- [x] 1.1 核对附近实现，记录最小具体改法与受影响契约。
- [x] 1.2 实施已确认行为，删除因此不再需要的代码。
- [x] 1.3 编译受影响源码、自查差异，准备交独立审查。

## Acceptance

控制台不再提供两个图入口；旧页面模板和脚本清理；统一入口、页面文案和受影响调用方一致。

## Verification

移除旧 UI 路由与对应安全配置，影响公开入口，采用独立静态审查。未执行测试、服务、数据库、浏览器或真实 API。新工作流的文件执行能力仍待 FD-108，不因此声称软件开发全部功能已迁入。

- `git diff --check` 通过。定向查找还发现配置目录的无原生 Plan 回退链接，已改为统一工作流入口，避免悬空链接。
- 离线 Maven `-DskipTests test-compile` 已通过；修正回退链接后再次编译。使用父仓库 FD-081 离线 settings、D:/.m2/repository 和低内存配置，不下载依赖。
- 仅移除不存在页面的拦截路径；authority API 路由和权限判断不变。未新增文件、抽象、依赖、兼容重定向或持久化结构。

首轮独立审查 `FD-104-000003-changes-requested` 发现当前配置参考仍将退休页面列为现行登录入口。已移除该表述，并说明统一工作流／任务入口、不提供兼容页面、历史结果未删除。修复仅文档，不重复编译；此前第二次 Maven 编译实际通过。后续复核只检查此说明与对应入口。

- 2026-11-14：独立静态审查结果为 changes-requested；报告 [review-20261114.md](reviews/review-20261114.md)，覆盖 afbcace 对 84f14b7，来源事件 FD-104-000002-review-requested，Reviewer codex-review-fd104-20261114。P2：当前配置说明仍将已删除的两个页面列为现行受保护/登录返回入口，需同范围修正文案。审查及产物均位于本 FD 工作树；未运行测试、编译、脚本、浏览器、服务、数据库、网络或模型。

- 2026-11-14：独立静态复核通过（verification-passed），P2 当前配置说明已修复，无剩余阻塞。报告 [review-recheck-20261114.md](reviews/review-recheck-20261114.md)，覆盖修复提交 6d931ec，来源事件 FD-104-000004-implementation-ready，Reviewer codex-review-fd104-20261114。仅文档复核，未运行测试、编译、脚本、浏览器、服务、数据库、网络或模型；产物仅写本 FD 工作树。

## Sources

- docs/FUNCTION-INVENTORY.md, 07, 28.
- 用户要求：完成剩余优化，逐个使用 fd-workflow auto，并遵守 Small Software。

**Completed:** 2026-11-13
