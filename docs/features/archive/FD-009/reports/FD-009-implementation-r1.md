# FD-009 Worker 实施报告 r1

<!-- aiw-data: FD-009-implementation-r1.json -->

- **Worker session：** `fd009-worker-20261007-9c4e61`
- **来源事件：** `FD-009-000006-work-requested`
- **FD：** `docs/features/FD-009_TYPESCRIPT_WORKER_AGENTRUNTIME_SPI.md`，实施修订版 7
- **分支/worktree：** `feature/FD-009` / `.wt/FD-009`

## 完成内容

1. 新增后端无关的 `AgentRuntime` 输入、结果、事件与能力契约；当前仅声明 run、attempt 信号取消与事件能力，不声称可跨进程 resume 或按 run ID 取消。
2. `process-typescript` 的唯一入口要求显式 `AIW_AGENT_RUNTIME=codex-sdk|openai-agents`，在注册前选择适配器并校验后端条件。注册标签沿用 `codex-sdk:<role>`、`openai-agents:<role>`；默认 Worker ID 分别沿用 `process-<role>-1` 和 `process-api-<role>-1`。
3. Codex 与 Agents 的 SDK 执行分别封装在适配器中。原有 Codex session launcher、沙箱/审批、usage、资源不确定处理及 Agents 模型、工具、只读、shell、预算约束保留；共用 profile、指令和已批准模型选择逻辑。
4. 旧 `workers/process-typescript-with-api` 包、启动脚本和源码已删除；依赖与锁文件合入唯一包，编译/发布脚本、角色脚本、Quick Start 与运维说明已指向单入口。

对应 Work Item 1.1–1.7 均已在 FD 中勾选；每项在 worktree 中有独立提交。未改 Control Plane、数据库或其他语言 Worker。

## 静态证据与命令

- 检查了 `AttemptAdapter`、`runWorkerSlot`、两个原入口、role/profile、runtime 标识及包/脚本引用；旧包删除前核对了 worktree 内的绝对路径。
- 每项提交前执行限定差异的 `git diff --check` 并查看路径/统计；提交为 `c2d27d5`、`2ed625a`、`0e963f5`、`a82f1dc`、`c2498da`、`880c57e`、`5442f54`，另有静态修正 `c4ea16e`。
- 唯一 compile-only 命令：`$env:AIW_TYPESCRIPT_COMPILER='D:\03_projects\AI-tools\aiw-dev\workers\process-typescript\node_modules\typescript'; node scripts/compile.mjs`，在统一包目录执行，结果为 `TypeScript syntax compilation passed (no artifacts; no dependency/type validation).`
- 未运行测试、类型检查、最终构建、安装依赖、真实 CLI 或 OpenAI API 调用。

## 剩余风险与 Tester 关注点

- compile-only 只证明 TypeScript 语法可转译；静态迁移尚未证明类型兼容、锁文件安装、两后端实际执行及资源清理。独立 Tester 需如实区分准备的用例与实际执行证据。
- 历史 `tests/fd005` 用于旧 FD 的证据文件仍含已删除的旧包路径，不应作为 FD-009 通过证据。新黑盒验收需针对统一入口。
- 真实 Codex CLI、OpenAI API、网络、发布包和部署均未验证；需要单独授权及环境。
