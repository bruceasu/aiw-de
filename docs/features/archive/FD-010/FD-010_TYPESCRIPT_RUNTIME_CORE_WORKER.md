# FD-010: 将 TypeScript runtime-core 收归单一 Worker 包

**Status:** Complete
**Revision:** 8
**Priority:** Medium
**Test policy:** Independent
**Evidence policy:** Dual

## Problem

FD-009 已把 Codex SDK 与 OpenAI Agents SDK 后端收敛到 `workers/process-typescript`，但它们共同使用的 `workers/runtime-core` 仍位于包外。当前只有该 TypeScript Worker 在生产代码中导入这份 ESM；Go/Python 仅遵循相同的行为契约，并不导入它。包外位置让源码桥接在仓库路径与发布路径之间动态选择，构建要复制目录，发布又要从包外复制一次。维护者需要同时理解两个位置，迁移或发布时容易漏掉类型、脚本或测试路径。

## Options and decision

1. **保留顶层 `workers/runtime-core`。** 现有路径不变，但继续承担仅一个生产包使用的跨目录装配和双路径加载；拒绝。
2. **移到 `workers/process-typescript/runtime-core/`，作为该包的内部深模块。** 保留 attempt、工作区、资源、证据、工具与协议逻辑的模块接口，删除源码/打包双路径选择，并使发布目录从包内取文件；选择。用户已确认这一方向。
3. **把各文件摊入 `src/index.ts` 或两个后端适配器。** 会分散租约、清理和证据的不变量，使两后端重新耦合；拒绝。

## Solution

- 将现有 `.mjs`、`index.d.mts` 与该模块的契约文档原样迁入 `workers/process-typescript/runtime-core/`，只改相对路径和包归属，不改逻辑与协议。`AgentRuntime` SPI 仍位于 TypeScript Worker 源码中，`runtime-core` 继续处理 Pull/lease、attempt、工作区、证据和资源终态。
- `src/runtime-core.ts` 始终从包内 `../runtime-core/index.mjs` 加载，类型导入同样指向包内。移除复制到包内的 `stage-runtime` 步骤；构建不再依赖仓库根部的旧目录。发布脚本直接携带包内 `runtime-core/`，编译脚本扫描新路径。
- 更新仍在使用的仓库测试、公共契约/运维文档及源码引用。顶层 `workers/runtime-core` 路径删除，不保留转发入口；私有 Worker 包没有声明该仓库路径为外部可安装接口。历史 FD 归档与原始证据保持原文。若仓库外脚本硬编码旧路径，需要使用者迁移；本 FD 不假定有这样的脚本。
- 保持 Worker 启动参数、环境变量、默认身份、发布目录内 `runtime-core/` 相对布局、Control Plane 协议、故障类别、超时、取消及证据语义。迁移是路径与包所有权变更，不改 SDK 版本或依赖。

## Scope

Included:

- 单一 TypeScript Worker 内部化 `runtime-core` 源码、声明和当前契约文档。
- TypeScript 导入、构建/编译/发布脚本、当前文档及仍使用旧路径的测试引用迁移。
- 保持两个 AgentRuntime 后端共用一个 attempt 与 Control Plane 实现。

Excluded:

- Go/Python Worker 的实现、Control Plane、数据库、依赖升级或新的运行时后端。
- 改写 `runtime-core` 行为、扩大导出面、在线模型/服务调用，以及修改历史归档证据。

## Work items

- [x] 1.1 迁移内部模块与 TypeScript 导入。**Size:** Medium. **Difficulty:** Medium. **Dependencies:** none. **Completion:** 将顶层源码、类型声明和模块契约移入单包；桥接只解析包内目录；TypeScript 源码类型/值导入均不再指向包外旧路径；两个后端仍使用同一 attempt 实现。
- [x] 1.2 收敛构建、编译和发布路径。**Size:** Small. **Difficulty:** Medium. **Dependencies:** 1.1. **Completion:** 构建不再执行跨目录 stage；编译检查新目录；发布从包内携带 `runtime-core/`，目标目录仍提供原有相对布局；无旧顶层目录依赖。
- [x] 1.3 更新当前测试引用。**Size:** Small. **Difficulty:** Low. **Dependencies:** 1.1. **Completion:** 根目录现行测试及 fixture 的导入指向新包内模块；旧顶层路径的活跃测试引用清零；不修改测试断言和历史原始输出。
- [x] 1.4 更新当前文档与迁移说明。**Size:** Small. **Difficulty:** Low. **Dependencies:** 1.1, 1.2. **Completion:** 当前 Worker 契约、README、运维和编译说明只指向新源码位置，明确顶层路径已删除、发布布局与不变的运行时行为。

## Acceptance

- 仓库只保留 `workers/process-typescript/runtime-core/` 一份运行时源码；旧顶层路径不存在，两个后端经同一内部模块调用原有 Pull/lease/attempt 生命周期。
- 从源码启动和打包后的 Worker 均以包内模块路径解析，缺失模块时明确失败，不静默回退到仓库根目录；现有 runtime 选择、Worker 身份、注册及 Control Plane 请求形状不变。
- 发布目录仍包含 `runtime-core/`，不需要从包外复制；编译入口和当前测试引用指向新路径。旧路径引用不会在当前代码、脚本与现行文档中遗留，历史归档除外。
- attempt 的成功、失败、超时、取消、资源终态不确定、工作区清理与证据不因目录迁移改变；不新增权限、网络或依赖。

## Verification

- Worker 静态检查：已把 20 个模块文件迁入包内，TypeScript 导入统一指向 `../runtime-core/index.mjs`；移除双路径 fallback 与 stage 脚本，发布清单从包内携带 `runtime-core/`；19 个现行测试导入改指新路径。两个后端仍经同一 `src/runtime-core.ts` 使用公共 attempt/slot 实现。此项是静态证据，不代表源码或发布包已运行。
- 已执行一次无分发产物的项目内 compile-only：在 `workers/process-typescript` 设置 `AIW_TYPESCRIPT_COMPILER=D:\03_projects\AI-tools\aiw-dev\workers\process-typescript\node_modules\typescript` 后运行 `node scripts/compile.mjs`，退出码 0，仅证明 TypeScript 语法编译。`git diff --check master...HEAD` 退出码 0。未运行根目录多语言编译、类型检查、构建或测试。
- 独立 Tester 设计黑盒场景：源码/发布包模块路径解析、缺失模块的明确失败、两个后端共用的协议与 attempt 结果；每条执行命令先提报 Planner 审核并单独授权。若无法获准执行，报告未运行和残余风险。
- Reviewer 核对真实差异、测试/PM 证据与 FD 验收；发布目录的最终构建和在线后端调用没有授权时保留为未验证风险。
- 独立 Reviewer r1 静态核对 `master` 至 `feature/FD-010` 的实际差异、20 个模块迁移、`src`/`dist` 相对导入、发布清单、当前文档/测试路径，以及 Tester 授权、4/19 测试报告和 PM 覆盖例外；未执行新增运行时检查。当前差异的 `git diff --check master...HEAD` 在两份 Tester 报告末尾多余空行处报警，此格式问题不改变模块迁移的验收结论，详见 `docs/features/reviews/FD-010-review-r1.md`。

## TODO

- 预检阻塞已解决，记录见 `docs/features/reports/FD-010-blocker-preflight-20261007-142606.md`；父分支恢复干净后建立了 `feature/FD-010` 工作树。
- Worker 四项 Work Item 已完成并各自提交；等待独立 Tester、PM 和 Reviewer 的证据与决定。

## Sources

- Issue: none
- 用户确认：把 `runtime-core` 移入 `workers/process-typescript/`，保留为内部模块并清理双路径加载与发布复制。
- `docs/features/archive/FD-009/FD-009_TYPESCRIPT_WORKER_AGENTRUNTIME_SPI.md`：单一 TypeScript Worker 的前置决策。
- `workers/runtime-core/index.mjs`、`index.d.mts`、`PUBLIC_CONTRACT.md`、`TRANSPORT_AND_PLUGIN_CONTRACT.md`。
- `workers/process-typescript/src/runtime-core.ts`、`package.json`、`scripts/stage-runtime.mjs`、`scripts/package-release.mjs`。
- `scripts/compile-worker-runtime.mjs` 与 `tests/fd005/`、`tests/fd006/` 的直接导入。
- Planner session `fd010-planner-20261007-a7c214`；source event `FD-010-000002-design-requested`。

**Completed:** 2026-10-07
