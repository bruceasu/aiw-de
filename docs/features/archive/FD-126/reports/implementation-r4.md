# FD-126 implementation r4

Commit: `44baffa5`

## Changes

- Windows commands use Codex App Server `process/spawn` without Codex sandboxing. AIW now requires an explicit approval for every Windows command, including legacy risk strategy version 0, so host filesystem and network access cannot happen through an auto-approved command.
- Startup schema discovery verifies the actual `ProcessSpawnParams` fields on Windows before advertising command capability. Linux/WSL retains the `CommandExecParams` probe and sandboxed path.
- The missing-executable message now explains that Windows command execution requires Codex App Server; it no longer describes sandbox isolation as the reason.

## Verification

- Python syntax compile for `app_server_runtime.py`, `execution_guard.py`, `guarded_command.py`, and `guarded_preflight.py`: passed.
- `git diff --check`: passed.
- Maven compile was attempted but could not start because `mvn` is unavailable on PATH; the checkout has no Maven wrapper. The Java diagnostic string edit remains uncompiled.
- Tests and live Windows App Server execution were not run, per instruction.

## Remaining risk

Windows `process/spawn` field support and runtime cleanup remain statically reviewed but not exercised against the installed App Server on Windows.
