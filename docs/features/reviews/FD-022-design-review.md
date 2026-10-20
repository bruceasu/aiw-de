# FD-022 独立设计评审

<!-- aiw-data: FD-022-design-review.json -->

结论：**changes-needed**。评审对象为 `docs/features/FD-022_WORKER.md` Revision 2，来源事件 `FD-022-000002-design-requested`；Planner session 为 `01a15a47-fc32-7362-ac13-f660487b7632`。本评审只提供设计证据，不产生实现 Reviewer 事件。

## 阻止 design-ready 的发现

### D1：新旧授权的共存边界尚未闭合（高）

事实：`WorkflowRepairAuthorizationService.authorize/consume/decide` 只处理旧单轮授权、Plan/Task 版本及一小时有效期，不读取新代理开关、人工归属或新累计账本。V4 固定 `round_budget = 1`，但不能因此推导为“同一目标的累计额度已经受到限制”。

FD Solution 8 一方面保留旧在途语义，另一方面要求旧路径不能绕过新累计额度和人工接管，却没有确定目标从旧路径进入新策略的边界。若新旧消费可以交错，同一目标会出现绕过或重复计算。

修订要求：明确目标纳入时机、旧 PENDING/在途执行处理、历史轮次基线与以后旧 authorize/consume 的统一检查。保守可选方案是存在旧 PENDING 或未结算执行时拒绝纳入，等待完成/到期或人类既有撤销；纳入后旧入口使用同锁、归属检查和累计账本。另一方案是纳入时将旧 PENDING 标记失效并转人工，但必须同步修改“保留在途原语义”的承诺，保留原授权与失效审计。不得更改 V4/V5 历史迁移或把旧 scope 文本转换成新权限。

这是已确认业务约束内的工程选择。只有要求同时无条件保留旧在途执行、又让新撤权追溯取消已经生效工作时，才需要人类重新决定冲突政策；当前没有此要求。

### D2：普通 Worker 协议可以污染专用代理身份（高）

事实：`WorkerProtocolTokenFilter` 使用可空的共享 token，不提供每个 Worker 的身份认证；`WorkerProtocolV1Controller` 接受请求中的 Worker ID；`WorkerService.register` 写入 ACTIVE。仅让新代理入口认证凭证，不能证明相同 ID 的旧 register/claim/start/complete 等调用受到保护。旧执行/评审参与记录同样按 Worker ID 关联。

修订要求：专用代理 ID 在普通协议中保留，涉及该 ID 的注册、心跳、领取、执行和状态变更必须认证对应凭证或拒绝；共享 token 不等于该代理凭证。定义首次将既有 Worker 提升为代理时的身份绑定及历史参与保留，不能清空或改名绕过自审限制；代理批准需要服务端追溯来源成果所有相关执行/评审参与记录，缺失可靠证据时转人工。普通非代理 Worker 的本地协议可继续保留原语义。

该边界可由 Planner 自行确定；用户已经明确要求认证身份和禁止同成果自审，无需再次确认。

### D3：既有秘书 admission Gate 未定义合法接入方式（高）

事实：`SecretaryPlanGate.check` 阻止托管 Plan 通过普通确认、续修等路径启动；`WorkflowPlanService.reserveRepair/continueAuthorizedRepair` 调用此 Gate。`SecretaryCoordinator.completed` 还校验秘书状态、授权、Plan hash/version、源任务及独立协调结果，再通过服务端 `advance` scope 推进。

因此“调用既有 application services”会使托管 Plan 的 A01/A03/A04/A05 等被拒绝；直接包一层 `gate.advance` 则可能绕过原人工授权快照与独立协调。

修订要求：明确哪些动作适用于托管 Plan，代理如何形成经过新授权校验的有限 admission，如何保留秘书取消/接管及独立协调约束，以及动作后如何刷新秘书 Plan 快照。将入口、事务 owner、失败转人工和后续协调结果关联写入 FD；不得把聊天或普通协调的 PROCEED 解释为通用授权。

这是模块接口设计问题，可自行解决，不是新的业务范围问题。

### D4：同事务再校验尚不能证明人工优先（高）

事实：秘书已有全局锁及 Plan/Task 锁；旧授权消费、Plan 版本转换和审批 CAS 各自提供局部保护。FD Solution 3/6 没有指定新撤权、开关、人工接管和所有人工目标变更如何与代理共享串行化边界。

修订要求：确定统一锁序，例如代理全局互斥行 → 秘书互斥行 → 业务目标锁，并核对既有完成/调度/取消路径无反向锁序；不能只在新代理提交中加锁。所有相关人工入口，包括旧 Worker 协议状态变更、Fleet、归档、删除、审批，都必须推进同一目标 generation 或参与相同串行化。定义线性化时点：人工变更先提交则旧代理提交冲突；代理动作已经提交则保留事实，后续撤权不回滚。提交执行、额度记账、幂等结果及审计需原子完成；失败审计/转人工记录不能随异常事务全部回滚。

锁方案及事务划分可自行确定。运行时并发尚未验证，静态设计评审不能表示并发通过。

### D5：A12 仍可被普通计划确认绕过，稳定目标键不够具体（高）

事实：`TaskOutputService.publication` 验证输出文件、完成态及依赖 PASS review，创建 `publish-document` 草稿并保存发布关联。真正发布通过后续 Plan confirm 创建 Worker 任务进行，是本地项目文档写入，不是环境部署。

修订要求：A12_EXECUTE 应绑定发布计划关联、精确文件 ID/hash、来源 attempt/review、repository/workspace、目标路径和计划版本；A05 confirm、重试及其他可创建同一发布任务的代理路径不得绕过独立 A12_EXECUTE 权限。仅有 EXECUTE 不隐含 CREATE。至少明确 publication CREATE/EXECUTE 各自的稳定目标映射，不能使用每次新建的发布 Plan ID 充当累计额度身份以便重置计数；建议按服务端可信 repository/workspace 身份和规范目标路径建立发布目标，并保存源文件/证据绑定。其他 A13–A17 同样需给出 Fleet 固定身份、Worker 稳定身份、归档/删除对象身份及对应版本。

这些均为既定“目标＋动作”规则的工程落实，可自行选择可靠键；无法建立可靠身份的请求转人工，不能扩大作用域。

### D6：Work Items 未达到可执行拆分和验收追溯要求（中）

FD 只说“全部 30 例逐项映射”，实际没有映射；没有逐项规模、难度、依赖与独立完成标准。1.2 合并迁移/认证/授权/模板，1.3 合并待办/接管/幂等/额度/审计，1.5 合并 A05–A12，1.7 合并处理循环/控制台/详情/文档，不能据此认定均为半日内小型中低难度工作。

修订要求：实施前按独立成果拆分，分别标注依赖、规模、难度和完成证据；为 A01–A17 建立动作→目标身份→原服务→权限/额度→失败行为→Work Item 的简表，并为验收例 1–30 建立实际映射。处理循环应落实到真实 Worker runtime/profile 和协议接入，不得仅有服务端候选执行器。自然语言转草稿、人工结构化确认及原始文本保留应有明确入口和成功/缺字段例。所有动作仍须保留首期范围，不能以目录展示或持久化任意 JSON 代替实际接入。

## 覆盖与已成立的方向

草案在文字上覆盖 A01–A17，并正确区分 A01 完整执行、A03 技术重试、A04 重评，以及 A12_CREATE/A12_EXECUTE。独立凭证、禁止优先、显式替代、服务端时间、默认关闭、模板不复制凭证/消耗/启用状态、人工主动交回等方向与 Issue 第 1 节已确认规则一致。

目前动作表述的覆盖不等于完整设计：D1–D5 尚缺可实现的兼容、认证、串行化、托管 Plan 和目标映射决定；D6 尚缺可审阅实施单元。补齐后可进行一次仅针对实质修订的有限复核，不应自动开启多轮评审。

## 决定归属、假设与未验证事项

- 人类决定：现有已确认规则足够支持上述保守工程修订，未发现必须新增的人类业务决定；不得把 `changes-needed` 自动解释为要再征求用户批准。
- 工程选择：新旧目标纳入、凭证绑定、锁序、事务划分、发布稳定身份、合法 secretary admission 和 Work Item 拆分由 Planner 决定并写入唯一 FD。
- 假设：本轮产品实现不启用任何实际代理授权、不执行当前环境发布、不 push/部署；A12 产品执行指本地项目文档发布能力。
- 未验证：没有实现存在性结论，没有执行测试、编译、迁移、服务、Worker、浏览器或网络请求；并发与数据库锁行为仍仅为静态评估。

## 静态证据与命令

已读根 `AGENTS.md`、四份 core 规则、Java Spring/review 规则、work-management、fd-workflow planning/证据合同，FD Revision 2、正式 Issue Plan 的全部已确认规则与 30 条验收、原始授权目录；并检查 Worker 协议/filter/service、旧 V4/service/claim policy、WorkflowPlanService 续修、SecretaryPlanGate/Coordinator、ApprovalService、TaskOutputService publication 和 pom.xml。项目未发现其他适用子树 AGENTS/CODEX。

实际命令为 `Get-Content`、`Get-ChildItem`、`Select-Object`、`Select-String`、`rg` 和文档 `apply_patch`。早期查找 `go/` 和 `task-modes/review.md` 未命中，随后按实际 Java 源码与 `code-review.md` 修正，未执行运行时命令。仅新增本报告及 JSON；未修改 FD、实现、状态、receipt 或 Git。
