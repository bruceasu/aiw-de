# Workers

This directory contains the R9 process workers, small Codex examples, and a separate Java deployment demo. The Java Control Plane owns task state, scheduling, leases, approvals, retries, and persistence. A worker claims bounded tasks, executes them in an isolated workspace, and reports results through Worker Protocol v1.

## Process workers

平台部署的任务 Skill 已统一由 Control Plane 管理。三语言的 SDK/API 与 App Server 使用固定文件集和按需只读资源工具；Codex CLI 不支持新平台文件集任务。独立部署仍可配置 `AIW_PROCESS_SKILLS_ROOT`。部署切换、版本及能力边界见 [平台 Worker Skill 文件集](../docs/WORKER-SKILL-FILES.md)。下述包内 Skill 仅保留为独立部署兼容资源。

The bundled role Skills include adapted AIW task methods. See
[Worker Skill methods](SKILL_METHODS.md) for the source-to-role map, loading
behavior, version, boundaries, and unrun evaluation scenarios.

These are the maintained multi-role workers:

| Package | Language | Runtime choices | Details |
| --- | --- | --- | --- |
| [`process-typescript`](process-typescript/README.md) | TypeScript | `codex-cli`, `codex-app-server`, `openai-agents` | One entry point selects an `AgentRuntime` backend per process. |
| [`process-go`](process-go/README.md) | Go | `codex-cli`, `codex-app-server`, `openai-api` | An `AgentRuntime` SPI keeps model execution behind the shared Worker lifecycle. |
| [`process-python`](process-python/README.md) | Python | `codex-app-server` (default), `openai-agents` | App Server stdio or Agents API through the Python `AgentRuntime` protocol. `codex-sdk` was removed. |

Each process selects one runtime at startup. API runtimes require `OPENAI_API_KEY`; Codex runtimes require their corresponding installed/authenticated Codex setup. Runtime-specific profile and environment requirements are documented in each package README.

## 角色 × 语言 × 后端支持矩阵

以下是**源码支持范围**，不代表已通过真实模型、端点、平台或生产禁存认证。
`CLI` = `codex-cli`；`App Server` = `codex-app-server`；`Agents` = `openai-agents`；`API` = `openai-api`。

| 语义角色 | TypeScript 后端 | Python 后端 | Go 后端 |
| --- | --- | --- | --- |
| `goal-discussion` | CLI / App Server / Agents | App Server / Agents | CLI / App Server / API |
| `requirements` | CLI / App Server / Agents | App Server / Agents | CLI / App Server / API |
| `architect` | CLI / App Server / Agents | App Server / Agents | CLI / App Server / API |
| `planner` | CLI / App Server / Agents | App Server / Agents | CLI / App Server / API |
| `coder` | CLI / App Server / Agents | App Server / Agents | CLI / App Server / API |
| `integrator` | CLI / App Server / Agents | App Server / Agents | CLI / App Server / API |
| `repairer` | CLI / App Server / Agents | App Server / Agents | CLI / App Server / API |
| `reviewer` | CLI / App Server / Agents | App Server / Agents | CLI / App Server / API |
| `stage-reviewer` | CLI / App Server / Agents | App Server / Agents | CLI / App Server / API |
| `verifier` | CLI / App Server / Agents | App Server / Agents | CLI / App Server / API |
| `integration-verifier` | CLI / App Server / Agents | App Server / Agents | CLI / App Server / API |
| `recovery-decider` | CLI / App Server / Agents | 未实现 | 未实现 |
| `release-readiness` / `orchestrator` | advisory，不可执行 | advisory，不可执行 | advisory，不可执行 |

Go 通过 TOML profile 文件启动，同一语义角色的文件名可不同，见各包 README。
Python 和 TypeScript 的 Codex App Server 路径在原生 Windows 上拒绝启动，使用 Unix/WSL；Python/TS 的 Agents 路径不受该 Codex 进程树限制。Go 沿用本包的平台约束；此表不承诺跨平台运行验证已完成。

### 共同目标讨论契约

三语言的 `goal-discussion` 均注册 capability/skillRef `goal-discussion`，使用 `EXTERNAL_WORK`、只读 `REPORT`。
目标数据、历史、资料和版本由 Java 控制平面保存；每次任务使用冻结输入，在独立空目录执行，拒绝工作区 assignment。API 讨论不提供文件、shell 或 AIW 工具；Codex 保持只读并禁网络，原生沙箱仍是宿主安全边界。

规范角色资产位于 [`shared/goal-discussion`](shared/goal-discussion)，独立部署包内保留逐字节副本。
只修改规范源，再用 `python scripts/sync-goal-discussion-assets.py`（仓库根目录）同步三包。
`--check` 可做只读一致性检查，执行仍受仓库验证授权约束。运行时不依赖仓库共享目录，各包可独立部署。
修改角色或后端支持时同步更新本矩阵、公共契约和各包说明；包内副本不是新的规范源。

Java 的 backend 是类别：`openai` 允许 Agents/API；`codex` 允许 CLI/App Server。
它依据任务冻结的已批准 profiles 筛选，不按实现语言排除 Worker，也不跨类别自动回退。

### Python Codex SDK 迁移

Python 已删除 `openai-codex` 依赖和 `codex-sdk` 运行路径，改用已安装 CLI 的 App Server。
旧配置明确报错，不静默迁移；设置 `AIW_AGENT_RUNTIME=codex-app-server`（新默认）或 `openai-agents`。
App Server 使用 `AIW_CODEX_APPROVAL_MODE=deny-all`（或 `never`），原 SDK `auto-review` 和 `full-access` 设置需移除/显式改为受支持配置。
App Server 不接受 `fast=true`；更新对应的控制平面 AI Profiles，勿把旧 `codex-sdk` profile 冒充为已支持。
旧冻结请求、用户运行配置及批准记录不会被此源码变更自动改写；需由运营者显式处理旧队列和进程。
详见 [Python 迁移说明](process-python/README.md#codex-app-server-migration)。

## Codex demos

The `demo-*-with-codex` directories are small language-specific Worker Protocol examples. They are separate from the maintained role-based process workers and are useful as protocol references.

## Java deployment demo

[`demo-deployer`](../demo-workers/demo-deployer/README.md) demonstrates a separate deployment workflow: validate a request, package a Java artifact, and copy the verified artifact into an allowed directory. It does not restart an application, switch a live-release pointer, or roll back a release. Deployment permissions and approval must be designed independently for production use.

## Shared contracts and operations

- [Worker runtime contract](WORKER_RUNTIME_CONTRACT.md) describes role, task, evidence, and runtime behavior.
- [Worker operations](WORKER_OPERATIONS.md) documents deployment configuration and current verification limits.
- [SDK capabilities](SDK_CAPABILITIES.md) records the inspected SDK behavior and known unknowns.

## Personal secretary (FD-024)

The personal secretary is an independent user-owned process and database. See [personal-secretary/README.md](../personal-secretary/README.md). The old in-platform secretary and obsolete secretary/coordinator/delegate specialist roles are retired; no compatibility mode is provided. Migration tools only produce offline plans; actual migration and cutover are disabled.
