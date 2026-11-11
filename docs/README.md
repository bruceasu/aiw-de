# 文档导航

先读 [系统如何工作](SYSTEM-OVERVIEW.md)，再按目标选择下面的文档。这里是 `docs/` 的统一入口。

本文按仓库实现和文档职责整理，不代表全部部署或模型流程已经运行验证。功能支持与验证范围见对应说明和 FD；设计草案不能作为已上线能力。

## 使用与日常操作

| 想做什么 | 文档 |
| --- | --- |
| 理解 Plane、秘书、工厂、Worker 和 Plan | [系统如何工作](SYSTEM-OVERVIEW.md) |
| 配置默认模型和自动启动 Worker | [Agent 工厂](AGENT-FACTORY.md) |
| 配置 ChatGPT、API Key、DeepSeek 和模型目录 | [模型连接与认证](MODEL-AUTHENTICATION.md) |
| 安装、连接和使用独立秘书 | [个人秘书指南](../personal-secretary/README.md) |
| 启动桌面组件 | [桌面启动](DESKTOP-STARTUP.md) |
| 在许愿池讨论需求 | [需求讨论](current-zh/GOAL-WORKSHOP.md) |
| 创建和推进 Plan | [Flexible Workflow Plans](current-zh/FLEXIBLE-WORKFLOW-PLANS.md) |
| 准备单项任务、组合工作方法 | [任务准备](EXECUTION-CONFIGURATION.md)、[组合角色](COMPOSABLE-ROLES.md) |
| 查看执行、补充方向、暂停或取消本次执行 | [Worker 工作台](WORKER-WORKBENCH.md) |
| 理解目录、worktree 与交接文件 | [Plan 位置与交接](PLAN-LOCATIONS-AND-HANDOFFS.md) |
| 选择自检、里程碑或独立 Reviewer | [评审策略](WORKFLOW-REVIEW-POLICY.md) |
| 回答执行中的正式问题 | [正式问答](FORMAL-QUESTIONS.md) |

## 部署与安全

| 主题 | 文档 |
| --- | --- |
| 工具、执行权限与计量模式 | [受控执行](EXECUTION-GUARD.md)、[动作权限](ACTION-PERMISSIONS.md) |
| Worker 环境与启动 | [Worker 总览](../workers/README.md)、[Python](../workers/process-python/README.md)、[TypeScript](../extern-workers/process-typescript/README.md) |
| 通用配置和运维 | [配置参考](current-zh/07-CONFIGURATION-REFERENCE.md)、[运维](current-zh/06-OPERATIONS-GUIDE.md)、[故障恢复](current-zh/11-TROUBLESHOOTING-AND-RECOVERY.md)；阶段性内容需与专题及源码核对 |
| Docker | [Docker 指南](current-zh/DOCKER.md) |

## 扩展与界面维护

| 主题 | 文档 |
| --- | --- |
| 软件任务与工作流图 | [软件开发](SOFTWARE-DEVELOPMENT.md)、[工作流图](WORKFLOW-GRAPHS.md) |
| 能力与任务输出 | [Worker 能力](current-zh/WORKER-CAPABILITIES.md)、[任务输出](current-zh/TASK-OUTPUTS.md) |
| Skill 文件与选配 | [技能文件](WORKER-SKILL-FILES.md)、[可选技能](WORKER-OPTIONAL-SKILLS.md) |
| 方法与原型 | [技能方法](WORKER-SKILL-METHODS.md)、[委派方法](WORKER-DELEGATION-METHODS.md)、[原型技能](WORKER-PROTOTYPE-SKILL.md) |
| UI 与外观 | [Web UI](web-ui.md)、[外观](APPEARANCE.md)、[外观资源](APPEARANCE-ASSETS.md) |

## 设计、研究与历史

以下资料解释需求和演进，不作为默认安装步骤。

| 资料 | 用途 |
| --- | --- |
| [FD 索引](features/INDEX.md) | 设计、Work Items、状态和验证证据；状态以各 FD 为准，索引可重建 |
| `issues/`、`research/`、`archive/` | 需求发现、调查和历史阶段依据 |
| [用量与价格设计](WORKER-USAGE-AND-PRICING-DESIGN.md) | 设计入口；按其状态及相关 FD 判断实施范围 |
| [Pi 融合计划](worker-pi-integration-plan.md)、[工厂参考评估](agent-factory-reference-assessment.md)、[Codex no-store 设计](worker-codex-no-store-design.md) | 方案与评估，不等于当前支持 |
| [Runtime SPI](<AgentRuntime SPI.md>)、[AIW review](aiw-review.md) | 专题设计，使用前核对实现 |
| [知识载体需求](aiw-knowledge-carrier-requirements.md)、[秘书知识交接](personal-secretary-knowledge-handoff.md) | 知识库需求与交接设计 |
| [早期秘书安排](SECRETARY.md) | Plane 内安排功能的阶段记录，区别于独立个人秘书 |
| `templates/` | 文档模板 |

## 旧版中英文手册

[`current-zh/`](current-zh/README.md) 和 [`current/`](current/README.md) 保留成套参考手册，但包含 R8/R9 阶段的架构、命令和能力描述，尚未逐篇完成当前实现核对。`current` 这个目录名不代表内容全部最新，英文版也不能假定与中文专题同步。

发生冲突时按 [文档基线](DOCUMENTATION-BASELINE.md) 核对适用范围、源码及 FD。不要把旧 Workspace 管理、固定 Git 交付、早期秘书和 Codex-only 流程拼成当前使用步骤。
