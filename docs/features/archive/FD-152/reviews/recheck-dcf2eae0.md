# FD-152 独立复核

- 结论为 `verification-passed`
- 来源事件 `FD-152-000005-implementation-ready`，FD 修订 5
- 会话 `codex-fd152-independent-recheck-dcf2eae0`
- 原复审版本 `e96d84d1f275834506baaf0c41d668df0cc9e883`，修复代码 `278db898221680fc2034fcdfde1ed739e2df8513`，复核提交 `dcf2eae0eaa4d90053effe3b9cc64e7595e383e8`

## 原发现的处理结果

1. 工厂上限为 1 时的异构 Worker 回收已修复。`ManagedWorkerProvisioner.releaseIdle` 使用任务能力、固定运行时及模型连接确认排队任务能否使用该进程。`FactoryWorkerRegistry.drainIdle` 更新进程状态时继续检查活跃租约及尝试。隔离验收观察到 Chat Completions Worker 完成后进入 `OFFLINE`，随后 Responses Worker 取得容量并完成任务。真实账号连接切换未在这轮运行，连接版本与归属校验已静态核对。
2. 无 Profile 的写入也在领取事务中受同一目录互斥保护。`TaskClaimService.claimNext` 对写任务独立检查占用。`FactoryTaskProfiles.workspaceAvailable` 将无 Profile 的租约纳入查询并持久化规范路径，过期且未确认停止的占用仍保留。协议验收核对了父目录的规范路径，以及该租约下无 Profile 和固定 Profile 的两个子目录任务均未领取。更早的双 Worker 验收继续支持已配置 Profile 的同目录排队与竞争领取。
3. 显式 Profile 可以覆盖员工默认模型。`AuthorityTaskPolicy.selection` 使用派发固定的 Profile，`WorkflowRuns.dispatchTask` 和 `TeamMembers` 保留明确的员工模型许可名单检查。隔离验收中，同一员工的两项任务分别指定 `fixture-main` 与 `fixture-other`，第二项成功完成并使用指定版本。
4. 经理超时会等待本机进程树退出。`ManagerProfileSelection` 追踪可观察到的子进程，以有界清理确认已登记进程退出，已知残留进程会阻止下一次启动。验收通过系统进程列表核对真实父子进程均已退出，失败审计记录明确的超时原因。人工重试追加成功审计并派发了节点。清理基于本机进程观察，验收范围为正常父子进程关系。

## 复核范围与证据

- 查阅了 Worker 修复报告、FD Verification、调用链、隔离脚本断言和两个原始 JSON 结果。`target/fd152-review-fix-mtw9xg0n/outcomes.json` 支持容量和超时用例，首次目录场景的准备失败保留在记录中。`target/fd152-review-fix-jdsq5k5g/outcomes.json` 支持修正登记能力后的两项目录用例，以及容量和显式 Profile 用例。
- 本次独立复核使用 `git status`、`git rev-parse HEAD`、`git diff`、`rg`、`Get-Content`、`aiw fd claim` 和结果登记。实施提交范围的 `git diff --check e96d84d1..dcf2eae0` 通过。
- 运行证据来自实施验收。独立复核未重新执行测试、外部模型调用、全量构建或部署。
