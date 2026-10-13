# 计划与 Worker 委派

当前产品只提供计划流程。FD-011 最初以独立入口实现，FD-017 增加页面；现在 `/ui`、创建入口、列表和详情统一使用 `/ui/plans`。固定自主 Run 的 HTTP 创建与管理入口已移除，旧 `/ui/runs` 只重定向。`/ui/workflow-plans` 保留为计划 JSON API。开发库按单文件 V1 基线从空数据库初始化；旧 Run 数据不会自动转换。

页面提供“Core development”核心开发模板（需求分析→方案设计→实施规划→编码与必要测试）、单步分析、分析→架构→规划、单步编码模板及空白编辑。核心开发模板的终点是代码通过阶段评审，不包含代码专项审查、独立测试验证、集成、集成验证、专项修复、部署或生产发布；编码步骤保存前必须选择有效工作区，并按实际需要设置语言能力。Git 基准提交可选：非 Git 目录保持为空，直接使用工作区目录；填写时必须为完整不可变 Git commit，并使用隔离 Git worktree。模板可修改和删除步骤；步骤按列表顺序执行，删除或改序后须由管理者修正声明的依赖。每一步可独立设置角色、必需技能及偏好技能，步骤指令同时写明验收条件。保存草稿不创建业务任务；确认后只创建第一步。每步执行完成后生成独立 `workflow-review` 评审任务；PASS 才生成下一步，REPAIR_REQUIRED 则将评审意见与原产出交给同一步的下一次执行。每步最多三次执行；第三次评审仍需返工时停止自动推进，详情显示 NEEDS_ATTENTION，由管理者另行决定。后续步骤接收此前已接受的产出，代码步骤在同一工作区使用最新已接受的结果 revision。显式 Worker 执行失败仍按现有任务失败规则处理，不伪装成评审返工。复制会创建新草稿，不改原计划。模板不包含固定 AR 的工程 Gate 或人工批准。

New Plan 的 `Workspace reference` 从已启用的 Workspace 目标中选择，可输入名称、引用或相对路径过滤；过滤文字不作为提交值，当前已选目标在过滤时仍保留显示。没有可用目标时，先在 Workspaces 注册，再刷新编辑页。复制计划中的失效引用显示为不可用，必须重新选择或清空后才能保存；需要工作区的角色不能清空。列表是页面加载时的快照，后续禁用或删除仍以服务端现有校验为准。

### 验证 Worker 的覆盖范围

当前核心模板可以实际验证 `requirement-analysis`、`solution-architecture`、`implementation-planning`、`coding` 四种执行角色，以及每一步自动生成的 `workflow-review`。给编码步骤设置 `typescript,node` 等必需能力，并给阶段评审 Worker 配置相同技术专长，可验证按阶段能力匹配。每个执行任务完成后应出现独立评审任务；只有评审 PASS 才进入下一步。用可修复的评审意见触发 REPAIR_REQUIRED，可验证带反馈的重试和三轮上限；**重试仍由原步骤角色领取，并非 `repair` Worker**。

现有计划执行器不能通过单个模板验证所有 Worker：编辑器尚不接受 `code-review`、`verification`、`integration-verification` 作为步骤角色；虽然可手动增加 `integration` 和 `repair` 步骤，当前流程把它们当普通线性工作，无法根据专项审查或验证的 REPAIR_REQUIRED 判定自动路由到 `repair`。因此不要把“阶段评审通过”当作代码专项审查或独立测试通过。要做全角色端到端验证，需先实现专项审查与验证的判定分支、修复后重新审查/验证，以及同一代码 revision 的传递；再分别运行成功路径和返工路径。单个成功路径无法触发只在失败时需要的 `repair` Worker。

计划页面只接受可执行角色能力与精确 `skillRef`；自动评审使用专属 `workflow-review` 角色，执行步骤不得声明该角色。必需技能影响 Worker 领取；页面的舰队匹配是最多 100 个 Worker 的当前快照，无匹配或窗口外状态均不保证未来执行。详情展示保存的图、真实执行与评审任务、报告、判定及创建/确认/收回审计字段。未记录的报告显示未知。存在 CLAIMED/RUNNING 的计划整体拒绝收回，完成结果保留。固定自主 Run 页面已退出当前产品。

确认后的计划可由操作员填写原因，从当前步骤跳到更靠后的步骤，或提前停止。跳转会取消尚未完成的旧任务并记录被跳过的步骤；目标步骤只引用已有且通过独立评审的产出，缺失的产出不会被伪造成通过。提前停止使计划进入 `STOPPED`，已完成的任务与产出保留。若当前任务已被 Worker 领取，操作先进入 `PENDING_STOP`，请求 Worker 停止；只有收到对应租约的停止确认后才应用。Worker 在确认前自然完成或失败时，该请求记为 `CONFLICT`，正常完成流程继续；租约到期后可由操作员把滞留的请求记为冲突，再重新决策。详情展示每次决定及状态。不能跳回较早步骤，也不能修改最终评审已通过的计划。

TypeScript 全角色启动器会额外启动一个 `stage-reviewer`。角色能力自动注册；只有实际具备技术工具链的角色才需声明相应技能。例如使用 `AIW_WORKER_CAPABILITIES_CODER=node,typescript` 为编码角色添加技术能力，需求分析角色无需声明它们。`AIW_WORKER_CAPABILITIES_<ROLE>` 中的角色名使用大写及下划线，例如 `STAGE_REVIEWER`；未设置的角色不继承共享的 `AIW_WORKER_CAPABILITIES`。Go 和 Python Worker 也提供 `workflow-review` profile。统一能力名称、分配与限制见 [Worker 能力与分配](WORKER-CAPABILITIES.md)。

### AI 推荐边界

`AIW_FLEXIBLE_WORKFLOW_RECOMMENDATIONS_ENABLED=true` 单独开启推荐，且必须配置现有 `aidev.ai` 的 EXTERNAL_WORK policy 和可用只读 planner Worker；默认关闭。模型、凭据、预算及 Worker attempt 期限由管理员现有策略决定，不在浏览器配置。点击一次会创建一个 `workflow-recommendation` 的只读规划分析任务，可能产生模型费用；仅发送最多 8000 字符目标和固定可信角色目录，不附带工作区或源码。任务使用现有 Pull/lease、报告与审计机制，建议不能自行确认业务图。

推荐返回必须是单个关联当前 requestKey/inputVersion 的 JSON 对象，包含理由与合法 WorkflowPlanSpec。未知角色能力、越界字段、不一致目标或自行指定工作区会拒绝应用。页面等待最多 10 分钟；停止等待不代表终止 Worker。排队分析可收回，已领取/运行的分析不能强停。需求或编辑内容变化后旧建议失效；管理者主动应用并补足工作区后，仍需保存草稿和另行确认。推荐失败时可继续手动编辑。

写操作沿用 Web Console 会话 OPERATOR/ADMIN 和 URL 查询参数 `_csrf`，只读角色不能保存、推荐、确认或收回。`/ui/plan-editor/**` 是页面专用接口；`/ui/workflow-plans/**` 使用相同的步骤角色校验和逐步评审执行语义。AI 推荐仍须有可用策略并由用户显式点击，页面不会自行发起模型任务。

管理者可以只委派一次分析或自己选择的有限步骤。技能说明 Worker 能做什么；步骤与依赖说明执行流程。技能名称使用现有 Worker capabilities，`skillRef` 是实际执行技能引用；必需技能决定资格，偏好技能用于同一有效优先级下的匹配排序。管理者须确保引用能被所选 Worker 执行，系统不会安装新技能或扩大内部工具权限。

编辑页的能力指南列出可选步骤角色、最多 100 个 Worker 快照中 ACTIVE Worker 声明的技术能力及独立评审 Worker 数量；完整的当前 Worker 声明和 READY 任务匹配诊断可在 `/ui/workers` 查看。技术能力是 Worker 自报标签，不能证明工具已安装。AI 推荐按钮已存在；推荐按每一步的实际需要分配技术能力，独立评审由系统自动插入，推荐结果仍需人工检查当前 Worker 匹配并保存、确认。

## 管理接口

沿用 Web Console 登录会话；写操作要求 OPERATOR/ADMIN，并在查询参数中携带该会话的 `_csrf`（JSON body 不含 CSRF 参数）。生产环境应启用既有控制台认证；默认本地免认证模式仍使用 local-console 身份和 CSRF。外部流程/Worker 的提交令牌不能调用这些管理操作。

| 方法 / 路径 | 行为 |
|---|---|
| GET `/ui/workflow-plans` | 最近 100 个计划，包含版本、来源和审计信息 |
| GET `/ui/workflow-plans/{id}` | 计划及创建后的任务状态 |
| POST `/ui/workflow-plans` | 保存草稿；body 为下方计划格式 |
| POST `/ui/workflow-plans/{id}/copy` | 复制为全新草稿，可复用模板；不会启动 |
| POST `/ui/workflow-plans/{id}/confirm` | body `{"version":0}`，明确确认当前草稿版本 |
| POST `/ui/workflow-plans/{id}/withdraw` | body `{"version":1,"reason":"收回剩余委派"}` |
| POST `/ui/workflow-plans/{id}/jump` | body `{"version":1,"targetStep":2,"reason":"跳过中间步骤"}`；`targetStep` 从 0 开始 |
| POST `/ui/workflow-plans/{id}/stop` | body `{"version":1,"reason":"已有产出足够"}`；提前停止 |
| POST `/ui/workflow-plans/{id}/resolve-stop` | body `{"version":2}`；仅在原租约不再活动且未确认停止时，将滞留请求记为冲突 |
| POST `/ui/workflow-plans/{id}/archive` | 已结束计划从当前列表隐藏 |
| POST `/ui/workflow-plans/{id}/schedule-delete` | 管理员标记 30 天后删除数据库记录 |
| POST `/ui/workflow-plans/{id}/undo-delete` | 管理员在到期前撤销删除标记 |

示例仅委派分析，不自动增加代码、审查或部署阶段：

```json
{
  "goal": "分析报表慢查询的原因并提供建议",
  "source": "AI",
  "steps": [{
    "key": "analysis",
    "requiredSkills": ["requirement-analysis"],
    "preferredSkills": ["postgresql"],
    "skillRef": "software-requirements-analyst",
    "instruction": "分析现有材料并提出优化建议，不修改生产数据。",
    "repositoryRef": null,
    "baseRevision": null,
    "dependsOn": []
  }]
}
```

`source` 为 MANAGER 或 AI，表示来源，不表示批准。AI 生成的建议由管理者导入，确认前不会创建可领取任务。本轮不调用 AI 生成器。草稿不可原地修改；调整目标、技能或步骤时提交新草稿，旧草稿保持未执行状态。

每个计划有 1–32 个有序步骤。key 唯一，依赖须指向更早步骤，禁止循环和缺失前置工作。工作区规则复用现有任务：`repositoryRef` 可为工作区目标或旧仓库 ID，`baseRevision` 可为空，此时沿用直接目录执行模式，不要求目录具有 Git；若提供必须为完整不可变 commit ID，且必须同时指定 `repositoryRef`。必需技能为空、非法技能、空指令和不合法版本均拒绝。参数错误返回 400，未知计划 404，版本变化/活动工作/状态冲突 409，权限不足 403；未认证与 CSRF 失败由既有拦截器处理。

## 确认、保留与收回

创建计划时仅保存 DRAFT。管理者确认后以同一事务变更 CONFIRMED 并创建第一步 EXTERNAL_WORK；失败回滚，不留下部分任务。每个后续执行或评审任务由前一步完成事务幂等创建，评审未 PASS 不创建下一步。同一已确认计划重试原确认版本或当前版本不会重复创建工作。

管理者保留的工作留在自己手中，执行图只列本次实际委派部分。若 Worker 工作需要这些人工工作的结果，管理者先准备输入，再提交有界计划；系统不伪造人工步骤完成结果。每个新任务沿用现有 Pull/lease 机制；无满足该步必需技能或 `workflow-review` 的 Worker 时任务等待，偏好不足不阻止领取。

收回要求计划 CONFIRMED 及当前 version。存在任一 CLAIMED/RUNNING 工作时整体拒绝，租约、计划和所有任务不变。否则取消 READY/BLOCKED/FAILED 工作并记录原因及操作人，计划成为 WITHDRAWN；完成结果保留。收回不实现停止运行中的 Worker。重新委派需复制或创建新的草稿，并再次确认。

## 兼容和运维

当前单文件 V1 基线包含计划、偏好、控制与归档表，并在末尾删除旧固定 Run 表。无偏好任务保持原排序；偏好位于有效优先级后、同 subject 活跃租约/等待时间排序前，不绕过准入、并发预算或原子领取。

升级按既有运维流程排空/停止应用、备份数据库、启动应用执行 Flyway、确认启动后恢复 Worker。回滚须先停用入口并排空新增工作，按备份恢复兼容的数据库/应用组合；不要删表、修改历史迁移或 Flyway history。本次未执行迁移或数据库集成测试；SQLite/PostgreSQL 兼容性依据 SQL 静态检查，不能作为实际运行通过。
