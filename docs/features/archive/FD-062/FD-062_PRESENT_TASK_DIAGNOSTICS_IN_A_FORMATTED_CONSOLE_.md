# FD-062: Present task diagnostics in a formatted console dialog

**Status:** Complete
**Revision:** 3
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

诊断入口直接导航到原始 JSON，未使用统一弹窗或可读格式。当前主题任务编码因未指定基线而失败，页面允许留空，Python Worker却要求完整SHA。用户进一步明确：未指定时使用当前rev，并保留先前通过步骤。

## Options and decision

诊断采用项目已有 console-confirm/工作流历史对话框，按失败原因、影响步骤、恢复建议、折叠技术字段和格式化原始数据展示，提供复制。Git默认值由Worker读取指定仓库HEAD，启动后以租约保护API固定到任务，再取得平台生成的隔离工作区/分支身份；不直接在源目录编码、不凭空生成基线，也不重新审批原已通过步骤。

## Solution

新增Worker协议 workspace-baseline，由现有机器认证路由处理。仅RUNNING、持有当前worker/token且租约未过期、instruction_revision匹配的普通flexible-plan coding任务可调用；受控guard、动态配置和无仓库任务拒绝。完整SHA写入现有base_revision，记录PIN_BASELINE审计，同值可重放但不能覆盖不同基线。结果提交、评审和交付继续使用已持久化基线。用户显式指定的基线不读取或改写HEAD；未指定时首次执行固定HEAD，后续重试保留该基线。Git无提交时给出明确错误。

## Scope

任务诊断弹窗/翻译、Worker默认HEAD选择、任务租约保护基线绑定、基线说明及相关定向回归。无schema迁移，不重写正式计划快照或已完成步骤；不改变受控执行范围。沿用本会话定向测试/独立审查/备份加载/原任务恢复授权。保留新API及旧Worker显式基线路径兼容。

## Work items

- [x] 1.1 格式化系统诊断弹窗、安全渲染、关闭取消请求及复制原数据。
- [x] 1.2 Worker读取并固定当前HEAD，平台租约/基线CAS保护和审计，复用隔离分支机制。
- [x] 1.3 更新基线留空语义、明确错误说明和中日文，准备编译/定向检查及独立审查。

## Acceptance

诊断可在原页面查看，无原始JSON跳转或浏览刷新干扰，动态内容只以textContent渲染。无基线编码可选择当前HEAD并保存到任务，随后隔离执行；显式基线不变，错误租约、过期、不同基线、guarded范围拒绝。原编码任务可恢复，之前通过的分析和规划不重做。最终结果按真实执行与Git回执报告。

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.
- Independent：新增Worker协议、执行基线持久化和租约并发保护，需独立审查后加载。尚未声称真实浏览器弹窗交互通过。
- Java离线 compile 319源成功；Python两文件内存compile成功。`mvn -o -Dtest=WorkspaceBaselineRepositoryTest,WorkspaceBaselineServiceTest,WorkflowFailureDiagnosticTest,WorkflowReviewedCompletionTest test` 23项通过（4 SQL租约/CAS +4 service范围/审计 +3诊断 +12完成状态）；Python test_current_baseline 3项通过，覆盖当前HEAD、显式版本不重选、无可读HEAD。三个修改JS的node --check通过，无全库测试。
- 原仓库检查干净，当前HEAD为6e80f479a5feb5e1b7fd671871e5827f0a645db5。独立静态审查无阻断后备份数据库到D:/temp/data/aidev-r3.before-FD062-20261028-073119.db（integrity ok），通过管理接口drain/offline并精确停止4个空闲旧Worker，重启加载平台。
- 仅重试原code任务，attempt 71233b0c-c4aa-4ada-9aba-6c83c6e21154通过新协议自动固定上述HEAD，隔离编码完成并产生真实提交b6d24a7f1a6f8fee2a5bcf7f8e10c7377a131441（git show核对仅sudoku2.htm，27增/1删）；原分析和规划结果保留。编码报告生成时尚未提交，后续Worker完成阶段实际commit以API resultRevision为准。
- 业务code独立评审fa91c3bc-83da-46e8-a1f8-3eeab82dfcd2要求补充计划规定的真实浏览器主题/数独操作验收，未PASS；自动补正work-2正确继承b6d24a7作为显式基线，没有重新读取原HEAD。该补正报告确认Edge启动被执行策略阻止、无浏览器自动化工具，无新增代码；这是独立环境阻塞，不能用静态检查代替浏览器验收，不能提前合并或声称业务计划完成。本FD修复默认HEAD与诊断展示，不扩大权限或绕过评审。
- 重启后通过认证HTTP读取task-detail.js及翻译目录均200且包含新标记；此证据不代表真实浏览器交互/视觉验收。
- 最终只读回执保存在%TEMP%/aiw-goal-execution/fd062-runtime-receipt.json：业务自动补正到work-3/review-3后仍为REPAIR_REQUIRED，缺少真实浏览器验收；原master未改、结果未合并，保留真实提交和全部报告，不追加同类重跑。本FD的自动HEAD接入已通过真实执行；业务计划未完成。
- 独立审查通过：`reviews/20261028-independent-static-review.md`，Reviewer `codex-fd062-review-20261028`，来源 `FD-062-000002-review-requested`。已核对默认HEAD固定、隔离提交及补正继承基线回执，保留业务REPAIR_REQUIRED/未合并事实。review-3另有未充分考虑work-1实际提交的判据问题，报告单独指出，不要求无意义新增代码。Reviewer未运行测试、模型、API或进程操作，未宣称浏览器弹窗交互验收通过。

## Sources

- 用户要求统一格式化诊断弹窗，并明确未指定基线时Worker应使用当前rev。

**Completed:** 2026-10-27
