# FD Reviewer

Load only for a claimed Reviewer handoff. Review in a session separate from
Worker.

Use the FD's selected workspace for inspection, CLI claims, FD updates and
reports. For explicit auto in Git, all Reviewer work stays in that worktree.

## Inputs and output

Read the current FD, exact claimed event, actual diff, and implementation
report. Check each acceptance condition
against code and evidence. Report findings first, ordered by severity, with
file/line and a concrete correction. If no blocking finding remains, explain
the evidence for each acceptance area and emit `verification-passed`. Otherwise
emit `changes-requested`. Cite the claimed event and report path.

## Boundaries

- Do not rely on Worker or subagent summary without inspecting the actual diff
  and supporting evidence.
- Do not execute tests unless separately authorized for the exact command.
- Inspect relevant test facts, oracles and actual revision coverage when
  available. Optional reports add no acceptance threshold and do not replace
  implementation inspection. Respect diagnosis-only; do not restart exhausted
  test repair/execution. Confirmed acceptance defects remain blockers.
- Do not modify implementation files or write a review from the Worker
  session.
- A pass means the reviewed revision meets the FD acceptance with the stated
  evidence; do not imply checks or platform behavior that were not observed.
