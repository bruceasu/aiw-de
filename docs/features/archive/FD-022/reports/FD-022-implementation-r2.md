# FD-022 第一轮评审修复

<!-- aiw-data: FD-022-implementation-r2.json -->

- Worker：`01a15a47-fc32-7362-ac13-f660487b7632`。
- source event：`FD-022-000005-changes-requested`，已实际 claim。
- 源代码提交：`9306b35`。保留原完整实现报告，本报告仅补充 R1–R3 修复，不替换历史评审。

## 修复事实

R1：V9 增加发布 Plan 纳入记录及逐任务、逐 attempt 的发布许可。直接 A12_EXECUTE 与 A01/A03/A05/A06 附加发布授权在同一业务事务消耗额度、写许可，绑定授权 revision、代理 generation 和 boot。A12_EXECUTE 支持 CONFIRMED Plan 上唯一待执行任务的新许可；FAILED 任务仍走原显式重试。自动新轮次无许可，租约重领 attempt 不匹配则拒绝；旧许可因撤权、到期、替换、关闭或身份变化失效。claim 只读，start 在 task 锁前取 delegation 首锁并在 task 锁后重新校验；不在持 task 锁的自动完成路径反取首锁。Scheduler 为缺许可任务保留 HUMAN 或已有待处理决策。原人工创建/重试入口显式记录人工许可，首次启用前保留旧本地模式。发布基线按相同规范物理目标导入历史创建数、真实执行 attempt 数，副授权共用原累计 budget.consume。

R2：专业 claim/start 按稳定 Plan 加步骤查询全部 execution_attempt，排除与本次执行/评审角色相反的历史参与 Worker，覆盖跨轮次、失败重试、恢复评审和重注册；模型相同不影响不同 Worker 的隔离。A11 另检查 Gate 源执行者和评审/验证者历史交集，避免批准纳入前形成的自评结果。

R3：DelegationConditions 统一 ALLOW/DENY 的状态及 minimumRounds 条件，普通授权和发布副授权共用 GrantService.permitted；适用禁止抛出带精确版本的原因，由原失败独立事务保留 HUMAN。发布 start 还检查当前适用的禁止，避免已排队许可绕过新禁止。

使用文档和稳定规范同步补充上述合同。FD 全部实现项保持完成，PM 评审/交付/清理/归档待办仍未实际完成。

## 实际验证和限制

修复实现后以本地 JDK25 和 Maven 3.9.9 执行离线生产 `compile`，260 个源文件成功，无测试、下载或最终 jar。最后针对首次激活前兼容的一个条件表达式调整后，完成下述同一离线编译确认最终源版本。`git diff --check` 无错误，检查本次 12 个文件的修复差异及文档。

未运行测试、服务、迁移、HTTP、浏览器、真实模型、网络或部署。迁移方言、实际锁竞争、跨实例恢复和发布副作用仍未验证。静态推导：旧 attempt 无法匹配新许可，自动 round 缺许可不能进入 start；这不是运行时通过声明。

本次为第 2 次实现评审请求，未重置原循环计数。请独立确认 R1–R3 是否充分修复及有无回归。
