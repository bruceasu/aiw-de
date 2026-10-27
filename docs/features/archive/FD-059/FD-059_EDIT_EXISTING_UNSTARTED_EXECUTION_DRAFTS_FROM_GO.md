# FD-059: Edit existing unstarted execution drafts from Goals and Plans

**Status:** Complete
**Revision:** 3
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

用户已生成执行草案后，Goal 只提供查看链接，详情没有编辑入口；“确认正式版本”实际只保存目标快照，易被理解为已启动执行。新建计划的能力说明还采用浮出 details，并使用旧角色说明。用户要求补齐页面编辑、明确保存与执行语义，并使用统一弹窗。

## Options and decision

复用现有计划编辑器，通过 edit 参数加载原草案并以版本 CAS 保存同一计划。保留新建/复制路径。不自动执行保存操作，而把“保存目标版本”“生成执行草案”“确认并开始执行”明确分开并暴露在页面顶部。

## Solution

GET/POST plan-editor/plans/{id}/draft 使用控制台 Operator 会话及原 CSRF 保护。仅原人类所有者、非代理、未归档、无执行任务、非托管 DRAFT 可编辑；保留来源、创建者和原 Plan ID。只更新 spec、版本与修改审计，不创建任务。每个非空工作目录必须登记，其他图与步骤验证复用既有契约。已确认或开始的计划不能借此更改范围。编辑器保留冲突时的输入并显示失败，成功返回原详情。

## Scope

编辑目标正文、步骤角色/指令/验收、依赖、已登记工作目录、Git 基线，并保留已有模型偏好；Goal 提供查看/编辑链接，详情顶部提供编辑与启动按钮。能力说明使用 workspace-confirm / console-confirm 对话框和既有滚动布局，说明工厂自动供给/复用、外部 Worker、独立审查、工作目录与工具权限边界。补齐新增中英文日文文案。用户的明暗主题任务仍保持草案，不代替用户编辑或启动。不直接修改数据库，不新增 schema，不修改已保存目标快照。

## Work items

- [x] 1.1 新增原草案读取/保存 API 与共享未开始草案保护，版本 CAS 和修改审计。
- [x] 1.2 复用编辑器保存同一草案；Goal/计划详情显示编辑及明确的启动入口。
- [x] 1.3 能力说明统一弹窗、更新当前流程说明及中日文。
- [x] 1.4 编译、定向回归和脚本语法检查，准备独立审查；通过后备份加载并只读检查真实用户草案入口。

## Acceptance

原草案可在页面修改后仍为相同 ID 的 DRAFT、版本递增，保存不创建任务。旧版本、非所有者、已执行/归档/托管计划被拒绝且不覆盖。新建和复制继续创建新草案。Goal 保存目标版本不再使用“确认正式版本”的误导标签；真正执行按钮明显显示且调用原 confirm API。说明弹窗可键盘 Escape/关闭并滚动，新增文案有中日文。

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.
- Independent：原草案更新涉及持久化和权限/并发，复用既有流程授权开展定向测试、独立静态审查；通过后按会话授权备份并重启。
- `mvn -o -DskipTests=true -Dmaven.test.skip=true compile` 成功，319 源；`mvn -o -Dtest=WorkflowDraftEditingTest,WorkflowReviewedCompletionTest test` 20 项通过（8 草案编辑边界 +12 完成状态），失败/错误/跳过均 0。`node --check` 对 workflow-editor.js、goals.js、console-translations.js 均成功。未运行全库或浏览器交互验收。
- 独立审查发现编辑加载期间可能覆盖目标输入，已在 edit 模式禁用目标、模板和新增步骤直到加载成功；失败保持不可编辑/保存，帮助与取消仍可用。修正后 `node --check workflow-editor.js` 成功，复核无剩余静态阻断。
- 备份 `D:\temp\data\aidev-r3.before-FD059-20261027-235325.db`（5,218,304 bytes，integrity ok），无 CLAIMED/RUNNING 任务时重启加载（launcher 15480）。只读 API 验证用户原计划 FP-26e5cf21-3e6c-46fe-985d-d26fcee8ad18 的新 draft 端点返回 DRAFT/v0、任务 0；Goal 新标签、详情顶部启动入口、编辑页 dialog 标记、新保存/加载保护脚本及翻译均 HTTP 读取成功。
- 回执 `%TEMP%/aiw-goal-execution/fd059-readonly-receipt.json`；未对用户草案提交任何修改或确认执行，未调用模型、未运行真实浏览器交互或全库验证。读到 HTML/脚本不等于弹窗视觉和键盘交互已验收。
- 独立审查通过：`reviews/20261027-independent-static-review.md`，Reviewer `codex-fd059-review-20261027`，来源 `FD-059-000002-review-requested`。本轮加载覆盖缺陷已修复关闭；已核对同ID/CAS权限边界、20项执行者测试证据与原草案只读回执，未额外执行测试、API、模型或启动用户计划。

## Sources

- 用户要求补齐草案编辑，修正正式版本/草案语义，并将能力说明改为项目统一弹窗、更新过时说明。

**Completed:** 2026-10-27
