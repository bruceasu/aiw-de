# FD-056 独立静态审查

- Reviewer：`codex-fd056-review-20261027`。
- 来源事件：`FD-056-000002-review-requested`，本 Reviewer 已 claim。
- 比较基线：`7c1d51a8c177b69a6be438b3863559d3cb34e300` 上本 FD 增量；不重复审查工作区保留的 FD-054/055 改动。
- 最终结论：**通过**。本轮失联恢复缺陷已修复并静态复核关闭；修复后七项定向测试及无模型 API/历史核对证据已读取，无未解决阻断。

## 发现

**P1：短暂心跳过期会永久停用仍存活的 Worker。** `FactoryWorkerRegistry.retireUnavailable` 把过期的 ACTIVE Worker 更新为 OFFLINE；现有 `WorkerService.heartbeat` / `JdbcWorkerRepository.heartbeat` 仅更新时间，不恢复 ACTIVE 或注销标记。Windows 休眠、服务调度暂停等超过 90 秒后，如果注销先于下一次心跳执行，进程恢复心跳仍不能领任务且在列表隐藏。`ManagedWorkerProvisioner` 见原进程仍存活，不重启；启动超时后继续拒绝。应区分自动心跳失联与显式 OFFLINE：例如仅改变可见性并在 ACTIVE Worker 心跳恢复时清除注销，保留人工离线/排空状态，而不是永久改写运行状态。

## 已核对

- 本轮修复：注销阶段只对 Worker 行无操作 UPDATE 取得事务锁，不改运行状态；第二步按离线或心跳过期条件写可见性标记。成功心跳在同一事务先更新 Worker，再仅对 ACTIVE 身份清注销；人工 OFFLINE 不自动恢复。与 register 的先 Worker 后注册表顺序一致，避免注销与恢复交错留下过时标记。

- 工厂归属只由显式 remember 或经文件名、receipt workerId 一致性校验的旧状态回执导入；factory 前缀本身不使外部 Worker 注销。
- V22 为新增独立登记表；视图过滤不删除 Worker、历史任务、尝试、权限实体。工厂启动前允许预记尚未注册的身份，因此登记表没有 Worker 外键。
- 注销排除活动容量、运行任务及运行尝试；两个写步骤在同一事务。重新注册先更新 Worker，再清除注销，锁顺序与当前注销流程一致。
- 兼容键包含实际角色、仓库引用、运行时、模型、effort、Python、执行文件及 guardHome，不再包括设置审计版本。在线存活进程可复用；已退出且没有遗留活动工作的进程可原 ID 重启；已报告初始化失败在同设置版本拒绝重复启动。
- Fleet、配置协议和权限选择项只排除已注销工厂记录；原历史查询按 Worker ID 仍可追溯。未新增在线空闲超时 kill。

## 证据和限制

- 本 Reviewer 实际仅运行 `git diff/status`、`rg`、`Get-Content` 等静态阅读和真实 `aiw fd show/claim`；未运行测试、构建、模型、API 或重启。
- 已阅读 FactoryWorkerRegistryTest 的七项测试与断言，临时 SQLite 覆盖幂等、历史保留、外部前缀不代表归属、在线保留、心跳过期、容量/任务/尝试保护、重新登记及兼容键。原测试缺少恢复覆盖；本轮修改后增加过期 ACTIVE 保持状态、更新心跳恢复可见、显式 OFFLINE 不被心跳恢复断言，并同步新增行锁语句所需测试列。执行者报告修复后同组七项全部通过，最终 compile-only 成功（316 源）。
- 执行者报告七项定向测试与 compile-only 成功；不把这些报告改称 Reviewer 独立执行。
- 跨进程同 ID 重复运行的分布式互斥、数据库多节点部署、真实 Windows 休眠恢复未运行验证。本次以单本机平台的进程注册模型审查，不扩大为分布式调度实现。

## 实际运行证据核对

- 已读取 `C:/Users/suk/AppData/Local/Temp/aiw-goal-execution/fd056-registration-receipt.json`，注册视图 28 → 25，仅三条已证明归属的旧工厂 coder 注销；两条仍持续新鲜心跳的 ACTIVE 工厂 Worker 保留，符合不清理在线空闲进程的要求。
- 同一回执记录 Worker 28、工作项 31、执行尝试 37，前后均不变；historyPreserved=true，V22 success。列表过滤没有删除历史实体。
- FD Verification 记录备份 `D:/temp/data/aidev-r3.before-FD056-20261027-081651.db`、integrity_check=ok、平台重启、登录页 HTTP 200 及未调用模型。以上为执行者实证，Reviewer 仅静态核对，并未再次操作服务或数据库。
- 剩余未验证范围：真实休眠/恢复、完整退出后原进程 ID 再启动、PostgreSQL 并发及浏览器交互。当前相关安全行为由定向测试与源码支持，不宣称这些实际场景已运行通过。
