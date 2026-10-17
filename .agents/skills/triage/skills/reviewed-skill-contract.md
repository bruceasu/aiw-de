# Reviewed Skill Contract

All reviewed Skills MUST document their trigger and non-trigger conditions,
required inputs, outputs, completion criteria, unresolved-input behavior, and
verification scope.

Use `%% NEEDS_INPUT: <question or missing evidence>` for unknowns. Use an
explicit `BLOCKED` or `INCOMPLETE` status when an unknown prevents a valid
result. Never guess missing evidence or claim skipped sibling work completed.

Unless explicitly authorized by the Skill contract, a Skill is read-only: it
does not modify files, change FD records or OpenSpec changes, create
branches or worktrees, commit, publish externally, or run runtime validation.

## FD-first vocabulary

For new work, the numbered FD owns decisions, Work Items, status, and
Verification. `.ai/fd/<id>/` stores role handoff receipts and logs, not a
second authored plan or status owner. Roles are PM, Planner, Worker, Reviewer/Verifier, and the
human decision maker. A role must hand off an existing artifact and actual
evidence. Do not invent a review or rerun an unknown dispatched event.

| Feature Design | FD author | Engineering decisions and human-authored work items. |
| Specification | OpenSpec | Stable capability requirements and optional change artifacts. |

Skills report changed artifacts, proposed Evidence, unresolved Gates, and a
recommended next action. They MUST NOT fabricate role receipts or outcomes,
advance FD state outside their assigned role, or silently rewrite confirmed
Issue scope. Methods loaded by an active entry inherit its scope and limits;
loading a reference does not invoke another Skill or grant extra operations.

FD workspace, commit, archive, merge, and cleanup rules belong to
`skills/work-management.md`; reviewed Skills must
reference that contract instead of redefining lifecycle rules. External
publication requires an explicit user request.

Static evidence and commands actually run are reportable. Runtime tests,
builds, formatters, linters, validators, broad reviews, and sibling Skills are
opt-in; skipped work must be stated as skipped.
