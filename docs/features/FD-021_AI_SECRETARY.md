# FD-021: AI Secretary：通用工作安排、持久调度与结果跟踪

**Status:** Open
**Revision:** 3
**Priority:** Medium
**Evidence policy:** Dual

## Problem

单机用户需要在秘书对话中澄清工作，把平台已支持的工作安排到未来，并查询真实进度。现有 Plan 确认会立即启动首步；没有持久的一次性安排、错过后重新确认或独立协调 Worker。批准来源为 ISSUE-00008 第 10 节，本轮交付 A+B。

## Options and decision

1. 扩展原 Plan 为调度器并把所有旧 Plan 迁到协调 Worker：影响既有语义及历史记录，拒绝。
2. OS cron 或新调度依赖：仍需业务版本、归属与取消控制，增加安装运维成本，拒绝。
3. 新增秘书模块，应用内扫描持久安排，仅对秘书安排的 Plan 增加独立协调任务：采用。既有 Plan 行为不变；协调 Worker 返回受限推进/转人工建议，平台保留确定性执行权，不允许改写步骤。

首期能力采用当前平台支持的开发/分析 Plan，以及本地站内提醒；直接查询进度由平台读取事实。任意外部操作、未安装专业 Worker 的能力不能由聊天承诺为已支持。秘书讨论使用独立 secretary-chat 任务和上下文，不复用 goal-discussion 的数据身份。

## Solution

新增 secretary 工作区：按控制台 actor 归属保存标题、工作说明、消息、关联草稿 Plan；安排冻结文本、Plan 版本/规范摘要、明确日期、IANA 时区和分钟。用户显式保存和确认，AI 建议不能直接安排。首期 Plan 从已有编辑器创建并由秘书关联；仅接受当前 actor 创建的 DRAFT，且每 Plan 只能有一条秘书工作记录，防止两条安排启动同一计划。

安排状态 DRAFT / SCHEDULED / NEEDS_CONFIRMATION / QUEUED / ACTIVE / DONE / CANCELLED / STOP_REQUESTED / NEEDS_HUMAN。数据库新增 V8 表及索引，不改旧表，不回填历史归属。revision CAS 与事务绑定状态变更和 WorkTask 创建。重复确认同版本不生成多份委派。采用带偏移的时间并核对 IANA 时区，不静默解释 DST 歧义。过去时间不直接安排；同一目标分钟触发正常，已越过目标分钟则 NEEDS_CONFIRMATION。应用重启不补跑；已入队不作错过处理。扫描每 5 秒仅处理待触发安排，单条事务隔离错误。

Plan 版本/规范或秘书确认输入改变使旧安排失效。平台创建初始 coordinator 任务，到期表示协调任务入队，不表示业务开工。独立 plan-coordination capability 读取冻结输入和事实，返回精确 PROCEED 或 NEEDS_HUMAN；仅 PASS+PROCEED 可请求平台启动/推进。每次 Plan 工作或评审完成后先创建唯一协调请求，然后平台按现有确定性步骤逻辑执行；原任务报告仍为开发评审依据。协调 Worker 不得是该 Plan 已参与的专业执行/评审 Worker，后续专业执行/评审也不得由参与协调的 Worker 领取。无合适 Worker 时保持排队。协调失败、无效或过期结果显示需人工处理，绝不自动重新做业务动作。

秘书取消在安排行 CAS 成功后阻止后续协调与领取；待触发直接 CANCELLED，已入队逻辑取消并禁止后续领取，执行中只记录 STOP_REQUESTED 并指向现有 Plan/Task 控制，不承诺停止或回滚。不调用会删除 Git 分支的 WorkflowCancellationService。现有 Worker lease/stop 管理仍为运行中停止事实来源。调度与取消、协调与推进均锁定同一秘书记录；取消后已领取协调结果不能推进。

独立设计评审 D1/D2 修订：采用单机秘书互斥行 `secretary_lock`，所有秘书写操作、托管任务领取及执行策略首先 UPDATE 同一互斥行并持锁至事务提交，再操作秘书/任务/Plan。领取 policy 在同一事务内复核状态与身份，写入协调/专业 Worker 身份参与记录；单个全局互斥行避免批次领取跨多个安排行产生相反锁序。TaskExecutionService.start 的执行策略检查移到 task 写锁之前；complete/fail/heartbeat 本来先经过执行策略再写任务，均先锁秘书。取消只更新秘书事实，不写/删除旧任务；已排队任务可保留 READY，但 claim/start 拒绝，UI 显示取消事实。运行中 complete/fail/heartbeat 必须允许上报并释放容量，完成 handler 在取消状态只保留结果、不推进。stop 等原有操作不获取秘书锁，不形成 task→secretary 锁序；协调消费再锁定 Plan 行并检查控制/最终人类结果/归档。

托管计划写权限使用仅服务端同步调用的 ThreadLocal scope，绑定 exact plan ID，finally 清理，任何外部 actor 或模型文本不能启用。WorkflowPlanService.confirm、jump、retryFailedTask、resumeReview、reserveRepair、continueAuthorizedRepair，以及 createExecution/createReview 均检查此 gate；WorkTaskService.createInternal 对 flexible-plan 的创建同样检查，防止 correction 等通用入口旁路；直接修改托管任务指令/重试拒绝并引导人工处理。未关联 Plan 不变。stop/withdraw/complete 等收紧/终止动作保留，失效协调不能推进；托管 Plan 不自动从旧控制恢复。

协调请求保存唯一 request ID、秘书 revision、源 task ID、原始完成报告 attempt ID、预期 Plan version、consumed CAS；秘书关联 Plan 唯一。初始确认后更新运行游标（合法 Plan version + spec hash），不把自身版本更新误判为外部修改；读取源报告/有效位置后才用原 advance。角色 claim 同时将 worker ID 与 Plan、角色写入唯一参与记录，反向身份也检查，防止协调 Worker 在之后兼任专业执行/评审。协调失败从实际 task 状态投影 NEEDS_HUMAN，不自动重做业务；只允许用户显式重新请求只读协调，并重建当前有效 source/version，历史请求保留。新增表对 Plan ID 保留逻辑引用，不加阻止旧 Plan purge 的外键；关联 Plan 丢失时转 NEEDS_HUMAN，秘书证据保留。

新增 /ui/secretary 页面及 /ui/api/secretary 会话/CSRF API，沿用现有组件、CSS 和控制台角色。actor 从服务器会话取得，所有秘书读写按 actor 筛选。关闭认证时沿用 web:local-console 并明确标注；不宣称租户隔离。受认证 operator 的人类确认是本次执行授权，无代理授权路径；到期重新检查该 actor 仍对应当前有效控制台配置/角色，配置变更时转重新确认。Worker 协调输出只消费原已授权范围，不能附加权限。

风险：新增持久化及并发/时间恢复边界，须独立设计评审；仅静态+离线 compile，不执行迁移、启动服务或网络。升级先备份数据库，Flyway 向前新增表；回退代码保留新增表，先停用秘书安排，不能删除在途记录。历史 Plan 不受新协调影响。

## Scope

实施约束：TaskExecutionService.reviseInstruction/forceRequeue 的托管 gate 放在 tasks.lock 之前；协调表 UNIQUE(work_id, revision, phase)，其中 phase 为 START 或源 task ID，防止不同 request ID 重复同一委派。start 检查前移后重新读取任务状态。独立设计评审报告为 reviews/FD-021-design-review.md，D1/D2 已补入设计，并由独立评审聚焦复核。

A+B、一次性安排、改期/取消、内容变化/错过确认、独立协调、基本结果查询与站内提醒。排除重复/条件安排、长期记忆、C 的独立通知投递及自动分析、代理授权、多租户、外部部署集成、运行时自主改写已确认计划。分析由用户通过原有入口发起。

## Work items

- [ ] 1.1 持久秘书记录及归属、修订、冻结安排契约；小/中等，约半天，独立验收新增表及 CAS，依赖无。
- [ ] 1.2 秘书独立对话任务与受限建议、上下文隔离和真实请求状态；小/中等，约半天，依赖 1.1。
- [ ] 1.3 一次性分钟调度、错过/内容变化重新确认及取消；小/中等，约半天，依赖 1.1。
- [ ] 1.4 独立协调 Worker 及受控 Plan 推进、身份隔离、重复/过期响应控制；小/中等，约半天，依赖 1.1、1.3。
- [ ] 1.5 控制台秘书页面、会话/CSRF API、真实状态/结果与导航；小/中等，约半天，依赖 1.1–1.4。
- [ ] 1.6 稳定规范、Worker 技能与操作说明、compile 和 Worker 报告，独立评审后本地交付；小/中等，约半天，依赖 1.1–1.5。

以上为一个端到端安排成果的依赖拆分，非独立业务子 Issue。每项完成须有实际代码/静态证据，不把时间估计当承诺。

## Acceptance

1. 对话保存真实请求 ID，秘书建议不自动启动任务；不同工作/Goal 上下文隔离；离线显示排队。映射 1.1、1.2、1.5。
2. 保存一次性未来分钟及明确时区，目标分钟内仅一条有效协调委派；错过分钟转重新确认，不恢复补跑。映射 1.3。
3. 改期、工作说明或关联 Plan 内容版本变更使旧确认/旧协调失效，用户查看最新冻结内容后重新确认。映射 1.1、1.3、1.4、1.5。
4. 独立协调 Worker 管理秘书 Plan 的初始启动和完成后的下一步；平台验证版本、状态、原始报告和人类授权；开发工作保留独立评审。旧 Plan 维持原路径。映射 1.4。
5. 取消后旧事件/协调结果不能新建后续任务；已领取工作显示停止请求及真实运行状态，不声称已停止/回滚。映射 1.3–1.5。
6. 错误/无效/失败报告保留事实，需人工处理，不自动重新执行业务；结果查询与反馈不重新启动工作，分析由用户发起。映射 1.4、1.5。
7. 所有新增记录带 actor，越权读写拒绝；无认证采用共享 local-console。没有新增 A01–A17 代理权限。映射 1.1、1.5。

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.
- 计划命令：`mvn -o -DskipTests compile`；scripts/compile-worker-runtime.mjs 仅适用 TypeScript，不用于 Java。不会下载依赖或生成最终发行包。
- 独立设计评审检查新增表/并发/授权/协调契约；独立 fd-review 为最终交付 Gate。
- 未授权测试、迁移执行、服务启动、浏览器或网络；这些未运行项目须保留为限制。

## TODO

完成独立设计评审后 emit design-ready，在隔离 worktree 实现以上项目；不遗漏当前设计的安全约束。

## Sources

- Issue: ISSUE-00008
