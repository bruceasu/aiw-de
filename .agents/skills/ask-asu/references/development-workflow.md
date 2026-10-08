# AIW development workflow routing

| Stage | Role or Skill | Handoff |
| --- | --- | --- |
| Discover a problem | `issue-management` | Approved Issue or clear direct FD request |
| Create a numbered FD | `fd-workflow`, `aiw fd new "title" [--issue ID]` | `design-requested` |
| Design | Planner, `fd-workflow` | `design-ready` with FD path |
| Implement | Worker, `implement` | `implementation-ready` with implementation report |
| Review | Independent Reviewer, `fd-review` | `changes-requested` or `verification-passed` with report |
| Decide delivery | Human or PM | Explicit commit, merge, push, release, or archive action |

Inspect `aiw fd --help` for exact syntax. `aiw fd list` and `show` are
read-only; `new`, `emit`, `resume`, `close`, and `worktree add` can change state.
`AIW_FD_ROLE_RUNNER` can name an executable that starts each role. Without a
runner, events remain pending and a Skill in the current host can continue.

One numbered FD owns its authored progress. `.ai/fd/<id>/` holds event receipts
and runner logs. `aiw fd resume` never starts a second writer for an event
already marked dispatched or launching. New work does not need AIW Task or
Workflow Core.

OpenSpec stable specs remain relevant; a change directory is created only on
explicit request.
