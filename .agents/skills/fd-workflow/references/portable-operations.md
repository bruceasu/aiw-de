# Numbered FD command lookup

Load only for command/path lookup. Stage rules live in the linked references;
this is not a second lifecycle or authorization source. Verify installed CLI
compatibility and required authority before use; do not invent flags.

## Repository layout

- Template: `docs/templates/TEMPLATE.md`.
- Fixed navigation: `docs/features/FEATURE_INDEX.md`; use `aiw fd list` for status.
- Active FD: `docs/features/FD-XXX/FD-XXX_SLUG.md`, with `index.md`, `reports/`, `reviews/`.
- Archive: `docs/features/archive/<FD-ID>/<FD-ID>_SLUG.md` and associated evidence.
- Receipts/workspace coordinates: `.ai/fd/<id>/`; isolated worktree: `.wt/<id>`.
- FD owns status/items. No second status store or shared status table; read legacy
  flat layout as compatibility input, use folders for new/reopened records.

## FD commands and events

| Operation | Command/event | Required procedure |
|---|---|---|
| New | `aiw fd new "title" [--issue ID] [--handoff] [--worktree]` | Current workspace by default; use --worktree for explicit auto/worktree requests in Git, before planning |
| Inspect | `aiw fd list`, `aiw fd show FD-001`, `aiw fd resume` | [handoffs](handoffs.md) |
| Claim | `aiw fd claim <id> <event-id> --session <session-id>` | [handoffs](handoffs.md) |
| Design handoff | `design-ready --producer planner --artifact <fd-path> --source-event <event-id>` | [planning](planning.md) |
| Implementation handoff | `implementation-ready --producer worker --artifact <report-path> --source-event <event-id>` | [implementation](implementation.md) |
| Review result | `verification-passed` / `changes-requested` with report and source event | [review](review.md) |
| Decision | `needs-decision`, later `decision-recorded` | Existing CLI/contract; never guess decision |
| Fresh review | `aiw fd request-review <id> --reason "..."` | [handoffs](handoffs.md) / [preflight](preflight.md) |
| Refresh Worker | `aiw fd refresh-worker <id> --reason "..."` | [handoffs](handoffs.md) |
| Reopen | `aiw fd reopen <id> --reason "..."` | [handoffs](handoffs.md) |
| Correct unclaimed reopen reason | `aiw fd reopen <id> --reason "..." --correct-reason` | [handoffs](handoffs.md) |
| Direct review | `aiw fd request-review <id> --reason "..."` | Ready Open/In Progress/Complete work can request review without a forced status change |
| Close | `aiw fd close FD-001 Complete` | [delivery](delivery.md); Closed/Deferred require reason |
| Explicit PM override | `aiw fd close <id> Complete --force --reason "..."` | Only user-chosen [preflight](preflight.md) exception |

## Git lookup

- `aiw git wt add FD-001`: use only for an explicit isolated existing-FD request;
  its plan must already be committed. New isolated FDs use new --worktree,
  which calls add --prepare before the plan exists. Outside Git, skip both.
  Read [preflight](preflight.md) and verify recorded coordinates.
- `aiw git wt status FD-001`: inspect workspaces.
- `aiw git wt local-merge FD-001`: after archive/commit inside the FD worktree,
  squash-deliver implementation and archive together; read [delivery](delivery.md).
- `aiw git wt sync FD-001 develop`: only if diagnosis requires a separate sync
  and `develop` is the recorded parent. Never proactively sync every FD.
- `wt commit` stages all changes; it is not conflict resolution. Use only after
  every changed file is resolved, intentional and in scope. Do not rebase either branch.
- Commit coherent reviewable changes; a small FD may use one implementation
  commit. Do not combine unrelated changes into any scoped commit.

`$fd-workflow auto` is a host request, not an `aiw fd auto` command.
Optional test support adds no Tester state/vote or coverage threshold; it never
waives actual acceptance defects or silently skips requested scope. Follow
[testing](testing.md). Local merge, archive and release are distinct operations.
