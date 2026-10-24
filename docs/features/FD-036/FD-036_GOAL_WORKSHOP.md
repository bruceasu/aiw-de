# FD-036: Goal Workshop 列表删除与已删除目标恢复入口

**Status:** Open
**Revision:** 3
**Priority:** Medium
**Evidence policy:** Dual

## Problem

用户反馈 Goal Workshop 缺少删除功能。静态事实：详情已有删除/恢复按钮，GoalController 和 GoalDiscussionService 已提供按当前 actor 与 revision 的可恢复删除，goal_draft.deleted/epoch 保留历史并使旧讨论回复失效；列表仅有打开按钮，删除和恢复缺少清晰列表入口。未用浏览器复现。

## Options and decision

- 新增永久删除、级联删除附件/正式版本/执行计划：超出用户提出的入口问题，破坏已有可恢复语义，不采用。
- 沿用现有删除/恢复协议，在列表提供直接入口，并区分当前目标与已删除目标：采用。无需 API、权限、持久化、迁移或执行计划语义变化，无需独立设计审阅；最终独立实现审阅仍必需。

## Solution

默认显示当前目标，每条记录旁有“删除”；“已删除”入口显示可恢复记录，每条有“恢复”。两个视图显示本次接口返回集合的数量（现有 API 最多 100 条），提供刷新和明确空状态。详情保留删除/恢复操作，列表和详情使用同一处理函数与原 revision。

删除前明确目标名称并确认：可恢复，旧待处理讨论回复不再采用，已创建的执行计划继续保留；不执行任何真实记录删除作为开发验证。原请求若失败或结果未知不自动重试写入，保留提示与刷新入口；已明确返回成功后即更新本地列表，后续刷新失败不能描述为删除失败。删除当前目标清空详情，删除其它目标保留当前选择。恢复从已删除视图移出，并提示到当前目标查看。列表操作不绕过当前未保存编辑保护。切换视图不会自动删除或恢复记录。

复用 Console 组件、颜色和现有确认模式，文本使用 textContent，目标名称不拼接 HTML。补充本页 hidden 样式，避免按钮/布局样式覆盖隐藏语义。入口和数量按当前返回集合渲染，不虚称全库总数；不扩充现有列表上限或历史清理策略。

## Scope

仅 goals.html、goals.js、goals.css、附近使用说明和本 FD 证据。延续当前主工作区范围提交，保留并行秘书、专业任务、配置、权限路由及其它未提交改动。无新依赖，无后端/数据库修改，不运行服务、模型、浏览器、网络、迁移、测试、打包、重启或发布。

## Work items

- [x] 1.1 在列表提供当前/已删除视图、数量、刷新、行内删除/恢复和空状态。规模小、难度低，无依赖；完成证据为本页模板/样式/渲染逻辑。
- [x] 1.2 统一列表/详情删除恢复流程，保留原 revision、确认、未保存保护和明确成功/未知状态。规模小、难度中，依赖 1.1；完成证据为原接口调用及选择/列表更新路径。
- [x] 1.3 更新使用说明、完成 JS 语法编译和静态证据，再交独立实现审阅。规模小、难度低，依赖 1.2；只准备 Worker 证据，Reviewer 结论属于后续交接阶段。

## TODO

1.1–1.3 已完成，源码 70cc7bd；待独立实现审阅，通过后范围归档。

## Acceptance

1. 不打开目标也可在当前列表看到并点击删除；显示目标名称并要求确认，取消不发请求（1.1–1.2）。
2. 删除记录退出当前列表，已删除视图可查看/恢复；原有内容/正式版本和执行计划不被级联清理（1.1–1.2，原后端协议）。
3. 删除当前目标后详情清空；删除其它目标不误删/切换当前记录，未保存编辑需先处理（1.2）。
4. 原 actor/CSRF/revision 协议保持；失败或未知不自动再次写入，成功后刷新失败单独提示且列表先反映已确认结果（1.2）。
5. 页面有数量、空状态、刷新和当前选中标记，隐藏状态可靠；文档解释可恢复删除和计划保留（1.1、1.3）。

## Verification

仅静态检查模板、JS 调用/状态路径、原 Controller/Service/Repository 的删除语义，并执行一次 node --check src/main/resources/static/ui/goals.js。不修改 Java，无需 Maven 编译；不运行未授权测试/浏览器/数据库/服务。前期辅助资料路径未找到和 PowerShell rg glob 错误均不作为验证证据，已有目标源码和附近说明足够实施。

## Sources

- 用户反馈：Goal Workshop 中缺少删除功能。
- Planner/Worker Session：secretary-desktop-20261024-a6529740；真实来源 FD-036-000002-design-requested。
- src/main/resources/templates/ui/goals.html；static/ui/goals.js、goals.css、console.css。
- goal/GoalController、GoalDiscussionService.deleted、GoalRepository，V7__goal_discussion.sql（只读）。
- docs/current-zh/GOAL-WORKSHOP.md。

- Worker 证据：../reports/FD-036-implementation-r1.md 与同名 JSON。3d8f026..70cc7bd 完成页面与说明；git diff --check 通过，node --check src/main/resources/static/ui/goals.js 通过。无 Java 修改，未运行 Maven、测试、浏览器、数据库、服务或重启。无持久化或 API 变化，复用原删除/恢复协议，不额外改变稳定能力规格。
