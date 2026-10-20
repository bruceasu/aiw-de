# FD-022 独立实现评审：需要修改

<!-- aiw-data: FD-022-review.json -->

- Reviewer：`fd022-independent-review-20261021-r1`，与 Worker 会话独立。
- 源事件：`FD-022-000004-implementation-ready`，已实际 claim。
- 审查基线：`375ee82`；实现及补充证据 HEAD：`ebf68e12859bfb4c27dbbb39e63ea23fc9cf4b2e`。
- 本次为第 1 次实现评审；结论：`changes-requested`。

## 阻断发现

### R1 / P1：发布后续执行可绕过独立 A12_EXECUTE 授权与累计额度

位置：`src/main/java/me/asu/aidev/secretary/application/DelegationActions.java:222`、`DelegationTaskPolicy.java:28`；相关原路径 `workflow/application/WorkflowStepCompletedHandler.java:56`、`work/jdbc/JdbcWorkTaskRepository.java:353`。

事实：A12_EXECUTE 在确认 Plan 时消耗一次额度，但其目标只创建 `PUBLICATION:*` delegation_target，`retryAllowed` 仅识别存在 `PLAN:*` 目标的 Plan。直接通过 A12_EXECUTE 启动发布后，租约失效会恢复 READY，再次领取及执行没有 A12 授权/额度检查。另外，无论通过 A12_EXECUTE 还是 A05 启动，原 `WorkflowStepCompletedHandler` 在第 1/2 轮评审要求返工时直接生成下一轮发布任务；新任务 attempt_no 为 0，现有策略允许首次领取，无需再次校验或扣除 A12_EXECUTE。

后果：仅授权一次实际发布，即可产生多次发布执行；甚至授权已撤销/到期或额度耗尽后仍能进入后续发布尝试。不满足 Issue 已确认的“实际发布分别授权”和 FD D4/3.8 的发布重试保护。

最小修正：持久绑定发布 Plan 的受管身份；为每个发布执行任务/attempt 建立固定、事务化的独立发布准入记录。没有该记录的自动新轮次及租约重领不得执行，应保留人工待办或重新走当前 A12_EXECUTE 授权、证据和额度校验。保持既定锁顺序，不能在持 task 锁时反取 delegation_lock。

### R2 / P1：执行者与评审者没有服务端独立性约束

位置：`src/main/java/me/asu/aidev/secretary/application/DelegationTaskPolicy.java:36`；相关 `execution/application/TaskClaimService.java:134`、`secretary/application/SecretaryTaskPolicy.java:40`。

事实：新的 claim 策略对未 enroll 的专业 Worker 只检查重试额度及模型配置，没有查询成果执行参与历史；原 claim 服务也没有该检查。秘书策略把执行和评审都归为 SPECIALIST，并不区分二者。Worker 注册能力可以同时包含执行和 workflow-review，也可在同一稳定 Worker ID 下重新注册另一个专业 profile。因此，同一 Worker 可先执行 A01 生成的任务，再领取其 workflow-stage-reviewer 任务并提交 PASS。A11 的 lineage 检查只排除代理审批者参与，不能弥补执行与评审已相同的问题。

后果：Issue 第 12 条及稳定规格要求的“执行、评审、代理批准使用不同 Worker”未满足；A01 所谓独立评审及后续轮次结算可建立在自评上。

最小修正：在适用的专业评审 claim/start 边界，根据被评成果的 execution_attempt 历史排除参与执行的 Worker；覆盖重试、恢复评审、重注册及秘书托管路径，保留允许不同 Worker 使用相同模型的规则。不能只依赖 Worker profile 或提示词。

### R3 / P2：禁止规则忽略适用条件，误封本应允许的动作

位置：`src/main/java/me/asu/aidev/secretary/application/DelegationGrantService.java:112`；对照 `DelegationRequests.java:63`、`DelegationActions.java:298`。

事实：`permitted` 对 DENY 仅检查当前有效性、action 和稳定目标，完全忽略该规则明确保存的 states、minimumRounds。例：同目标 A01 的 DENY 仅在第 6 轮起适用，ALLOW 在第 3 轮起适用；第 3 轮仍会被 DENY 拦住。发布附加权限又另行实现 states 过滤但遗漏 minimumRounds，判定口径不一致。

后果：用户配置的条件不决定禁止规则的实际范围；“所有适用禁止优先”被扩大为“同目标所有有效禁止优先”，首个核心多轮授权场景可能长期不产生候选，而且静默返回而不留下冲突人工待办。

最小修正：统一在可信当前目标快照上计算规则适用性，并使普通授权与发布附加授权使用同一判定；只有适用的 DENY 才优先。冲突/无法判断应留下原因和人工待办，不应静默跳过。

## 审查范围和证据

已静态检查 Issue 已确认规则、FD 与验收映射、稳定 delegated-decisions 规格、Worker 报告、基线 diff；实际追踪身份认证、人工认证激活屏障、授权版本与禁止、候选与失败事务、三类预算、A01–A17 固定动作、秘书 admission、模型 start 校验、发布原服务、前端自然语言草稿与模板、V9 表以及 Worker 凭证传递。AUTH1–AUTH3 的新增持久认证、context-path 归一和旧请求 receipt 屏障在源码中存在；本轮未发现需要重新向用户决定的认证政策问题。

实际命令仅包括 `Get-Content`、`rg`、`git diff/status/rev-parse` 等静态读取，以及 `aiw fd show/claim`、`aiw fd emit --help` 和本报告后的交接/提交命令。若干初次读取使用了错误目录/文件名，随后改为实际路径读取；未将失败读取视为证据。

本 Reviewer 未运行测试、编译、构建、服务、HTTP、浏览器、迁移、网络或其他子代理。Worker 报告的 Java 25 离线生产编译、TypeScript 内存语法编译和 JS 语法通过作为已有证据引用，未重复执行，也不推定运行时通过。未提供运行时证据不是本次单独阻断理由；以上三项是已定位的实现缺陷。

剩余未验证风险：迁移执行、Spring 运行时装配、跨实例并发与恢复、真实 Worker/模型和发布副作用。修复 R1–R3 后由 Worker 按原预算提供更新证据并重新交接，不扩大验证预算。
