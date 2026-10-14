# 给 Boss 的 3～5 分钟介绍稿

## 30 秒：这是什么

> 我们现在做的是一个 AI Development Manager。它不是再做一个 Coding Chat 工具，而是给 Codex 这类 AI Developer 提供一个软件工程 Control Plane。系统可以管理多个 AI 员工，从需求分析、架构、计划、编码、Review、Verification、Repair 一直到 Human Approval。

## 45 秒：为什么需要它

> 现在 AI Coding 本身已经很强，但真实开发不只是“生成代码”。我们还需要任务状态、依赖关系、Worker 崩溃后的恢复、多个 AI 并行工作但不互相覆盖、独立 Review 和测试验证、必要时的人类批准，以及能够证明最终到底接受了哪个 Git Revision。AI Worker 主要解决的是这些软件工程管理问题。

## 60 秒：系统如何工作

> 用户提交自然语言需求后，Control Plane 会生成工程任务，而不是固定调用某个 Agent 流水线。AI Worker 注册自己的 Capability，有空时自己向 Control Plane 申请任务。这意味着以后可以随时加入更多 AI 员工，而且 Worker 可以用 Java、Go、Python、JavaScript 或 TypeScript 实现。
>
> Coding Worker 使用独立 Git Worktree。如果实现计划适合并行，多个 Worker 可以同时开发，最后由 Integration Task 合并结果，再统一做 Code Review 和 Verification。如果发现问题，Reviewer 不直接改代码，而是要求一个新的 Repair Task。

## 45 秒：控制与安全

> Java Control Plane 始终保留最终 Authority。AI 说“完成了”并不代表系统就接受。Task 有 Lease 和 Attempt History；代码结果使用不可变 Git Commit 标识；Review 和 Verification 最终形成 Engineering Gate；需要时还必须经过独立 Human Approval。我们现在也支持 Telegram Approve/Reject，但 Approval Authority 仍然记录在 Control Plane 中。

## 45 秒：运维和扩展

> 系统是 Local-first 的，本地 SQLite 就能运行，共享环境可以使用 PostgreSQL。Worker 采用 Pull 模式，所以增加 AI 人力不需要改中央 Pipeline。现在已经具备 Priority、Fairness、Worker/Run/Fleet 并发限制、运行时动态调整、Pause/Resume/Cancel/Recover、Run Dashboard、Fleet Dashboard 和 Audit Timeline。

## 30 秒：当前状态与下一步

> 当前 Execution Platform 基线已经通过 Maven 验证，R9 在其上增加了完整 Web Console；现在普通用户不需要直接调用 REST API，就可以创建需求、监控执行、处理失败、调整并发和完成审批。现在基础执行平台已经比较完整，所以我们暂时不继续增加 Scheduler 功能。下一步更有价值的是用真实 Codex 对一个小型项目做完整 End-to-End，验证 AI 开发质量、Repair 是否能收敛、Integration 是否稳定，然后再做 PostgreSQL 并发、安全加固，以及最终的 PR/Release 接入。

## 一句话总结

> 如果 Codex 是一个 AI Developer，那么 AI Worker 就是管理一组 AI Developer 的软件工程组织和 Control System。

## 可选 1 分钟 Demo

1. 打开 Web Console，点击 New Run 提交一个 Requirement；
2. 展示 Dashboard 和 Run Detail；
3. 如果条件允许，展示两个 Worker 并行领取 Implementation；
4. 展示 Review + Verification；
5. 展示 Engineering Gate 和 Approval；
6. 最后展示 `SUCCEEDED` 和精确 `resultRevision`。
