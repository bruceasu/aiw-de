# FD-024 第 1 轮独立实现审核

<!-- aiw-data: FD-024-implementation-review-r1.json -->

结论：**changes-requested**。以下三项为可定位的实现缺陷，阻止完整 FD 验收；不是因未运行测试而拒绝。

## R1-F1（高）：V10 丢失已有的永久人工认证锁存

位置：`src/main/resources/db/migration/V10__personal_worker_authority.sql:7`；`authority/AuthorityBoundary.java:32`；`webconsole/security/WebConsoleSecurityInterceptor.java:28`。

要求：FD §6.2 的 delegation_security 映射与 §6.4 明确保留不可清零的 legacy_auth_required；取消旧执行模式不授权降低既有认证边界。

事实：V10 将 `authority_metadata.human_auth_required` 固定初始化为 FALSE，没有读取 V9 `delegation_security.legacy_auth_required`。新 Boundary 和 Console 拦截器只读取新值。`AuthorityHttpConfiguration` 虽然保护 `/api/r1/**` 写入，却不覆盖既有 `/ui/**` 写入。`WebConsoleSecurityService.authenticated/role` 在认证关闭时仍返回本地已认证/ADMIN。

触发与后果：原库已开启永久锁存，升级后暂时关闭 Console 认证；旧 UI 表单可重新采用 local-console ADMIN，例如 `/ui/system/fleet-scheduling`。新私有数据封存 Gate 只阻止新委托启用，不会保护这条 UI 管理路径。原来的持久安全事实被实际清零。

最小修正：新增迁移保留既有锁存（或让新权威读取该持久事实，不能取 FALSE 覆盖 TRUE）；核对认证关闭时既有 UI 和 API 管理写入都失败关闭。无需恢复旧秘书模式。

## R1-F2（高）：间接发布绕过 PUBLICATION 目标的接管及世代检查

位置：`authority/AuthorityActions.java:341`、`:363`；`authority/ActionAdmissionService.java:164`；`authority/AuthorityTaskPolicy.java:80`；`authority/AuthorityControlService.java:44`。

要求：FD AC5/AC6、§6.6 及稳定规范 Start is a new authority boundary：固定发布 attempt 包括通过确认/重试等入口到达时，必须重查对应目标暂停、接管、世代和禁止版本。

事实：A05/A03 等动作的主目标为 `PLAN:...` 或其 step；`requirePublicationAuthority` 为其附带检查 A12_EXECUTE 授权并扣发布额度，但 `grants.permitted` 不检查 PUBLICATION 目标的 paused/generation/restrictionRevision。`authorizePublicationTasks` 只保存发布授权/身份信息；最终 `bindPermits` 的目标世代取自原 A05/A03 目标。actual start 的发布分支重查授权/身份与 DENY，却不重查 PUBLICATION 目标暂停/世代。

触发与后果：已分别授予 A05 与 A12_EXECUTE，人工接管 `PUBLICATION:<hash>` 后，仍保持未暂停的 PLAN 目标；提交 A05 可以扣额、生成发布单次许可并通过 start。若在准入之后、start 之前接管 PUBLICATION 目标，也不能使这份以 PLAN 为主目标的许可失效。用户已完成的发布目标接管被旁路。

最小修正：将附带 A12 能力对应的 PUBLICATION 目标作为独立强制边界保存其世代、禁止版本和 owner，在间接准入及实际 start 首锁内重查；保持主 PLAN 目标的检查。所有直接与间接发布入口共用该谓词。

## R1-F3（高）：人工重试覆盖 UNKNOWN 意图，跳过显式对账

位置：`authority/ManagedPlanAdmission.java:24`、`:29`；`workflow/application/WorkflowPlanService.java:243`、`:278`；`authority/AuthorityTaskPolicy.java:50`。

要求：FD AC4、§6.6 和 Unknown effects are not failed effects：未知效果不能被普通重试当作无效果；人工也必须先对原意图记录明确结论/证据，保留历史后另行批准新的执行。

事实：个人动作入口有 UNKNOWN 拒绝及 effect_history 保存；但是人工接管后直接调用既有 `retryFailedTask`，会在任务重置后调用 `allowHumanTask → humanTask`。后者的无条件 ON CONFLICT 更新把原许可的 started 改为 FALSE、state 改为 PREPARED、grant 改为 HUMAN，没有检查原许可是否 UNKNOWN/未对账，也没有将旧意图先写入 effect_history。actual start 的 HUMAN 分支在校验当前操作角色后直接返回允许。

触发与后果：受托任务已发生或可能发生外部效果，报告失败/租约回收后被观察为 UNKNOWN；owner 人工接管并点击普通技术重试（只有重复风险警告，没有原意图对账结论）即可覆写唯一许可并开始新 attempt。尚未查清的旧意图与回执可能失去关联或被后续观察更新；专用 `/effects/{id}/reconcile` 的保护被正常 UI 路径绕过。

最小修正：在改变任务/许可前，从统一首锁下检查原 task+attempt 的 UNKNOWN 或其他未确认运行意图；要求先走明确证据对账。所有人工替换许可的路径保留旧 attempt 意图和原回执关联，再建立新许可；不要把人工角色等同于旧效果已确认。

## 审核依据与证据

- Reviewer 独立身份：`reviewer-fd024-r1-20261021-4c735`，不同于 Worker；已先确认 pending receipt，再 claim `FD-024-000007-implementation-ready`。
- 比较基线：`c182720`；审核实现 HEAD：`5136c40932297ccac01897c9c1ea0656af90dced`。emit 产生的 FD Revision 7 / Pending Verification 是受控未提交变更，一并保留。本轮是实现审核 1/3，设计审核不计入。
- 已读取最新 FD、Worker 双文件报告、当前 secretary/delegated-decisions 稳定规范及旧 Worker 协议证据，核对用户取消旧模式兼容、仅实现迁移工具、禁止实际迁移/切换的决定。G1 已按最新决定成为未来执行 Gate，不是本轮工具实现的未答阻塞。
- 实际审阅 V10 schema、新 authority 的认证/路由、Grant/Control、ActionAdmission/20 动作适配、TaskPolicy/实际 start、预算、ManagedPlanAdmission/Coordination、UNKNOWN Observer，以及相关业务服务变更；阅读 Python 私库、outbox/runtime、独立 UI、迁移 source/planning/CLI。迁移工具的只读快照、归属不明封存、ARCHIVE_ONLY 及禁用执行入口有静态依据。
- Worker 报告的 compile-only 入口明确离线 Java compile、Python 内存编译、JS 语法检查与 TS 语法转换；阅读脚本后未发现把测试/运行检查伪称通过。Reviewer 没有重跑编译，也没有执行测试，不能证明 SQL/Spring、并发、HTTP/浏览器或外部效果已运行通过。
- 本次实际命令仅 `Get-Content`、`Select-String/Select-Object`、`rg`、`git status/diff/log`，必要的 `aiw fd --help/show/claim/emit --help`，以及报告/FD 编辑和受控 Git/交接操作。未运行测试、编译、服务、HTTP、模型、网络、真实数据库迁移/切换、格式化、额外代理、merge/archive/push。

以上后果来自具体调用链的静态推导，未经运行复现。只修改审核双文件与 FD Verification；生产代码由 Worker 修正。修正后应按新 implementation-ready 事件进入下一轮独立审核，不能将本报告当作 verification-passed。
