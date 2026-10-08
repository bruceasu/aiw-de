# 测试、验证与当前限制

## 1. 当前正式基线

```text
R8-S13 Fix2
package: me.asu.aidev
Java 21
Spring Boot 4.0.8
Flyway V1-V14
9 tables
29 Failsafe IT methods
```

用户本地的 authoritative `mvn clean verify` 已通过该基线。

## 2. 主要覆盖范围

当前 Test 覆盖 Task Graph、Worker Lease、Crash Recovery、Worker Protocol、Git Workspace/Result、Result Handoff、Completion Handler、Repair Loop、Engineering Gate、Human Approval、Telegram、Autonomous Run、Parallel Implementation/Integration、Operator Control、Timeline/Dashboard、Fair Scheduling、Run/Fleet Admission 与 Runtime Scheduling Control。

## 3. 当前最重要的未验证项

### 真实 AI 长流程

下一步最应该验证的是：真实 Codex + 真实小型 Repository，从 Requirement 一路跑到 Approval。

重点观察 Prompt、Planning Manifest 稳定性、Repair 是否收敛、Integration conflict、Token/时间成本。

### PostgreSQL 多实例并发

需要专门压测：claim vs claim、claim vs pause/cancel、claim vs scheduling change、completion vs lease expiry、多 Control Plane 共享 Fleet budget。

### Production Auth

Management API 当前没有完整 Auth/RBAC。

### Release/Delivery

当前生命周期在 Engineering Gate + Approval 后结束，没有正式 PR/Merge/Release/Deploy。

### Capability Quota

原计划 R8-S14 已暂缓。现阶段已有 Worker capacity + Run limit + Fleet limit。只有真实负载证明需要共享资源 quota 时再实现。

### Multi-repo Final Authority

已有 repositoryRef，但当前 Parallel Integration 主要面向同一 Repository/同一 Base 的 WorkItems。正式 Cross-repo Gate 仍是未来议题。

## 4. 建议推进顺序

1. 冻结并完善文档；
2. 真实 Codex E2E；
3. PostgreSQL concurrency/chaos；
4. Management API security；
5. PR/Release delivery；
6. 再由真实数据决定是否需要更多 scheduler feature。



## R9 Web Console Consolidated 验证

R9 在 `R9S1WebConsoleIT` 基础上增加：

- `R9WebConsoleOperationsIT`：创建 Run、Pause/Resume、Run/Fleet Scheduling、Worker Drain/Offline；
- `R9WebConsoleSecurityIT`：Session Login、CSRF、VIEWER 角色拒绝写操作；
- `R9WebConsoleApprovalIT`：通过 WebUI 对现有 Approval Authority 执行 Approve。

预期 Failsafe integration-test methods 总数：**33**。最终验收命令仍为 `mvn clean verify`。
