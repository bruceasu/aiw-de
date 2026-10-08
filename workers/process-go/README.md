# AI Dev Manager Process Workers (Go)

This worker package converts the R2 software-process roles into specialized R9 pull workers without creating separate implementations for every role. Each process explicitly selects `codex-cli` or `openai-api` through the Go `AgentRuntime` SPI; scheduling, leases, workspace preparation, evidence, and publication remain in the Worker.

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
| `requirement-analyst.toml` | software-requirements-analyst | `requirement-analysis` | `REQUIREMENT_ANALYSIS` | read-only |
| `solution-architect.toml` | solution-architect | `solution-architecture` | `SOLUTION_ARCHITECTURE` | read-only |
| `implementation-planner.toml` | implementation-planner | `implementation-planning` | `IMPLEMENTATION_PLANNING` | read-only |
| `coding-executor.toml` | coding-work-package-executor | `coding` | `IMPLEMENTATION` | writable |
| `integration-executor.toml` | R9 integration role | `integration` | `INTEGRATION` | writable |
| `code-reviewer.toml` | software-code-reviewer | `code-review` | `CODE_REVIEW` | read-only |
| `verifier.toml` | software-test-verifier | `verification` | `VERIFICATION` | read-only |
| `integration-verifier.toml` | software-test-verifier, integration scope | `integration-verification` | `INTEGRATION_VERIFICATION` | read-only |
| `repair-executor.toml` | R9 repair role | `repair` | `REPAIR` | writable |

## Preserved R2 profiles not schedulable by R9 yet

These R2 roles are included so their process definition is not lost, but R9 currently has no matching `TaskType`, so do not launch them yet:

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
openai-api  OpenAI Responses API; requires OPENAI_API_KEY and provides bounded workspace tools.
```

Both runtimes use the same role profile and Worker lifecycle. AI guidance must contain an approved profile whose `provider` is `openai` and whose `runtime` matches the selected runtime. `openai-api` skips fast-tier profiles because the Go API adapter does not implement the Codex service-tier setting.

## Required runtime

- For `codex-cli`, Codex CLI available as `codex` (or set `AIW_CODEX_PATH`).
- For `openai-api`, `OPENAI_API_KEY`; `OPENAI_BASE_URL` is optional for compatible endpoints.
- R9 Control Plane running.
- For repository-backed tasks, a local Git repository matching `AIW_REPOSITORY_REF`.

## Start one role

Linux/macOS:

```bash
export AIW_PROCESS_PROFILE=profiles/requirement-analyst.toml
export AIW_WORKER_CAPABILITIES=java,maven
export AIW_REPOSITORY_REF=core
export AIW_REPOSITORY_ROOT=/work/core
./process-worker
```

PowerShell:

```powershell
$env:AIW_PROCESS_PROFILE = "profiles/requirement-analyst.toml"
$env:AIW_WORKER_CAPABILITIES = "java,maven"
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
AIW_WORKER_CAPABILITIES        technical capabilities, e.g. java,maven,postgresql
AIW_WORKER_MAX_CONCURRENCY     default 1
AIW_WORKER_POLL_MS             default 1000
AIW_WORKER_HEARTBEAT_MS        default 20000
AIW_WORKER_HTTP_TIMEOUT_MS     default 15000
AIW_REPOSITORY_REF             logical repository identity
AIW_REPOSITORY_ROOT            local repository path
AIW_WORKTREE_ROOT              optional isolated worktree root
AIW_GIT_PUBLISH_REMOTE         optional, e.g. origin
AIW_CODEX_PATH                 default codex
AIW_CODEX_MODEL                optional
AIW_CODEX_REASONING_EFFORT     default high
AIW_CODEX_APPROVAL_POLICY      default never
AIW_CODEX_NETWORK_ACCESS       default false
AIW_CODEX_SANDBOX              read-only roles force read-only; writable roles default workspace-write
AIW_AGENT_RUNTIME              required: codex-cli or openai-api
AIW_OPENAI_MODEL               OpenAI API default gpt-4.1
AIW_OPENAI_REASONING_EFFORT    optional: minimal, low, medium, high; applied to reasoning models
OPENAI_API_KEY                 required for openai-api
OPENAI_BASE_URL                default https://api.openai.com/v1
AIW_OPENAI_TIMEOUT_MS          default 120000
AIW_AGENT_MAX_TURNS            default 40 API tool-loop turns
AIW_AGENT_MAX_OUTPUT_BYTES     default 24000 bounded tool output
AIW_AGENT_TOOL_TIMEOUT_MS      default 60000 per tool call
AIW_AGENT_ALLOW_SHELL          default false; explicitly enables shell for writable API profiles
```

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
AIW_WORKER_CAPABILITIES=java,maven
AIW_REPOSITORY_REF=core
```

The runtime adds `repo:core` automatically.

## Read-only enforcement

Profiles marked `readOnly=true` require Codex sandbox `read-only`. The worker refuses startup if a read-only profile is explicitly configured with a writable sandbox.

Writable roles use `workspace-write` by default and still operate only inside the attempt-specific Git worktree assigned by the Control Plane.

## Completion semantics

The worker retains the R9 completion protocol:

- `REQUIREMENT_ANALYSIS`, `SOLUTION_ARCHITECTURE`, `IMPLEMENTATION_PLANNING`: full final response is persisted as PASS summary.
- `CODE_REVIEW`, `VERIFICATION`, `INTEGRATION_VERIFICATION`: final response must end with exactly one verdict marker required by R9.
- Repository-changing roles finalize the attempt-specific Git result and optionally publish it.
- `REPAIR` consumes authoritative review/verification findings from `inputs[]`.

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

