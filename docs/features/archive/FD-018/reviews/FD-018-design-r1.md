# FD-018 独立设计审阅（第 1 轮）

<!-- aiw-data: FD-018-design-r1.json -->

- 结论：**changes-needed**。这是设计证据，不是实现验证通过或 Design Readiness 声明。
- 审阅对象：`docs/features/FD-018_AI.md` Revision 2，以及 ISSUE-00005 已确认需求。
- 来源事件：`FD-018-000002-design-requested`（由 Planner 交付）。
- 范围：一次有界独立静态审阅，重点检查持久化与竞态、Worker 配置契约、刷新恢复、租约及迟到结果。未实现、未运行测试、未访问网络、未修改工作流状态或 Git。

## D1 · high · 单例配置首次写入和关闭/领取的数据库线性化点未定义

事实：

- FD-018 Solution 目前只声明事务串行化，没有单例初始化、行锁/版本条件、锁顺序或并发结果规则。
- TaskClaimService.claimNext 明确采用 admission/run → worker → task 顺序，JdbcWorkTaskRepository.claim/cancelInactive 用状态条件裁决。

风险推断：两个事务都读到无设置记录，或关闭与提交/领取交错时，仅有 @Transactional 不能保证无重复设置、关闭后无新排队，以及无反向加锁死锁。

需要补全：指定固定单例主键及迁移初始化或原子缺行插入策略；给出保存设置、创建请求、领取及撤回的事务步骤和统一锁顺序；明确领取先赢则 CLAIMED 继续、关闭先赢则取消的线性化结果。分别解释 SQLite 与 PostgreSQL 的实现方式。

## D2 · high · 严格配置与冻结配置尚无跨语言可执行契约

事实：

- AiProfileCatalog 构造时绑定 Environment；canRun 只看 runtime，guidance 返回 approved profiles，select 按全局 version/profileId 校验。
- Python choose_ai_profile 会过滤 provider/runtime/fast/effort，并按历史失败选择下一 profile；既有 profile 路径不能自动视为显式配置的严格支持。
- FD 把配置取值和 guidance/start 精确契约标为待完成，planner 含义也明确未决。

风险推断：控制面认为在线适配，Worker 实际过滤后拒绝；显式配置被选择器回退；进程重启或 catalog 修改后按当前 profile 解析旧请求，导致冻结配置不可重现。

需要补全：待用户定义 planner 后，明确其取值与 runtime/profile/worker 的关系、model/effort 空值与非法值规则；冻结解析后的有效 profile 内容及版本或等价不可变引用；明确三语言适配能力和拒绝原因，并让 readiness/claim/guidance/start 共用同一约束。说明旧 Worker 能力不足时如何排除，而非仅新增可忽略字段。

## D3 · high · 租约失败需定义 task/attempt/容量的原子收敛，而非只改状态

事实：

- TaskExecutionService.recoverExpiredLeases 当前在单事务中 requeueExpired、将 attempt 转 EXPIRED、释放并发预算与 Worker capacity。
- JdbcWorkTaskRepository.findExpiredLeases/requeueExpired 有 stop_requested/PENDING_STOP 条件；terminalTransition 只允许未过期 RUNNING lease，不能直接复用来处理过期 CLAIMED/RUNNING。

风险推断：只将推荐 task 写 FAILED 可能泄漏容量、留下活动 attempt 或继续进入重排；并发 heartbeat/complete/recovery 竞争可能双释放或接受错误结果。原有 stop 条件也可能使推荐永久滞留。

需要补全：给出仅推荐任务的过期失败 repository 条件更新与 transaction 步骤，包含原 token、到期时间、活动状态的 CAS、attempt 终态、容量/预算一次释放、失败原因/审计；说明 stop 条件适用性与非推荐保持旧行为。定义 heartbeat/complete/recovery 谁先提交的结果，并检视迟到 report/output 写入与最终可应用结果的边界。给出 60 秒租约加调度周期的实际等待上界/配置来源，而不声称固定精确秒数。

## D4 · medium · 刷新恢复的上下文生命周期与版本规则未形成接口契约

事实：

- workflow-editor.js 当前 requestKey/inputVersion/currentRequest 保存在内存，并以 goal/version 比较防旧结果应用；inputVersion 初始化为 0。
- FD 提议 context/Plan 关联及 URL 标识，但未指定上下文创建、保存 Plan 绑定、恢复 DTO、版本和重复请求策略。

风险推断：刷新后版本重置或 goal 未恢复会使有效结果不可用或旧结果误用；新 Plan 保存后断开上下文关联；多标签页、多次请求或猜测上下文 ID 时行为不明确。

需要补全：明确 actor 所有的上下文创建/读取/绑定 Plan 接口与 DTO，恢复所需 goal/inputVersion/requestKey/状态/结果及最新请求选择规则；定义服务端 version 的生成/更新、重复 requestKey 幂等性、编辑后过期判定与多标签页冲突；对历史任务无关联给出不伪造恢复的用户体验。

## 未决项与验收追溯

planner 的业务含义正由主会话询问用户，审阅者不替用户选择。D2 中的具体字段方案以该回答为前提；其余三项可以先推进工程设计。FD 已正确保留默认启用、弃用旧环境变量、仅排队撤回、禁用后历史结果可用、Worker 唯一推理路径等已批准规则，本审阅没有更改这些验收基线。

D1 对应 Work Items 1.2–1.4、验收 1/2/4；D2 对应 1.1/1.3、验收 3；D3 对应 1.4、验收 6；D4 对应 1.5/1.6、验收 5。建议确定性测试覆盖上述竞争胜负、明确配置拒绝、恢复后版本一致性与不重派；本报告没有执行这些测试，也没有额外授予命令预算。

## 证据与限制

已静态阅读 FD、Issue、四份 core 约束、工作管理与领域入口、planning 方法、推荐服务、领取服务、AI profile catalog、执行服务关键路径、JDBC lease 状态条件、Python profile 选择及编辑器关联路径。针对性 `Get-Content -Encoding utf8` 与 `rg` 用于只读定位；首次 PowerShell 默认编码出现乱码后，改用 UTF-8 读取。部分定位路径不存在，未执行任何探测性恢复或运行检查。

假设：现有 Spring 事务与 JDBC repository 边界仍是实施基础；双数据库兼容要求沿用 FD。未验证：数据库隔离/锁实际行为、Worker 端全部 Runtime 兼容性、浏览器恢复交互、真实模型调用以及 60 秒租约的调度延迟上界。不存在的根 CONTEXT/CONTEXT-MAP 不补造。本报告只提出设计缺口，不宣称当前实现已经违反尚待实施的新增规则。

