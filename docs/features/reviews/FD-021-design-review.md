# FD-021 独立设计评审

<!-- aiw-data: FD-021-design-review.json -->

当前结论：**ready（设计就绪）**。评审对象为 FD-021 Revision 2；来源事件为 `FD-021-000002-design-requested`。初评为 changes-needed，以下保留原 findings；本次唯一聚焦复核见文末。未作实现验收结论。

## Findings

### D1 / high / blocking：旧 Plan 写入口可以绕过秘书调度及协调

事实：`WorkflowPlanController.confirm` 直接调用 `WorkflowPlanService.confirm`；后者把 DRAFT 改为 CONFIRMED 并立即创建首步。`WorkflowPlanService.jump/applyControl`、`retryFailedTask`、`resumeReview`、`continueAuthorizedRepair` 也有直接新建或重新开放任务的路径。FD 只规定完成事件分流和秘书取消，没有规定这些现存管理入口对秘书托管 Plan 的行为。

影响：秘书关联的 DRAFT 可以提前开工；秘书取消后，旧控制入口可能重新开放专业任务；协调 Worker 未完成判断也能通过旧入口推进。只给新增秘书 API 和完成 handler 加校验不能覆盖此边界。

建议：在 Plan 服务的写入口增加统一的托管判定。未关联 Plan 保持原语义；已关联 Plan 的原 confirm 拒绝并引导到秘书确认，不允许原 API 直接开工。为 jump、retry、resumeReview、repair 明确拒绝或受控接入同一秘书锁、版本及重新授权契约；stop/complete/cancel/archive 则保留事实控制，并使尚未消费的协调失效。仅平台内部的协调消费入口可以在校验后执行确定性推进。不要把公开 actor 字符串当作内部绕过凭证。把这些路径列入 Work Item 1.4 和验收。

### D2 / high / blocking：领取的原子性及跨调用锁顺序尚未落到具体契约

事实：`TaskClaimService.claimNext` 在 `tasks.claim` 之前调用 `TaskClaimPolicy`，随后仍执行并发预算、Worker 容量预留和任务领取。现有 `TaskExecutionService.complete` 先完成任务状态写入，再调用 completion dispatcher。FD 只写“锁定同一秘书记录”，尚未定义领取是否持锁至提交，也未说明完成路径与秘书取消/消费路径的任务锁顺序。

影响：只读状态的 policy 会在校验后与取消竞争；若新增路径以秘书行→任务行加锁，而完成路径已以任务行→秘书行加锁，则引入锁顺序冲突。重复事件和身份隔离也需要数据库约束，不能仅依赖先查后写。

建议：规定 policy 在领取事务内对所属秘书行取得数据库锁/等价串行化写锁，并持有到领取提交；重新核对状态、安排 revision、Plan 内容与授权，并把协调/专业身份预留写入同一事务。梳理 complete、fail、cancel、调度、协调消费与原 Plan 控制的实际写锁顺序，选用一致顺序，或用无交叉锁的 CAS/持久请求模型。明确唯一键至少覆盖“一条工作关联一个 Plan”和“安排 revision + 源任务/阶段的一条协调请求”；协调消费 CAS 与下一任务创建同事务。若采用新增 execution policy，必须允许取消后的在途任务上报终态和释放容量，仅禁止新 start/推进，不能拒绝 complete/fail 而丢失事实。把约束和原子性方案补入 Work Items 1.1、1.3、1.4。

## 实施检查点

- 按父 Planner 补充，复用 `WorkflowStepCompletedHandler` 的单一 EXTERNAL_WORK authority，提取旧确定性推进方法。协调完成后从存储重读原任务、最新有效报告和 Plan 位置，不使用协调任务自身报告替代开发评审。旧 Plan 仍走原路径。
- 协调请求冻结安排 revision、内容摘要、预期 Plan 版本、源任务与对应报告/尝试身份。初始化从 DRAFT 到 CONFIRMED 会增加 Plan 版本，应明确更新托管运行游标，避免把自身合法状态变化误判为内容变更；后续控制或替代报告须使旧游标失效。
- 现有安全服务持有当前配置用户名/角色，session 则保存登录时角色。秘书应提供不依赖历史 session 的当前授权检查，在到期及每次消费/领取前核对配置；认证开关、用户名或角色变化使旧授权失效，不向 Worker 暴露密码或可复用凭据。
- 协调 fail、失效报告、lease 过期和无 Worker 分开呈现。无 Worker 保持排队；已终结的失败不自动重新委派业务。失败监听和状态投影须覆盖不进入 completion handler 的路径。
- 新 Worker profiles/skills 按 Planner 说明只落在 process-typescript，明确其他 runtime 未新增对应角色。对话输出为建议，不得调用会安排/确认/推进的写入口。
- 本地提醒不必虚构 Plan 或协调执行：明确其到期状态及可查询提示；直接状态查询不新建业务动作。
- V8 仅新增表；不回填历史归属。回退先停用秘书调度并保留表/在途证据。Plan 归档清理与新增外键应明确限制或安全保留策略，不能级联删除秘书证据。

## 证据与限制

事实：读取了根 AGENTS、四个 core 规则、工程工作管理规则、code-review task-mode、fd-workflow planning、FD-021、ISSUE-00008 第 10 节及相关近邻源码。实际命令为 `Get-Content`、`rg`、`Select-Object`；首次尝试不存在的 `task-modes/review.md` 和 go/web 目录后，改用实际 `code-review.md` 及 src 路径。使用 `apply_patch` 写入本报告与 JSON。未修改实现、状态、receipt，未 commit、委派或运行网络。

假设：当前 FD 的“协调建议 + 平台确定性推进”实现遵循上述受控边界，不扩展代理授权或自主改写计划。

未验证：实现尚不存在；未运行测试、编译、迁移、服务、Worker、浏览器或验证脚本。本报告不是实现验收通过凭证。所列运行边界仅为静态设计判断。

## 唯一聚焦复核：D1/D2 设计修订

结论：**ready**。D1/D2 已在设计层面解决；原 findings 保留为历史，不再阻塞 design-ready。复核范围仅为 Solution 新增三段和 TaskExecutionPolicy、TaskExecutionService 实际检查顺序，没有扩展到新一轮全量评审。

事实：新增集中 gate 明列旧 confirm、jump、retryFailedTask、resumeReview、reserveRepair、continueAuthorizedRepair、创建执行/评审和内部 correction 创建入口，并覆盖任务指令修改/重试。内部 scope 限 exact Plan ID、同步服务端调用和 finally 清理，未托管 Plan 不变。该契约覆盖 D1 提出的旁路。

事实：现有 start 先 `tasks.lock` 再执行 policy；heartbeat、complete、fail 则在任务写入之前执行 policy。修订明确 start 前移 policy，并让秘书写入、领取和执行策略首先持有同一 `secretary_lock` 至事务提交。取消只更新秘书事实，不反向锁旧任务；RUNNING 终态仍可记录、释放容量，取消后的完成不推进。该单机全局串行化方案可实现 D2 所需的原子边界，代价是秘书事务共享串行吞吐。协调 request、consumed CAS、Plan 游标、源尝试和身份参与记录提供去重及陈旧响应控制。

已静态回读 FD 第 28、30、42 行，确认取消措辞已统一为逻辑取消；实施约束已写明 reviseInstruction/forceRequeue 的 gate 在 task 锁前、`UNIQUE(work_id, revision, phase)`（phase 为 START 或源 task ID），以及 start 前移检查后重读任务状态。单次只读核对同时成功解析 JSON，当前 recommendation 为 ready。

实施时必须保持以下设计细节：

- reviseInstruction/forceRequeue 的 gate 放在 `tasks.lock` 前；若 gate 获取秘书锁，不能沿用现有先 task 锁的顺序。所有入口严格遵守修订段的“首先获取互斥行”总规则。
- 对取消的描述按新增修订段理解为逻辑取消和禁止领取，旧段“已入队取消未领取任务”不可实现成反向更新旧 task。READY 记录仍可保留，UI 不据此声称安排可运行。
- 唯一协调请求由数据库键约束“秘书 ID + revision + source task/phase”，而不只是随机 request ID 唯一；消费 CAS 与创建下一任务同事务。显式重新请求协调必须有新的受控请求代次且不重做业务。
- policy 读到的 task 状态用于区分新 start 和终态报告；取消后 RUNNING 的 complete/fail 不抛拒绝异常，也不能经完成 handler 创建后续任务。CLAIMED 尚未 start 的取消任务不能开始业务。
- 实施与最终独立评审仍须核对事务代理、生效边界、旧 stop/acknowledgeStop 不引入反序锁，以及原子 SQL 的数据库兼容性。

限制：上述是可实现的设计约束，不是已实现的锁安全证明。新增秘书代码尚不存在；本轮只运行 `Get-Content`、`Select-Object`、`rg`、`ConvertFrom-Json` 并用 `apply_patch` 更新设计证据，未运行测试、编译、迁移、服务、Worker 或网络；未修改 FD 状态、receipt 或实现。无需再扩展设计评审轮次，落实检查点由实现及最终 fd-review 负责。
