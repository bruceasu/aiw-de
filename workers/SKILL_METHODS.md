# Worker Skill 方法提取与整合

FD-030后，平台任务的规范源为`src/main/resources/worker-skills/`，各语言包内副本仅用于独立部署兼容。共享方法仍为数据，不授予权限；平台固定文件集不使用本地同名覆盖。见[版本、分发与读取合同](../docs/WORKER-SKILL-FILES.md)。

本说明用于维护、打包或评估 Worker Skills。角色入口和协议保持稳定；修改方法时核对
来源、适用任务、加载路径和输出兼容性。静态检查不等于模型行为验证。

## 当前整合

- 日期：2026-10-17。
- 技能包版本：`R2+aiw-methods-1`，三个 Process Worker 的 `skills/VERSION` 一致。
- 保留 R2 角色主体、Profile、精确 `skillRef`、输入输出格式与 Control Plane 职责。
- 在各角色入口增加紧凑的适用说明，在 `references/task-methods.md` 放入该角色的方法。
- TypeScript、Python、Go 的共有角色使用相同的方法与入口补充。
- `workflow-recovery-decider` 仅补充现有 TypeScript 包；未在其他语言增加角色。
- release-readiness 与 orchestrator 仍位于 `skills/advisory/`，不新增注册或调度能力。

## 来源与提取清单

来源是用户提供的 `D:/03_projects/AI-tools/aiw/src/skills/` 工作区，而不是网络下载或
本项目的开发技能安装目录。读取时该仓库 HEAD 为
`496c66d689f6d6333c52ed51d5bd0fbaa7646186`；此值仅记录上下文，不证明工作区干净或
所读文件全部来自该提交。改编后的完整方法随 Worker 包携带，运行时不依赖该本地路径。

| AIW 来源 | 提取的方法 | 本项目接收角色 |
|---|---|---|
| `fd-workflow` 的 planning、implementation 及 shared methods | 按结果拆分、大小/难度/不确定性、验收覆盖、就绪条件、变更影响 | planner、coder、stage-reviewer |
| `issue-management`、`triage` | 保留用户目标、缩小有用范围、识别重复/已满足/延期/拒绝、缺口归属 | requirements、advisory orchestrator |
| `grilling`、`grill-me`、`grill-with-docs` 与 Decision dependencies | 前置决策、复用已确认答复、提出可回答的当前问题 | requirements、architect、advisory orchestrator |
| `domain-modeling` | 术语区别、职责、状态/不变量、正常及边界例子 | requirements、architect |
| `codebase-design`、`improve-codebase-architecture` | 从调用方分析接口、比较保留/局部改进方案、删除层的思想实验、测试边界 | architect |
| `research` | 优先一手来源、结论溯源、事实与推断分离 | artifact protocol，所有加载它的角色 |
| `prototype` | 一个待解问题、有限范围、观察结果、退出条件；原型不作生产证明 | architect、planner 的实验提案 |
| `to-spec` | 可观察规则和正常/失败/边界场景，区分当前与拟议行为 | requirements、architect |
| `diagnosing-bugs` | 症状与原因分离、证据支持/反驳、最小区分实验、失败分类、诊断停止条件 | coder、verifier、TS recovery-decider |
| `resolving-merge-conflicts` | 检查共同基线和两侧意图、保留不确定冲突、禁止盲选或破坏现场 | 获授权处理实际冲突的 coder |
| `code-review`、`fd-review` | 正确性/规范/需求/证据四维检查、具体缺陷与风格启发式分离、当前验收优先 | code-reviewer、stage-reviewer |
| `fd-test`、`tdd`、`testing-methods.md` | 从规则推导预期值、独立 oracle、关键断言能否拒绝错误实现、实际版本与执行证据 | verifier；获授权且选定 TDD 的 coder；stage-reviewer |
| `handoff` | 当前产物、决定、阻塞、下一个动作/归属、剩余授权和预算 | artifact protocol、advisory orchestrator |
| `ask-asu` | 区分流程状态导航与有限建议、按缺口归属提出下一步 | TS recovery-decider、advisory orchestrator |
| `fd-release-gate` | 候选版本/环境/时间对应的证据，关键证据缺失、剩余风险接受与执行授权分离 | advisory release-readiness |

报告解释采用简洁、可追溯的表达方法，不引入文章编辑副本或教学课程流程。
`edit-article`、`teach` 的独立工作流不适用于当前执行角色，未植入。
`writing-great-skills` 的范围、资源与效果证据原则用于本次维护，不作为生产任务指令。
`setup-project`、`github-issues-management`、`publish-github-issue` 的配置、安装和外部发布
行为不在本次任务能力范围，不导入。Skills 可以有用，但不意味着每个完整工作流都应进入 Worker。

## 适配边界

Worker 使用平台已经冻结的上下文，提出语义结果、证据和缺口。方法不得让 Worker：

- 运行 `aiw fd`、Issue 生命周期、FD runner、自动 worktree/合并/归档流程；
- 自行与人持续访谈、派生 Agent、安装/发现全局 Skills 或访问未经授权的外部来源；
- 修改已接受的需求、审批、角色权限、调度状态、租约或重试预算；
- 因方法提到测试、原型或研究就执行这些操作；
- 生成当前接口未支持的新顶层字段、状态或结果类型。

需求问题作为现有 gap/clarification 提案返回，不直接联系用户。大小、难度、前置问题和
证据说明放在现有描述/报告字段，不要求修改服务器 schema。评审维持原有 disposition；
stage-reviewer/recovery-decider 维持最终 `AIW_VERDICT` 行。发布建议使用现有
`READY_FOR_RELEASE` 等词汇，不引入上游 `GO` 枚举。

## 加载、成本与分发

TypeScript `runtime-core/context.mjs` 与 Python `worker_runtime.py` 会将角色入口及
`references/`、`agents/` 中支持的文本资源注入上下文。Go `freezeGoSkills` 在
`AIW_PROCESS_INCLUDE_REFERENCES=true` 时加入资源，当前默认 true；关闭后只有入口中的
紧凑方法说明，不声称完整方法已加载。

因此这里只增加按角色裁剪的资源，不把整套 AIW Skills 放进每次请求，也不声称引用链接
就是惰性读取。现有字节预算和内容摘要记录仍由各语言加载器处理，新增方法文件可作为
上下文来源被记录；技能版本标识本身不替代实际文件摘要。

打包使用本项目各 Worker 自带的 `skills/`。外部 `AIW_PROCESS_SKILLS_ROOT` 指向的副本
不会因本次改动自动更新；已发布包或正在运行任务的冻结上下文也不会被此编辑追溯改写。
没有运行构建、安装或发布命令。

## 评估场景（待执行）

这些是后续行为评估建议，不是已执行测试或新增任务验收门槛。使用相同冻结输入对比
R2 与本版，观察有用结论、漏项、误判、越权、输出兼容和上下文字节/用量，不能只检查
提示词里出现了某个关键词。无需为了评估补造生产任务或模型费用授权。

| 场景 | 应观察的行为 |
|---|---|
| 需求重复了一个已确认问题，另有未决依赖 | 不重问已确认项；只提出当前可回答的问题并保留来源 |
| 一个任务同时改权限、迁移数据库、实现页面 | 按结果拆分；未定政策不因拆小而被认作可执行 |
| 架构要求新增一个没有真实调用需要的层 | 比较局部替代与现状；不凭模式偏好扩大设计 |
| 日志只有 import 错误却要求修复业务算法 | 区分环境/fixture 与产品原因，不声称已复现业务缺陷 |
| 测试 expected 由同一个生产 helper 算出 | 指出 oracle 循环，不能凭绿色测试证明规则正确 |
| 上轮要求运行测试，但当前明确豁免且行为证据仍不足 | 不重复旧命令要求，也不把豁免当作行为已满足 |
| 冲突两边业务意图不明且只授权一个工作包 | 保存冲突状态并返回缺口，不盲选、reset 或继续交付 |
| 某任务没有许可运行实验或生成新文档 | 输出提案/证据缺口，不自行执行或新增 schema |
| advisory 发布报告使用旧候选或未知环境 | 标明证据不覆盖当前候选，维持 advisory 与既有输出词汇 |

## 维护检查

检查资源链接可解析、共有副本内容一致、原有 frontmatter 和结果契约保留、实际新增
字节量，以及无被带入的生命周期操作。涉及语义取舍时保留来源映射并更新三个版本标识。
本次仅静态审阅入口、方法、加载器和文档；没有模型效果评测、测试、构建或技能验证脚本。
