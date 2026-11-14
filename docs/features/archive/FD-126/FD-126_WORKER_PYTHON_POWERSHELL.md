# FD-126: Worker 支持 Python 优先与 PowerShell 命令

**Status:** Complete
**Revision:** 13
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Dual

## Problem

Worker 的跨平台通用命令应优先使用 Python，但 Windows 专用操作需要 PowerShell 通道。用户也需要清楚知道哪些命令会等待人工审核。

## Options and decision

沿用现有 Worker 命令工具和 AIW 危险操作审批。Python 继续负责跨平台通用操作；PowerShell 用于 Windows 特有操作。Windows 命令使用 Codex App Server 无沙箱执行时，每条命令都必须经 AIW 人工批准，以保留工作区外访问需要明确授权的边界。Linux/WSL 继续使用现有沙箱和危险操作审批。

## Solution

Windows 通过 Codex App Server 的 `process/spawn` 运行固定命令。该接口不使用 Codex 沙箱，因此不要求 Windows Restricted Token 沙箱可用。由于它可访问宿主资源，Windows 每条命令执行前都须经 AIW 人工批准。Windows Job Object 负责清理进程树并限制内存和进程数；Worker 校验输出上限。Windows Worker 只在任务声明 COMMAND 时启动此适配器。

PowerShell 固定解析为系统 Windows PowerShell，禁用 Profile 和交互；Python 固定使用当前 Worker 的解释器。Python 仍优先处理跨平台操作；PowerShell 用于 Windows 专有操作。Codex App Server 只负责执行命令，不替代 Python Agent Loop 或模型后端。Worker 不自动安装它。

## Scope

增加 Windows PowerShell 命令路径并接入 AIW 命令审批。Windows 使用 Codex App Server `process/spawn` 的无沙箱运行模式，不要求 Restricted Token 沙箱可用；每条 Windows 命令都须人工批准。保留 Job Object 进程树清理、内存/进程限制、超时和输出上限。更新命令能力协商，使本机 Windows Worker 只为显式声明 COMMAND 且 App Server 支持 `process/spawn` 的任务启用适配器；Linux/WSL 保留现有 cgroup 和受限 `command/exec` 配置。Python 仍是跨平台通用操作的首选。不增加依赖，也不自动重试已启动的命令。

## Work items

- [x] 1.1 复用 AIW 命令审批、Worker 当前 Python 和进程清理能力，提供 Windows PowerShell 执行路径。
- [x] 1.2 明确 Worker 优先使用 Python 处理跨平台操作；PowerShell 用于 Windows 专有操作。
- [x] 1.3 编译并自查能力协商、命令审批、输出边界与进程限制。

## Acceptance

本机 Windows 只为声明 COMMAND 且 App Server 能力探测确认 `process/spawn` 支持的任务启动此适配器；可使用系统 PowerShell 与 Worker 当前 Python。命令通过 Codex App Server 的 `process/spawn` 无沙箱执行，每条 Windows 命令执行前须经 AIW 人工批准。超时或取消会清理整个进程树，并应用冻结的内存、进程数和输出上限。没有 Codex App Server 时明确报告执行器不可用；Linux/WSL 继续使用显式配置的 cgroup 与受限 `command/exec` 适配器。

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test` when requested and do not gate FD acceptance.
- The initial independent review and fixes are recorded in [review-20261115-r1.md](reviews/review-20261115-r1.md) and [implementation-r2.md](reports/implementation-r2.md). Their sandbox-required decision is superseded by this revision.
- The first unsandboxed implementation and its compile limitation are in [implementation-r3.md](reports/implementation-r3.md). Independent review requested changes in [review-20261115-r3.md](reviews/review-20261115-r3.md); this revision fixes all three findings.
- The latest Python compile-only result and offline Maven availability are recorded in [implementation-r4.md](reports/implementation-r4.md).
- Independent review of commits `44baffa5` and `14169c05` is recorded in [review-20261115-r4.md](reviews/review-20261115-r4.md): verification passed on static evidence. Tests, builds and live Windows App Server execution were not run; runtime behavior remains unverified.
- 用户确认：Python 优先用于跨平台通用操作，Windows 专有操作可使用 PowerShell；工作区外访问需要明确授权。
- Codex App Server `process/spawn` is an explicit API for running a host process without Codex sandboxing: [App Server process execution](https://learn.chatgpt.com/docs/app-server#process-execution).

**Completed:** 2026-11-14
