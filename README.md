# AI Worker: Digital Employee

让 AI 以数字员工的方式协作，把需求推进为可审查、可验证、可追溯的工程结果。

**AI Worker** 是一个面向数字员工协作的平台。你提出需求，由不同角色的 Worker 承担需求分析、架构设计、实施规划、编码、集成、审查、验证和修复。平台负责组织任务、跟踪执行、管理审批，并保留结果与决策依据。

当前落地场景是**软件工程**。数字员工通过外部 Worker 进程执行任务，Java 控制平面统一管理任务状态、依赖、调度、租约和工程门禁，人通过 Web Console 观察进度、干预执行并作出批准决定。

## 数字员工如何协作

```text
提出需求
  → 需求分析 → 架构设计 → 实施规划
  → 一个或多个实现任务 → 按需集成
  → 代码审查 + 验证 → 按需修复
  → 工程门禁 → 按策略进行人类审批
```

每个 Worker 声明自己的能力并主动领取已就绪的任务。任务之间通过依赖关系衔接；控制平面维护状态和执行权，Worker 在租约约束下完成具体工作。审查、验证和审批关联到明确的代码结果，便于追踪做了什么、验证了什么、批准了哪个版本。

| 组成 | 职责 |
| --- | --- |
| 控制平面（Control Plane） | 管理任务与运行生命周期、依赖、调度、租约、工程门禁和审批 |
| Worker | 按角色与能力领取任务，调用 AI Runtime 和工具，提交结果与证据 |
| Web Console | 创建需求、查看任务和时间线、管理 Worker 与并发、处理审批 |
| 人类负责人 | 确认需求与执行方案，在需要时干预或审批 |

项目全称为 **AI Worker: Digital Employee**，正文简称 **AI Worker**。“数字员工”描述协作主题，`Worker` 指具体执行进程；控制平面和 Web Console 是平台组件。

## 当前能力

- **工程流程协作**：从自然语言需求到实现、集成、审查、验证和修复，支持角色分工与任务依赖。
- **执行管理**：能力匹配、Worker Pull、租约与心跳、过期租约恢复、执行尝试历史，以及运行暂停、恢复和取消。
- **并发与隔离**：运行级和 Worker 集群级并发预算，Git Worktree 工作区隔离。
- **人工监督**：工程门禁、人类审批、控制台操作、时间线与审计记录。
- **可替换执行端**：提供 Python、TypeScript、JavaScript、Go、Java Worker 示例，以及 Go、Python、TypeScript 流程 Worker。
- **可选工作流方案**：启用 Flexible Workflow 后，可以起草、导入和确认委派方案；草案确认后才创建可执行工作。

不同 Worker 的角色覆盖、运行时支持和准入条件以各自指南为准。产品主题面向数字员工协作，当前能力范围仍以软件工程任务为主。

## 开始使用

建议先在可丢弃的本地 Git 仓库中体验。完整步骤见 [Quick Start](docs/current-zh/QUICK-START.md)，包括仓库基线、Worker 配置、任务创建和审批。

### 1. 启动控制平面

准备 JDK 21 和 Maven。在仓库根目录执行：

```sh
mvn spring-boot:run
```

默认使用本地 SQLite。启动后打开 `http://127.0.0.1:8080/ui`。

### 2. 启动数字员工

按指南配置并启动 Worker，提供控制平面地址、仓库路径、角色、技术能力，以及所选 AI Runtime 的认证信息：

- [TypeScript 流程 Worker](workers/process-typescript/README.md)：角色、Runtime、单进程与多角色启动说明。
- [Python 流程 Worker](workers/process-python/README.md)。
- [Go 流程 Worker](workers/process-go/README.md)。
- [Worker 总览](workers/README.md)：其他语言示例与协议入口。

完整工程流程需要相应角色就绪；具体角色与能力配置见 Worker 指南。Worker 需要额外准备其运行环境、依赖和 AI Runtime。

### 3. 提出需求并监督执行

在 Web Console 创建运行，观察任务、Worker、时间线和结果；按需暂停、恢复、取消或处理人类审批。功能与操作步骤见 [Web Console 指南](docs/current-zh/15-WEB-CONSOLE.md)。

以上是使用说明，本次文档更新未执行启动命令或验证端到端流程。

## 技术基础与运行边界

| 项目 | 当前基础 |
| --- | --- |
| 控制平面 | Java 21、Spring Boot 4.0.8 |
| 持久化 | JDBC + Flyway；本地 SQLite / 共享 PostgreSQL |
| Worker 协议 | 外部进程、主动领取任务、Worker Protocol v1 |
| 配置 | 环境变量使用 `AIW_` 前缀；具体配置键见配置参考 |
| Java 命名空间 | `me.asu.aidev` |

浏览器控制台认证默认关闭，适合本地体验。需要启用时配置：

```text
AIW_WEB_CONSOLE_AUTH_ENABLED=true
AIW_WEB_CONSOLE_USERNAME=admin
AIW_WEB_CONSOLE_PASSWORD=<strong secret>
AIW_WEB_CONSOLE_ROLE=ADMIN
```

控制台角色包括 `VIEWER`、`OPERATOR`、`APPROVER`、`ADMIN`。浏览器认证仅保护控制台；管理 REST API 仍有独立的信任边界。部署时应限制控制平面只能从本机或受保护的内网访问，或为管理 API 单独提供保护。

工程门禁与人类审批分别管理。当前平台不承诺自动完成 PR 合并、发布或生产部署；生产使用前需单独确认认证、权限、运行时准入和部署要求。更多内容见 [安全与信任边界](docs/current-zh/09-SECURITY-AUTHORITY-AND-TRUST-BOUNDARIES.md)及 [当前限制](docs/current-zh/12-TESTING-VALIDATION-AND-LIMITATIONS.md)。

项目名称更新不改变现有包名、配置键、API 路径或 Maven 产物名。文档中的历史路径、发布记录和来源文件名按原始事实保留。

## 文档导航

| 需要了解 | 文档 |
| --- | --- |
| 向团队介绍项目 | [3～5 分钟介绍](docs/current-zh/BOSS-INTRO-3-5-MIN.md) |
| 浏览完整文档 | [中文文档](docs/current-zh/README.md) · [English documentation](docs/current/README.md) |
| 理解系统结构 | [系统架构](docs/current-zh/02-SYSTEM-ARCHITECTURE.md) |
| 接入 Worker | [Worker 协议与参考实现](docs/current-zh/04-WORKER-PROTOCOL-AND-WORKERS.md) |
| 使用 API | [API 与用户手册](docs/current-zh/05-API-USER-MANUAL.md) |
| 部署与运维 | [运维手册](docs/current-zh/06-OPERATIONS-GUIDE.md) · [配置参考](docs/current-zh/07-CONFIGURATION-REFERENCE.md) |
| 使用容器环境 | [Java 25、Node.js 24 与 Codex 容器](docs/current-zh/DOCKER.md) |
| 设计委派方案 | [Flexible Workflow 方案](docs/current-zh/FLEXIBLE-WORKFLOW-PLANS.md) |
| 排查启动与执行问题 | [故障排查与恢复](docs/current-zh/11-TROUBLESHOOTING-AND-RECOVERY.md) |

`docs/current/` 与 `docs/current-zh/` 是当前正式文档入口。其他阶段文档保留设计背景与变更历史；R8、R9、R10 等标记表示演进阶段，不属于项目名称。

## Personal secretary

FD-024 separates private secretary work into a user-owned process and SQLite store. Platform identity, minimal authority and receipts are managed at /ui/authority. See [operations](workers/personal-secretary/README.md) and [offline migration planning](scripts/secretary_migration/README.md). Old secretary APIs/modes are removed; actual data migration and cutover remain disabled.
