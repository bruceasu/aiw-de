# Worker 能力与分配

`recovery-decider` 是独立的恢复决策角色，自动声明 `workflow-recovery-decision` 和
`task-outputs-v1`，使用 `workflow-recovery-decider` Skill。TypeScript 启动器提供该角色；
它只能处理管理者指定给自己的单次授权，不是计划中的编码或评审步骤，也不拥有新的授权签发权。
角色使用只读 profile；PASS 仅表示使用现有人类授权，不代表代码验收通过。

Fleet、Worker 页面和计划编辑器使用 90 秒心跳窗口，与调度的候选 Worker 新鲜度窗口一致。
超过 90 秒无心跳的 ACTIVE 或 DRAINING Worker 在快照中显示 OFFLINE，不计入可领取容量或
能力匹配；刷新快照后生效。历史注册信息及已有任务租约保留，租约仍由原恢复机制处理。
超时判定不修改管理员设置的状态：新心跳到达后恢复原 ACTIVE/DRAINING 显示，手动设为
OFFLINE 的 Worker 不会仅因心跳而恢复 ACTIVE，需重新注册。

TypeScript Worker 自动声明 `task-outputs-v1`，表示实现附件登记、带租约的固定版本读取和
正式文档入库校验。正式文档入库计划要求此能力，其 stage-reviewer 也必须具备它。
这不是语言专长；未实现协议的 Worker 不应声明它。已有任务的技术必需能力匹配保持不变。

## 四种不同信息

| 信息 | 用途 | 例子 |
|---|---|---|
| 角色能力 | 决定这一步由哪种角色执行；由 profile 自动注册 | `requirement-analysis`、`coding`、`workflow-review` |
| 技术专长 | 声明该进程实际能处理的技术任务；由管理员按角色配置 | `typescript`、`go`、`python`、`sql` |
| 工具链与产品 | 声明实际可用的执行环境或特定产品知识 | `node`、`postgresql`、`spring` |
| 仓库范围 | 限定可领取的仓库任务；由已配置的仓库生成 | `repo:core` |

`skillRef` 是该角色执行时加载的精确 Skill 引用，不是技术专长标签。TypeScript、Python、Go 只是 Worker 实现语言时，不自动产生同名任务能力。环境变量不得声明 `*`、`repo:`、角色能力或 `flexible-work-v1`；角色和仓库标签由 Worker 自动生成。能力是声明，不证明工具已安装，也不扩大文件、网络、数据库或审批权限。

Worker 不允许执行 `git push`。流程 Worker 只在隔离工作区生成本地提交并报告 `resultRevision`；`AIW_GIT_PUBLISH_REMOTE`、可写 Codex 网络访问和 API 运行时的主机 Shell 配置会被拒绝。跨机器代码交接不能依赖 Worker 推送分支，需另行设计由人或受控发布服务执行的交接。

## 首批技术专长目录

| 标签 | 含义与使用边界 |
|---|---|
| `typescript` | 能处理 TypeScript 源码；需要 Node 工具链时另声明 `node` |
| `node` | 能使用当前环境中的 Node 工具链 |
| `go` | 能处理 Go 源码及相应工具链；统一使用 `go`，不另造 `golang` |
| `python` | 能处理 Python 源码及相应工具链 |
| `java`、`spring` | 能处理 Java、Spring 项目；需要 Maven 时可另声明 `maven` |
| `sql` | 能编写、检查 SQL；不自动代表数据库方案设计能力 |
| `database-design` | 能设计表结构、约束、索引与迁移方案 |
| `postgresql` | 能处理 PostgreSQL 专有语法或工具；不自动代表已获数据库访问权限 |

目录是推荐用名，不是服务器对工具或知识的自动认证。新增标签应先写明含义，并避免同义词。不要把“IT”或通用工程能力作为替代所有专长的通配资格。

## 按步骤和角色分配

- `requirements` 通常只需 `requirement-analysis`。目标实现语言可写在需求里，不因后续写 TypeScript、Go 或 Python 就要求分析 Worker 拥有这些标签。
- `architect` 做普通架构时不要求实现语言；复杂数据库方案可要求 `database-design`，需要特定 PostgreSQL 设计时再加 `postgresql`。
- `planner` 一般保持通用；语言或数据库背景可作为偏好。
- `coder`、`integrator`、`repairer` 只声明自己实际能处理的语言、SQL 与工具链。同一 coder 确实具备多种能力时可以同时声明，不必按语言另造角色。
- `reviewer`、`verifier` 只声明自己能独立评审或验证的相应技术。执行能力不自动授予评审资格。
- `stage-reviewer` 使用专属 `workflow-stage-reviewer` Skill 负责灵活流程每步的独立验收。代码阶段的评审任务还要求执行步骤中的技术必需能力；评审 Worker 需独立具备这些专长。

示例：需求分析步骤只要求 `requirement-analysis`；TypeScript 编码步骤要求 `coding`，若必须运行 Node 工具则另要求 `node,typescript`；普通 SQL 修改可要求 `coding` 并偏好 `sql`，高风险 PostgreSQL 迁移才把 `sql,postgresql` 设为必需。数据库方案未确定时，先由具备 `database-design` 的架构步骤形成产出，再交给 coder 实现。

每一步的**全部必需能力必须由同一个 Worker 满足**。偏好能力不阻止领取。READY 阶段任务的前 30 秒内，若有最近 90 秒心跳且有空闲容量、满足全部必需能力并具备更多偏好能力的 Worker，通用 Worker 暂不领取该任务；窗口结束后通用 Worker 可以兜底。必需能力不可兜底；`*` 不再授予任意能力。该窗口只衡量已注册 Worker 的声明，不能证明它一定会领取。流程编辑页显示最多 100 个 Worker 的快照，实际在线声明和 READY 任务诊断以 `/ui/workers` 为准。

每个阶段产出后由独立评审给出 `PASS` 或 `REPAIR_REQUIRED`。返工任务包含上一轮评审意见，最多执行 3 轮。超过上限由人处理：可以跳到后续步骤、直接完成、停止或取消。取消仅清理已确认属于本流程且未发布、未并入共享分支的本地隔离 Git 分支；直接共享工作区和已发布结果必须人工还原。活跃 Worker 要先停止并确认释放，才能取消。

## 配置示例

每个可执行角色 profile 自动提供自己的角色能力。技术能力由运行该角色的进程设置，例如：

```text
AIW_PROCESS_ROLE=coder
AIW_WORKER_CAPABILITIES=typescript,node,sql
```

这同样适用于由 Go、Python 或 TypeScript 实现的进程 Worker；声明前应确认该进程的 Skill、工具与工作区确实能完成对应任务。TypeScript `start-all.mjs` 使用 `AIW_WORKER_CAPABILITIES_CODER=typescript,node,sql` 等逐角色变量；未配置的角色不继承共享技术能力。单角色启动时仍使用 `AIW_WORKER_CAPABILITIES`。不要给所有角色复制同一组技术标签。

小规模团队可以用几个同角色、不同 Worker ID 的进程分别声明专长，例如 TypeScript coder 使用 `typescript,node`，Go coder 使用 `go`，Python coder 使用 `python`；确实会写 SQL 的 coder 再加 `sql`。数据库架构 Worker 可使用 `database-design,postgresql`，对应评审 Worker 只在能独立检查该技术时声明相同专长。通用分析和规划 Worker 可以不声明技术专长。一个进程也可以同时声明多项真实能力，无须按语言创建新角色。

`profiles/` 根目录只保留可执行角色；`profiles/advisory/` 和 `skills/advisory/` 存放尚未接入执行链的参考定义。
