---
name: resolving-merge-conflicts
description: "Resolve an in-progress merge/rebase conflict using the FD and both change histories; preserve state when a safe resolution is unclear."
---

# Resolve merge conflicts

Read `skills/reviewed-skill-contract.md`, `skills/work-management.md`, and the
repository rules. Use only for an actual in-progress merge/rebase conflict,
not for proactive parent sync or unrelated refactoring. Obtain the affected
workspace, current operation, FD or merge goal, both histories, and authorized
scope. Do not take over a foreign or in-flight role.

A full FD lifecycle may load this Skill automatically for one bounded recovery
attempt after failed delivery. A standalone invocation allows conflict edits
within its requested scope; commits, rebase continuation and delivery still
require the applicable lifecycle or explicit authorization. This Skill grants
no tests, builds, formatting, network access or broader Git cleanup authority.

1. Inspect local status, merge/rebase metadata, unmerged index, changed files
   and both histories. Read the FD and relevant local issues/specs to establish
   each side's intent. Do not fetch external PRs or issues without authority.
2. Resolve only hunks whose intent is supported by that evidence. Preserve both
   intended behaviors where possible. Do not invent behavior, choose a whole
   side blindly, restore an intentional deletion without evidence, or expand
   the FD scope. If a conflict needs a new requirement or high-risk decision,
   stop at that conflict.
3. If any resolution is uncertain or cannot safely be completed, report BLOCKED
   with resolved/remaining paths, evidence and the missing decision. Preserve
   the in-progress operation and remaining unmerged entries. Keep clearly
   resolved edits documented. Do not abort/reset, stage unresolved paths,
   commit a partial merge, or repeatedly try speculative resolutions.
4. Inspect the resolved diff, conflict markers and index within the repository
   validation budget. Run only authorized checks; report checks not run. Do
   not automatically run tests, type checks, formatters or final builds.
5. Only after all conflicts are intentionally resolved, stage the exact scoped
   paths and commit or continue the operation when authorized. `wt commit`
   stages everything, so use it only when all changes are resolved and in
   scope. Do not claim a commit alone proves semantic correctness. Do not
   automatically continue unrelated rebase commits beyond the assigned scope.
6. Report the operation, FD/source event when present, original and recovery
   commits, resolved paths, decisions, commands, skipped checks and residual
   risks. For FD recovery, write the supplemental evidence in the repository's
   required format and update blocker feedback. Return to the host PM; this
   Skill does not emit Reviewer events, merge to the parent, clean resources
   or archive. Implementation changes outside the current pass require fresh
   independent review through fd-workflow before delivery can resume.

Completion means every scoped conflict is resolved and inspected, the allowed
Git continuation is recorded, and the result is ready for the next lifecycle
gate. If blocked, preserved state and an explicit unresolved report are the
output; never describe that state as a completed merge.
