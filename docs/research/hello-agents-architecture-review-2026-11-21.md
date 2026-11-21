# Hello Agents 对 AIW-DE 的架构评审

阅读基于 Hello Agents `2eb1c7547018` 的中文课程，覆盖前言和第 1–16 章，补充阅读与上下文、Skills、Agent 工程、演进和评估相关的扩展章。对照了第 4、6、7 章及旅行助手、深度研究的配套代码。评审基于 AIW-DE `bc2a25c2` 及当前工作区的 Worker 和恢复改动。课程中的英文译文、面试题答案库、安装步骤和社区共创项目没有逐项复读。示例和真实模型均未运行。[课程目录](https://github.com/datawhalechina/hello-agents/blob/2eb1c7547018b519b85cb57acf5533f12d65bf8e/README.md) · [补充文章](https://github.com/datawhalechina/hello-agents/tree/2eb1c7547018b519b85cb57acf5533f12d65bf8e/Extra-Chapter)

用户本机的 `D:/03_projects/AI-tools/hello-agents-main` 与该快照的 17 篇中文主课程、16 份补充 Markdown 及第 4、6、7 章 26 个 Python 示例逐文件比对一致，比较时统一了换行符。

## 评审结论

**优先缩短普通任务的执行路径，让单 Worker 工作稳定。复杂任务复用现有工作流，从成员目录按需组队，通过明确的交付物协作。** 这两层可以独立演进。

用于复盘的手动重试从 Worker 接手到完成约 7 分钟，其中接近 6 分钟在等待旧工作区锁。解锁后一个 Worker 完成了 3 次模型调用，记录了 11,115 个输入 Token、7,168 个缓存命中的输入 Token 和 14,285 个输出 Token。该次超长等待发生在首个模型请求前。交付质量、角色协作收益，以及各运行方式的相对成本仍需对照验证。

## 按优先级的发现

### P0 普通任务在首轮模型请求前经过太多业务步骤，等待原因呈现太晚

`ProcessSlot._execute_task` 串联技能加载、任务启动与续租、原生通道和检查点、工作区准备、工作区基线、上下文拼装，然后才进入 Agent 运行时。租约心跳只说明 Worker 仍有联系。之前的旧锁和日志字段遗漏都能让任务一直停在首轮模型之前。现有改动已让工作区等待进入事件记录，也补齐了 Token 证据字段。统一的阶段呈现在其他中断路径仍需验证。 [任务入口](../../workers/process-python/process_worker.py#L524) · [模型请求检查](../../workers/process-python/openai_api_runtime.py#L372)

建议保留 Worker 内的一条主路径，由它负责准备、执行、交付和退出。Plane 负责派发、租约、人工决定及可信结果。用户可直接看到「等待领取、准备工作区、模型调用、执行工具、验证交付、等待人工」和最近结果。普通文字任务需要工作区交付报告时，仍可用 Plan 临时目录。按实际工具和交付需要准备，可避免为了文字回答先扫描仓库或创建 worktree。对外部效果无法确认的情况保留租约与核对流程。

教程第 4、7 章的 ReAct 和原生函数调用，加上 Pi 对回合和工具结果的记录，更适合用来核对这条执行路径。现有 API Key 与 ChatGPT 连接已共享 SDK 的 Agent 入口。DeepSeek Chat、OAuth 与常规 SDK 路径仍有不同的传输和流处理，应用层应统一阶段、取消和结果，保留各提供商的协议差异。Codex app-server 继续作为备选运行时。 [第 4 章](https://github.com/datawhalechina/hello-agents/tree/2eb1c7547018b519b85cb57acf5533f12d65bf8e/docs/chapter4) · [第 7 章](https://github.com/datawhalechina/hello-agents/tree/2eb1c7547018b519b85cb57acf5533f12d65bf8e/docs/chapter7) · [Pi 的执行循环](https://docs.pi.dev/latest/how-pi-works) · [SDK 模型适配](../../workers/process-python/openai_api_runtime.py#L104)

### P1 跨步骤的上下文仍由报告文本累加，200K 模型上限无法解除上游限制

`WorkflowRuns` 把旧结果和补充信息追加到 `run.context`。上下文达到 64,000 字符会阻止继续累加，拼出的下一节点指令超过 16,384 字符时无法派发。Worker 自己另有请求体限制、Token 估算、工具输出分页和历史压缩。这些限制处在不同层，模型有剩余容量时，下一步仍可能卡在 Plane。长报告已经可以保存为 `.ai/handoffs`，跨步骤仍有继续累计整段摘要的路径。 [上下文累加](../../src/main/java/me/asu/aidev/workflow/application/WorkflowRuns.java#L411) · [下一步派发](../../src/main/java/me/asu/aidev/workflow/application/WorkflowRuns.java#L755) · [结果接续](../../src/main/java/me/asu/aidev/workflow/application/WorkflowRuns.java#L834) · [Worker 上下文窗口](../../workers/process-python/context_window.py#L202)

建议先将已有 Plan 原目标、当前步骤、验收条件和用户补充保留在每次执行输入中。前序节点只提交相关结论、阻塞及交付物引用。需要核实时，从原文件、Git 版本或来源页按需读取。长任务可在现有 `.ai/` 写一份进度笔记，记录已验证事实、下一步和阻塞。压缩保留直接用于判断的证据，避免反复读取。就这一项改动而言，现有文件和检索接口足够试验。 [第 8 章记忆与检索](https://github.com/datawhalechina/hello-agents/tree/2eb1c7547018b519b85cb57acf5533f12d65bf8e/docs/chapter8) · [第 9 章上下文工程](https://github.com/datawhalechina/hello-agents/tree/2eb1c7547018b519b85cb57acf5533f12d65bf8e/docs/chapter9)

### P1 两条工作流路径并存，新增协作应挑一条验证

日常执行由 `WorkflowRuns` 承载，支持简单任务、模型决策节点、人工问答和工作流修改提案。另有 `GraphRunner` 处理分支、并行区域、回跳与执行准备。两条路径已有各自的派发和重试语义。近期重试问题也表明，按钮是否成功不能仅凭任务行状态判断，还要核对工作流当前节点及尝试。 [Web Console 入口](../../src/main/java/me/asu/aidev/workflow/protocol/http/WorkflowConsole.java#L39) · [常用工作流](../../src/main/java/me/asu/aidev/workflow/application/WorkflowRuns.java#L28) · [图执行设计](../../docs/WORKFLOW-GRAPHS.md) · [重试与恢复](../../docs/features/FD-132/FD-132_USE_A_GENERAL_WORKER_WHEN_NO_TEAM_MEMBERS_EXIST.md)

首个协作实例可走熟悉的工作流入口，用一个实现节点交接一个独立验证节点。已声明的判断节点让模型选择有效出口，验收由结果和可验证事实决定。改变图结构或跳过必要节点仍走已有的人类确认。先用同一套执行者、交付和失败原因验证协作，遇到确实需要独立并行或回跳的场景再评估图执行入口。教程对 LangGraph 的价值主要在于显式状态与条件转移，AutoGen 的价值主要在于决定谁接手。 [第 6 章](https://github.com/datawhalechina/hello-agents/tree/2eb1c7547018b519b85cb57acf5533f12d65bf8e/docs/chapter6)

### P2 成员和 Skills 已能复用，先给角色明确的交付职责

`TeamMembers.choose` 按 Skills、是否接单与负载选择成员，`WorkflowRuns.advance` 冻结本次指派。成员经验和 Skill 修订有来源、版本和修改依据。共享经验目前优先取最新八项，随后以资料提供给 Worker。改进点在按项目、Skill 与当前问题检索，并区分已有验证的知识和单次经验。秘书的私有知识可继续在本机检索，仅把用户选中的资料或澄清结果委派给 Plane。 [成员匹配](../../src/main/java/me/asu/aidev/worker/application/TeamMembers.java#L258) · [成员知识与经验](../../src/main/java/me/asu/aidev/worker/application/TeamMembers.java#L350) · [版本化修订](../../src/main/java/me/asu/aidev/worker/application/TeamMembers.java#L150) · [秘书行为边界](../../personal-secretary/secretary_host.py#L12)

可以预定义「需求澄清、实现、验证」这三种职责。任务开始前，由人或模型提议所需角色，从已有成员中分配。角色给出专业方法、所需输入、输出和验收条件。成员保留身份、Skills 与经验。Worker 进程按需启动或复用。模型选择和已有授权继续按原边界处理。没有合适的成员时，界面给出缺少的能力和可操作的下一步。

配合现有交付和问答，初期只需要三种交流动作：

| 动作 | 交付内容 | 谁决定下一步 |
|---|---|---|
| 提交给下一个节点 | 简短结论、产物路径与版本、已验证的事实、接手事项 | 工作流按已声明的转移推进 |
| 请求澄清 | 一个具体问题、缺失信息、已有证据 | 指定成员或原用户回答后恢复 |
| 验证退回 | 未通过的验收项、可复查证据、期望修正 | 工作流按明确的退回规则或人工决策处理 |

先做一个可见的交接，再测补问与退回的价值。相同目录保持符合现有 Git/worktree 约定的并发约束。适合并行的任务应有独立输入和明确汇合点。 [CAMEL 角色示例](https://github.com/datawhalechina/hello-agents/blob/2eb1c7547018b519b85cb57acf5533f12d65bf8e/code/chapter6/CAMEL/DigitalBookWriting.py) · [AutoGen 软件团队示例](https://github.com/datawhalechina/hello-agents/blob/2eb1c7547018b519b85cb57acf5533f12d65bf8e/code/chapter6/AutoGenDemo/autogen_software_team.py#L116-L142) · [第 10 章协议](https://github.com/datawhalechina/hello-agents/tree/2eb1c7547018b519b85cb57acf5533f12d65bf8e/docs/chapter10)

### P2 先用应用场景评估，再决定增加角色、检索或自进化能力

以三个代表性场景做对照。将平台失败、模型行为失败和验收失败分开计数。工具调用可验证参数与结果，最终质量优先使用真实产物和确定性验收，主观指标再用抽样盲评。对照使用相同模型、输入、工具权限与验收条件，分开记录模型调用前等待、实际用量、耗时、返工和人工干预。 [第 12 章评估](https://github.com/datawhalechina/hello-agents/tree/2eb1c7547018b519b85cb57acf5533f12d65bf8e/docs/chapter12) · [工程实践与训练实战补充](https://github.com/datawhalechina/hello-agents/tree/2eb1c7547018b519b85cb57acf5533f12d65bf8e/Extra-Chapter)

1. **简单任务**。空团队下的文字分析一次走完，包括首轮模型调用和交付，记录总耗时与首轮前等待。
2. **失败与恢复**。模拟原 Worker 意外退出，预期界面指出阶段与原因。Owner 确认后人工重试一次，原结果和尝试可见，当前 Worker 可领取。用可控模型替身验证，避免把模型波动混进恢复测试。
3. **开发质量对照**。选范围明确、能独立复验的任务，比较同一个 Worker 自检和「实现 → 独立验证」。独立验证直接检查相同产物。先看完成质量和实质性缺陷，再看是否值得额外调用。遇到配置或权限影响的任务，保留相同的人类决策条件。

## 全书的参考价值

| 章节 | 对本项目的启发 |
|---|---|
| [1 初识](https://github.com/datawhalechina/hello-agents/tree/2eb1c7547018b519b85cb57acf5533f12d65bf8e/docs/chapter1)、[2 发展史](https://github.com/datawhalechina/hello-agents/tree/2eb1c7547018b519b85cb57acf5533f12d65bf8e/docs/chapter2)、[3 模型基础](https://github.com/datawhalechina/hello-agents/tree/2eb1c7547018b519b85cb57acf5533f12d65bf8e/docs/chapter3) | 为自治任务明确目标、环境、工具和观察。技能分工建立在不同专业输入与产物上。模型可换，事实从工具与数据核验。 |
| [4 经典范式](https://github.com/datawhalechina/hello-agents/tree/2eb1c7547018b519b85cb57acf5533f12d65bf8e/docs/chapter4) | ReAct 负责探索和行动，复杂问题才展开规划，反思应有证据和次数边界。 |
| [5 低代码](https://github.com/datawhalechina/hello-agents/tree/2eb1c7547018b519b85cb57acf5533f12d65bf8e/docs/chapter5) | 确定性触发、发送和结果记录交给流程，模型做不易规则化的判断。操作入口围绕用户任务。 |
| [6 框架实践](https://github.com/datawhalechina/hello-agents/tree/2eb1c7547018b519b85cb57acf5533f12d65bf8e/docs/chapter6) | AutoGen 看接手和停止，CAMEL 看角色互补，AgentScope 看状态化消息与并发，LangGraph 看明确的控制流。AutoGen 示例中工程师与审查员未接入文件或测试工具，用户代理演示的是人类参与。 |
| [7 自建框架](https://github.com/datawhalechina/hello-agents/tree/2eb1c7547018b519b85cb57acf5533f12d65bf8e/docs/chapter7) | 用少量可理解的模型、回合、工具接口控制主路径。原生函数调用优先，同一工具语义适配不同模型协议。 |
| [8 记忆检索](https://github.com/datawhalechina/hello-agents/tree/2eb1c7547018b519b85cb57acf5533f12d65bf8e/docs/chapter8)、[9 上下文](https://github.com/datawhalechina/hello-agents/tree/2eb1c7547018b519b85cb57acf5533f12d65bf8e/docs/chapter9) | 区分当前执行、跨步骤事实、跨项目知识。采用按需引用和结构化笔记，先验证命中质量，再加更复杂的检索。 |
| [10 通信协议](https://github.com/datawhalechina/hello-agents/tree/2eb1c7547018b519b85cb57acf5533f12d65bf8e/docs/chapter10) | MCP 面向工具，A2A 面向跨系统任务与工件，ANP 涉及开放网络发现。同一 Plane 内先用已有工作流、问答和交付。 |
| [11 Agentic RL](https://github.com/datawhalechina/hello-agents/tree/2eb1c7547018b519b85cb57acf5533f12d65bf8e/docs/chapter11)、[12 评估](https://github.com/datawhalechina/hello-agents/tree/2eb1c7547018b519b85cb57acf5533f12d65bf8e/docs/chapter12) | 先能复现和区分失败来源，固定基线和验收，再评估 Skill 或模型的更新。训练示例不能替代运行质量证据。 |
| [13 旅行助手](https://github.com/datawhalechina/hello-agents/tree/2eb1c7547018b519b85cb57acf5533f12d65bf8e/docs/chapter13) | 固定分工后汇总，适合明确的业务产物。已知城市查天气可直接用工具，缺数据时显示缺项。 |
| [14 深度研究](https://github.com/datawhalechina/hello-agents/tree/2eb1c7547018b519b85cb57acf5533f12d65bf8e/docs/chapter14) | 规划、检索、分项总结、报告，来源和阶段成果可追溯。普通路径按 TODO 顺序执行，流式实现用线程运行独立子任务再汇合。两个入口都从新状态开始，笔记可供续做参考，执行租约和原地恢复仍是 AIW-DE 自己的职责。 [配套实现](https://github.com/datawhalechina/hello-agents/blob/2eb1c7547018b519b85cb57acf5533f12d65bf8e/code/chapter14/helloagents-deepresearch/backend/src/agent.py#L125-L284) |
| [15 赛博小镇](https://github.com/datawhalechina/hello-agents/tree/2eb1c7547018b519b85cb57acf5533f12d65bf8e/docs/chapter15) | 成员形象、历史、进度可让秘书界面呈现团队感。案例中的身份与记忆对产品表达有帮助。 |
| [16 毕业设计](https://github.com/datawhalechina/hello-agents/tree/2eb1c7547018b519b85cb57acf5533f12d65bf8e/docs/chapter16) | 先拿小场景完成可演示的闭环，明确接受条件，再扩展协作。 |
| [补充文章](https://github.com/datawhalechina/hello-agents/tree/2eb1c7547018b519b85cb57acf5533f12d65bf8e/Extra-Chapter) | Extra 02 的上下文筛选、Extra 05/08 的 Skills 渐进加载、Extra 09 的最短闭环与工程调试、Extra 10 的版本化演进、Extra 12/14 的可复验环境，直接支持上面的改动顺序。 |

两条实现路线可供比较。第一条沿用 Plane 和现有 Python Worker，先验证最短闭环，再在一个现有工作流中加入一次交接，成本集中在减少当前路径的摩擦。第二条引入团队消息管理器和动态选人，适合多个同等有效角色反复协商，代价是增加状态归属、终止、授权和恢复的协调工作。结合最近的运行证据与现有成员能力，建议采用第一条。是否进一步引入动态协调，以交付质量对照为依据。
