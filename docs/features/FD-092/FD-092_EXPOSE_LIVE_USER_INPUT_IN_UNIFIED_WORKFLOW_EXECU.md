# FD-092: Expose live user input in unified workflow executions

**Status:** In Progress
**Revision:** 3
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

统一工作流不能在执行中追加资料；原生通道仍仅允许 codex-app-server，误拦截已具备原生控制能力的 OpenAI API Worker。

## Options and decision

修复领取与通道打开两处运行时白名单，保留 Owner、租约、配置指纹和交接能力校验。补充内容放在现有执行 JSON 中，不新建数据库表。API Worker 在模型调用边界获取内容，收到真实模型终态后记录生效；失败或结果不明不自动重放补充内容。

## Solution

界面/API 提供补充上下文、下一轮生效两种意图。两者都不能修改在途模型请求，最早于下一次模型调用生效。没有正在执行的模型任务时，内容保存到后续上下文。模型任务开始后通过匹配原租约的批次读取与回执接入同一个 Agent loop；结束前原子封闭输入窗口，避免接受后被遗忘。Codex 备用路径不增加专用实现，不宣称支持实时注入。

## Scope

仅处理 OpenAI 原生通道接入和统一工作流追加内容。复用现有上下文压缩、轮数、停止与执行记录。无新依赖、新存储表或新模型循环；不调用真实模型，不改秘书及文件权限。

## Work items

- [x] 1.1 修复 OpenAI Worker 领取和打开原生执行通道。
- [x] 1.2 补充内容持久化、边界读取和模型终态回执。
- [x] 1.3 接入已有 Python loop，停止后不启动额外调用。
- [x] 1.4 界面/API、生效状态和中英日文案。

## Acceptance

同一补充 requestId 不重复提交；回执绑定原任务、Attempt 和批次；确认模型终态前不显示已生效；不支持的运行时明确拒绝；停止/结束不接受新输入；最终输出前有排队输入时在原轮数内继续，不能默默遗漏。

## Verification

- 涉及跨模块运行协议与持久化内容，使用独立静态审查。
- 仅计划离线编译 Java、Python 和页面脚本；不运行测试、真实服务、数据库迁移、模型或网络。
- 2026-11-13：使用 JDK25，离线 Maven `-o -s .ai/fd/FD-081/offline-maven-settings.xml -Dmaven.repo.local=D:/.m2/repository -DskipTests compile` 通过。Python 内置 compile 编译 native_run.py、openai_api_runtime.py、workflow_inputs.py；`node --check src/main/resources/static/ui/workflows.js` 通过。
- 用户输入仅在模型调用边界投影到既有 SDK history；返回模型终态后 ACK。失败批次不自动重用；最终输出前 DRAIN 与用户输入写入共用原事务锁。不增加外部协议依赖。
- 当前仅证明编译和静态调用链；未运行真实 SDK、模型、数据库迁移或浏览器。独立静态审查结果为 changes-requested；报告 [review-20261113.md](reviews/review-20261113.md)，覆盖 705f9df 对 9d305a0，来源事件 FD-092-000002-review-requested，Reviewer codex-review-fd092-20261113。阻塞项：停止请求会拒绝原批次 ACK/UNKNOWN/DRAIN，Python 将确定拒绝误标资源结果未知并跳过停止 ACK。

- 已修复停止竞态：ACK/UNKNOWN/DRAIN 保留精确原租约校验但允许停止后的事实记录；停止态不预留新输入，DRAIN 不启动额外调用；补充元数据失败只阻止重放，不将执行资源误标为未知。修复后 Java/Python 编译通过，待聚焦复核。

## Sources

- docs/FUNCTION-INVENTORY.md 第 16、17、27 项；FD-091 静态复核及后续接入检查。
