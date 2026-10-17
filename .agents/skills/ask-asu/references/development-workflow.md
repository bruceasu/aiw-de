# AIW development workflow routing

| Stage | Role or Skill | Handoff |
| --- | --- | --- |
| Discover a problem | `issue-management` | Approved Issue or clear direct FD request |
| Create a numbered FD | `fd-workflow`, `aiw fd new "title" [--issue ID]` | `design-requested` |
| Design | Planner, `fd-workflow` | `design-ready` with FD path |
| Implement | Worker stage of `fd-workflow`, its `references/implementation.md` | `implementation-ready` with implementation report |
| Review | Independent Reviewer, `fd-review` | `changes-requested` or `verification-passed` with report |
| Local completion | Host PM through `fd-workflow` | Reviewed local delivery/archive under the full FD request; honor stage limits; push/release remain separate |

Inspect `aiw fd --help` for exact syntax. `aiw fd list` and `show` are
read-only; `new`, `emit`, `resume`, `close`, and `worktree add` can change state.
`AIW_FD_ROLE_RUNNER` can name an executable that starts each role. Without a
runner, events remain pending and a Skill in the current host can continue.

One numbered FD owns its authored progress. `.ai/fd/<id>/` holds event receipts
and runner logs. `aiw fd resume` never starts a second writer for an event
already marked dispatched or launching. The numbered FD owns engineering
planning and execution; do not introduce a separate ticket-mapping stage.

OpenSpec stable specs remain relevant; a change directory is created only on
explicit request.

## Navigation versus consultation

This table recommends workflow actions; Ask Asu does not execute them. Optional
consultation follows [consultation.md](consultation.md), with separate authority
and one bounded channel. Consultation creates no role event and does not stand
in for an independent Reviewer or a human decision. Unknown in-flight work is
reconciled from existing receipts/logs, not retried via another model.
