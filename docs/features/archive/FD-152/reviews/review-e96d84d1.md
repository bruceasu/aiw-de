# FD-152 独立复审

- 结论为 `changes-requested`
- 复审事件 `FD-152-000002-review-requested`
- 会话 `codex-fd152-independent-review-e96d84d1`
- FD 修订 2。基线 `bf9a2aff018a19a305a4199f1e607eef496012bc`，实现 `e96d84d1f275834506baaf0c41d668df0cc9e883`

## 阻断项

1. **P1 · 工厂满额时，不同连接的后续节点可能永久待领取。** 调度器用运行时与模型连接等条件筛选 Worker。设上限为 1，第一个节点使用连接 A，结算后 Worker A 空闲。下一个同类节点使用连接 B，Worker A 无法领取。回收查询只按 READY 任务的 Skill 和仓库判断，因此这个节点使 A 无法回收，B 无法得到启动位。FD 1.2 和 1.3 要求兼容容量调度和逐节点连接。回收时应使用与派发一致的任务兼容条件，并保留事务内的无活跃租约保护。 [回收调用](../../../../src/main/java/me/asu/aidev/execution/config/ManagedWorkerProvisioner.java#L134) [回收条件](../../../../src/main/java/me/asu/aidev/execution/config/FactoryWorkerRegistry.java#L86) [调度条件](../../../../src/main/java/me/asu/aidev/workflow/application/WorkflowWorkerProvisioning.java#L144)
2. **P1 · 目录写入互斥漏掉未绑定 Profile 的任务。** 工厂未配置默认 Profile 时，通用任务仍可派发给已有外部 Worker，领取却跳过目录互斥。已有 Profile 的候选也只检查有 Profile 的占用者。父目录和子目录分别设置 Worker 本地锁，两个允许的写任务可分别取得租约。FD 1.2 要求 Plane 在所有同目录和重叠目录写入之间串行准入，并保留未确认停止的占用。应将互斥准入与 Profile 匹配分开，查询时纳入所有相关写任务。 [领取入口](../../../../src/main/java/me/asu/aidev/execution/application/TaskClaimService.java#L135) [占用查询](../../../../src/main/java/me/asu/aidev/execution/config/FactoryTaskProfiles.java#L77) [Worker 目录锁](../../../../workers/process-python/plan_workspace.py#L37)
3. **P2 · 显式运行 Profile 会被员工偏好在开工时拒绝。** 带固定 Profile 的派发遵循任务指定优先，但开始校验只用旧 `WorkflowAiPreferences` 判断显式覆盖。员工有合法的默认模型 A、任务显式指定模型 B、且员工无模型许可名单时，固定 Profile B 可以进入队列并被领取，随后成员偏好过滤使开工校验失败。FD 2.2 要求显式任务或节点配置优先。应保存并使用已选 Profile 的来源，并在派发时说明剩余硬性模型限制。 [派发解析](../../../../src/main/java/me/asu/aidev/workflow/application/WorkflowRuns.java#L1080) [开工校验](../../../../src/main/java/me/asu/aidev/authority/AuthorityTaskPolicy.java#L133) [员工逻辑](../../../../src/main/java/me/asu/aidev/worker/application/TeamMembers.java#L441)
4. **P2 · 经理超时后未核对进程树已结束。** FD 1.5 要求停止后核对进程树。当前清理仅在根进程存活时向当前子孙进程和根发送强制终止，未验证退出便进入失败、释放占用和允许人工重试。终止请求可失败或延迟，根进程提前退出时也不会处理仍存活的子进程。应对进程树清理作有界确认，并保留未确认状态和明确的审计原因。 [超时和清理](../../../../src/main/java/me/asu/aidev/execution/config/ManagerProfileSelection.java#L109)

## 证据与验证范围

- 静态检查覆盖上述变更调用链，以及任务开始、模型凭据、共享工具申请和结算、Skill 版本与员工绑定、Worker 接口及两份稳定规格。核查了本机隔离报告和测试断言。`git diff --check bf9a2aff..e96d84d1` 通过。
- `target/fd152-final-vjytcq6j/outcomes.json` 支持同一 Profile、两个真实 Worker、不同项目并行、同目录串行、单任务竞争租约、员工配置和平台 Skill 版本冻结，以及停用 Skill 阻塞新任务。该证据没有覆盖异构连接在容量上限的排队回收、缺省 Profile 的目录冲突或新命名 Profile 与员工偏好冲突。
- `target/fd152-final-feo54q70/outcomes.json` 支持售后补件、凭证核对及审批，也支持经理超时、父进程退出、失败审计、人工重试和派发。该超时替身没有子进程，进程树的退出确认仍未验收。
- 运行记录由实施方提供。复审使用了 `git status`、`git diff`、`rg`、`Get-Content` 和本次 AIW 复审领取与结果登记。复审没有执行运行测试、外部模型调用、全量构建或部署。
