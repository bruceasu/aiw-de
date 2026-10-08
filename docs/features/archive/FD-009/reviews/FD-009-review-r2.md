# FD-009 独立审查报告，r2

<!-- aiw-data: FD-009-review-r2.json -->

- **Reviewer 会话：** `fd009-reviewer-r2-20261007-9bd145`
- **来源事件：** `FD-009-000014-test-accepted`
- **审查提交：** `feature/FD-009` 的 `452d0830d1ab55a02059f37d27a47ff36a5b2aa9`；第 1 轮回交点 `6a39c6b`；父分支 `master` 的 `ccf26036fd8ae4ad2292752bd9395ab8196168a0`
- **结论：** Verification 通过，保留明确列出的运行时证据风险。

## 发现与判定

本轮没有新的阻断发现。第 1 轮冲突选择缺陷已修复：`workers/process-typescript/scripts/run-role.ps1:10-13` 和 `workers/process-typescript/scripts/run-role.sh:10-13` 都在修改环境变量、设置 Worker 身份和调用 Node 之前，拒绝非空且与参数不同的 `AIW_AGENT_RUNTIME`。PowerShell 对两个反向冲突均输出准确诊断。静态控制流还显示冲突分支之后的 Node 调用不可达；没有独立进程跟踪，不将“Node 未启动”记为运行时实测。

Tester 工具的两次退出码均为 1，Worker 静态报告按脚本 `exit 2` 预期为 2。FD 的验收条件要求冲突配置在注册前**明确失败**，未指定具体进程退出码。非零退出与准确的冲突诊断支持拒绝行为；脚本源码支持在 Node 调用前退出。因此，此差异不构成 FD 验收缺陷，也不能据此声称已实测退出码 2。实际退出码来源未获独立解释，作为残余证据限制保留。

## 证据核对

- Worker r2 报告只声称脚本修复和静态路径检查，未冒称行为测试通过。第 1 轮 TypeScript compile-only 结果仅适用于当时源码；本轮没有改 TypeScript 源码。
- Tester r2 的两条命令与 Planner r2、r3 的逐条授权完全一致；授权均绑定 `FD-009-000012-implementation-ready`、FD 修订 12、摘要 `c91d54d5fe9bf52fe8c8a0d85fd2da5f1b99a5f4030c2c7d093d91589d40cc82` 和 Tester 会话 `fd009-tester-r2-20261007-95171d`。授权依据为聚焦离线低风险检查，未要求人工批准。
- Tester 把双向冲突分为 S09A、S09B；另 24 个行为场景均未计入覆盖。需求场景覆盖率 2/26（7.69%）、分支覆盖率未测量。PM r2 对低覆盖作了明确例外，同时记录退出码与 Node 进程跟踪的限制。第 1 轮 Node 测试运行器 `spawn EPERM` 不属于产品行为失败。
- 第 1 轮已静态审查 SPI、统一入口、SDK 适配器、共享 Pull/lease/attempt、旧包删除和文档；本轮相对回交点的实现差异只涉及两个角色脚本，未见其余实现路径变化。

## 执行与剩余风险

本轮实际执行只读的 `aiw fd resume`、`git status`、`git log`、`git diff 6a39c6b...HEAD`、`git diff --check 6a39c6b...HEAD`、`git rev-parse`、`Get-Content` 和限定文本检索；并领取 `FD-009-000014-test-accepted`。未运行测试、类型检查、构建、网络、真实 Codex CLI/OpenAI API 或其他后端验证。差异空白检查未报告问题。

两后端实际注册、权限、模型执行、取消、attempt 终态、lease 和 Control Plane 兼容性仍无独立运行时证据。PowerShell 退出码 2、POSIX shell 冲突路径和 Node 未启动也未获独立实测。本次通过表示没有发现新的静态验收缺陷，并遵循 PM 已记录的覆盖例外；不表示这些场景已通过测试。
