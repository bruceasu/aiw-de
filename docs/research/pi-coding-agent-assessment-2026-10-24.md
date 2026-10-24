# Pi 文章核对与吸收建议

研究日期：2026-10-24。用户入口为[2026-09-14 的 Pi 介绍文章](https://www.heyuan110.com/zh/posts/ai/2026-09-14-pi-coding-agent-deep-dive/)。本笔记核对当前官方资料，区分外部事实、对 AIW 的建议及尚无证据的说法；不创建 FD、替换运行时或执行文章示例。

版本限制：官方站当前展示 Pi 1.0，文档路径为 `docs/latest`，会继续变化；本轮未固定仓库 commit，也未复现 9 月文章写作时的旧版本。下列差异表示文章不能直接充当当前接口规范，并不证明它在全部历史版本都错误。[官方发布说明](https://earendil.com/posts/pi-1-0/)

## 值得吸收的事实与建议

| 官方可确认的事实 | 对 AIW 的建议（推论，尚未实施） |
| --- | --- |
| Skill 启动时仅列名称、说明和路径，任务需要时才读取完整入口；也支持显式调用。描述承担路由作用，不保证模型一定选中。[Skills](https://pi.dev/docs/latest/skills) | 在已冻结、已批准的技能集合内进一步按需加载入口；关键工程协议保持明确必需。冻结字节和模型每次看见多少内容是两件事。 |
| 工具结果区分模型用的 `content` 与展示/状态重建用的 `details`；大结果可以截断并指出完整输出位置。扩展事件分别处理输入、调用、结果和展示。[Extensions](https://pi.dev/docs/latest/extensions) | 让模型得到任务所需摘要及证据引用，人类看到可展开的详情；不要把全部 UI 数据或完整日志一并塞进模型。原始证据和脱敏规则仍保留。 |
| SDK 明确区分 `steer()`、`followUp()` 与 `abort()`；流式执行时普通 prompt 必须明确选前两种行为。`agent_end` 后可能还有恢复或排队工作，最终安定事件是 `agent_settled`。[SDK](https://pi.dev/docs/latest/sdk) | 人工输入分“调整当前工作”和“完成后再处理”，页面显示已接收、排队与应用状态；停止是另一动作。后续需版本化设计，不把一段提示词当实时控制能力。 |
| 会话保存为带 `id/parentId` 的树。上下文选活动分支，压缩保留摘要及最近条目；原条目不被删除。[Sessions](https://pi.dev/docs/latest/sessions)、[Session Format](https://pi.dev/docs/latest/session-format) | 后置考虑分支探索与可回查压缩。会话位置和物理工作区状态应分别表示；没有证据的代码回退或未知副作用不能随摘要消失。 |
| 包声明支持个人与项目范围及固定版本；项目资源须先经过信任决定。当前官方命令使用 `pi install` 与 `--local`/`-l`。[Packages](https://pi.dev/docs/latest/packages) | 能力按任务/仓库选择并固定版本，避免全局自动扩散。项目作用域决定加载范围，不能替代 Owner、工具和操作系统权限。 |
| 自定义消息可有供 UI 使用但不发送给模型的 `details`；持久化非上下文条目也有独立类型。[Message Types](https://pi.dev/docs/latest/message-types) | 给模型上下文、人类展示、持久事实分别定义边界，适合未来同时支持 Web 与桌面秘书，不必为了新 UI 另造 Agent 生命周期。 |

Steering 的精确时机存在官方页面表述差异：首页写当前工具之后及中断剩余工具，SDK 与 How Pi Works 写当前 assistant turn 及工具调用之后。本轮只确认两类队列与执行边界的区别；没有运行或源码版本证据，不能保证特定调用粒度。取消也不能推导为撤销已落地的文件、网络或其他副作用。[官网](https://pi.dev/)、[执行流程](https://pi.dev/docs/latest/how-pi-works)

## 文章中不应直接照搬的部分

| 说法或示例 | 核对结果 |
| --- | --- |
| 无内置 MCP 是永久设计原则 | 当前官网已明确更改为内置 MCP 与 Codemode；1.0 说明也列出此项。应吸收渐进披露和最小上下文，不能据文章删除一种协议。[官方解释](https://earendil.com/posts/you-said-no-mcp/)、[1.0 说明](https://earendil.com/posts/pi-1-0/) |
| SDK 包名为 `@pi/ai`、`@pi/agent` 等 | 当前官方包为 `@earendil-works/pi-ai`、`pi-agent-core`、`pi-coding-agent`、`pi-tui`，前述包名不能作当前安装指令。[包文档](https://pi.dev/docs/latest/packages) |
| `registerMiddleware`、`emitMessage`、`showConfirmationDialog`、`run_bash_command` 示例可直接使用 | 当前文档提供 `pi.on('tool_call', ...)`、`pi.registerTool()`、`pi.registerCommand()` 的 `handler`、`pi.sendMessage()`/`sendUserMessage()`、`ctx.ui.confirm()`/`notify()` 等接口；文章代码与它们不匹配。本轮未对旧版本作穷尽查证，不把示例当验证通过的插件。[Extensions](https://pi.dev/docs/latest/extensions) |
| 项目信任、守则文本或字符串检测能形成安全隔离 | 官方明确：扩展与 Pi 同进程权限，工作目录和信任选择都不是沙箱。按 `.env` 或 `rm ` 子串判断只能覆盖部分形式，无法证明权限边界。AIW 的受控工具和真实授权仍应保留。[Security](https://pi.dev/docs/latest/security) |
| 会话树回退配 `git reset --hard` 即可无忧恢复 | 官方树结构处理的是对话上下文。安全工作区恢复仍需单独设计所有权、快照和副作用核对；不能把破坏性 Git 指令当通用恢复步骤。[Session Format](https://pi.dev/docs/latest/session-format) |
| 1100 对 18000 token、快 1.5–2 倍、Databricks 同成本质量第一 | 本轮未找到文章给出的对应原始实验配置与可复现记录，保持未核实。作者公开介绍的是 2025 年 Terminal-Bench 2.0、Opus 4.5、每题五次试验的运行，并附结果/runner；这不支持文章另一组基准、模型和普遍速度结论。[作者原文 Benchmarks](https://mariozechner.at/posts/2025-11-30-pi-coding-agent/#benchmarks) |

## 对当前系统的吸收顺序建议

优先比较技能入口渐进加载、模型与展示数据分层、人工引导与后续排队。它们能分别改善上下文、可读性和人工控制。会话树及可恢复工作区涉及另一组持久化与副作用语义，可后置；不因阅读文章重做已有平台编排、权限或预算策略。

## 当前实现对照（本地静态事实）

宿主检查了下列文档和 Python 执行路径，不把其他运行时未检查的行为推定为相同。仓库只有根 AGENTS 指令，未发现这些目录另有指令；根 CONTEXT、CONTEXT-MAP 与 docs/adr 不存在，未创建占位文件。

| 能力 | 已观察到的现状 | 吸收时的边界与优先级（建议） |
| --- | --- | --- |
| 运行时分层 | [AgentRunRequest 与 AgentRuntime](../../workers/process-python/agent_runtime.py) 已将一次任务执行和具体后端分开；[API runtime](../../workers/process-python/openai_api_runtime.py) 及 [受控 app-server](../../workers/process-python/guarded_app_server.py) 负责各自模型/工具适配 | 沿用现有分层，借鉴小接口与明确事件；阅读本文不构成新增 Pi 依赖或替换后端的决定 |
| 技能与上下文 | [SkillFiles](../../src/main/java/me/asu/aidev/execution/skills/SkillFiles.java) 冻结完整文件与引用；[Python Reader](../../workers/process-python/skill_files.py) 校验包并按需读取附属文件，但 `load` 的 entries 和 `prompt()` 仍展开全部所选 SKILL.md；[Prepared.prompt](../../workers/process-python/execution_configuration.py) 还展开知识与固定上游输入 | 优先研究入口也按需读，保留必需工程协议、精确引用和原结果约束。完整文件冻结不等于所有正文必须首轮注入；也不能只删正文便声称语义等价或节省已验证 |
| 消息与结果分层 | API runtime 已记录 model-request、model-terminal、tool-proposed 等事件，模型输出与执行证据有各自处理；本轮未证实跨后端存在统一的模型 content/UI details 协议 | 优先复用已有结果与证据引用，明确模型摘要、完整记录及 UI 展示边界；避免为新 UI 复制执行生命周期。实际字段及脱敏需后续有界设计 |
| 人工输入 | 已检查的 AgentRuntime 边界只有单次 instruction/run；API 路径用一次 `Runner.run_streamed` 加 cancel，受控 app-server 用 `turn/start`。后者 `ask_question` 明确让出任务，答案通过新任务续行 | 现有问答续行不能称作实时纠偏。补充指令与后续排队应在正式任务/Owner/Attempt 和版本边界内设计；改变已冻结范围或授权不能静默注入，已发生副作用也不能回滚承诺 |
| 角色与委派 | [组合角色](../COMPOSABLE-ROLES.md) 已支持同一 Worker 使用多项技能；[FD-043 方法](../WORKER-DELEGATION-METHODS.md) 已增加委派判断、证据交接和只读执行记录 | 复用这些能力，跨 Worker 仍由平台编排；不另建固定专家进程、技能生命周期或预算门禁 |
| 历史规划与会话探索 | [2026-10-04 Pi 融合计划](../worker-pi-integration-plan.md) 已考虑执行边界、钩子、截断和压缩等，但明确为待评审、未整体实施的历史计划，并将会话树/消息队列排除在当时首期外 | 避免重复大计划。当前研究可提出与历史首期不同的后续候选；不把设想当完成。会话树与工作区恢复后置，分别处理上下文和真实代码状态 |

建议第一步先限定到“任务冻结技能集合内的入口渐进加载”，这是已观察到的直接上下文改进机会。随后分别评估工具结果分层和人工输入双通道；它们涉及跨后端事件、持久化或任务合同，不宜打包成一项无边界重构。此次只是比较与建议，没有新 FD、实施验收、性能保证或运行许可。

## 实际范围与未验证事项

本轮通过网页工具读取用户文章、官网、官方文档、作者原文，通过 `rg --files`、有界 `rg -n`、标准库文本读取检查仓库指令、上述源码和相邻说明，并撰写本笔记。几处初始猜测的上下文/文档路径不存在及输出截断，随后仅补读准确的相关路径；不存在的资料不作为证据。官网跳转的几处 GitHub 路径被网页工具拒绝取回，随后使用同一官方站的公开文档；没有通过其他渠道绕过限制，也未获取固定仓库源码版本。

未安装或运行 Pi，未执行文章命令/插件、模型、测试、编译、服务、数据库或性能基准；没有修改产品代码、FD 状态、既有 Pi 计划或 Git 提交。事实支持架构取舍讨论，不证明本系统接入或优化后的实际效果。
