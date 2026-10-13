# R10 R2 Process Workers (Python)

## FD-016 profile transition

Bundled profiles accept only `EXTERNAL_WORK` and require nine fields including `resultKind`: `REPORT`, `VERDICT`, or `WORKSPACE`. Old task types and eight-field profiles are rejected. The launch role fixes the capability, exact `skillRef` and result kind; verifier never switches to integration-verifier. Release-readiness and orchestrator remain non-executable advisory profiles. See the [shared contract](../WORKER_RUNTIME_CONTRACT.md). This is local delivery; runtime checks, model calls and production no-store certification are separate evidence.

This worker runtime ports the **software-process execution side** of the frozen R2 implementation onto the R9 decentralized Worker Pull protocol.

It deliberately does **not** port R2 lifecycle authority into workers. The R9 Java Control Plane remains authoritative for task state, leases, retries, run control, approvals, scheduling, engineering gates and persistence.

## Runtime model

Model execution is isolated behind the `AgentRuntime` protocol in
`agent_runtime.py`. The worker owns task, lease, workspace, and report handling;
the runtime owns SDK session creation, execution, and final output validation.
The current `CodexAgentRuntime` preserves the existing Codex SDK behavior.

The bundled Skill source version is recorded in `skills/VERSION` (currently `R2`).
Directory names stay stable when the Skill source version changes. If you set
`AIW_PROCESS_SKILLS_ROOT` explicitly, point it at the current `skills/` directory.

One Python runtime is started as multiple independent worker processes by selecting `AIW_PROCESS_ROLE`. `AIW_AGENT_RUNTIME` selects `codex-sdk` (default) or `openai-agents`. Role names resolve to Python's TOML profiles in `profiles/`. Go uses the same TOML field format but keeps its own files and role instructions. Python 3.11 or newer is required for `tomllib`.

The OpenAI Agents runtime uses `openai-agents==0.23.1` with `openai==3.26.0`, requires `OPENAI_API_KEY`, and does not require Codex CLI authentication. It provides bounded workspace read/list/search and write/edit tools; shell tools are not available in this Python API runtime. Set `AIW_AGENT_RUNTIME=openai-agents` before starting the role process. Use `AIW_OPENAI_MODEL`, `AIW_OPENAI_REASONING_EFFORT`, and `OPENAI_BASE_URL` to configure the API runtime.

| Role | R9 task type(s) | R2 primary Skill | Workspace |
|---|---|---|---|
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

The complete frozen R2 `skills/` tree is retained under `skills/`. At execution time the worker composes:

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

For the OpenAI API runtime, set `$env:AIW_AGENT_RUNTIME = "openai-agents"` and `$env:OPENAI_API_KEY = "..."` in PowerShell, or export those variables in Linux/WSL before starting role scripts. `AIW_AGENT_RUNTIME` defaults to `codex-sdk` for existing deployments.

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
