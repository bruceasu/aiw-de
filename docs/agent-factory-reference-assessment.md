# Agent Factory 对 AIW 的参考价值

研究日期：2026-10-14。

后续需求记录：[AIW 知识库载体需求](aiw-knowledge-carrier-requirements.md)，用于讨论如何按 AIW 需要改造 knowledge-base。

## 结论与范围

值得参考，尤其是 Worker 产品化、上下文来源治理和评估闭环。AIW 现有的软件工程控制平面可以作为这些能力的基础。建议先围绕一个现有角色探索，形成可审阅的规格和案例，再决定是否增加平台功能。

本次假设：“我们的目标”指本仓库描述的 AI 软件工程 Worker 管理与执行平台，并可能逐步扩展到其他工作领域。跨领域目标尚未确认。

事实来源为官网章节和仓库正式文档；以下平台现状仅代表文档声明。本次没有审计实现代码、运行系统、连接书的 MCP 服务，也没有核验书中市场数据、效率数字和第三方产品兼容性。建议不是已批准的需求或实现计划。

## 事实：相似方向与不同范围

书将 AI Worker 描述为承担具体角色、持续执行工作并接受人类监督的系统，强调规格、领域知识、工程架构和监督。[About](https://agentfactory.panaversity.org/docs/about)

书的目标覆盖企业 workforce、个人代理和业务领域知识。AIW 的 README 将本项目定位为 Java Control Plane，管理采用 pull 模式的软件工程 Worker。因此，双方方向相近，但范围并不相同。[Thesis](https://agentfactory.panaversity.org/docs/thesis)、[本仓库 README](../README.md)

仓库文档声明已有：需求到实施的工程流程、独立 Review/Verification、Repair、Engineering Gate、人类审批；任务持久化、租约和恢复；结果与不可变 Git revision 的绑定；Worker 注册、能力匹配和并发控制。[架构](current/02-SYSTEM-ARCHITECTURE.md)、[能力清单](current/13-CURRENT-CAPABILITIES.md)

## 建议一：建立从成功任务到 Worker 的晋升流程

书提出，先确认任务经常出现、形态稳定且值得自动化，再把已证明有效的方法转化为长期 Worker。晋升包含四项：brief 成为规格；人工检查成为 eval；人工逐步指挥成为自主执行和异常升级；临时会话成为持久运行环境。[From One-Off to Worker](https://agentfactory.panaversity.org/docs/from-one-off-to-worker)

对 AIW 的建议：选一个已有角色，如 Code Review Worker，从真实历史任务中归纳输入、输出、完成条件和异常情况。先形成一个版本化角色包，记录：

- 角色职责、输入输出契约、适用及不适用范围。
- Skill 与工具需求、知识来源、允许动作和资源边界。
- 人工标注的正常、失败和边界案例，以及验收规则。
- 遇到缺失证据、矛盾要求或超范围动作时，交给谁处理、提交什么材料。
- 角色包版本和变更原因。

这是待设计的逻辑契约，不预设要增加数据库表、修改 Worker Protocol 或统一所有运行时。书中“第三次重复任务”的提示可以作为发现机会的启发，不能当作可靠性的验收标准。

## 建议二：给 Reviewer 和 Verifier 本身建立可信度证据

书强调校准检查者：用人类已判定的好坏案例比较评判结果，分别统计误放行和误拒绝；按类别与严重程度观察，防止总体一致率掩盖危险漏判。[Trusting the Checker](https://agentfactory.panaversity.org/docs/trusting-the-checker-crash-course)

AIW 文档已有独立 Review/Verification 和版本绑定，这提供了责任与证据的结构；这些文档没有证明评判准确率。

建议从少量人工确认的案例开始，包含正确结果、明显缺陷、需求不符和证据不足。记录检查者是否识别问题及提供可定位依据。模型、Skill 或 rubric 变更时，对同一组案例比较结果。阈值由我们的风险和案例分布决定，不直接采用课程中的经验数字。

## 建议三：把上下文组织成有来源的任务包

书的 System of Context 将检索与权威确认分开：搜索发现资料，由拥有记录的系统确认适用性；动态事实通过实时查询获取。任务上下文应保留来源、版本、适用时间、冲突、缺失证据和权限，并随任务变化刷新。[System of Context](https://agentfactory.panaversity.org/docs/ecosystem/system-of-context)

AIW 可以先在任务材料中记录需求版本、Git base revision、适用仓库指令、Skill 版本、验收条件、来源指针和未决问题。任务状态及审批状态仍由 Control Plane 的权威记录决定。

这里有两种不同记录：平台保存任务和审批等运行状态；仓库规格和领域资料提供工作依据。向量索引可用于寻找资料，但不能凭相似度决定哪个版本有效，也不能替代数据库、Git 或正式审批记录。

书还把自身内容提供为只读 MCP 知识服务，并声称可引用原章节、随书更新。这可作为将来的知识接口参考，本次只查看介绍，没有验证服务可用性或回答质量。[Content System of Record](https://agentfactory.panaversity.org/docs/ecosystem/system-of-record)

## 建议四：把约束落到实际执行边界

书将 harness 的职责归纳为约束、提供信息、验证、纠错、升级，并区分提示指导和工具／系统的强制限制。[Harness Engineering](https://agentfactory.panaversity.org/docs/harness-engineering-crash-course)

AIW 的租约、状态机和审批 authority 在文档层面与这种思路一致。下一步可审视工具调用、网络、路径和资源预算：哪些由运行环境强制执行，哪些仅由提示表达。

当前文档明确说明，Web Console 登录不能覆盖所有管理 REST API，通用 Skill/tool 权限治理也尚未完成。扩展到远程业务系统前，应单独设计身份、授权和审计边界。[安全边界](current/09-SECURITY-AUTHORITY-AND-TRUST-BOUNDARIES.md)

并发上限只限制同时运行数量，不能据此推断已有金额、token 或单次任务成本控制。是否需要新增预算机制，应先确认实际使用场景。

## 建议五：让失败经验形成可审阅的改进

书建议将真实轨迹经过人工分流后转成评估案例，并分别检查输出、工具使用、执行轨迹和安全边界；同时承认 eval 无法覆盖所有新情况。[Eval-Driven Development](https://agentfactory.panaversity.org/docs/eval-driven-development-crash-course)

对 AIW 的建议路径是：失败 attempt → 人工归类 → 代表性案例 → Skill／rubric 修改提案 → 比较评估 → 人工确认角色包版本。需要记录成功率之外的修复次数、人工介入、耗时和成本，并明确统计口径。

Worker 可以提出改进，但不能自行降低通过条件或扩大权限。生产轨迹进入案例集之前，还需要明确敏感信息处理和使用授权。

## 优先次序与暂缓事项

| 优先级 | 探索项 | 首个可审阅产物 |
|---|---|---|
| 1 | 一个现有角色的 Worker 规格与异常升级 | 角色说明和版本化契约草案 |
| 2 | 检查者校准 | 人工标注案例、判定标准和误判分类 |
| 3 | 任务上下文来源 | 一份来源与版本清楚的任务材料样例 |
| 4 | 失败到改进的闭环 | 案例晋升和角色包变更流程 |
| 按需求 | 权限与成本治理 | 已确认的执行边界与预算需求 |

书的架构选择章节强调，复杂度必须由具体任务属性支持。[Choosing Agentic Architectures](https://agentfactory.panaversity.org/docs/choosing-agentic-architectures-crash-course)

据此，本次不建议把采用 Paperclip、OpenClaw、Kubernetes、Dapr 或向量数据库直接列为下一步，也不建议立即扩展递归 Agent、自动扩编、自修改或自动支付。先用真实工作证明需求，再评估对应组件。10-80-10 可以帮助表达人的职责，但不能作为固定时间比例或效率承诺。

## 本地配套仓库与导览

用户提供的参考仓库：`D:/03_projects/AI-tools/panaversity-agentfactory`。其 README 直接链接 Agent Factory 网站，包含课程书稿与配套平台应用，确认与教材相关。

用户已有的 [overview.md](../../panaversity-agentfactory/overview.md) 可作为本地资料入口。本次已阅读全文，并与仓库 README、应用目录、课程目录和 PanaversityFS README 做局部交叉核对；导览中的所有服务行为并未逐项验证。

| 参考入口 | 对 AIW 的用途 |
|---|---|
| `apps/learn-app/docs/thesis.md` | 理解 Agent Factory 的目标和术语 |
| `apps/learn-app/docs/01-General-Agents-Foundations/` | 查找规格驱动开发、上下文与通用 Agent 方法 |
| `apps/learn-app/docs/05-Building-Custom-Agents/` | 查找角色 Worker、工具集成和 Agent 实现材料 |
| `apps/learn-app/docs/05-Building-Custom-Agents/47-evals-agent-performance/` | 评估数据集、binary criteria、LLM judge、错误分析和回归保护的专题资料 |
| `apps/panaversity-fs-py/README.md` | 查看教材内容存储、搜索、版本冲突和审计的 MCP 设计说明 |
| `specs/`、`papers/`、`history/` | 在具体问题需要时查阅设计及历史背景，先确认材料状态 |

注意两个事实边界：

- 本次读取的本地 HEAD 为 `df8aec72`，提交日期 `2026-02-25`，标题 `update preface`。本地课程布局与本次查看的官网章节不完全对应；提交日期不能单独证明工作区所有内容的更新时间。引用本地资料应记录路径和 revision，不默认与官网同步，也不自动执行 pull。
- PanaversityFS README 列出了 `read_content`、`write_content`、`delete_content` 等工具。这是具备内容管理能力的 MCP；官网介绍的 `https://sor.panaversity.org/mcp` 是只读知识检索服务。本次在 apps/libs 的定向搜索未找到该线上地址及 `zia-tutor-ai`，尚不能确认两个服务的实现关系。

根目录 `evals/` 本次看到的是 `assessment-architect` 和 `flashcards`，与用户导览所述教学评估用途相符；不能据此认定仓库已提供可直接复用的生产 Worker 评估套件。

使用方式：将本地仓库作为可定位、可引用的资料源；围绕 AIW 的具体问题读取相关章节，区分书中的原则、教学示例和配套平台实现，再形成适用于 AIW 的建议。暂不安装其中的 Skill、不运行服务、不迁入依赖。

## 调研与校验记录

- 阅读官网 About、Thesis、From One-Off to Worker、System of Record、System of Context，以及检查者、eval、harness 和架构选择章节的相关段落；不是全书通读。
- 使用 PowerShell 的 Get-Content、Get-ChildItem、rg --files 和 git status --short 读取指令、了解文档结构与工作区状态。
- research 技能的仓库路径和用户技能路径均无法读取；未依该技能执行额外流程。
- 新增本研究笔记，未修改源码、协议、配置或正式能力声明。
- 仅进行文档静态检查；未运行测试、编译、构建或运行验证。
