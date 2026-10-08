# TypeScript Process Worker

This is the single TypeScript Worker package for the R9 Worker Pull protocol. Each process runs one R2 role with an explicit `AIW_AGENT_RUNTIME`: `codex-sdk` or `openai-agents`. The Java Control Plane owns task state, leases, retries, scheduling, approvals, and persistence. The Worker owns task instructions and result interpretation; the selected AgentRuntime owns model execution. The former `process-typescript-with-api` package and launcher have been removed.

The Codex backend uses the official Codex TypeScript SDK and an existing, authenticated Codex CLI. It resolves the native executable from `AIW_CODEX_PATH` or `PATH` before registering; Windows npm launchers resolve to the installed native executable. It does not use a worker-local CLI. The package's `.npmrc` omits optional SDK platform binaries, while the pinned JavaScript launcher remains in the lockfile.

The Agents backend uses OpenAI Agents SDK and Responses API. It requires `OPENAI_API_KEY` before registration and does not need Codex CLI. The SDK version is checked at startup. Agents model and tool body logs and tracing are disabled before SDK import.

## Roles

Bundled role profiles live in `profiles/`; their R2 Skill source version is in `skills/VERSION`. `AIW_PROCESS_PROFILES_ROOT` and `AIW_PROCESS_SKILLS_ROOT` can point to externally supplied copies.

| Launch role | Task type | Access |
| --- | --- | --- |
| `requirements` | `REQUIREMENT_ANALYSIS` | read-only |
| `architect` | `SOLUTION_ARCHITECTURE` | read-only |
| `planner` | `IMPLEMENTATION_PLANNING` | read-only |
| `coder` | `IMPLEMENTATION` | writable |
| `integrator` | `INTEGRATION` | writable |
| `reviewer` | `CODE_REVIEW` | read-only |
| `verifier` | `VERIFICATION`, `INTEGRATION_VERIFICATION` | read-only |
| `integration-verifier` | `INTEGRATION_VERIFICATION` | read-only |
| `repairer` | `REPAIR` | writable |

`verifier` also registers the integration-verification capability and selects its dedicated profile for that task. The `release-readiness` and `orchestrator` profiles remain bundled for traceability but have no executable R9 task type. The same profile, Skill, task instruction, and completion-report code is used for both runtimes.

## Install and start

Node.js 22+, npm, and Git are required. Install and build from this directory:

```sh
npm ci --omit=optional
npm run build
```

Set `AIW_CONTROL_PLANE_URL`, `AIW_REPOSITORY_REF`, `AIW_REPOSITORY_ROOT`, and any additional technical `AIW_WORKER_CAPABILITIES` for each process. The role and `repo:<ref>` capabilities are added automatically. Start one process per role. Examples:

```powershell
./scripts/run-role.ps1 requirements codex-sdk
./scripts/run-role.ps1 coder codex-sdk
$env:OPENAI_API_KEY = '<API key>'
./scripts/run-role.ps1 reviewer openai-agents
```

```sh
sh ./scripts/run-role.sh requirements codex-sdk
sh ./scripts/run-role.sh coder codex-sdk
export OPENAI_API_KEY='<API key>'
sh ./scripts/run-role.sh reviewer openai-agents
```

Both scripts require the role and runtime. You may instead set `AIW_PROCESS_ROLE` and `AIW_AGENT_RUNTIME` explicitly, then run `npm start`. Missing or unknown runtime fails before registration; there is no automatic fallback. Use a unique `AIW_WORKER_ID` for each replica. The default is `process-<role>-1` for Codex and `process-api-<role>-1` for Agents, preserving existing Control Plane identities. The runtime registration labels remain `codex-sdk:<role>` and `openai-agents:<role>`; task AI profiles must approve the selected runtime.

For a small run without integration work, `integrator` is optional. Check `GET /api/r8/fleet/dashboard` for unmatched READY tasks after starting the fleet.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `AIW_AGENT_RUNTIME` | required | `codex-sdk` or `openai-agents` |
| `AIW_PROCESS_ROLE` | `requirements` | Role for this process |
| `AIW_WORKER_ID` | runtime dependent | Unique Worker Protocol identity |
| `AIW_WORKER_TOKEN` | unset | Worker Protocol bearer token |
| `AIW_WORKER_MAX_CONCURRENCY` | `1` | Concurrent claim slots |
| `AIW_CONTROL_PLANE_URL` | `http://127.0.0.1:8080` | Control Plane URL |
| `AIW_WORKSPACE` | repository root or cwd | Fallback task workspace |
| `AIW_WORKTREE_ROOT` | sibling `.aidev-worktrees/` | Attempt worktrees |
| `AIW_GIT_PUBLISH_REMOTE` | unset | Shared Git remote for cross-machine handoff |
| `AIW_WORKER_POLL_MS` | `1000` | Empty-claim retry interval |
| `AIW_WORKER_HEARTBEAT_MS` | `20000` | Worker and task heartbeat interval |
| `AIW_WORKER_HTTP_TIMEOUT_MS` | `15000` | Control Plane request timeout |
| `AIW_CODEX_PATH` | installed CLI on `PATH` | Existing native Codex executable |
| `AIW_CODEX_MODEL` | SDK default | Codex model |
| `AIW_CODEX_REASONING_EFFORT` | `high` | Codex reasoning effort |
| `AIW_CODEX_APPROVAL_POLICY` | `never` | Unattended Codex approval policy |
| `AIW_CODEX_SANDBOX` | role dependent | Read-only roles force `read-only` |
| `AIW_CODEX_NETWORK_ACCESS` | `false` | Network in writable Codex sandbox |
| `OPENAI_API_KEY` | required for Agents | OpenAI credential |
| `OPENAI_BASE_URL` | SDK default | Agents API endpoint |
| `AIW_OPENAI_MODEL` | `gpt-4.1` | Agents model without an approved task profile |
| `AIW_OPENAI_REASONING_EFFORT` | unset | Agents reasoning effort |
| `AIW_AGENT_ALLOW_SHELL` | `false` | Agents host shell for writable roles only |
| `AIW_AGENT_MAX_TURNS` | `40` | Agents turn limit |
| `AIW_AGENT_TOOL_TIMEOUT_MS` | `60000` | Agents tool timeout |
| `AIW_AGENT_MAX_OUTPUT_CHARS` | `24000` | Agents output budget, enforced as UTF-8 bytes |
| `AIW_AGENT_AIW_TOOL_CATALOG` | unset | Optional fixed external aiw tool catalog |

`AIW_AGENT_COMPACT_HISTORY` defaults to false. Enabling it requires `AIW_AGENT_COMPACT_AFTER_BYTES` and `AIW_AGENT_SUMMARY_MAX_TOKENS`. The package-local [runtime-core](runtime-core/PUBLIC_CONTRACT.md) controls attempt deadline, cleanup, evidence, workspace isolation, and result publication. See [Worker operations](../WORKER_OPERATIONS.md) for those budgets and the Codex deployment validation requirement. The optional aiw catalog format is shown in [aiw-tools.example.json](aiw-tools.example.json); it is external to the task workspace and grants no authority by itself.

Agents read-only roles have file inspection tools. Writable roles can use bounded write/edit tools and can enable a host shell explicitly with `AIW_AGENT_ALLOW_SHELL`; that shell is not an OS sandbox, so deploy on an isolated host/container. Codex read-only roles force a read-only sandbox. No runtime claims cross-process resume or cancel by run ID; cancellation is scoped to the active attempt's signal.

On SIGINT/SIGTERM the Worker drains, finishes in-flight attempts, then goes offline. A lost lease aborts the active attempt and suppresses stale results. If termination is uncertain, it preserves the workspace and stops claiming work.

## Compile, tests, and release

`npm run compile` performs TypeScript syntax compilation in memory without a release artifact. `npm test` builds and runs the existing Codex role tests; run it only when your validation policy authorizes tests. Agents behavior and online endpoints require separate verification.

`npm run release` creates `out/process-typescript-<version>/` with `dist/`, `profiles/`, `skills/`, package-local `runtime-core/`, `bin/`, scripts, configuration sample, and package metadata. It copies `runtime-core/` from this package, excludes `node_modules/`, and refuses to overwrite an existing release. On a destination, run `npm ci --omit=dev --omit=optional`, then start the selected runtime. An offline destination can receive preinstalled production dependencies from a staging machine. Codex destinations still need their own installed CLI and authentication; Agents destinations need an API key.
