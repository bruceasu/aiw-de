# FD-011: Worker 灵活任务与流程编排

**Status:** Complete
**Revision:** 6
**Priority:** Medium
**Evidence policy:** Dual

## Problem

ISSUE-00001 要求管理者按目标选择技能和有限流程，将明确边界的工作交给 Worker。现有自主需求流程固定；ExternalWorkflowController 支持有限 DAG，但其提交凭证由 intake Worker 持有，不能作为人工确认的证明，也没有持久化的待确认计划或技能偏好。

## Options and decision

1. 改造全部 AutonomousRequirement 完成处理器：影响已有运行、审批及恢复，不采用。
2. 扩展 external-workflow 提交令牌为人工确认：该令牌实际交给 acceptor Worker，不能满足确认边界，不采用。
3. 增加独立灵活计划入口，复用 WorkTask、依赖解析和 Pull：采用。管理者通过现有 Web Console 会话与 CSRF 提交/确认/收回；计划可来自人或 AI，但来源文字不授予执行权。

## Solution

- 计划包含目标、来源（MANAGER/AI）、有序 1–32 个步骤；每步有 key、必需技能、偏好技能、执行 skillRef、instruction、可选仓库和不可变 revision、前置步骤。技能映射为现有 Worker capabilities，不引入内部工具权限。
- 草稿由管理者保存，未确认不创建 WorkTask。管理者明确确认当前 version 后，在同一事务创建 EXTERNAL_WORK DAG 并将计划标为 CONFIRMED，记录 actor 与时间；重复确认同一已确认版本不重复创建工作。
- 不强制开发模板；单步分析即可提交。模板复用通过复制任一已存计划为新 DRAFT，重新确认。AI 建议/计划由管理者导入，AI 调用与新规划 Worker 不在范围。
- 管理者可在草稿中保留工作、不委派；执行图只包含本次选定的 Worker 工作。管理者保留的工作不伪造完成输入，不隐式进入执行图。若某工作依赖管理者保留工作，须先由管理者整理输入后再提交新的有界计划；本 FD 不增加人工步骤执行协议。
- 确认后收回整个计划：事务内按 TaskId 排序锁定所有任务，若任何任务 CLAIMED/RUNNING 则整体拒绝，不改变租约；否则取消 READY/BLOCKED/FAILED 工作，保留完成结果，将计划置 WITHDRAWN 并记录原因。该操作取消后续委派，不声称停止运行中的 Worker。再次委派通过复制新草稿。
- 偏好只在有效优先级相同时参与排序，之后保留现有活跃租约/等待时间/ID 排序；必需能力仍由现有 supports 检查。通配 Worker 匹配所有偏好。无偏好任务保持原顺序。
- 新增独立计划和 task preference 表，不改变 WorkTask record、现有 Worker JSON、外部流程请求结构及自主运行语义。使用现有 SQLite/PostgreSQL 可移植 SQL、epoch 时间和事务。
- 新的管理入口位于 /ui/workflow-plans，复用会话、operator 权限和 CSRF。先提供可审阅的管理 API；专门的交互表单和导航属于后续 UI 范围，现有页面视觉不变。

## Scope

包含持久计划、校验、显式确认、复制模板、非运行中收回、必需/偏好匹配与管理 API、附近文档。排除强制停止、修改现有自主流程、自动 AI 模型调用、人工任务完成协议、新部署和实际迁移执行。

新增 schema/API/调度属于风险边界：仅追加表，不重写历史迁移；旧任务无偏好。升级遵循现有运维文档的停止/排空、备份、Flyway、恢复 Worker 顺序。回滚先停用本入口与排空新增任务，再恢复兼容备份/应用组合；不删除新表或修改 Flyway 历史。未运行迁移兼容性测试是明确残余风险。

## Work items

- [x] 1.1 计划/步骤模型与有界 DAG 校验。小（≤半日），中难度；无依赖；拒绝重复、自依赖、前向/缺失依赖、无效技能及工作区参数。
- [x] 1.2 可移植计划、偏好持久化与追加迁移。小（≤半日），中难度；依赖 1.1；持久保存 JSON、version、actor/时间和状态，任务偏好单独存储。
- [x] 1.3 草稿、复制、确认、收回应用事务。小（≤半日），中难度；依赖 1.2；未确认无任务、事务回滚、确认幂等、收回活动任务整体拒绝。
- [x] 1.4 现有控制台鉴权边界内管理 API。小（≤半日），低难度；依赖 1.3；operator/CSRF、version 与错误映射、输入来源不授执行权。
- [x] 1.5 偏好排序接入 Pull。小（≤半日），中难度；依赖 1.2；保留必需能力准入、有效优先级与旧无偏好排序。
- [x] 1.6 文档、编译与实现证据。小（≤半日），低难度；依赖 1.1–1.5；更新管理契约、实际静态与编译证据，跳过检查如实记录。

原 1.1 是未实现占位，现替换为模型结果并新增 1.2–1.6；未开始实施，无完成历史被删除。

## Acceptance

1. 管理者保存 AI 或人工计划后，确认前无可领取任务；只有 operator 会话显式确认当前 version 才创建任务。
2. 单步或自选 DAG 独立运行，不自动补齐固定开发流程；复制模板得到新草稿。
3. 必需技能不满足的 Worker 不领取；偏好不足不禁止领取，优先级相同候选按匹配数优先。
4. 不合法图/参数全部拒绝，不产生部分任务；失败事务可恢复，旧确认不扩展范围。
5. 非活动工作可收回并留下 actor/reason；存在 CLAIMED/RUNNING 时拒绝整个收回，不冒充停止确认。已完成结果保留。
6. 现有 Worker Pull/lease、外部流程、自主运行和无偏好排序保持兼容。

## TODO

1.1–1.6 实现与证据已完成。用户 confirm 授权依赖下载，`mvn -DskipTests compile` 已通过；下一步 implementation-ready 与独立 Reviewer。未运行检查不记为通过。

## Verification

静态追踪输入校验、确认事务、收回与领取竞争、会话/CSRF、持久化和偏好排序。Java 优先使用适用 compile 脚本，否则 `mvn -o -DskipTests compile`（无最终发行包、无依赖下载）；不运行测试、迁移或应用。独立 Reviewer 按本 FD 与实际 diff/实现报告审查，最多三个结果。

独立 Reviewer r1：`fd011-reviewer-20261013-7d093b` 已领取 `FD-011-000005-implementation-ready`，审查基线 `2a96a71` 到 `02162e0`；静态验收无阻塞发现，verification-passed。证据见 `reviews/FD-011-review-r1.md` 与 JSON。Reviewer 未执行测试、编译、迁移、应用或网络；数据库/JSON/并发运行风险仍保留。当前独立 Reviewer outcome 数量为 1。

## Sources

- Issue: ISSUE-00001（APPROVED，revision 3）
- WorkTaskService、DependencyResolver、TaskClaimService、PullSchedulingPolicy、ExternalWorkflowController。
- WebConsoleSecurityService / WebConsoleSecurityInterceptor；workers/demo-deployer/src/acceptor.ts 持有既有 submission token。
- docs/current/06-OPERATIONS-GUIDE.md 的备份与迁移顺序（旧 migration 版本列表有滞后，以源码为准）。
- Planner Session: fd011-host-20261013-c94b7e；source event: FD-011-000002-design-requested。

## Worker evidence

- Session: fd011-host-20261013-c94b7e；source event: FD-011-000004-design-ready。
- 报告：reports/FD-011-implementation-r1.md + JSON。
- `mvn -o -DskipTests compile`：退出 1，缺少离线 Spring Boot 4.0.8 父 POM，未进入源码编译；未重复执行。
- 已执行一次定向静态只读检查；未运行测试/迁移/网络/最终构建。
- Gate：reports/FD-011-blocker-compile-20261013-c94b7e.md（resolved）。
- Reviewer outcome 数量：0；尚未 handoff、合并或归档。

- 编译恢复：用户 confirm 授权下载，`mvn -DskipTests compile` 退出 0，194 Java 源文件，9.980 秒。历史离线失败不擦除。

**Completed:** 2026-10-13
