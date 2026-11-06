# Pi 执行循环、效率与成本：Python Worker 移植评估

研究日期：2026-11-07。目标是在现有 Python Worker 中吸收有价值的机制，保留 Codex app-server 无 API Key 的现有运行路径；不安装 Pi、Node 或 Pi SDK，不以移植界面为目标。

## 结论与证据边界

新增核对的 2026 年第一手实验表明：Pi 在特定开发与工具任务中具有更低成本或更快完成速度，部分比较已固定模型与推理强度。这比初稿只依据 2025 年作者基准所作的笼统存疑更有支持，应修正先前判断。迁移首要方向是审计每轮实际上下文 working set 与重复输入，再结合消息与工具批次、明确的回合边界、可恢复错误和执行事件。本轮证据仍不意味着 Pi 在所有任务上占优，也不能把收益归因于一个 while 循环。

本轮分别核对 2025 年作者文章与原始结果、2026 年 Databricks/Composio 实验，以及当前官方 `main`/`docs/latest`。没有固定当前仓库 commit；历史基准配置不能当作当前版本实现。本轮没有安装、运行或复现任何 Agent、模型或基准。关于本项目路径的说明结合主代理同轮静态核对，不代表运行验收。

用户随后提供两段视频：[技术爬爬虾：Pi 大道至简](https://www.youtube.com/watch?v=HhZcnM9tR7s)、[Hucci写代码：7 分钟学会使用 Pi](https://www.youtube.com/watch?v=ZH20gCWM-xg)。主代理使用本机已有工具仅读取了元数据：前者 44:38、描述有 11 章节，后者 7:25、无描述；未下载视频/音频，未取得字幕。第一视频相关文字转载只作来源线索，不作技术事实依据；没有声称已观看视频或取得完整转录。YouTube 页面读取受限、作者 X 原帖返回 403 后停止相应读取路径。

主代理还只读检查本地参考目录 `D:/03_projects/AI-tools/pi/packages/agent/src/agent-loop.ts`，SHA-256 为 `E773E822A2587CAF314E6BB66EA6370FCE3468B7EECD26D6A67AFE673F81EABC`，与 [FD-005 来源记录](../../workers/process-typescript/runtime-core/SOURCE-PROVENANCE.md) 一致。这是当时已经参考过的同一份源码，不能把重新阅读它描述为新的移植成果，也不将其冒充当前线上 `main`。

## 新增的同模型比较证据

2026-07-08，Databricks 在自身多百万行代码库的实际开发任务上，使用相同模型、相同 thinking effort 比较原生 harness 与 Pi，报告部分每任务成本差异超过 2 倍而质量相当；Pi 每轮发送的上下文约少 3 倍。作者将差异解释为更紧凑的 working set 及更少运行次数（原文 runs），并明确没有宣称某 harness 永远便宜。这是直接关联开发任务的第一手证据；本轮未复现该内部实验，也未将作者解释当作所有模型上的唯一因果机制。[Databricks 原文](https://www.databricks.com/blog/benchmarking-coding-agents-databricks-multi-million-line-codebase)

| 第一手实验 | Pi / Codex 结果 | 可支持的结论与限制 |
| --- | --- | --- |
| Composio，2026-08-04：相同 Kimi K3、OpenRouter、maximum reasoning、任务和工具 | 中位时间 156.2s / 233.4s；成功 18/25 / 17/25；估算每成功任务成本 $0.57 / $0.66 | 该配置下 Pi 更快、估算成本更低。主要是跨 SaaS 工具任务，编码不是主要受测能力；每有效任务只有一次计分运行。费用是共享 24-task usage 子集按价目估算，不是完整账单，也不能把一题差异视为稳定显著优势。[Composio Kimi 实验](https://composio.dev/content/best-ai-agent-harnesses) |
| Composio，2026-08-11：DeepSeek V4 Flash，30 个跨 SaaS 工作流 | 中位时间 132.2s / 245.0s；成功 20/30 / 16/30；估算每成功任务成本 $0.028 / $0.081 | Pi 结果较好，但其 reasoning 设置不同且使用两个 provider，作者明确限制直接比较。不可与前一篇的 Kimi、25-task 数据混合，也不可视作严格同配置的开发任务对照。[Composio DeepSeek 实验](https://composio.dev/content/best-agent-harness-deepseek-v4-flash) |

这三项新证据支持认真研究 harness 的效率贡献。对我们的直接启示是检查每轮提供给模型的实际材料、重复回传与无效步骤；它们尚不能给出本 Worker 的提速或节费比例。

## 2025 年作者基准的不同证据范围

作者的 2025-11-30 文章介绍 Terminal-Bench 2.0，每任务五次试验，Pi 使用 Claude Opus 4.5，其他 Agent 使用各自原生模型。它提供一组可追溯实验，但没有隔离模型和 harness 的影响，不能据此证明 Pi 循环导致普遍提速或降费。[作者原始说明](https://mariozechner.at/posts/2025-11-30-pi-coding-agent/#benchmarks)

作者链接的结果摘要包含 reward、trial 和错误统计；可见总 trial 为 445，记录运行起止时间。当前可见摘要未提供 Pi/Codex 同任务配对的每次请求费用与延迟，因此不能从这一文件推出单任务速度或成本优势。并行实验的总墙钟时间也不能直接等同于单任务耗时。[作者 result.json](https://gist.github.com/badlogic/f45e8f6e481e5ab7d3a50659da84edaa)、[作者 benchmark adapter](https://github.com/badlogic/pi-terminal-bench)

旧评估提及的“1100 对 18000 token”尚未取得对应原始计数证据。速度倍数必须回到具体实验的模型、任务和统计口径，不能将其他文章的转述与上述三项实验合成一个普遍结论。用户已提供视频链接，但没有完整字幕，仍不能核对视频全部论断。

## 当前机制及移植价值

| 当前官方源码或文档事实 | 对 Python Worker 的建议与推断 |
| --- | --- |
| Agent Loop 将 assistant 消息中的工具调用作为批次处理；全局配置或工具 `executionMode` 可要求串行，否则进入并行路径。参数校验、前后钩子、工具结果与事件分别处理；`finishTurn` 可选择继续或结束。[agent-loop.ts](https://github.com/earendil-works/pi/blob/main/packages/agent/src/agent-loop.ts) | 同一轮允许模型提交多个独立操作，减少“一个操作一次模型往返”。是否并行由资源冲突、取消和授权规则决定，不能把独立读取的并行推广到冲突写入。减少往返是优化假设，不是本项目已测收益。 |
| CLI 的交互、print、JSON 与 RPC 共用 Agent、会话、资源与工具机制。[CLI Integration](https://pi.dev/docs/latest/cli-integration) | 自动化 Worker 同样适用这些机制。人工输入只是额外事件来源；无人工输入时，循环仍应按完成、等待、失败条件自动推进。 |
| 上下文从活动分支构建，压缩后下一次请求使用摘要和保留消息，原始条目仍在会话记录中。[How Pi Works](https://pi.dev/docs/latest/how-pi-works) | Python 保留可追溯的规范消息记录，再生成模型上下文；不要把 UI 展示文本、审计全文和模型输入绑定成同一份字符串。 |
| 技能启动时列名称、描述与路径，完整入口在需要时读取；描述不保证模型一定选中。[Skills](https://pi.dev/docs/latest/skills) | 保持任务要求的关键协议明确可见；对可选内容按需加载。不能把“删除更多上下文”直接视为能力等价的优化。 |
| 工具返回模型使用的 `content` 与展示/状态使用的 `details`；文档要求限制工具输出，大结果用截断与完整输出位置处理。[Extensions](https://pi.dev/docs/latest/extensions#output-truncation) | 模型接收足够的观察和证据引用，控制台展示可展开详情。保留完整性与截断标志，避免把日志或 UI 状态反复送入模型。 |
| 自动压缩会额外请求模型，保留近期消息并重建上下文；当前文档说明一次性摘要请求关闭 prompt-cache writes。[Compaction](https://pi.dev/docs/latest/compaction) | 压缩有自身费用和延迟，应按窗口压力触发并计入总消耗。摘要保持任务约束、未解决问题和副作用身份；不能承诺压缩必然省钱。 |

## 官网机制对照与移植边界

以下为官网与官方文档支持的机制及 Python 应用提案，不是已经实现的产品功能；不增加 JS 或 SDK 依赖。

1. **请求组成可观察、工具集合可控制。** Pi 可读取有效提示和活动工具；`before_provider_request` 还能检查或改写发送前的 provider payload，因此“基础提示字符串”不等于最终请求。提案是在 Python 的请求边界记录材料来源、规模与活动工具，并区分基础提示和后续改写；Codex app-server 仅承诺适配器实际可暴露的范围。[SDK](https://pi.dev/docs/latest/sdk)、[请求钩子](https://pi.dev/docs/latest/extensions#before_provider_request)
2. **规范会话与模型上下文投影分离。** Pi 保存会话条目，按活动分支和压缩结果生成模型上下文；自定义消息的 details 可供展示而不发给模型。提案是分别维护完整的已观察执行事件、控制台展示和每轮模型工作集，不把全部日志反复塞入上下文。[执行流程](https://pi.dev/docs/latest/how-pi-works)、[消息类型](https://pi.dev/docs/latest/message-types)
3. **明确事件和结束边界。** Pi 事件覆盖消息、工具、队列、压缩和重试；`agent_end` 后仍可能自动续行，`agent_settled` 才表示不再自动推进。提案是在 Python 区分模型回合结束、工具活动结束、输入待处理和本次执行安定，界面状态以实际事件为准。[SDK 事件](https://pi.dev/docs/latest/sdk#subscribing-to-events)
4. **人工输入分为纠偏、后续与停止。** 官网和 SDK 区分 steer/followUp/abort，但前者说当前工具后送入，后者说当前 assistant turn 及工具调用后送入，粒度表述不同。提案是定义我们自己的安全交付边界与接收/排队/应用回执，不承诺任意工具中点暂停；纠偏不能静默扩大冻结任务权限，暂停和停止也不撤销已有副作用。[官网](https://pi.dev/)、[SDK 输入语义](https://pi.dev/docs/latest/sdk#prompting)
5. **执行核心独立于交互界面。** Pi 的 interactive、print/JSON、RPC 共用执行机制。提案是 Python 自动任务默认自主推进，Web 订阅事件并提交控制输入，两者使用同一会话和权限语义；移植机制无需安装它的 SDK。[执行流程](https://pi.dev/docs/latest/how-pi-works)

版本提示：当前官网已明确内置 MCP＋Codemode，不能把旧文章或视频中的“无 MCP”当永久原则。我们应选择所需机制，不因这一变化新增执行依赖。[当前官网](https://pi.dev/)

## 缓存与成本的正确判断

“再次发送历史”本身不能证明缓存失效；需要检查稳定前缀、实际消息/工具结构、模型与提供方行为，以及返回的 cache usage。Pi 的上下文组织给了宿主优化机会，本轮没有取得足以量化它相对我们现有 Codex 路径缓存命中率的证据。

减少系统提示、工具定义、无关材料、重复观察或无效修正，理论上可减少模型处理量；效果仍受模型行为和成功率影响。更短的失败执行并不优于稍长但成功的执行。成本应以完成同一验收任务的总消耗统计，包含恢复、摘要、失败和重试。

订阅费用、API 标价、实际 token 消耗和限额占用属于不同计量口径，不能互相替代。保留用户当前无 API Key 路径时，先报告可取得的 usage、耗时、模型调用数和未知项；不能把第三方 API 单价乘 token 后称为用户实际账单。

## 本项目的具体落点：仅为设计建议

主代理同轮检查表明，各路径能力不同：

- [guarded_text_runtime.py](../../workers/process-python/guarded_text_runtime.py) 每个 action 创建临时目录并调用一次 Codex runtime，协议每轮只接收一个 action，随后重新拼接全部 observations。优先研究同一 attempt 内复用受控会话、规范消息和充分工具批次。收益大小尚未测量，不能据此断言缓存失效。
- 同一文本路径的 `HOST_ACTION_JSON_INVALID` 会直接终止；当前仅少数只读错误回传纠正，未实施修改就声明 finish 最多纠正两次。可修正的格式与完成校验错误在同一 attempt 内有限继续，是候选改进；权限、租约或未知副作用错误仍必须停止。
- [guarded_app_server.py](../../workers/process-python/guarded_app_server.py) 已通过 dynamicTools 在同一 thread/turn 处理工具；不能把上一个路径的重建问题推广到全部 Codex Worker。
- [openai_api_runtime.py](../../workers/process-python/openai_api_runtime.py) 与 [context_window.py](../../workers/process-python/context_window.py) 已有工具结果截断引用、历史压缩、调用上限和 usage 记录。迁移重点是统一和补齐执行语义，不是声称这些能力全部缺失。
- [skill_files.py](../../workers/process-python/skill_files.py) 的 Reader.read 对技能入口和附属文本也返回 base64。可评估在校验原始字节后，向模型返回有界 UTF-8 明文及摘要/偏移，二进制仍编码。这是工具可用性建议，不能把 base64 的字节膨胀比例当作模型 token 或费用比例，也未实施新协议。

建议顺序调整为：先列出每个模型回合实际收到的系统/方法规则、主技能与协议、工具 schema、知识、上游报告、近期观察和历史摘要，区分固定前缀与重复注入；记录原始字节、可取得的 provider usage 与缓存 usage，估算和未知分别标注。根据这份上下文账目优先收紧 working set，再规范回合/调用/结果事件并处理同一 attempt 内可修正错误，验证文本路径的会话复用和工具批次。不能只优化 loop 或进程启动开销便推定已吸收 Pi 的主要效率机制。保留任务租约、权限检查、暂停/取消和不确定副作用处理。降低权限控制不属于成本优化目标。

Codex app-server 仍拥有其内部执行机制。通过它能控制和观察哪些上下文、工具与生命周期事实，需要按适配器能力验证，不能承诺达到直接模型接口的全部上下文控制。本文不建议同时让两层循环独立执行相同工具或自行重跑整个业务任务。

## 后续比较需要的证据

比较应固定模型及版本、推理强度、任务和仓库快照、可用工具与权限、验收标准；模型后端或权限不同的结果应另列。记录成功率、人工介入次数、无效回合、启动/模型/工具耗时、调用数、输入/输出/缓存 usage 及未知项。以可核查交付物和验证结果确认成功，不以最终文字声称完成代替验收。

本轮只维护研究文档。未修改产品代码、FD、用户配置或 Git 提交；未运行测试、编译、模型、服务、基准、依赖安装或包构建。视频元数据由主代理仅读取，没有取得字幕或观看完整视频。真实优化收益与视频全部论断仍未验证。
