# FD-078: Respect blocked report verdicts and repair clarification recovery

**Status:** In Progress
**Revision:** 2
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

计划 FP-18fdafff-7124-4ab9-96a9-360852e0fc86 连续进行了五轮 clarify 与独立 review，编码尚未开始。用户两次人工追加的修复授权均已消耗，但授权范围为“反馈的内容”，没有新增默认主题或配色决定。澄清报告正文明确 verdict=REPAIR_REQUIRED，Python Worker 却对 REPORT 无条件上报 PASS；评审反复指出矛盾与尚未回答的 Q-1/Q-2。

只读数据库证据：该计划无正式问题或回答记录、无旧式 task_output_bundle；问题仅存在于 completion_summary 内。现有 WorkflowQuestionService 从旧式输出文件提取 questions，不能直接消费本次报告。Plan location 是系统临时目录，业务目标目录仅写在目标文本中；这是后续编码访问风险，尚未到该阶段，不能认定它是当前阻塞原因。

## Options and decision

后续复查：计划推进到 implement~work-4/review-4，Worker 已确认目标 sudoku.htm 存在，但项目目录不在当前临时执行目录的可写范围，因而报告 BLOCKED_BEFORE_IMPLEMENTATION；其完成元数据仍为 PASS。此时目录绑定与纯文本阻塞状态已经成为新的已证实故障。用户随后明确要求先修复 bug，再终止该计划。

先做可隔离的 Python 报告判定修复，保留原始报告并尊重显式的结构化 verdict；不通过关键词猜测普通文本的判定，不强制放行历史任务。正式问题接入与阻止缺少新输入的重复修复需要在主对话完成跨模块设计和独立审查。

## Solution

REPORT 的完整 JSON 对象（可带完整 Markdown JSON 围栏）如包含顶层 verdict，只接受 PASS/REPAIR_REQUIRED 并原样上报；非法显式判定拒绝。没有显式判定的普通报告继续沿用已有 PASS 行为。DELIVERY 原有 BLOCKED/FAILED/INCOMPLETE 与 completed=false 识别保留，并支持完整 JSON 围栏；VERDICT 末行协议保持不变。

待主对话落实：将问题以来源可追溯、操作者隔离且可确认的形式接入问答，区分 WAITING_INPUT 与可自动修复；没有新增决策时，不把同一个澄清缺口当作新修复机会。不得把“允许重试”当作“已经回答问题”，也不得替用户默认扩大视觉设计授权或文件访问范围。

## Scope

本侧对话获用户明确授权“修正发现的问题”及后续“先修正 bug，然后终止这个计划”。当前工作区局部修改报告判定、编辑器与服务端保存校验及回归用例；未创建 worktree，不直接修改数据库、历史报告、权限、用户配置或源业务文件，不触发模型、服务重启或重试。新授权只允许通过既有停止流程终止该计划，保留审计。当前侧对话明确禁止子代理，不能执行本 FD 所需的独立审查；不自动提交、归档或宣称跨模块后续范围全部完成。

## Work items

- [x] 1.1 修复 Python REPORT 判定与原始报告矛盾，补充边界回归用例及说明。
- [x] 1.2 完成局部代码编译与静态自检，记录实际证据。
- [x] 1.3 修复正文首行的明确阻塞状态识别，并在保存/确认时阻止把目标中的 workspace 声明默认为临时目录；前后端提示一致。
- [x] 1.4 按用户新授权，通过现有登录、CSRF 和停止 API 终止目标计划，保留报告与文件。
- [ ] 2.1 主对话接入正式问题与确认回答，设计无新输入时的重试阻止及恢复路径。
- [ ] 2.2 主对话独立审查最终范围，必要时处理多语言 Worker 判定一致性，完成本地交付。

## Acceptance

1. Python REPORT 正文显式 REPAIR_REQUIRED 不再上报 PASS，包含本次模型使用的 Markdown JSON 围栏格式。
2. 非法显式判定不标为成功；普通文本及合法 PASS 报告、既有 VERDICT/DELIVERY 语义保持受支持。
3. 待实现：澄清缺口成为可回答的正式问题；未有新输入时不会因重复授权而再次执行相同澄清。
4. 历史报告与授权不改写；不通过强制 PASS、猜测用户决定或调整目录来伪造当前计划恢复。
5. 目标明确以 workspace/location/工作空间/工作目录 声明位置、但 Plan location 留空时，执行草稿保存/确认会明确拒绝，要求操作者填写；推荐候选仍可生成，不能由模型或文本解析自动扩大目录权限。无需指定位置的新任务仍可使用临时目录。
6. REPORT/DELIVERY 的首行若是明确 BLOCKED/FAILED/INCOMPLETE 状态，不再上报 PASS；正文举例或历史故障描述不触发误判。

## Verification

- Review policy Independent：判定影响跨模块任务推进，后续问答与重试涉及公共契约和持久化恢复。
- 回归用例仅编译，测试执行未授权。当前侧对话禁止子代理与 worktree，因此独立审查和跨模块后续实现留待主对话。
- 局部实现：REPORT 解析完整 JSON 或完整 Markdown JSON 围栏，尊重顶层 PASS/REPAIR_REQUIRED；非法非字符串或未知判定明确拒绝。保留完整原始 summary，不通过普通文本内的关键词或 JSON 示例推断判定。
- 新增 test_completion_report.py 的七项回归用例，覆盖未解决报告、大小写/无语言标签围栏、正常报告、非法判定类型、正文示例、带围栏阻塞 DELIVERY 及原 VERDICT 末行协议。未执行用例。
- 已执行一次 `git diff --check` 与限定文件差异审阅；自检补齐 re 导入及非法判定类型检查。使用 `python -c` 对 process_worker.py 和 test_completion_report.py 的源文本分别调用内置 compile(..., 'exec')，两份源码均编译成功；不导入产品模块、不写 pyc、不执行测试。全量 Worker 编译脚本会覆盖无关语言，本轮使用最窄 Python 编译检查。
- 未运行 Java 编译、测试、模型、服务或数据库迁移；Java 源码尚未修改。当前 Plan、数据库历史、用户配置和原业务文件均未修改；代码未提交，也未生效到运行中的 Worker。
- 后续修复证据：增加 WorkflowEditorService 的非候选草稿/确认校验与 Web 编辑器同等校验，检测明确的 workspace/location/工作空间/工作目录 声明但未填写 location；只要求操作者显式选择，不从文本自动授予目录权限。新增三项 Java 回归用例，候选推荐与无目录的新任务仍保留。
- Python 后续补齐 REPORT/DELIVERY 的顶层未完成状态与首行明确状态声明识别，覆盖本次 `**结果：` + BLOCKED_BEFORE_IMPLEMENTATION + `**` 形式；不扫描正文里的例子或历史故障。显式 PASS 与顶层受阻状态矛盾时拒绝报告。增加四项回归用例，累计十一项 Python 用例，均未执行。
- 实际检查：`JAVA_HOME=C:/green/jdk-25.0.1+8`，`C:/green/apache-maven-3.9.9/bin/mvn.cmd -o -Dmaven.repo.local=D:/.m2/repository -DskipTests test-compile` 成功（321 主源码、56 测试源码）。两份 Python 源码内置 compile 成功；`node --check` 检查 workflow-editor.js、console-translations.js 成功；`git diff --check` 和限定差异自检通过。无依赖下载、测试执行、应用/Worker 重启。
- 2026-11-07 停止操作：按用户明确要求，使用当前已配置的控制台身份，通过既有登录及 CSRF 保护的 `/api/r1/plan-editor/plans/{id}/stop` 操作。操作前 CONFIRMED/version=9，响应 STOPPED/version=11，随后同一详情接口核对 STOPPED、activeTaskCount=0，STOP 控制记录 APPLIED。未使用替代身份、直接数据库修改或取消/分支删除接口；未输出或持久化凭据。
- 新修改仍未独立审查、未提交；问答与缺少新输入时的修复策略仍未完成。旧计划已终止，不再以追加重试验证代码修复；业务 sudoku.htm、历史报告与原授权保留。

## 主对话接续范围

1. 先审阅本次 Python 局部修复，核对 REPORT 结构化判定与其他语言 Worker、平台完成语义的一致性，不能把“进程成功执行”与“澄清已经完成”混为一谈。
2. 问题接入须沿用任务/attempt/操作者关联及正式确认机制。当前板读取 task_output_bundle JSON，而该 Plan 完成报告没有此类输出记录；不得通过让 Control Plane 扫描任意 .ai 路径、恢复批量文件上传或把自由文本直接当用户决定来绕过缺口。
3. 重试恢复须区分等待新输入与可执行修复，校验新增问题答案或明确的自主决策授权，避免同一未解问题消耗更多修复轮数；保留已消耗授权和历史报告。允许推荐方案的输入也应明确记录其来源与适用范围。
4. 目标目录只存在于 goal、Plan location 却为临时目录的问题，当前仅确认配置现状；尚未到编码阶段，不自动重绑目录或扩大文件访问。需结合实际 Worker 工具范围核对后续编码可达性。

## TODO

- INCOMPLETE：本次报告状态与目录配置保护已实现且编译通过，目标旧计划已 STOPPED；正式问答/重试策略及独立审查仍未完成，不得将本 FD 标 Complete。
- %% NEEDS_INPUT: 主对话接续跨模块问答/重试修复与独立审查；本侧对话不能使用子代理。

## Sources

- 用户侧对话报告计划执行受阻，并明确要求修正发现的问题。
- 只读分析 D:/temp/data/aidev-r3.db 中该 Plan 的任务、报告、授权和问题记录。
