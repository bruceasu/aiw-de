# R10 R2 Process Workers (Python)

环境变量的设置方式、默认值和运行时差异见 [Worker 环境变量](../ENVIRONMENT.md)。

## FD-016 profile transition

Bundled profiles accept only `EXTERNAL_WORK` and require nine fields including `resultKind`: `REPORT`, `VERDICT`, or `WORKSPACE`. Old task types and eight-field profiles are rejected. The launch role fixes the capability, exact `skillRef` and result kind; verifier never switches to integration-verifier. Release-readiness and orchestrator remain non-executable advisory profiles. See the [shared contract](../WORKER_RUNTIME_CONTRACT.md). This is local delivery; runtime checks, model calls and production no-store certification are separate evidence.

This worker runtime ports the **software-process execution side** of the frozen R2 implementation onto the R9 decentralized Worker Pull protocol.

It deliberately does **not** port R2 lifecycle authority into workers. The R9 Java Control Plane remains authoritative for task state, leases, retries, run control, approvals, scheduling, engineering gates and persistence.

## Runtime model

Model execution is isolated behind the `AgentRuntime` protocol in
`agent_runtime.py`. The worker owns task, lease, workspace, and report handling;
the runtime owns SDK session creation, execution, and final output validation.
`CodexAppServerRuntime` uses an installed Codex CLI over bounded stdio JSON-RPC.
The Python Codex SDK runtime and `openai-codex` dependency have been removed.

The bundled Skill source version is recorded in `skills/VERSION` (currently `R2+aiw-methods-1`).
Directory names stay stable when the Skill source version changes. If you set
`AIW_PROCESS_SKILLS_ROOT` explicitly, point it at the current `skills/` directory.

One Python runtime is started as multiple independent worker processes by selecting `AIW_PROCESS_ROLE`. `AIW_AGENT_RUNTIME` selects `codex-app-server` (default) or `openai-agents`. Role names resolve to Python's TOML profiles in `profiles/`. Go uses the same TOML field format but keeps its own files and role instructions. Python 3.11 or newer is required for `tomllib`.

The OpenAI Agents runtime uses the installed `openai-agents` and `openai` packages without version pins. It checks required APIs and records actual versions; compatibility changes belong in the adapter. It requires `OPENAI_API_KEY` and does not require Codex CLI authentication. Set `AIW_AGENT_RUNTIME=openai-agents` before starting the role process. Use `AIW_OPENAI_MODEL`, `AIW_OPENAI_REASONING_EFFORT`, and `OPENAI_BASE_URL` to configure the API runtime. Installing or updating dependencies is an explicit deployment action.

Guarded execution is opt-in with `AIW_EXECUTION_GUARD=true` and a configured machine token. Both backends use platform-authorized broker tools; the model never receives the platform credential or direct project filesystem access. The optional command broker is separate from the model backend and requires explicit command permission plus Linux/WSL cgroup and toolchain configuration. See [execution guard deployment](../../docs/EXECUTION-GUARD.md). Legacy tasks keep their existing protocol; they do not acquire the new capability automatically.

| Role | R9 task type(s) | R2 primary Skill | Workspace |
|---|---|---|---|
| `goal-discussion` | `EXTERNAL_WORK` | `goal-discussion` | independent empty directory, read-only |
| `requirements` | `EXTERNAL_WORK` | `software-requirements-analyst` | read-only |
| `architect` | `EXTERNAL_WORK` | `solution-architect` | read-only |
| `planner` | `EXTERNAL_WORK` | `implementation-planner` | read-only |
| `coder` | `EXTERNAL_WORK` | `coding-work-package-executor` | writable |
| `integrator` | `EXTERNAL_WORK` | `coding-work-package-executor` | writable |
| `reviewer` | `EXTERNAL_WORK` | `software-code-reviewer` | read-only |
| `stage-reviewer` | `EXTERNAL_WORK` | `workflow-stage-reviewer` | read-only |
| `verifier` | `EXTERNAL_WORK` | `software-test-verifier` | read-only |
| `integration-verifier` | `EXTERNAL_WORK` | `software-test-verifier` | read-only |
| `repairer` | `EXTERNAL_WORK` | `coding-work-package-executor` | writable |

Two additional frozen R2 profiles are preserved in `profiles/advisory/` and their Skills in `skills/advisory/`; they are not executable in R9:

- `release-readiness` -> `release-readiness-manager`
- `orchestrator` -> `software-development-orchestrator`

The `verifier` launch role registers only verification. Integration verification requires an independently launched `integration-verifier`.

The R2-derived `skills/` tree is retained under `skills/`, with adapted task methods
in the role entries and `references/task-methods.md`. These are procedure changes,
not new role registration or output schemas. See [method provenance](../SKILL_METHODS.md).
At execution time the worker composes:

1. R2 authority-boundary instructions;
2. `engineering-artifact-protocol`;
3. the role-specific R2 primary Skill and its references;
4. the instruction from the Python TOML profile;
5. the current R9 WorkTask instruction;
6. authoritative upstream `inputs[]` supplied by the Control Plane.

## Install

```bash
cd workers/process-python
python -m pip install -r requirements.txt
```

Codex authentication is the same as the existing `workers/codex-python` reference worker.

For the OpenAI API runtime, set `$env:AIW_AGENT_RUNTIME = "openai-agents"` and `$env:OPENAI_API_KEY = "..."` in PowerShell, or export those variables in Linux/WSL before starting role scripts. The default is now `codex-app-server`; explicit `codex-sdk` is rejected.

The OpenAI Agents SDK is loaded only when selecting `openai-agents`. Starting a Codex Worker does not import that optional backend or require its `agents` package.

Factory-managed Codex Workers without an explicit guarded home use `AIW_CODEX_GUARD_MODE=host-actions`. The shared text-only transport inherits the existing CLI login. Native execution, browsing, plugins, agents and editing tools are disabled and checked; every file or Git action is executed by the existing guarded host broker. No second login directory or credential copy is required. An explicitly configured guarded home retains the existing dynamic-tool adapter. Both modes remain subject to the same task scope, lease and budget policy.

Host `read` offsets are 1-based line numbers and limits are 1–500 lines; Skill-file offsets remain bytes starting at zero. Correctable read-range failures are returned as observations. Writable results with no actual changed files or a structured blocked/incomplete outcome cannot be reported as PASS.

Guarded feature checks tolerate the CLI's fixed implementation flags (`item_ids`, `resize_all_images`, `terminal_resize_reflow`, `tool_search_always_defer_mcp_tools`, `tui_app_server`, `unified_exec`). They still require shell/browser/agent capabilities to be disabled, verify the closed permission profile, and reject native tool calls or unexpected tool items. Other enabled flags fail the check.

## Codex App Server migration

FD-020 removes the Python Codex SDK dependency and execution path. Set `AIW_AGENT_RUNTIME=codex-app-server` (default), configure the installed CLI via `AIW_CODEX_PATH` if needed, and prepare CLI authentication separately. The Worker never installs the CLI, logs in, or modifies credentials. It verifies an existing account with `account/read` and rejects missing authentication.

The App Server transport now implements native Windows ownership with a kill-on-close Job Object, in addition to Unix/WSL process groups. Its bootstrap starts the target only after Job assignment; normal completion and cancellation terminate the Job and query its active process count before release. Set `AIW_CODEX_PATH` to an installed `codex.exe` if npm shim resolution cannot find it. No shell, taskkill, downloads or privilege changes are used by this transport. This is source support, not a real Windows/CLI/protocol or production certification. API registration does not require a Codex binary.

Windows npm `codex.cmd` / `codex.ps1` resolution accepts both current `vendor/<target>/bin/codex.exe` and older `vendor/<target>/codex/codex.exe` layouts, including the nested `@openai/codex-win32-x64` or ARM64 platform package. A missing launcher and a launcher with a missing native binary produce separate diagnostics.

The personal secretary calls the same `CodexAppServerRuntime` / `OpenAIApiRuntime` implementations through `model_runtime.complete_text`, with `AgentRunRequest.model_only=True`. Codex is resolved from PATH by default, with optional existing `AIW_CODEX_PATH` or an explicit executable override. It inherits the operator's Codex configuration, CODEX_HOME and login. No dedicated login home, credential copy or generated permission profile is required. Model-only requests use an empty temporary workspace, a read-only sandbox, no dynamic host tools and disabled shell/search/multi-agent features. Worker guarded execution still uses its existing guard independently. The caller retains private context, business tools and authorization; details are in the [secretary README](../personal-secretary/README.md).

Set `AIW_CODEX_APPROVAL_MODE=deny-all` (default) or `never`; both use the native `never` policy and decline host approval requests. The SDK-only `auto-review` setting is rejected. Sandbox choices are `read-only` or `workspace-write`; old `full-access` is rejected. Read-only roles always use the read-only sandbox. Network access remains disabled. App Server profiles require `fast=false`; `xhigh` is accepted only for the App Server effort path within existing profile constraints. No fallback to removed SDK or to another provider occurs.

Each attempt starts its own App Server process group and ephemeral thread. The adapter checks thread metadata and sandbox/cwd/approval receipts, bounds RPC frames, event queues and output, and rejects unknown host interactions. Cancellation, lease loss and protocol errors interrupt the turn where possible, then terminate and confirm the process group before resources can be released. Unknown termination stops the slot and preserves the workspace. Normal completion also stops the attempt's server before reporting. The focused Windows Sudoku goal flow recorded real CLI/model execution, an isolated worktree commit and an independent review; see the evidence in `docs/current-zh/GOAL-WORKSHOP.md`. This does not certify cancellation, other platforms or production no-store behavior.

For native App Server tasks, a command's concrete nonzero exit is returned to the model as an observation instead of aborting the entire turn. Approval denials, cancellations and unknown tool failures remain terminal. A missing declared Skill lookup returns an explicit error without reading outside the frozen manifest; integrity failures remain terminal. The Worker supplies the actual materialized directory for each repository attempt in the prompt, so models need not infer a worktree path from historical reports. Focused offline regressions are in `tests/worker-runtime/test_codex_command_failures.py`; their results do not certify Windows sandbox access or successful business delivery.

On Windows, native Codex repository worktrees need ACLs that the restricted sandbox token can use. Python's private `mkdir(0700)` container can otherwise make checked-out files invisible even when `cwd` is correct. `windows_workspace_acl.py` uses DACL-only APIs, without audit privileges or ownership changes. It grants local Users traversal of the worktree container and inherited read/execute access to the assigned worktree only after checking that the source directory and each corresponding source entry already allow those reads. Coding worktrees can also retain Authenticated Users modify access only when that same access exists on the source directory and every corresponding entry. Users do not receive a write grant. A private source or unsupported ACL fails closed instead of expanding its sharing scope. The original checkout, evidence directories and guarded broker workspaces are not modified. Codex still enforces read-only or workspace-write policy and disabled network access. Offline policy regressions are in `tests/worker-runtime/test_windows_workspace_acl.py`; focused local probes confirmed sandbox reads and workspace writes after this fix, without calling a model.

For a native flexible-plan review, revision validation checks the exact matching `~work-N` input against the assigned review revision and repository. Earlier accepted analysis and planning outputs remain context and may name older revisions. Missing or mismatched current work output is still rejected. A resumed review also receives the authenticated recovery reason as context, so supplemental verification evidence can be assessed without rewriting historical reports or rerunning accepted implementation work.

Update deployed environment files and approved AI Profiles explicitly from `codex-sdk` to the intended supported runtime. Existing frozen requests are not rewritten; manage old queued/in-flight work through the control plane. Old processes must be stopped before replacing a deployment. Merely installing new source does not migrate existing deployments or validate an installed CLI version.

`goal-discussion` is shared with Go/TypeScript through the [canonical assets](../shared/goal-discussion) and [runtime contract](../WORKER_RUNTIME_CONTRACT.md). Start it with `AIW_PROCESS_ROLE=goal-discussion` or the role launcher. It uses a new empty directory for each task, rejects assigned workspaces and disables API tools. Source changes belong in the canonical assets, then sync bundled copies. The [role/language/backend matrix](../README.md) is the current support reference.

## Start a complete process-worker fleet

Set common environment values first. Example PowerShell:

```powershell
$env:AIW_CONTROL_PLANE_URL = "http://127.0.0.1:8080"
$env:AIW_REPOSITORY_REF = "core"
$env:AIW_REPOSITORY_ROOT = "D:/03_projects/example-repo"
$env:AIW_WORKER_CAPABILITIES = "" # Configure per role only after confirming its actual tools and expertise.
```

Then open separate terminals:

```powershell
./scripts/run-role.ps1 requirements
./scripts/run-role.ps1 architect
./scripts/run-role.ps1 planner
./scripts/run-role.ps1 coder
./scripts/run-role.ps1 integrator
./scripts/run-role.ps1 reviewer
./scripts/run-role.ps1 verifier
./scripts/run-role.ps1 repairer
```

Linux / WSL:

```bash
./scripts/run-role.sh requirements
./scripts/run-role.sh architect
./scripts/run-role.sh planner
./scripts/run-role.sh coder
./scripts/run-role.sh integrator
./scripts/run-role.sh reviewer
./scripts/run-role.sh stage-reviewer
./scripts/run-role.sh verifier
./scripts/run-role.sh repairer
```

You do not need all ten executable roles for a small test. A practical first fleet is:

```text
requirements x1
architect    x1
planner      x1
coder        x2
reviewer     x1
stage-reviewer x1
verifier     x1
repairer     x1
```

The same runtime can be horizontally replicated. Give every process a unique `AIW_WORKER_ID`.

## Capability matching

The runtime automatically adds the role capability. For example, `requirements` registers `requirement-analysis`; `reviewer` registers `code-review`.

`AIW_WORKER_CAPABILITIES` is therefore only for technical capabilities actually available to that role, for example on a qualified coder:

```text
typescript,node,sql
```

Repository capability `repo:<AIW_REPOSITORY_REF>` is added automatically. `*`, `repo:`, role capabilities and `flexible-work-v1` are reserved and rejected in the configured technical list. Do not share one technical list across every role. The initial TypeScript, Go, Python, SQL and database vocabulary is in [Worker capabilities](../../docs/current-zh/WORKER-CAPABILITIES.md).

## Permission boundary

R2's read-only rules are enforced by the runtime:

- requirements / architect / planner / reviewer / verifier expose read-only tools in both runtimes; Codex also uses its `read-only` sandbox;
- coder / integrator / repairer may use `AIW_CODEX_SANDBOX` (default `workspace-write`).

A read-only profile cannot widen itself to `workspace-write` through environment configuration.
The OpenAI API runtime exposes bounded workspace tools and does not expose shell execution. Git push is forbidden: `AIW_GIT_PUBLISH_REMOTE` and writable Codex network access are rejected at startup. Return a local result revision from a shared repository.

## R2 source extraction

See `../../docs/R10-R2-PROCESS-WORKER-EXTRACTION.md` for the exact mapping of R2 Java process code to the new workers and the list of authority code intentionally kept out of workers.


## Flexible work contract

All executable roles accept `EXTERNAL_WORK`. `skillRef` must equal the role’s primary Skill. REPORT and VERDICT roles are read-only; WORKSPACE roles require an assigned Git workspace and a 40/64-digit base revision. Reviews with code inputs must use the supplied resultRevision as their base. Tests need host authorization; Git push is forbidden; task text and reports cannot grant it. Plan proposals need manager confirmation; REPAIR_REQUIRED creates no automatic repair task.
# 按任务加载专业配置

设置 `AIW_DYNAMIC_CONFIG=true` 与 `AIW_EXECUTION_GUARD=true` 后，Worker 通过受控后端预检并登记 `aiw.execution.config.v1` 能力。新任务按服务器固定的配置正文及 Skill/知识摘要创建本次 Agent，不被启动 role 固定；旧任务继续使用启动 Profile。两种后端均消费相同 broker，不按 Codex/SDK 版本锁定兼容性。

控制台入口 `/ui/configurations`；完整字段、默认目录、原因和部署边界见 [专业配置合同](../../docs/EXECUTION-CONFIGURATION.md)。默认验证方法还要求明确的命令许可及 Linux/WSL 隔离适配器。配置切换不授予权限、不迁移旧数据，也不释放尚未确认停止的执行。

## Formal questions and local checkpoints (FD-028)

Both guarded Python backends expose ask_question through the trusted broker. Configure QUESTION in the immutable method requirements and in the explicitly approved execution policy. A question closes tool admission, retains a final local Git checkpoint and yields only after real runtime quiescence. A continuation requires the registered checkpoint-store capability and the same persistent local Git object store; no network fetch or original model session is required. Preserve the object store, worktree and result branch while a question is pending. See [FORMAL-QUESTIONS.md](../../docs/FORMAL-QUESTIONS.md) for the source, identity, stop and UNKNOWN contracts. No real backend or channel execution was run for this change.
# Worker 工作台与上下文

Python API 在原 Task/Attempt 内接收有收据的当前补充和后续排队，保留原权限、期限和实际模型总调用上限。控制平面先提供 V19/工作台协议，再更新并登记 Worker；原 Codex 路径本次不提供实时输入，不自动切换到 API。使用方式与真实支持矩阵见 [工作台指南](../../docs/WORKER-WORKBENCH.md)。

API 历史压缩保留可见来源、完整调用/结果配对和补充指令原文；摘要同模型、同计量。大工具结果提供有界预览与本次运行内的原文分页，不归档隐藏推理或声称持久云端日志。原任务和完整技能字节继续冻结，附加技能入口按需读取。来源及许可见 [参考许可](THIRD-PARTY-NOTICES.md)。
