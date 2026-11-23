# AI Worker: Digital Employee

让 AI 团队把想法变成明确需求，再通过可观察、可控制的工作流完成工作。

**AI Worker** 由 Control Plane、Worker 和可独立使用的私人秘书组成。许愿池帮助澄清需求，简单任务把目标交给 Worker 自主执行，工作流保存可重复执行的业务规则、人工确认和显式 Agent 步骤。平台保留真实过程、结果、资源记录与人工决定。

软件开发是应用场景之一，也可以处理文字分析和业务流程。Worker 的名称、职责、工作方法、Skills 和参考资料可不同，执行进程按需启动或复用。人通过 Web Console 观察和干预执行；秘书在自己的设备上管理私人对话与资料，按需连接 Plane。

## 数字员工如何协作

```text
模糊想法／原始需求 → 许愿池澄清 → 确认需求
  → 工作流草稿 → 核对并启动 → 任务／LLM 判断／人工问题 → 结果
简单请求 → 简单任务 → 结果
```

每个 Worker 声明自己的能力并主动领取已就绪的任务。任务之间通过依赖关系衔接；控制平面维护状态和执行权，Worker 在租约约束下完成具体工作。审查、验证和审批关联到明确的代码结果，便于追踪做了什么、验证了什么、批准了哪个版本。

| 组成 | 职责 |
| --- | --- |
| 控制平面（Control Plane） | 管理任务与运行生命周期、依赖、调度、租约、工程门禁和审批 |
| Worker | 按角色与能力领取任务，调用 AI Runtime 和工具，提交结果与证据 |
| Web Console | 创建需求、查看任务和时间线、管理 Worker 与并发、处理审批 |
| 人类负责人 | 确认需求与执行方案，在需要时干预或审批 |

项目全称为 **AI Worker: Digital Employee**，正文简称 **AI Worker**。团队成员身份长期保留，运行进程可以重启或复用；进程停止不等于成员资料被删除。

## 当前能力

- **需求与流程**：专业成员结合 Skills 与系统资料澄清愿望，生成待确认的统一工作流；附件可作为原始需求。
- **执行管理**：能力匹配、Worker Pull、租约与心跳、过期租约恢复、执行尝试历史，以及运行暂停、恢复和取消。
- **并发与隔离**：运行级和 Worker 集群级并发预算，Git Worktree 工作区隔离。
- **人工监督**：工程门禁、人类审批、控制台操作、时间线与审计记录。
- **可替换执行端**：提供 Python、TypeScript、JavaScript、Go、Java Worker 示例，以及 Go、Python、TypeScript 流程 Worker。
- **私人秘书**：独立对话、本地小工作、会话日志与真实执行过程查看；确认选定内容后可送入许愿池或委派任务。
- **安排与费用记录**：单次／每日／每周安排，错过时间等待决定；记录已获知的 Token 与价格，缺少信息时显示未记录。

不同 Runtime 的工具能力以当前配置和各自指南为准。保存需求或工作流草稿不会自动开始业务执行；失败时保留原因与结果，重做或修复由用户决定。

## 开始使用

从本机文字任务开始即可，不要求 Git、工作区或 Codex CLI。Windows、Linux／macOS 及远程连接的步骤见 [快速开始](docs/current-zh/QUICK-START.md) / [Quick Start](docs/current/QUICK-START.md)。

### 1. 启动控制平面

准备 JDK 25、Maven 3.9+ 和 Python 3.11+。按快速开始配置项目根目录 `.env` 中的数据库，然后运行现有启动脚本：

```sh
# Windows: .\scripts\startup.bat
bash scripts/startup.sh
```

启动后打开 `http://127.0.0.1:8080/ui`。默认启用 `sqlite` profile，其内置地址为 `jdbc:sqlite:d:/temp/data/aiwde.db`；请按 Quick Start 用 `AIW_DB_URL` 显式指定当前系统的路径。切换 PostgreSQL 时须同时调整 profile 和连接配置，不能只替换 SQLite profile 下的 URL。

### 2. 启动数字员工

在 Agent 工厂中配置模型连接和执行方式。工厂按任务需要启动本机 Worker；外部 Worker 在自身设备上管理模型连接，使用专属 Worker 凭据接入：

- [TypeScript 流程 Worker](extern-workers/process-typescript/README.md)：角色、Runtime、单进程与多角色启动说明。
- [Python 流程 Worker](workers/process-python/README.md)。
- [Go 流程 Worker](workers/process-go/README.md)。
- [Worker 总览](workers/README.md)：其他语言示例与协议入口。

Python 依赖与 Runtime 须事先准备好。私人秘书可通过 `personal-secretary/scripts/start.ps1` 或 `start.sh` 独立启动；连接本机 Plane 自动登记，远程连接须配对与确认。

### 3. 提出需求并监督执行

在 Web Console 创建运行，观察任务、Worker、时间线和结果；按需暂停、恢复、取消或处理人类审批。功能与操作步骤见 [Web Console 指南](docs/current-zh/15-WEB-CONSOLE.md)。

以上是使用说明，本次文档更新未执行启动命令或验证端到端流程。

## 技术基础与运行边界

| 项目 | 当前基础 |
| --- | --- |
| 控制平面 | Java 25、Spring Boot 4.0.8 |
| 持久化 | JDBC + Flyway；本地 SQLite / 共享 PostgreSQL |
| Worker 协议 | 外部进程、主动领取任务、Worker Protocol v1 |
| 配置 | 环境变量使用 `AIW_` 前缀；具体配置键见配置参考 |
| Java 命名空间 | `me.asu.aidev` |

所有平台控制台业务页面均要求真实登录，登录页始终可用。配置用户名和非空密码：

```text
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

Agent Factory 支持集中设置默认模型、优先复用在线 Worker，以及在执行范围检查通过后按需启动本机 Worker。外部自定义 Worker 继续按原协议接入。入口、运行环境要求和兼容边界见 [Agent Factory 与外部 Worker](docs/AGENT-FACTORY.md)。

FD-024 separates private secretary work into a user-owned process and SQLite store. Platform identity, minimal authority and receipts are managed at /ui/authority. See [operations](personal-secretary/README.md) and [offline migration planning](scripts/secretary_migration/README.md). Old secretary APIs/modes are removed; actual data migration and cutover remain disabled.

秘书连接与授权页面保存后会保留同一计划下的文件范围勾选，并在保存按钮旁持续显示实际保存结果；修改设置或重新检查状态后，提示会更新。勾选状态表示本次提交选择，不代表后台已经保存成功。
