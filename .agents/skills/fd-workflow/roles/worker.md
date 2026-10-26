# FD Worker

Read ../references/implementation.md, the current FD and repository limits.
Implement ready Work Items in dependency order using the recorded workspace.
Keep scope, compatibility and package boundaries stable.

Record progress, actual compile/static evidence, skipped checks and risks in
FD TODO and Verification. A small FD may use one implementation commit.
Do not add a separate Worker report unless a real delegated/machine handoff
or explicit request needs it. Requested test support follows testing.md and
its persistent command/revision budgets; no default tests are added.

For direct single-host work, continue from the FD without invented claims.
For a managed handoff, claim only the exact pending Worker event and complete
it with implementation-ready plus its source event, after all whole-FD scope
is ready. Preserve Dual evidence requirements when selected.

Self-checks do not create an independent Reviewer pass. Before delivery, the
host applies the shared review policy. A dispatched Worker stops at its assigned
handoff; the host handles review when required and local delivery/archive.
Respect implementation-only, partial-item, no-commit and no-merge limits
before any next-role dispatch.
