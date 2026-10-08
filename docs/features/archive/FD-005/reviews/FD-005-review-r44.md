# FD-005 独立审查 r44

<!-- aiw-data: FD-005-review-r44.json -->

**结论：changes-requested。** Reviewer Session：`fd005-reviewer-20261006-7c31f9a2`；来源事件：`FD-005-000044-review-requested`；FD revision 44，digest `0e1ec05b3f6a5b47a4bed029f829ce7d6036efa20eaa524af34b20a22b84da58`。

## 审查范围

- Worker 报告：`docs/features/reports/FD-005-implementation-r42.md` 与 JSON；Worker Session `fd005-worker-20261005-69fe2eb5`，来源事件 `FD-005-000041-test-rejected`。报告说明 r42 未修改生产源码。
- 测试与 PM 证据：Tester r39 报告及 JSON、Planner 授权 r39 及 JSON、`tests/fd005/evidence-r39/diagnostic.json`、`execution.json`、PM 决策 r41 及 JSON。
- 代码：`workers/process-typescript-with-api/src/index.ts`、`profiles.ts`、`api-adapter.ts`；测试夹具 `tests/fd005/r39/`。
- 版本范围：仓库当前唯一提交为 `184a0c2c48c5b745ec4bd9546cb35e268f381495`（`initial commit`，无可比较的父提交）。r42 报告称生产源码未变；当前相对 HEAD 的定向差异仅有 FD 文档状态/修订更新，没有 Worker 源码或 r39 测试代码差异。未把仓库中既有 `.agents/skills` 删除纳入 FD 审查。

## 发现

### F-1 — Acceptance 1 的保留角色拒绝行为仍未验证（阻止通过）

FD Acceptance 1 和 Work Item 1.1 要求 Release Readiness 与 Orchestrator 这两个保留但不可执行的角色拒绝注册。批准的需求计划也明确指出二者缺少匹配的可执行 TaskType，应保留不可执行状态。PM r41 保留了该要求；其覆盖率例外只允许以 69.70% 覆盖率进入审查，没有豁免 API-16 行为。

r39 授权命令及其五个调用文件摘要均与授权 JSON 相符。原始执行摘要记录命令 exit 1、测试断言已到达；两个角色子进程都以 `module-or-syntax-load-error` 退出，错误码和安全模块名均为空。测试的 `reserved-role` 断言未通过，因此没有证明生产入口到达角色拒绝逻辑，也没有证明注册前拒绝。

静态源码显示 `index.ts` 的 `loadConfig()` 在 `client.register()` 前调用 `assertExecutableRole()`，而 `profiles.ts` 对两个保留名称抛错；`createApiAttemptAdapter()` 也有同一守卫。这是有利的静态证据，但 r39 的实际失败仍未解释，且 r42 未提交生产修复或新行为证据。由于用户明确保留此验收要求，不能把静态调用顺序或加载阶段退出视为通过。

**要求 Worker 修复或澄清生产入口/测试加载链路后，交由独立 Tester 通过新授权证据证明这两个角色在生产入口注册前被拒绝。** 保留 r39 原失败事实；不得改写为通过，也不得通过放宽 Acceptance 1 消除此要求。

## 测试决策与其他验收状态

PM r41 接受 23/33（69.70%）需求场景覆盖及未测量分支覆盖率作为进入审查的例外。此审查尊重该例外。r39 报告仍列出 9 个未完整覆盖场景；其余 Acceptance 中涉及并行/资源冲突、取消与 drain、远端对账、预算与摘要、插件失败、跨运行时兼容、升级回滚及 G2 平台效果的部分，未由本轮 API-16 证据覆盖。按 FD 的测试范围和现有来源，它们仍是未验证风险，不能从 23 个已覆盖场景外推通过。

事件 `FD-005-000042-implementation-ready` 由 Tester Session `fd005-tester-20261005-r42-a9c4e2` 领取；当前可见回执没有对应测试结果或报告。PM r41 与当前 PM handoff 均保留结果未知。本审查不推断该 Session 是否运行了命令，也不将其计作通过证据。

## 命令、未运行检查与残余风险

实际命令与检查：`aiw fd resume FD-005`；精确领取 `aiw fd claim FD-005 FD-005-000044-review-requested --session fd005-reviewer-20261006-7c31f9a2`；`git branch --show-current`、`git rev-parse HEAD`、`git log --oneline --decorate -12`、针对 FD 源码/测试/报告路径的 `git status --short` 与 `git diff --name-status HEAD -- ...`；对 FD、报告、授权、回执、原始证据、API Worker 源码、r39 测试夹具及 ISSUE-001/整体计划的 `Get-Content` 和 `rg` 静态读取；依据 r39 授权 JSON 对五个测试调用文件执行 SHA-256 核对，五项均匹配。

未运行测试、覆盖率、编译、构建、格式器、网络或运行时命令。本审查未修改实现。残余风险包括：API-16 行为未通过证据；9/33 场景未完整覆盖且分支未测量；r42 Tester 结果未知；真实服务、远端 Git、平台 G2 与非 API Worker 行为没有本轮运行证明。
