# FD-019：R1 领取策略一致性阻塞与解除

<!-- aiw-data: FD-019-blocker-review-r1.json -->

- 阶段/角色：Review → Worker；来源 FD-019-000006-changes-requested。
- Session：fd019-worker-20261021-b63ea8；状态 resolved，2026-10-21。

R1 独立 Reviewer 静态发现：当前 AI catalog 在 TaskClaimService 对所有非推荐任务预过滤，而 goal 请求拥有冻结配置，配置变更后领取与 guidance/start 可能不一致。Worker 读取真实审阅报告和路径后领取修复事件，增加 GoalTaskPolicy 管理任务的预过滤豁免，后续冻结 canClaim 检查仍保留。

离线 Java compile 与一次差异检查通过；未执行运行验证。此代码修复已解除原原因，是否通过整体实现仍由 R2 独立审阅决定。无新的人类决策需求。可复用改进：新增冻结策略应同时审阅 admission、guidance、start 和通用预过滤，避免只新增局部 policy；本次未扩大修改共享技能。缺失的 blocker 模板仍沿用既有双格式结构。
