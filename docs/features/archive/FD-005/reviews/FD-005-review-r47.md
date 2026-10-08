# FD-005 独立审查 r47

<!-- aiw-data: FD-005-review-r47.json -->

**结论：changes-requested。** Reviewer session：`fd005-reviewer-20261006-r47-ee5fe4ab`；来源事件：`FD-005-000047-review-requested`；审查对象为 FD 修订 47，SHA-256 `3136aca67cc043f3e09d0aeec01d2813a35c31188caf02c4557e889b5a88496b`。审查基线为当前提交 `5b1a48baa9c3dd7ca575bab25c11d452f3cce912`；FD 相关生产源码、r39 测试文件和 Worker r42 报告相对该基线无工作区差异。

## 审查范围

- FD：`docs/features/FD-005_WORKER_PI.md` 修订 47；PM 决策：`docs/features/reports/FD-005-pm-override-r46.md` 及 JSON。
- Worker：实施报告 r42 及 JSON，Worker session `fd005-worker-20261005-69fe2eb5`；报告说明未改生产源码。
- Tester：报告 r39 及 JSON、授权 r39 及 JSON、`tests/fd005/evidence-r39/execution.json` 和 `diagnostic.json`。
- 历史审查：Reviewer r44 报告及 JSON。r44 是历史 finding，本报告按 r47 的 PM 决策重新判断。
- 生产入口静态线索：`workers/process-typescript-with-api/src/index.ts`、`profiles.ts`、`api-adapter.ts`。静态守卫顺序不能替代所需行为证据。

## Findings

### F-1 — Acceptance 1 保留角色拒绝行为仍无证据（阻止 verification 通过）

Acceptance 1 仍要求保留但不可执行的角色拒绝注册。PM r46 明确跳过 API-16、不再追加测试、接受覆盖率例外进入审查，同时明确保留 Acceptance 1，并保留 r39 的失败事实。该 PM 决定是有效的流程权限决定；本审查尊重其“不再运行 API-16”及 69.70% 覆盖率例外，不要求重跑测试。

但 r39 报告、获批授权和原始证据共同表明，获批命令退出码为 1，测试断言已到达；`release-readiness` 与 `orchestrator` 两个子进程均以 `module-or-syntax-load-error` 退出，错误码与模块 basename 为空。授权 JSON 中五个调用文件的 SHA-256 均与当前文件匹配。该结果没有证明任一保留角色在生产入口注册前被拒绝。源码可见 `assertExecutableRole` 守卫，但不能据此把 API-16 记为通过。

**要求：** 将此行为继续记为未验证。若 PM 选择在无该行为证据时直接接受或推进，应通过其最终流程权限明确记录该 override；不能将本 Reviewer 结论或行为要求记为已验证。当前 r46 只批准进入审查，未豁免 Acceptance 1，因此本 Reviewer 不能发出 `verification-passed`。

### F-2 — Verification 仍含与当前决定冲突的旧计划和过期计数

FD 修订 47 的 Verification 已记录跳过 API-16、覆盖率例外和残余风险；但同一节仍保留旧的“独立 Tester 重新盘点”计划，其中要求重新检查 API Worker 入口场景。它没有标明该计划在本轮被 PM 的“不追加测试、跳过 API-16”决定取代。同节还称 Reviewer outcome 仍为 0，而历史 r44/r45 已记录 `changes-requested`。这使当前测试授权边界及审查历史含有互相矛盾的陈述。

**要求：** 明确标记旧测试计划为历史/已被本轮决定取代，并校正 Reviewer outcome 计数；不得为消除矛盾而改写 r39 结果或 r44/r45 历史事件。

## PM 决定与验收状态

本审查接受 PM 的覆盖率流程例外：需求场景覆盖率为 69.70%（23/33），分支覆盖率未测量。例外仅支持继续审查，不代表这些比例达标，也不把失败或未运行场景算作通过。PM 对工作流的最终权限不等于 API-16 行为证据；本次报告如实保留缺证事实。

## 命令、未运行检查与残余风险

实际命令包括：`aiw fd resume FD-005`；`aiw fd claim FD-005 FD-005-000047-review-requested --session fd005-reviewer-20261006-r47-ee5fe4ab`；`aiw fd emit --help`；对 FD、r42/r46/r39/r44 报告、授权、事件回执及 r39 原始 JSON 的定向 `Get-Content` / `Select-String`；`rg --files`、`rg -n`；按授权记录逐一核验五个调用文件的 `Get-FileHash -Algorithm SHA256`；`Get-FileHash` 核验 FD 摘要；`git rev-parse HEAD`、`git branch --show-current`、对生产源码、公共核心、r39 测试及 r42 报告执行 `git diff --name-status HEAD -- ...`。

初始预检还尝试读取 `.ai/fd/FD-005/workspace.json`，该文件不存在；该记录不属于此 FD 的证据来源。未运行测试、覆盖率、编译、构建、格式化、网络或其他运行时检查；没有改动生产代码。r42 Tester handoff 仍为已领取但结果未知，本报告不推断其执行情况。剩余风险包括 Acceptance 1/API-16 行为未验证、9/33 场景未完整覆盖、分支覆盖未测量，以及真实服务、远端 Git、平台 G2 和非 API Worker 行为未获本轮运行证明。
