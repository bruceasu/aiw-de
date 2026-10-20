# FD-022 有限设计复核

<!-- aiw-data: FD-022-design-review-r2.json -->

结论：**ready**（仅设计就绪）。仅复核首轮 D1–D6 的实质修订；对象为当前 `FD-022_WORKER.md` Revision 2 中新增的具体接入方案、动作目标表、31 项 Work Items 和 30 条验收映射。来源事件为 `FD-022-000002-design-requested`。本轮初始发现的 D4 矛盾已在同一有限复核内修订，以下保留问题和核实依据。

## D4 的发现及本轮修订：完成路径间接获取全局锁

FD 写明 `WorkflowPlanService.confirm/repair` 等 application service 入口在所有原业务锁之前获取 `delegation_lock`，同时写明“普通完成不获取新锁”“V4 consume 不获取新锁”。这些规则与实际内部调用矛盾：

1. `TaskExecutionService.complete` 在同一事务中更新任务/attempt、释放 Worker 和并发资源，然后调用 `completionDispatcher.dispatch`。
2. 原 `SecretaryCoordinator.completed` 已持秘书及 Plan 锁，在 START 分支调用 `plans.confirm`。若 confirm 总在入口获取新锁，该路径就是秘书/任务/Plan → delegation。
3. 原 `WorkflowRepairAuthorizationService.decide → consume → plans.continueAuthorizedRepair` 同样发生在原完成事务中；若 repair 入口加新锁，就与“consume 不获取新锁”的承诺冲突。
4. 人工或新代理路径则按 delegation → 秘书/Plan/task 获取锁，形成反向等待的可能。只把新的 decision-delegate 候选完成延迟处理，不能消除上述两个旧调用链。

设计-ready 前需明确一种执行边界：例如拆出不获取新锁、只能由既有可信内部上下文调用的确认/续修实现，保留旧锁和纳入互斥；外部人工/代理包装入口再获取新锁。或者把相关旧 continuation 全部移入提交后的独立事务，并写清可靠重放与失败恢复。无需强制选较大重构；首种保守方案可由 Planner 自行确定，但内部上下文不得由 HTTP actor、参数或模型设置。Worker 应核对所有间接内部调用，不能只看控制器调用。

Planner 已将第一种方案写入 FD：两个明确的可信服务内调用使用 `legacyCompletion` scope，入口钩子在此 scope 不获取全局锁、不标记人工接管，其他调用仍先获取全局锁；例外不授予新通用权限。纳入同时排除旧 pending/在途修复和秘书在途协调请求，并在原 secretary/task/plan 锁下复核。已定向读取 FD 对应段落核实，不只依据 Planner 消息判定。

因此本轮设计矛盾已闭合。实现检查点是 scope 只包裹这两个固定 continuation，不得扩大到普通人工/代理调用；纳入检查和内部 continuation 保留既有原子性与锁序。是否实际正确落实由 Worker 和独立实现 Reviewer 检查，不由本报告提前判定运行时通过。

## 已闭合的发现

| 首轮发现 | 本轮结论 | 实质修订依据 |
| --- | --- | --- |
| D1 新旧授权兼容 | 设计上闭合 | 旧 PENDING/在途未结算时禁止纳入，旧 authorize 与纳入互斥，纳入导入历史基线，后续旧代理入口拒绝。D4 的内部锁调用仍须修订。 |
| D2 专用身份 | 设计上闭合 | 专用 ID 在普通协议逐身份认证，保留历史参与、活动租约禁止提升、凭证变更失效，不以共享 bearer 代替。 |
| D3 秘书 admission | 设计上闭合 | 受控入口、COORDINATOR/SPECIALIST 独立性、有效 authority/快照/无在途协调检查、候选自身独立 PROCEED、成功更新 generation/hash/admission。 |
| D4 人工优先与锁序 | 设计上闭合 | 人工入口覆盖、generation、事务外失败记录和两个可信 legacyCompletion 例外明确；纳入排除旧待决及秘书在途协调，并在原锁下复核。 |
| D5 A12 及目标键 | 设计上闭合 | 本地文档发布绑定源哈希/评审/目标，A05 和发布重试另需 EXECUTE，CREATE/EXECUTE 分账，使用 repository/workspace＋规范路径累计。A13–A17 均列稳定目标与业务约束。 |
| D6 工作项与验收 | 设计上闭合 | 31 个工作项注明规模难度、依赖及结果证据，全部动作和 30 条验收已有映射，真实只读 Worker profile 与服务器独立执行循环已落入计划。 |

模型执行也已明确冻结角色与 profile/catalog/model/effort，并在 guidance/start 强制校验，不能靠偏好回退扩大权限。调用点、DTO 和 SQL 的具体实现可以留给 Worker；不因本次设计复核要求额外测试或扩大范围。

## 证据与限制

事实：只读当前 FD 与针对上述调用链的 `TaskExecutionService.complete`、`SecretaryCoordinator.completed`；沿用首轮已读的 V4 consume/continueAuthorizedRepair 证据，没有扩大动作或源码搜索范围。

假设：Planner 继续在已确认 Issue 规则内解决锁边界，不启用实际授权或发布。未验证：实现、数据库实际锁行为、运行时并发。

实际运行 `Get-Content`、定向 `rg`、`Select-Object`、`Select-String`、JSON 只读解析和报告 `apply_patch`；未运行测试、编译、网络、迁移或工作流命令。只新增本轮 Markdown/JSON，不修改实现、FD、状态或 receipt，不产生实现 Reviewer 事件。
