# FD-126 implementation r3

Commit: `a259d630`

## Changes

Windows commands now use Codex App Server `process/spawn`, an explicit host-process API that runs without Codex sandboxing. Windows Restricted Token setup is no longer required. Existing AIW command-risk approval remains unchanged; PowerShell remains approval-gated. Windows Job Object cleanup, resource limits, timeout and output limits remain. Linux/WSL continues to use cgroups and sandboxed `command/exec`.

Factory startup diagnostics now report App Server availability without claiming that sandbox setup is mandatory.

## Verification

- Python syntax compile for `app_server_runtime.py`, `execution_guard.py` and `guarded_command.py`: passed.
- `git diff --check`: passed.
- Offline Maven compile was attempted with the repository settings, but `mvn` is unavailable on PATH and no Maven wrapper exists in the checkout. Java compile remains unverified after the diagnostic string edit.
- Tests and live Windows App Server execution were not run, per instruction.

## Remaining risk

The Windows `process/spawn` path was checked against the official App Server protocol but not executed locally. It runs without Codex filesystem/network sandboxing; the fixed command policy and existing AIW risk approvals still apply.
