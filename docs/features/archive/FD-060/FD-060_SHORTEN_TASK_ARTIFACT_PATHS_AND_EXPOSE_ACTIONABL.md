# FD-060: Shorten task artifact paths and expose actionable execution diagnostics

**Status:** Complete
**Revision:** 3
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

明暗主题计划 FP-26e5cf21-3e6c-46fe-985d-d26fcee8ad18 的需求分析两次在模型调用前失败。已读取精确执行证据并只读复现：workspace snapshot 遍历原数独仓库 .aiw 历史归档时，268 字符目录触发 WinError3/FileNotFoundError。Worker 丢失实际异常分类，只上报 OPERATION_FAILED；页面把任何 FAILED 误判为评审补正，用户找不到授权入口。

## Options and decision

新归档只使用 .aiw/o/<SHA256(attemptId)>，完整计划/任务/时间保留在数据库与 manifest，不在目录名重复堆叠。旧目录不移动、不删除，读取仍使用已保存的 directory。Python 扫描用 Windows 扩展路径支持历史长目录，而不是跳过历史文件或忽略访问错误。保留现有 fail 协议，发送安全具体错误码；API 返回诊断摘要与恢复建议。

## Solution

归档固定路径层数、固定长度 SHA256 防碰撞；Windows 目标文件总长度超过240时明确拒绝。快照仍检查所有文件、拒绝目录 junction，不隐藏 scandir 错误。分类错误包括路径过长、文件缺失、访问拒绝、IO、编码，真实已扩展路径缺失不能误标长路径。任务读取 API 复用既有控制台认证/计划成员校验，只提供任务、执行历史、安全错误码及说明，不提供租约凭证、原始异常文本或私有模型推理。历史通用错误明确标记具体原因未记录，不伪造诊断。

## Scope

TaskOutputService、Python Worker 快照/错误上报、任务诊断 API/页面、计划失败恢复提示和中日文。无 schema 变化，不重写旧失败记录、不迁移历史产物；原已确认任务可在修复加载后从失败步骤重试，保留原计划、既有产物和执行历史。未授权自动调用 AI 分析，本次提供可被认证 AI 读取的结构化数据。

## Work items

- [x] 1.1 固定短产物目录与历史长路径扫描兼容，保持安全快照。
- [x] 1.2 具体 Worker 错误码上报、诊断 JSON API 及用户说明。
- [x] 1.3 区分运行失败和评审补正，显示实际失败任务恢复入口。
- [x] 1.4 编译、定向回归、原工作区只读复现对比，并准备独立审查。

## Acceptance

新归档不再堆叠计划/任务/时间戳目录；旧历史仍可读。原数独目录同一快照函数可成功扫描。特定错误码能通过 fail 协议保存并由 API 展示，旧 OPERATION_FAILED 不被误说成缺授权。FAILED 显示诊断/重试，不展示无对应按钮的补正授权建议。读取诊断 API 不泄露 lease token。

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.
- 用户本轮明确要求修复，沿用此前平台定向测试/独立审查/备份重启授权。Java compile-only 319源成功；Python三个修改文件内存 compile 成功。
- `mvn -o -Dtest=TaskOutputPathTest,WorkflowFailureDiagnosticTest,WorkflowReviewedCompletionTest test` 16项通过；`python -B -m unittest test_workspace_paths` 初版4项通过，补充扩展路径缺失分类后5项通过；node --check 两个修改脚本通过。原目录快照从WinError3失败变为0.672秒通过，未写项目文件。
- Independent：路径持久化布局、工作区安全扫描及新 API 涉及跨模块/权限边界，审查无阻断后再备份加载。未运行全库或浏览器视觉验收。
- 审查提出 Python3.11 不支持 Path.is_junction；已改用 lstat.st_reparse_tag == IO_REPARSE_TAG_MOUNT_POINT 拒绝目录联接，专门的假 metadata 用例通过，不依赖管理员权限或3.12 helper。
- 备份 `D:\temp\data\aidev-r3.before-FD060-20261028-005007.db`（5,308,416 bytes，integrity ok），无活动任务时重启加载（launcher5464）。新诊断 API、任务页面 JSON 链接/说明均真实读取成功，无租约字段，保留2条旧 generic 的 specific=false。回执 `%TEMP%/aiw-goal-execution/fd060-readonly-receipt.json`。
- 原已确认失败步骤有界重试：attempt3被遗留旧Python进程接走，仍报旧generic错误，不把它作为修复通过。CIM核对4个本工作区 managed_worker.py 精确命令行与创建时间，均早于本修复；全局无活动任务/lease时通过认证UI action drain，确认 DRAINING/0，逐PID重新核对后精确停止进程树20460/17916/7332/13336，再通过UI offline。没有直接数据库修改或任意进程清理。此说明补全了此前硬停止Java未带动后台Worker更新的加载步骤。
- 环境已实际刷新后仅再重试一次：attempt4 `b9f585cf-84c0-4e62-903d-675d3c50c834` COMPLETED/PASS，证据含 workspace-assigned→context-built→model-terminal→report-posted；独立阶段评审也通过，原计划继续后续步骤，不宣称整个主题任务已完成。新真实归档目录114字符，最长文件路径126字符，包含manifest及4份报告/计划文件；原长路径和失败历史保留。API归档回执 `%TEMP%/aiw-goal-execution/fd060-short-archive-receipt.json`。
- 独立审查通过：`reviews/20261028-independent-static-review.md`，Reviewer `codex-fd060-review-20261028`，来源 `FD-060-000002-review-requested`。本轮Python3.11 junction缺口已修复关闭；已核对安全诊断API、新短归档及刷新Worker后原失败步骤恢复证据，保留遗留旧进程误领失败记录，不宣称整主题任务完成。Reviewer未额外执行测试、模型、API或进程操作。

## Sources

- 用户要求避免过长路径，明确向用户展示错误，并允许认证 AI 通过 API 获取诊断数据。

**Completed:** 2026-10-27
