# FD-102: Stop repeated read loops with clear progress diagnostics

**Status:** In Progress
**Revision:** 3
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

第 12 项要求无进展时结束并说明阻碍。现有 ProgressMonitor 仅检测连续相同读取、反复提醒；交替重复读取不能识别，提醒后仍可一直消耗模型轮数。用尽轮数的 SDK 异常也缺少直接说明。

## Options and decision

复用现有 ProgressMonitor、失败回报和进度事件，不增加规划 Agent、阶段状态机或新的预算系统。本轮只优化 OpenAI 兼容 API / ChatGPT 连接共用的 SDK 循环；备用 app-server 不扩展。

## Solution

- 识别本次调查中出现过的相同只读工具、参数与结果，参数 JSON 的空白／字段顺序不构成新进展。历史 call_id 不重复计数。
- 默认连续三次重复结果先提醒。提醒已交付后，重复累计达到六次且仍无新结果时，在下一模型调用和历史摘要之前抛出 MODEL_NO_PROGRESS。一次批次再多重复也必须先给模型一次处理提醒的机会；完整处理批次后再判断，批次末尾的新结果会重置。
- 新的读取结果、新参数、用户新增补充上下文，以及可能改变现场的非只读工具均重置重复判断。未知工具按可能有副作用处理，不据此声称未修改文件。
- 复用 progress-stalled 事件，记录既有 failed 状态、次数、工具名和安全错误码，不保存文件／工具结果正文。RunChannel 将工具名映射为现有 toolName 字段，保持 Java 白名单不变。沿用现有失败通道，保留现场，不生成虚假的完成报告或自动重试。
- SDK MaxTurnsExceeded 归一为已有 MODEL_TOTAL_CALL_LIMIT。执行详情与运行通道区分提醒和已停止；任务失败诊断说明先查看已完成操作和结果，再由用户决定是否重做。中英日文案同步。

边界：这是相同读取结果的确定性检测，不声称能理解所有无效推理；没有工具的低质量回答、不同但无用的搜索仍受原轮数限制。

## Scope

修改 progress_monitor.py、SDK 接入、已有失败诊断和邻近页面，增加纯单元回归用例但不执行。无依赖、权限、持久化结构或模型自动切换变更。实际终止行为有变化，须独立静态审查；停止／租约丢失优先沿用现有取消检查。

## Work items

- [x] 1.1 重复读取检测、一次提醒后的停止和上下文重置。
- [x] 1.2 接入共享 SDK 循环、明确失败原因和页面国际化。
- [x] 1.3 补充回归用例，编译、自查，准备提交独立审查。

## Acceptance

相同读取与交替重复读取均可有限结束；不因重放历史增加次数；新结果、补充内容或可能的修改重置。单批次不会在模型收到提醒前停止，批次中的新信息优先计入。停止不再发起模型／摘要请求，不将停止说成成功或授权问题。失败诊断和进度提示区分提醒、停止、总轮数上限。

## Verification

仅编译和独立静态审查。未授权测试执行、服务、浏览器、数据库、依赖安装或真实 API。

- `git diff --check` 通过，已自查修改差异及调用链：`_prepare` 在 history.prepare、_count、_begin 和模型请求之前停止；原始读取结果仅在内存中生成指纹，新增事件只有安全元数据。
- `python -m py_compile workers/process-python/progress_monitor.py workers/process-python/openai_api_runtime.py workers/process-python/worker_runtime.py workers/process-python/test_progress_monitor.py` 通过。只编译，不导入 SDK 或执行测试。
- `node --check` 对 workflows.js、worker-run.js、console-translations.js 均通过。
- 加载 `C:\green\bin\setup-jdk25.ps1`、`MAVEN_OPTS=-Xmx512m -XX:MaxMetaspaceSize=256m` 后，`mvn -q -o -s .ai/fd/FD-081/offline-maven-settings.xml -Dmaven.repo.local=D:/.m2/repository -DskipTests test-compile` 通过。
- 新增十项纯单元回归用例，覆盖提醒后停止、交替重复、历史不重计、单批次提醒机会、批次末尾新结果、JSON 规范化、新参数、未知副作用、用户补充重置、安全元数据和 SDK 限制错误码；未执行，不能视为运行通过。
- 尚未验证真实 SDK 输入历史、调度回报、浏览器呈现或模型效果；没有进行网络、数据库、服务、模型调用或依赖安装。原生后继校验沿用现有运行时源码摘要，更新 Worker 后旧检查点仍受原摘要一致性规则约束。

### 独立审查修复

- 首轮事件 `FD-102-000003-changes-requested`，报告 `reviews/review-20261114.md`，P1：progress-stalled 原小写 tool 和新增 stopped 状态不符合既有 Java 通道白名单，会导致事件批次被拒绝。
- 改为既有 failed 状态，并在 RunChannel 沿用 toolName 表示真实工具名；不扩大 RunContract 的安全白名单。UI 继续依据安全错误码区分提醒和停止。
- 补充 Python 从真实 ProgressMonitor 到 RunChannel 的组合回归用例，以及 Java ExecutionRuns 事件接收／ACK 回归用例；只编译，未执行。复核范围为字段契约和相关停止回报链路。
- 修复后 `python -m py_compile` 对 progress_monitor.py、run_channel.py、test_progress_monitor.py、test_run_channel.py 通过；同一离线 Maven `-DskipTests test-compile` 通过。未重复运行未修改的 JS 编译，未执行回归测试。

- 2026-11-14：独立静态审查结果为 changes-requested；报告 [review-20261114.md](reviews/review-20261114.md)，覆盖 0b02fcb 对 77ddd67，来源事件 FD-102-000002-review-requested，Reviewer codex-review-fd102-20261114。P1：新增 progress-stalled 的小写 tool 与 stopped 状态不符合 Java metadata 白名单，会拒绝通道事件批次，需规范化/同步契约。未执行测试、编译、验证脚本、迁移、浏览器、服务、数据库、网络或模型。

## Sources

- docs/FUNCTION-INVENTORY.md 第 12、13、27 项；已确认的失败后由用户决定重做规则。
