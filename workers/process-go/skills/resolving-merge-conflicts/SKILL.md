---
name: resolving-merge-conflicts
description: Resolve evidence-backed parts of an actual in-progress merge or rebase while preserving both sides' intent and uncertain operation state.
---

# Resolving Merge Conflicts

Use only when the assigned task permits conflict repair and an actual in-progress
merge or rebase is confirmed. Do not start synchronization to create this state.
Keep the primary role, workspace, output contract, tools and write/Git authority.

## Establish the operation

Obtain the current operation type and identity, target/source revisions, common
base, unresolved paths and unrelated edits from supplied evidence or an authorized
adapter. Do not infer a merge solely from similar text or a stale handoff. If an
earlier Git operation has an unknown result, reconcile that exact operation first;
do not replay it or begin another one.

Read the task requirements, relevant callers, conflict hunks and the base and
both sides' changes. If base/history or operation state is unavailable, name the
missing evidence and preserve the workspace. Generic READ/WRITE access is not
permission to manipulate Git internals or bypass a missing operation adapter.

## Resolve by intent

1. Explain what each side tried to change and why. Check surrounding behavior,
   interfaces and related files; conflict markers alone do not show all effects.
2. For each hunk, identify compatible changes, a superseded change with clear
   evidence, or an unresolved semantic choice. Never select a whole side merely
   because it compiles or removes markers faster.
3. Apply only the resolution supported by requirements and both histories,
   within assigned write scope. Preserve unrelated user changes. If the correct
   behavior is unclear, leave that part unresolved and report the exact decision
   needed through the normal question/gap path.
4. Inspect the result for lost behavior, broken callers and remaining conflicts.
   State which conflict-resolution checks actually ran. Marker removal alone is
   not semantic correctness; static inspection is not a successful runtime check.
5. Stage or finish the existing operation only when all its conflicts are resolved,
   required checks are satisfied, and the task and available Git adapter explicitly
   allow that step. If continuation is unavailable, report resolved file changes
   and the pending operation separately; do not claim the merge/rebase finished.

## Preserve and hand back

Use the primary result fields for the operation identity/revisions, affected files,
each side's intent, resolution choices, remaining conflicts, actual checks and
next permitted step. Completion means either the authorized operation is confirmed
finished, or its precise partial state and blocker are reported without losing
work. File edits alone do not prove a Git operation or commit succeeded.

Do not reset, abort, force-push, delete Git state, stage an unresolved result,
commit a partial conflict set, or continue unrelated rebase steps automatically.
Tests, commands, commits and delivery retain their existing permissions. When
the required adapter or evidence is missing, preserve the current state rather
than issuing a raw shell workaround or inventing a successful resolution.
