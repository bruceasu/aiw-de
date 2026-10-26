# FD planning and readiness

Load when creating or refining a design. The FD owns the plan. Read confirmed
intent, relevant approved Issue, nearby code and stable capability specs. Use
only the relevant section of skills/workflow-methods.md when it adds useful
splitting, specification or decision guidance.

## Write a proportionate plan

Select the workspace via preflight.md before authoring the plan. Explicit auto
inside Git starts the new FD in a worktree; ordinary requests use the current
workspace. Outside Git, skip worktree creation even for auto.

State the problem, scope, observable acceptance, necessary numbered Work Items,
planned verification and unresolved questions. Include the solution in the
repository template. Small changes need a short plan. Add alternatives,
interface details, failure/recovery behavior and compatibility analysis only
when a material decision needs them; do not fill sections with repeated prose.

Split work when it has separate outcomes or dependencies. Keep item IDs stable
after implementation starts; add new IDs and explicitly cancel superseded work
with reasons. Do not require size/difficulty labels, acceptance mapping tables
or a file inventory for every small item. Material business or engineering
unknowns use %% NEEDS_INPUT and block readiness; routine choices with clear
evidence do not need another confirmation.

For conflicting domain terms or states, use the relevant domain clarification
method. For a concrete module/interface decision, use codebase-design when
needed. Keep the result in the FD rather than adding another plan. Update
stable specs only when settled requirements change. Create an OpenSpec change
only on explicit request.

## Review and evidence

New routine FDs declare **Review policy:** Self and **Evidence policy:** Markdown.
Use Independent under the risk criteria in skills/work-management.md, or when
the user requests review. Reassess the actual diff before delivery. A short
reason in Verification is enough; no separate risk report is required.
Unmarked historical FDs and legacy Independent Tester FDs retain independent review.

Design review is optional additional work for an explicit request or a concrete
unresolved design question. Risk alone does not require both design and final
implementation reviews. If needed, use one bounded independent static agent;
keep findings in the FD or requested report and resolve material decisions
before implementation. Design feedback never counts as implementation verification.

No default tests or separate Worker report are added. Explicit test support
loads [testing](testing.md) and records scope, commands/revisions, authority and
finite budgets. Planned checks remain planned until actually performed.

## Continue

Respect design-only and other stage limits. In a single-host flow without
receipts, save a ready plan and set the FD Open only when required sections,
numbered items and material decisions are complete. This does not start work
outside the user's request. With a real Planner handoff, follow
[handoffs](handoffs.md) and emit design-ready from its claimed source event.
Do not create events merely to change responsibilities inside one host.
