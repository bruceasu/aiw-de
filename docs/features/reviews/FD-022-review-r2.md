# FD-022 第 2 轮独立实现评审：需要修改

<!-- aiw-data: FD-022-review-r2.json -->

- Reviewer：`fd022-independent-review-20261021-r1`，与 Worker 独立；沿用稳定身份。
- 源事件：`FD-022-000006-implementation-ready`，已检查 pending receipt 并实际 claim。
- 完整实现基线：`375ee82`；本轮修复比较基线：`db98391`；审查 HEAD：`fb0422a`，修复源码 `9306b35`。
- 当前循环第 2 次 outcome：`changes-requested`；前次报告及发现保留。

## 阻断发现

### R4 / P2：旧发布许可失效后，无法为同一个未领取 attempt 重新授权

位置：`src/main/java/me/asu/aidev/secretary/application/DelegationActions.java:347`，尤其 352 行；相关 `DelegationTaskPolicy.retryAllowed` 和 A12_EXECUTE 的 CONFIRMED 分支。

事实：`publicationCandidates` 只要发现同一个 task 的 `allowed_attempt == attempt_no + 1` 记录，就排除该任务，不检查记录绑定的 grant revision、到期时间、代理 generation 或 boot 是否仍有效。claim/start 现在会正确拒绝上述失效许可，但重新执行 A12_EXECUTE 时，CONFIRMED 分支和 `authorizePublicationTasks` 都要求候选恰为一个；旧记录使候选数为零。

具体路径：A12_EXECUTE 已确认发布 Plan 并为 attempt 1 写许可，专业 Worker 尚未领取；人工关闭再启用代理，或重启服务后重新人工启用，导致 generation/boot 不匹配。旧许可不能领取是正确行为；然而人工明确 return、更新/重新确认授权后，新的 A12_EXECUTE 候选依然因旧许可“存在”而失败，事务回滚到 HUMAN。续期换版或替换已撤销授权也有同样问题。原 attempt 尚未执行，不应通过改数据库、伪造技术失败或换 Plan 才能恢复。

违反：Issue 第 24 条和稳定规格的“人工明确交回后，基于最新身份、授权、状态及额度重新判断”，以及 FD 1.8/3.8 的恢复路径。当前恢复合同已经实现为允许 CONFIRMED Plan 重新许可，但过滤条件让常见失效路径无法到达。

最小修正：区分“仍可使用的相同 attempt 许可”和“存在但已失效的相同 attempt 许可”，允许经过完整新候选校验和显式人工交回的动作替换后者。保留先前授权/消费审计及累计消耗，不能重复消耗仍有效许可，也不能用直接删除记录绕过授权、额度或历史。保持当前全局首锁到 task 的顺序。

## 前轮发现核验

- R1：源码新增发布 Plan 稳定绑定及 task/attempt 许可；直接 A12_EXECUTE 和附加发布授权均写固定许可。claim/start 拒绝缺许可的新 round、attempt 不匹配的租约重领、撤销/换版/关闭/boot 变化；start 在 task 锁前获取 delegation 首锁并在锁后复查。原自动 round 通道在静态调用链上已受阻，新增的人类恢复回归见 R4，故不能判整个发布需求完成。
- R2：claim/start 对受管 Plan 同一步全部历史 execution_attempt 实施执行/评审角色分离；同 ID 切 profile、重注册、重试和恢复评审不能清除历史。A11 增加工程源与 review/verify 的历史参与交集检查。静态证据支持本次修复，未运行时验证。
- R3：普通授权和发布附加授权复用状态/轮次条件；适用 DENY 抛出含精确版本的原因，并由独立失败事务保存 HUMAN。静态证据支持本次修复。

本轮同时检查新增 V9 表、发布实际 attempt 基线、原人工 create/retry 的许可入口、后台自动完成上下文、模型与秘书 policy 叠加、claim/start 及失败/取消状态。没有要求重新决策已确认的认证兼容政策。

## 实际证据和限制

仅运行 `aiw fd show/claim`、`git log/diff/status`、`Get-Content` 等静态读取及本报告后的交接/提交；写报告后一次 `git diff --check`。未运行测试、编译、服务、迁移、HTTP、浏览器、网络或子代理。已阅读 Worker 第 2 轮 MD/JSON 报告，其中离线 Java25 生产 compile 260 文件通过；这是 Worker 证据，本 Reviewer 未重复运行，不代表运行时通过。

剩余风险仍是迁移方言、Spring 运行时装配、实际并发/跨实例恢复和发布副作用未验证；没有把缺少默认不要求的运行时检查另列为阻断。修复 R4 后可使用当前循环最后一次独立评审机会，不能重置已有 2 次 outcome 计数。
