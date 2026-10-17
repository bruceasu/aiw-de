# Workers

This directory contains the R9 process workers, small Codex examples, and a separate Java deployment demo. The Java Control Plane owns task state, scheduling, leases, approvals, retries, and persistence. A worker claims bounded tasks, executes them in an isolated workspace, and reports results through Worker Protocol v1.

## Process workers

The bundled role Skills include adapted AIW task methods. See
[Worker Skill methods](SKILL_METHODS.md) for the source-to-role map, loading
behavior, version, boundaries, and unrun evaluation scenarios.

These are the maintained multi-role workers:

| Package | Language | Runtime choices | Details |
| --- | --- | --- | --- |
| [`process-typescript`](process-typescript/README.md) | TypeScript | `codex-sdk`, `openai-agents` | One entry point selects an `AgentRuntime` backend per process. |
| [`process-go`](process-go/README.md) | Go | `codex-cli`, `codex-app-server`, `openai-api` | An `AgentRuntime` SPI keeps model execution behind the shared Worker lifecycle. |
| [`process-python`](process-python/README.md) | Python | `codex-sdk`, `openai-agents` | Runtime selection is implemented through the Python `AgentRuntime` protocol. |

Each process selects one runtime at startup. API runtimes require `OPENAI_API_KEY`; Codex runtimes require their corresponding installed/authenticated Codex setup. Runtime-specific profile and environment requirements are documented in each package README.

## Codex demos

The `demo-*-with-codex` directories are small language-specific Worker Protocol examples. They are separate from the maintained role-based process workers and are useful as protocol references.

## Java deployment demo

[`demo-deployer`](demo-deployer/README.md) demonstrates a separate deployment workflow: validate a request, package a Java artifact, and copy the verified artifact into an allowed directory. It does not restart an application, switch a live-release pointer, or roll back a release. Deployment permissions and approval must be designed independently for production use.

## Shared contracts and operations

- [Worker runtime contract](WORKER_RUNTIME_CONTRACT.md) describes role, task, evidence, and runtime behavior.
- [Worker operations](WORKER_OPERATIONS.md) documents deployment configuration and current verification limits.
- [SDK capabilities](SDK_CAPABILITIES.md) records the inspected SDK behavior and known unknowns.
