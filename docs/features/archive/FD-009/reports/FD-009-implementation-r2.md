# FD-009 Worker 修复报告，r2

<!-- aiw-data: FD-009-implementation-r2.json -->

来源事件 `FD-009-000011-changes-requested`，Worker Session `fd009-worker-20261007-9c4e61`。依据 [独立审查第 1 轮](../reviews/FD-009-review-r1.md)修复 Work Item 1.6 的角色启动脚本冲突处理。

`run-role.sh` 与 `run-role.ps1` 现均先验证参数；如果现有非空 `AIW_AGENT_RUNTIME` 与参数不一致，就在设置 `AIW_PROCESS_ROLE`、改写 runtime 和启动 Node 前向 stderr 报错并退出 2。相同值或未设置环境变量时沿用既有启动流程。静态路径检查覆盖 `codex-sdk` 与 `openai-agents` 双向冲突；本轮没有可声称通过的行为测试。

已执行一次 `git diff --check`，退出码 0。此前 TypeScript compile-only 通过的结果只适用于当时的 TS 源码；本轮只改启动脚本和 FD 记录，未重跑编译、测试、类型检查、构建或真实后端。Tester 第 1 轮因 Node 运行器 `spawn EPERM` 而 0/25 场景执行，PM 已明确接受该测试报告的覆盖例外；该例外不证明冲突行为。本次交接请 Tester 将新增冲突场景明确列入下轮报告，再由 PM 与 Reviewer 复核。

剩余风险：两个脚本的冲突拒绝尚无可执行验证；两个后端的注册、权限、attempt 终态、租约与 Control Plane 兼容性仍无独立运行时证据。
