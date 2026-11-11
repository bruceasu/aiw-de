# 系统如何工作

[返回文档导航](README.md)

本文依据当前模块、控制台路由和专题契约整理，是阅读地图，不是端到端运行验证报告。

## 先区分五件事

| 名称 | 职责与边界 |
| --- | --- |
| Control Plane | 保存任务、Plan、依赖、租约、权限、审批和执行记录，协调推进；模型连接成功不表示任务已经执行 |
| 个人秘书 | 与用户交互，连接 Plane 管理计划和任务；拥有独立运行环境与私有数据；区别于早期 Plane 内秘书安排功能 |
| Agent 工厂 | 保存默认模型与执行设置，准备或启动本机 Worker；配置不等于执行进程 |
| Worker | 注册能力、发送心跳、领取兼容任务、调用模型与工具、汇报结果；注册记录不证明仍在线，能力声明不证明工具已安装 |
| Plan / Task / Attempt | Plan 组织步骤和依赖，Task 是具体工作，Attempt 是一次执行；重试不能抹去原结果 |

代码入口：[`workflow`](../src/main/java/me/asu/aidev/workflow/)、[`work`](../src/main/java/me/asu/aidev/work/)、[`execution`](../src/main/java/me/asu/aidev/execution/)、[`worker`](../src/main/java/me/asu/aidev/worker/)、[`personal-secretary`](../personal-secretary/)。

## 从讨论到执行

1. 在许愿池或秘书中澄清目标，准备可确认内容。讨论回复不自动等于执行授权。
2. 确认 Plan 的工作内容、依赖、风险和验收方式；受控执行另有执行政策和权限条件。
3. 工厂按配置准备本机进程，或者使用已接入的外部 Worker。
4. Worker 领取符合能力与容量条件的就绪任务，平台记录租约和尝试。
5. Worker 调用模型与工具，汇报实际结果、已知用量和交接引用。
6. 平台按策略推进、自检或安排评审；失败、未知结果和人工决定分别保留。

这是阅读顺序，不表示所有任务经过同一条路径。旧 autonomous run、Flexible Plan 与配置任务有不同入口，不能互换状态或控制接口。

## 模型执行与认证分别配置

运行方式、服务商、协议、模型和认证来源是不同设置。ChatGPT 连接与 API Key 是认证选择，Codex app-server 是另一种运行方式。登录成功、目录可见或客户端可以创建，不代表模型与工具已经实际验证。

工厂本机进程、远端 Worker 和独立秘书可能运行在不同设备，不能假定环境变量、登录文件或依赖可以共用。见 [模型认证](MODEL-AUTHENTICATION.md)、[工厂](AGENT-FACTORY.md) 和 [个人秘书](../personal-secretary/README.md)。

## 目录和任务交接

Plan 可声明 `location`，由平台记录并传递，Worker 解析和准备。未声明时平台分配 Plan 专用临时目录。多 Worker 必须能访问约定的共享位置。

单仓库可用时 Worker 按契约建立 worktree；不能安全隔离时串行，多仓库目录不逐仓库自动建立 worktree。不同目录可并发。结果留在工作目录，通过 `.ai/handoffs/` 中的报告和 manifest 交接，不上传整份修改文件。

清理有独立条件，不是每个任务完成后的自动动作。完整约束见 [位置与交接](PLAN-LOCATIONS-AND-HANDOFFS.md)。

## 在哪里观察和控制

| 入口 | 当前用途 |
| --- | --- |
| `/ui/workers` | 注册 Worker、心跳、能力、租约占用与就绪任务诊断 |
| `/ui/configurations?view=workbench` | 执行尝试、运行控制、补充方向、工具记录与探索方案，按实际支持显示 |
| `/ui/agent-factory` | 默认模型、认证来源与本机自动启动设置 |
| 所属 Plan / Task | 计划级决定、任务状态和执行记录 |

Worker 的 `ACTIVE` 是管理状态，不等于正在执行。`DRAINING` 只停止领取新任务，现有租约继续；标记 `OFFLINE` 不证明远端进程已经退出。暂停和取消需要区分请求受理、Worker 确认和最终状态，不撤销已发生的修改。

相关实现：[`workers.html`](../src/main/resources/templates/ui/workers.html)、[`WorkerService`](../src/main/java/me/asu/aidev/worker/application/WorkerService.java)、[`FleetDashboard`](../src/main/java/me/asu/aidev/fleet/domain/FleetDashboard.java)。工作台限制见 [使用说明](WORKER-WORKBENCH.md)。Workers 页的重设计建议尚不能作为已实现界面说明。

## 评审、授权与费用

- 评审分为自检、里程碑和独立评审，不能概括为每次完成必启动 Reviewer。各 Plan 类型的门禁需分别判断，见 [评审策略](WORKFLOW-REVIEW-POLICY.md)。
- 模型账户不授予文件、命令、计划管理或审批权限，见 [动作权限](ACTION-PERMISSIONS.md)、[受控执行](EXECUTION-GUARD.md)。
- 已有计量和新增价格采集设计需区分。未知用量或价格不能当作零，估算费用不等于真实账单。设计中的能力结合对应 FD 判断落地范围，见 [用量与价格设计](WORKER-USAGE-AND-PRICING-DESIGN.md)。

## 已核对与未验证

根 `pom.xml` 声明 Java 25、Spring Boot 4.0.8，包根为 `me.asu.aidev`。组件依赖以各自 README 为准。

本文通过源码与文档静态核对整理，没有启动 Plane、Worker 或秘书，没有验证真实登录、模型调用、迁移、浏览器或远端停止行为。
