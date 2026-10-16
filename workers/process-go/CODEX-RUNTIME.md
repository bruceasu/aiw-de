# Go Worker Codex Runtime Baseline and Contract

This document records the source-level baseline and the minimum contract for
the FD-014 Go pilot. It does not certify runtime behavior.

## Existing Go baseline

| Area | `codex-cli` | `codex-app-server` | `openai-api` | Static source |
|---|---|---|---|---|
| Selection | Explicit `AIW_AGENT_RUNTIME=codex-cli`; `resolveAgentRuntime` returns `CodexAgentRuntime` | Explicit `AIW_AGENT_RUNTIME=codex-app-server`; fixed version and successful handshake required before registration | Explicit `AIW_AGENT_RUNTIME=openai-api`; requires `OPENAI_API_KEY` | `agent_runtime.go`, `main.go`, `app_server_runtime.go` |
| Execution | Starts one `codex exec --ephemeral --json` child for an attempt | One managed App Server child per Worker; each attempt gets its own ephemeral thread | Calls the Responses API and executes bounded workspace tools in the Go Worker | `main.go`, `app_server_runtime.go`, `openai_runtime.go` |
| Attempt boundary | Receives the attempt context, workspace, selected profile, instruction and evidence recorder through `AgentRunRequest` | Same request shape; routes session events by thread ID and terminal events by turn ID | Receives the same request shape | `agent_runtime.go`, `main.go`, `app_server_runtime.go` |
| Result | Session ID and final agent message; usage is not populated by the adapter | Thread ID and final agent message; usage is marked unknown | Session/response ID, final output and available token usage | `agent_runtime.go`, `app_server_runtime.go`, `openai_runtime.go` |
| Cancellation | `exec.CommandContext` with platform process-tree termination | Sends `turn/interrupt`, waits for terminal status, then kills the managed process tree if stop is unconfirmed | HTTP requests and tool operations use the attempt context | `main.go`, `app_server_runtime.go`, `process_windows.go`, `process_unix.go`, `openai_runtime.go` |
| CLI policy | Passes `--ephemeral`, sandbox, approval, reasoning effort, network and optional model/service tier; instruction is sent over stdin | Uses `ephemeral=true`, assigned workspace, model, effort, service tier, approval and sandbox settings | Uses Responses API settings and bounded Worker tools | `main.go`, `app_server_runtime.go`, `openai_runtime.go` |
| No-store admission | The inspected Go path does not validate a pinned CLI digest or session-policy manifest before registration; `--ephemeral` alone does not establish that every persistence channel is disabled | Version pin is checked, but ephemeral thread mode alone does not establish that every persistence channel is disabled | Responses requests and Worker evidence have separate storage behavior | `main.go`, `app_server_runtime.go`, `docs/worker-codex-no-store-design.md` |
| Resume | No resume operation is exposed by the Go adapter | No resume operation is exposed; starts a new ephemeral thread per attempt | No previous-response continuation is exposed by the adapter | `agent_runtime.go`, `app_server_runtime.go`, `openai_runtime.go` |
| Events | Reads JSONL events while the command runs; only selected terminal metadata is recorded | Reads JSON-RPC notifications and accepts only the matching completed turn as success | Uses non-streaming HTTP responses | `main.go`, `app_server_runtime.go`, `openai_runtime.go` |
| Tools | Codex CLI owns tool execution; Go records selected item terminal metadata | Codex App Server owns tool execution under its configured sandbox and approval policy | Go owns the bounded tool loop and workspace tool implementations | `main.go`, `app_server_runtime.go`, `openai_runtime.go` |

The shared attempt code owns lease heartbeats, deadline, workspace creation and
cleanup, read-only checks, result validation, publication and Control Plane
reporting. `AgentRuntime.Run` does not own those transitions. The Go capability
struct is currently Boolean; it cannot express `unknown`, and it has no usage
or approval capability fields.

## Minimum cross-mode contract

1. A runtime receives exactly one attempt-scoped context, task identity,
   workspace, read-only policy, frozen instruction, selected profile and
   evidence recorder.
2. A session identifier belongs to that task/attempt. It is reported through
   `OnSession` as soon as the runtime has a real identifier; it is not a lease
   or a Control Plane state transition.
3. A successful result contains the final user-facing output and is returned
   only after the runtime has observed a definitive successful terminal state.
   Usage fields are accompanied by an explicit known/unknown state.
4. Runtime events are attempt-scoped observations. They do not change Worker
   or Control Plane state. Unknown event fields are not inferred from another
   runtime's event model.
5. Cancellation uses the attempt context. The runtime must not report success
   after cancellation. If process/request termination cannot be confirmed, the
   caller must retain the workspace and slot as uncertain instead of treating
   `Run` as settled.
6. A configured runtime is selected explicitly. Startup or protocol failure
   returns an error; it never silently switches to another runtime.

## Capability semantics

Each capability is `supported`, `unsupported` or `unknown` for a specific
runtime and pinned version. `Unknown` means source or authorized runtime
evidence does not establish behavior. It is not equivalent to unsupported.

| Capability | Codex Exec source baseline | OpenAI API source baseline | Go App Server pilot |
|---|---|---|---|
| Resume | Unsupported by this adapter | Unsupported by this adapter | Unsupported by this adapter; each attempt starts an ephemeral thread |
| Attempt cancellation | Uses context and process-tree termination; runtime confirmation still needs execution evidence | Uses request/tool context; endpoint-side cancellation is not established | Sends `turn/interrupt`, waits for its RPC response and matching `turn/completed`; on missing confirmation, terminates the App Server process tree. Runtime confirmation still needs execution evidence |
| Event streaming | Supported as CLI JSONL input; only selected fields are retained | Unsupported by the current non-streaming adapter | Reads newline-delimited JSON-RPC; correlates notifications by thread and turn and accepts only `turn.status == completed` as success |
| Tools | Supported by Codex CLI; internal policy details remain unknown | Supported by Worker-owned bounded tools | App Server owns Codex tool execution under the configured approval and sandbox policy; approval UI and policy details remain unknown |
| Usage | Unknown in `CodexAgentRuntime` result | Supported when the response includes usage; otherwise marked unknown | Unknown in this adapter; result marks usage unknown |
| Approval | Unknown to this Go adapter beyond configured CLI policy | Not a Codex App Server capability | Approval policy is passed to the thread; approval interaction is unsupported by this adapter and is not a cross-mode guarantee |

## App Server protocol baseline

The implementation was developed against OpenAI Codex CLI `0.162.1`, official tag
`rust-v0.162.1`, and that tag's App Server v2 JSON schema. This is a reference
baseline, not a version restriction. At process startup the Worker records the
installed CLI version, starts one `codex app-server --listen
stdio://` child, completes `initialize`/`initialized`, and only then registers
with the Control Plane. Every attempt starts a separate `ephemeral` thread in
its assigned workspace and sends one `turn/start` request. The runtime reads
newline-delimited JSON-RPC, correlates `item/agentMessage/delta` and
`turn/completed` by thread/turn ID, and accepts only `completed` as success.
Cancellation waits for the `turn/interrupt` response and the matching terminal
notification; missing confirmation triggers process-tree termination, with
uncertain termination returned as an error so the Worker retains attempt
resources. Closing stdin is the graceful process shutdown request, followed by
a bounded process-tree termination fallback. The pinned stdio transport's
watchdog permits 45 seconds for shutdown; the Worker waits 50 seconds before
forcing the managed process tree down.

Primary sources, checked 2026-10-13:

- [Development baseline Codex CLI release](https://github.com/openai/codex/releases/tag/rust-v0.162.1).
- [ThreadStartParams v2](https://github.com/openai/codex/blob/rust-v0.162.1/codex-rs/app-server-protocol/schema/json/v2/ThreadStartParams.json) (`ephemeral`).
- [TurnStartParams v2](https://github.com/openai/codex/blob/rust-v0.162.1/codex-rs/app-server-protocol/schema/json/v2/TurnStartParams.json) (`threadId`, `input`, `effort`, `serviceTierForTurn`).
- [TurnInterruptParams v2](https://github.com/openai/codex/blob/rust-v0.162.1/codex-rs/app-server-protocol/schema/json/v2/TurnInterruptParams.json) (`threadId`, `turnId`).
- [TurnCompletedNotification v2](https://github.com/openai/codex/blob/rust-v0.162.1/codex-rs/app-server-protocol/schema/json/v2/TurnCompletedNotification.json).
- [OpenAI Developers App Server guide](https://developers.openai.com/siwc/token-sharing-open-source/codex-app-server) (stdio framing and initialization/turn sequence).
- [Pinned stdio transport](https://github.com/openai/codex/blob/rust-v0.162.1/codex-rs/app-server-transport/src/transport/stdio.rs) (EOF shutdown and the 45-second process deadline).

These sources define the implementation target. They do not establish runtime
behavior of this Go adapter. Authorized local fake-server and real CLI checks
are recorded in FD-014. Ephemeral threads express the protocol's non-persistent
thread mode; other CLI persistence channels, host isolation, and service-side
retention remain unknown. FD-014 delivers the local Go runtime and dual-auth
support only. Docker isolation, production admission and additional Linux
lifecycle/restart/rollback evidence are deferred to FD-015. Local delivery
does not certify production readiness.

## Deployment and migration recommendation

| CLI version | App Server runtime | Policy |
|---|---|---|
| Installed CLI, any version | Compatibility checked by actual commands and the v2 handshake | Version changes alone do not fail startup. Actual protocol or permission incompatibility fails without silent Exec fallback. |

The deployment owner installs, authenticates, upgrades, and rolls back Codex
CLI. The Worker does not download or replace it. To roll back the App Server
mode, explicitly change `AIW_AGENT_RUNTIME` to the existing `codex-cli` mode
and restart the Worker. Changing the installed CLI version does not require a
Worker version pin. Protocol incompatibilities may require adapter changes and
focused compatibility checks. No operational-cost
reduction is claimed because no comparative measurements were collected.

For Java, Python, and TypeScript follow-up, reuse the attempt-scoped request,
session correlation, terminal-result, cancellation, and capability semantics.
Keep process ownership and workspace isolation language-specific, record the
installed CLI version, validate actual protocol behavior, and do not infer approval, resume, usage, or persistence
behavior across adapters. This is a migration recommendation only; those
workers are not implemented by this FD.

## Local authentication and deferred Docker admission

The execution selector and the authentication selector are independent.
`AIW_CODEX_AUTH_MODE` accepts `inherit`, `api-key`, or `chatgpt`. Explicit modes
use the operator's already prepared `AIW_CODEX_AUTH_HOME`, reject homes inside
writable repository/worktree/attempt paths, and remove ambient API/access-token
variables from the native child environment. The Worker never invokes native
login/logout or copies credentials. It does not use forced_login_method to
switch a shared login cache.

The pinned CLI's login-status implementation emits the login type on stderr;
its bounded output is consumed only in memory and never included in errors.
App Server additionally checks only the account type from `account/read` with
`refreshToken=false`; account payloads are not recorded. Native login status is
not a proof of the credentials used by arbitrary custom providers. Those
providers and other login kinds are excluded from explicit-mode guarantees.

Stop and settle the Worker before changing authentication or execution mode,
then restart with the selected configuration. Existing `inherit` behavior is
unchanged. Docker is the chosen future isolation strategy and is not implemented
in this local stage. No local, Docker, host or service-side no-store certification
is claimed; both authentication modes need their own production admission
records when that stage resumes.

## Local resource failure boundaries

App Server startup explicitly forwards the Worker's network-access setting.
Once turn/start may have reached the server, transport and non-terminal event
failures require a confirmed terminal event or successful shared shutdown.
Concurrent shutdown callers observe the actual shutdown result. Unknown process
termination preserves the workspace and its lock, quarantines the slot, and
suppresses both failure reporting and stop acknowledgement. A confirmed shared
shutdown also ends other active sessions; no automatic runtime fallback occurs.
These paths were statically reviewed and compiled, without new runtime tests.
