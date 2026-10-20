# 自主工程生命周期

## 1. Requirement Intake

入口：

```text
POST /api/r1/requirements
```

第一项持久化任务是 `REQUIREMENT_ANALYSIS`。

当前 Task Type：

```text
REQUIREMENT_ANALYSIS
SOLUTION_ARCHITECTURE
IMPLEMENTATION_PLANNING
IMPLEMENTATION
INTEGRATION
CODE_REVIEW
VERIFICATION
INTEGRATION_VERIFICATION
REPAIR
```

Task State：

```text
BLOCKED READY CLAIMED RUNNING COMPLETED FAILED CANCELLED
```

## 2. 前置分析阶段

- Requirement Analysis：输出正式需求、假设、Acceptance Criteria。
- Solution Architecture：基于正式 Requirement 输出实现架构。
- Implementation Planning：输出实现计划，可选择嵌入机器可读 WorkItem Manifest。

这些阶段 Worker 的完整 final response 会作为 `PASS completionSummary` 持久化并传给下游。

## 3. 并行 WorkItem Manifest

```text
AIW_IMPLEMENTATION_WORK_ITEMS_V1

AIW_WORK_ITEM_BEGIN endpoint
AIW_CAPABILITIES java,maven
Implement the health endpoint.
AIW_WORK_ITEM_END

AIW_WORK_ITEM_BEGIN tests
AIW_CAPABILITIES java,maven
Add focused tests.
AIW_WORK_ITEM_END

AIW_IMPLEMENTATION_WORK_ITEMS_END
```

最多 16 个 WorkItem。Key 必须匹配：

```text
[a-z0-9][a-z0-9-]{0,31}
```

只有真正可以基于同一个 immutable base 独立实现的任务才应该并行拆分。

## 4. Git Worktree 隔离

Repository Task 会获得：

```text
repositoryRef
baseRevision
workspaceKey
branchName
```

Worker 创建 attempt-specific worktree，在其中运行 AI、提交修改、生成 `resultRevision`，并计算完整 `baseRevision..resultRevision` 的 changedFiles。

Retry 使用新的 Attempt 和新的 Workspace，不复用崩溃 Attempt。

## 5. Parallel Integration

多个 Implementation branch 完成后，系统只创建一个 `INTEGRATION` Task。Integrator 从 `inputs[]` 获得所有 result revision / publication ref / changed files，形成一个统一集成结果，再进入最终 Review/Verification。

## 6. Review / Verification / Repair

Code Review 和 Verification 是独立只读任务。Final response 必须带：

```text
AIW_VERDICT: PASS
```

或：

```text
AIW_VERDICT: REPAIR_REQUIRED
```

缺少合法 Verdict 时 Worker 应报告执行失败，不能自行猜测。

任意一方要求 Repair 时，Control Plane 创建新的 `REPAIR` Task；Repair 完成后重新 Review/Verify。

## 7. Engineering Gate

Gate 绑定“这一次精确代码结果”的：

- source task；
- result revision；
- changed files；
- publication ref；
- review task/report；
- verification task/report；
- closed time。

旧 Gate 不会因为后续 Repair 通过而被改写。

## 8. Human Approval

Approval 状态：

```text
PENDING -> APPROVED
PENDING -> REJECTED
```

Decision 完成后不可反转。相同 Decision replay 是幂等操作。

Telegram 只是交互 Adapter，真正 Approval Authority 仍然在 Control Plane。

## 9. Autonomous Run

```text
RUNNING
  ↓ explicit worker failure
FAILED --recover--> RUNNING
  ↓ Gate PASSED
ENGINEERING_PASSED
  ↓ Approval Request
WAITING_APPROVAL
  ├─ approve -> SUCCEEDED
  └─ reject  -> REJECTED
```

Operator Control 独立：

```text
ACTIVE <-> PAUSED
  └------> CANCELLED
```

### Crash 与 Fail 的区别

Worker 消失/Lease timeout 属于基础设施恢复，系统自动 requeue；Worker 主动 `/fail` 才是明确工程失败，Run 会进入 `FAILED`，需要 operator recover。

## 10. Pause / Resume / Cancel / Recover

- Pause：撤销活动 Lease、Attempt 标记 `EXPIRED/OPERATOR_PAUSED`、Task 回 READY、释放容量。
- Resume：恢复 Claim，下一次产生新 Attempt。
- Cancel：不可逆，未完成 Task 进入 CANCELLED，活动 Attempt 使用 `OPERATOR_CANCELLED` 终止。
- Recover：仅针对 FAILED Run，把当前 FAILED tasks 重新放回 READY。

## 11. Pull Scheduling

排序：

1. effective priority；
2. 当前 subject/run active leases 更少；
3. READY 等待时间更长；
4. createdAt 更早；
5. taskId。

Aging 每等待 10 分钟 +1，最大 +100。基础 Priority：25/50/75/100。

## 12. Admission

- Run `maxParallelism`：0 表示 unlimited。
- Fleet `maxParallelism`：0 表示 unlimited。

运行时可以收缩/扩张。收缩不会杀死健康 Lease，只阻止新的 Claim，等待现有任务自然下降。
