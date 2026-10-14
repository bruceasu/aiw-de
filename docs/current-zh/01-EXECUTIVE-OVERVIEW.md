# 执行摘要

## 1. AI Worker 是什么

AI Worker 是一个面向 AI 软件工程 Worker 的 **Control Plane**。它不是另一个聊天式 Coding UI，而是用来管理一组 AI 员工完成完整工程生命周期。

系统可以从自然语言需求出发，形成 Requirement Analysis、Architecture、Planning、Coding、Review、Verification、Repair、Engineering Gate 和 Human Approval，并把每个步骤变成可恢复、可审计、可并行的工程状态。

## 2. 它解决什么问题

单独使用 Codex/其他 Coding Agent 很适合完成一个任务，但真实软件开发还需要：

- Worker 崩溃之后能够恢复；
- 多个 Worker 随时加入/退出；
- 并行开发时彼此不覆盖工作目录；
- 明确的依赖与结果交接；
- Review/Verification 与 Coding 职责分离；
- 需要时由人类最终批准；
- Pause/Resume/Cancel/Recover；
- 能回答“为什么这个任务还没执行”；
- 用精确 Git revision 证明到底批准了哪份代码。

AI Worker 提供的是这一层软件工程控制能力。

## 3. 当前完整流程

```text
自然语言 Requirement
        ↓
REQUIREMENT_ANALYSIS
        ↓
SOLUTION_ARCHITECTURE
        ↓
IMPLEMENTATION_PLANNING
        ↓
一个或多个 IMPLEMENTATION
        ↓
并行时进入 INTEGRATION
        ↓
CODE_REVIEW + VERIFICATION
        ↓
必要时 REPAIR → 再次 Review/Verification
        ↓
Engineering Gate
        ↓
Approval Policy / Human Approval
```

Reviewer/Verifier 发现问题时不会直接改代码，而是产生 `REPAIR_REQUIRED`，由 Control Plane 创建新的 Repair Task。

## 4. 核心设计原则

1. **Worker Pull，而不是中央 Push。** Worker 有空时自己申请兼容任务。
2. **Java 保留 Authority。** 生命周期、状态转换、调度、Gate、Approval 都由 Control Plane 决定。
3. **Worker 可替换。** 当前提供 Python、TypeScript、JavaScript、Go、Java 五种参考实现。
4. **Attempt 保留历史。** Retry 新建 Attempt，不覆盖失败历史。
5. **Git revision 是代码结果身份。**
6. **Human Approval 独立于 AI 执行结果。**
7. **Local-first。** 默认 SQLite，本地就能运行；共享模式使用 PostgreSQL。
8. **小型数据库。** 当前仅 9 张有明确生命周期/并发/审计理由的表。

## 5. 当前成熟度

R8-S13 Fix2（`me.asu.aidev`）已经通过当前 Maven 验证基线，包含 **29 个 Failsafe Integration Test methods**。

但它应被理解为一个“工程执行平台基线”，还不是完整 Production Delivery 平台。当前尚未完成的重点包括：

- Management API 的生产级认证/RBAC；
- PR/Merge/Release/Deploy；
- PostgreSQL 多 Control Plane 并发/Chaos 压测；
- 长时间真实 Codex End-to-End 验证；
- 更完整的 Tool/Skill 权限治理。

## 6. 最重要的价值

系统允许未来增加更多 AI 员工，而不需要重新设计中央流水线。Worker 只要实现 Worker Protocol v1 并声明正确 capability，就可以独立使用 Java、Go、Python、JS/TS 甚至未来新的 AI Runtime。

因此 Control Plane 可以保持稳定，而 AI 模型、SDK、CLI、工具和 Worker 语言可以独立演进。
