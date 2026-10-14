# AI Worker Process Workers (Go)

## FD-016 profile transition

Bundled profiles accept only `EXTERNAL_WORK` and require nine fields including `resultKind`: `REPORT`, `VERDICT`, or `WORKSPACE`. Old task types and eight-field profiles are rejected. The launch role fixes the capability, exact `skillRef` and result kind; verifier never switches to integration-verifier. Release-readiness and orchestrator remain non-executable advisory profiles. See the [shared contract](../WORKER_RUNTIME_CONTRACT.md). This is local delivery; runtime checks, model calls and production no-store certification are separate evidence.

This worker package converts the R2 software-process roles into specialized R9 pull workers without creating separate implementations for every role. Each process explicitly selects `codex-cli` or `openai-api` through the Go `AgentRuntime` SPI; scheduling, leases, workspace preparation, evidence, and local result commits remain in the Worker.

One Go runtime is started multiple times with different role profiles. Go profiles live in `profiles/` and use the same TOML field format as the Python worker's profiles. Each worker keeps its own role instructions. Each process registers its selected runtime and role, pulls compatible READY tasks, heartbeats its lease, and completes/fails through Worker Protocol v1.

## Why this design

The bundled Skill source version is recorded in `skills/VERSION` (currently `R2`).
Directory names stay stable when the Skill source version changes. If you set
`AIW_PROCESS_SKILLS_ROOT` explicitly, point it at the current `skills/` directory.

R2 separated authority clearly:

- Java Control Plane owns lifecycle state and authoritative transitions.
- Skills/process roles define procedure and expected semantic output.
- Agents execute bounded work.
- Human approval remains human authority.

R9 already provides decentralized worker pull, leases, attempts, isolated Git worktrees, result handoff, repair loops and run/fleet scheduling. This package carries the R2 process specialization forward without reintroducing a central workflow engine.

## Active R9 profiles

| Profile | R2/R9 process role | Registered capability | Allowed task type | Repository access |
|---|---|---|---|---|
| `requirement-analyst.toml` | software-requirements-analyst | `requirement-analysis` | `EXTERNAL_WORK` | read-only |
| `solution-architect.toml` | solution-architect | `solution-architecture` | `EXTERNAL_WORK` | read-only |
| `implementation-planner.toml` | implementation-planner | `implementation-planning` | `EXTERNAL_WORK` | read-only |
| `coding-executor.toml` | coding-work-package-executor | `coding` | `EXTERNAL_WORK` | writable |
| `integration-executor.toml` | R9 integration role | `integration` | `EXTERNAL_WORK` | writable |
| `code-reviewer.toml` | software-code-reviewer | `code-review` | `EXTERNAL_WORK` | read-only |
| `stage-reviewer.toml` | independent workflow step review using `workflow-stage-reviewer` | `workflow-review` | `EXTERNAL_WORK` | read-only |
| `verifier.toml` | software-test-verifier | `verification` | `EXTERNAL_WORK` | read-only |
| `integration-verifier.toml` | software-test-verifier, integration scope | `integration-verification` | `EXTERNAL_WORK` | read-only |
| `repair-executor.toml` | R9 repair role | `repair` | `EXTERNAL_WORK` | writable |

## Preserved R2 profiles not schedulable by R9 yet

These R2 definitions live in `profiles/advisory/`, with their Skill packages in `skills/advisory/`. They remain advisory only; execution registration is denied:

- `release-readiness-manager.toml` -> would require `RELEASE_READINESS`
- `software-development-orchestrator.toml` -> would require an advisory orchestration task type

`engineering-artifact-protocol` is intentionally not a worker. It is a protocol/authority layer. The worker runtime embeds its core authority boundary in every role instruction: agents propose/execute; the Control Plane owns durable IDs, lifecycle state, approval and authoritative transitions.

## Build

```bash
cd workers/process-go
GOTOOLCHAIN=local go test ./...
go build -o process-worker .
```

Windows:

```powershell
cd workers/process-go
go test ./...
go build -o process-worker.exe .
```

The Go worker uses github.com/BurntSushi/toml to read its profiles in `profiles/`.

## Runtime selection

Set `AIW_AGENT_RUNTIME` explicitly before starting a worker:

```text
codex-cli   Existing authenticated Codex CLI; uses AIW_CODEX_* settings.
codex-app-server  Codex App Server v2 over managed stdio; uses the pinned Codex CLI release.
openai-api  OpenAI Responses API; requires OPENAI_API_KEY and provides bounded workspace tools.
```

All runtimes use the same role profile and Worker lifecycle. AI guidance must contain an approved profile whose `provider` is `openai` and whose `runtime` matches the selected runtime family. `codex-app-server` uses the approved Codex CLI profile; `openai-api` skips fast-tier profiles because the Go API adapter does not implement the Codex service-tier setting.

See [CODEX-RUNTIME.md](CODEX-RUNTIME.md) for the source-level runtime baseline,
cross-mode contract, pinned App Server protocol and capability matrix. The
App Server runtime accepts Codex CLI `0.162.1`; other versions fail before
Worker registration. Deployments install and upgrade the CLI themselves.

## Required runtime

- For `codex-cli`, Codex CLI available as `codex` (or set `AIW_CODEX_PATH`).
- For `openai-api`, `OPENAI_API_KEY`; `OPENAI_BASE_URL` is optional for compatible endpoints.
- R9 Control Plane running.
- For repository-backed tasks, a local Git repository matching `AIW_REPOSITORY_REF`.

## Start one role

Linux/macOS:

```bash
export AIW_PROCESS_PROFILE=profiles/requirement-analyst.toml
export AIW_WORKER_CAPABILITIES=
export AIW_REPOSITORY_REF=core
export AIW_REPOSITORY_ROOT=/work/core
./process-worker
```

PowerShell:

```powershell
$env:AIW_PROCESS_PROFILE = "profiles/requirement-analyst.toml"
$env:AIW_WORKER_CAPABILITIES = ""
$env:AIW_REPOSITORY_REF = "core"
$env:AIW_REPOSITORY_ROOT = "D:\projects\core"
.\process-worker.exe
```

The role capability is added automatically. Do not add `requirement-analysis`, `coding`, etc. manually unless you have a specific reason.

## Shared configuration

```text
AIW_CONTROL_PLANE_URL          default http://127.0.0.1:8080
AIW_WORKER_ID                  default <profile-prefix>-1
AIW_WORKER_TOKEN               optional Worker Protocol bearer token
AIW_WORKER_CAPABILITIES        technical capabilities actually available to this role, e.g. typescript,node,sql on a qualified coder
AIW_WORKER_MAX_CONCURRENCY     default 1
AIW_WORKER_POLL_MS             default 1000
AIW_WORKER_HEARTBEAT_MS        default 20000
AIW_WORKER_HTTP_TIMEOUT_MS     default 15000
AIW_REPOSITORY_REF             logical repository identity
AIW_REPOSITORY_ROOT            local repository path
AIW_WORKTREE_ROOT              optional isolated worktree root
AIW_GIT_PUBLISH_REMOTE         forbidden; Worker startup fails if set
AIW_CODEX_PATH                 default codex
AIW_CODEX_MODEL                optional
AIW_CODEX_REASONING_EFFORT     default high
AIW_CODEX_APPROVAL_POLICY      default never
AIW_CODEX_NETWORK_ACCESS       false only; Codex workspace network access is forbidden
AIW_CODEX_SANDBOX              read-only roles force read-only; writable roles default workspace-write
AIW_AGENT_RUNTIME              required: codex-cli, codex-app-server, or openai-api
AIW_CODEX_APP_SERVER_VERSION   default 0.162.1; App Server requires this exact supported version
AIW_CODEX_AUTH_MODE            default inherit; explicit api-key or chatgpt for Codex runtimes
AIW_CODEX_AUTH_HOME            absolute prepared CODEX_HOME required for explicit authentication
AIW_OPENAI_MODEL               OpenAI API default gpt-4.1
AIW_OPENAI_REASONING_EFFORT    optional: minimal, low, medium, high; applied to reasoning models
OPENAI_API_KEY                 required for openai-api
OPENAI_BASE_URL                default https://api.openai.com/v1
AIW_OPENAI_TIMEOUT_MS          default 120000
AIW_AGENT_MAX_TURNS            default 40 API tool-loop turns
AIW_AGENT_MAX_OUTPUT_BYTES     default 24000 bounded tool output
AIW_AGENT_TOOL_TIMEOUT_MS      default 60000 per tool call
AIW_AGENT_ALLOW_SHELL          false only; API host shell is forbidden
```

## Local Codex authentication selection

The current FD-014 stage runs locally. Docker isolation and production no-store
admission are deferred; local mode is not certified for production persistence.

`AIW_AGENT_RUNTIME` selects execution (`codex-cli`, `codex-app-server`, or
`openai-api`). `AIW_CODEX_AUTH_MODE` separately selects a prepared Codex credential
context. The default `inherit` keeps the existing native environment and login
behavior. Explicit `api-key` or `chatgpt` requires an existing absolute
`AIW_CODEX_AUTH_HOME` outside repository, worktree and attempt directories.

Prepare each credential context using the official native login flow before
starting the Worker. The Worker does not create a credential directory, log in,
log out, copy credentials, or inject `forced_login_method`. Explicit modes use
native cached credentials, not the ambient `OPENAI_API_KEY`, `CODEX_API_KEY` or
`CODEX_ACCESS_TOKEN`; those variables are excluded from the Codex child environment.
`OPENAI_API_KEY` remains the credential for the separate `openai-api` runtime.
Custom provider authentication and workload/access-token login are outside these
explicit API Key/ChatGPT checks; leave them under `inherit` when applicable.

For example, after preparing the two native credential contexts:

```sh
export AIW_AGENT_RUNTIME=codex-app-server
export AIW_CODEX_AUTH_MODE=chatgpt
export AIW_CODEX_AUTH_HOME=/srv/aiw-auth/chatgpt
# Start the Worker using its existing role/profile configuration.
```

To switch, stop the old Worker and wait for its active attempts and managed
process to settle, then change `AIW_CODEX_AUTH_MODE=api-key` and select the
separately prepared API login directory before restarting. Switching execution
mode works the same way via `AIW_AGENT_RUNTIME=codex-cli`. There is no in-process
credential hot reload or silent fallback.

Startup checks native `codex login status` before registration. Exec checks again
before each attempt; App Server also checks `account/read` after initialization
and before creating a thread. Unknown login output, missing credentials and
mode mismatch fail without printing native status text or account responses.
Only fixed failure codes are reported. Native status may contain a masked key,
so its bounded capture remains in memory and is never copied into evidence.
These checks select credentials; they do not prove host or service-side no-store.

## Capability matching

The Control Plane requires every task capability to be supported by the worker. Example:

```text
Task requirements:
  requirement-analysis
  java
  maven
  repo:core
```

The Requirements Analyst process automatically contributes:

```text
requirement-analysis
```

Configure the remaining technical capabilities and repository:

```text
AIW_WORKER_CAPABILITIES=
AIW_REPOSITORY_REF=core
```

The runtime adds `repo:core` automatically.

Configured technical capabilities must reflect the selected profile's actual expertise and tools. `*`, `repo:`, role capabilities and `flexible-work-v1` are reserved; do not put them in `AIW_WORKER_CAPABILITIES`. Go, Python and TypeScript are task capabilities, independent of the language used to implement this Worker. See [Worker capabilities](../../docs/current-zh/WORKER-CAPABILITIES.md).

## Read-only enforcement

Profiles marked `readOnly=true` require Codex sandbox `read-only`. The worker refuses startup if a read-only profile is explicitly configured with a writable sandbox.

Writable roles use `workspace-write` by default and still operate only inside the attempt-specific Git worktree assigned by the Control Plane.

## Completion semantics

The worker retains the R9 completion protocol:

- `EXTERNAL_WORK`, `EXTERNAL_WORK`: full final response is persisted as PASS summary.
- `EXTERNAL_WORK`, `EXTERNAL_WORK`: final response must end with exactly one verdict marker required by R9.
- `resultKind=WORKSPACE`: require a pinned attempt workspace, valid resultRevision, changedFiles and a nonempty PASS report; publish only when configured.
- `EXTERNAL_WORK` consumes authoritative review/verification findings from `inputs[]`.

## Recommended initial fleet

For a first real autonomous-development run:

```text
1 x Requirements Analyst
1 x Solution Architect
1 x Implementation Planner
2 x Coding Executor
1 x Integration Executor
1 x Code Reviewer
1 x Verifier
1 x Integration Verifier
1 x Repair Executor
```

All are ordinary independent pull workers. They can be stopped, restarted, multiplied or implemented later in another language without changing Control Plane authority.

## Exact R2 process assets

This directory now contains `skills/`, copied from the actual frozen R2 source package rather than reconstructed from documentation. At execution time the worker composes:

```text
engineering-artifact-protocol
    -> role primary R2 Skill
    -> R9 role adaptation / task instruction
```

`AIW_PROCESS_INCLUDE_REFERENCES=true` (default) also injects the Markdown files under each resolved Skill's `references/` directory. Set it to `false` only when you intentionally want a smaller prompt context.

`AIW_PROCESS_SKILLS_ROOT` can override the bundled `skills/` location.

See `SOURCE-PROVENANCE.md` for the R2 Java/Skill boundary used during extraction.


## Flexible work contract

All executable roles accept `EXTERNAL_WORK`. `skillRef` must equal the role’s primary Skill. REPORT and VERDICT roles are read-only; WORKSPACE roles require an assigned Git workspace and a 40/64-digit base revision. Reviews with code inputs must use the supplied resultRevision as their base. Tests need host authorization; Git push is forbidden; task text and reports cannot grant it. Plan proposals need manager confirmation; REPAIR_REQUIRED creates no automatic repair task.
