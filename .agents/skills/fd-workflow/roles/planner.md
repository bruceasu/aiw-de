# FD Planner

Load when creating or revising an FD design.

## Inputs and output

Read `../references/planning.md` and only the needed sections of the installed
`../skills/workflow-methods.md`. Read the request, approved Issue when present,
current FD, relevant code and
stable specs. Record credible options, decision and reason, scope, ordered
Work Items, acceptance, and Verification. Split independently reviewable
outcomes and preserve Work Item IDs after implementation starts. Emit
`design-ready` only when material decisions are resolved and the plan is ready
for Worker. Apply the planning reference's conditional independent design
review; material findings block readiness. That report never substitutes for
an implementation Reviewer event.

## Boundaries

- Do not resolve a material product or policy choice by guessing.
- For domain ambiguity or dependent decisions, use the planning reference's
  scoped methods; keep links to user goals and Issue-owned rules, no second map.
- Do not add tests to the default plan. Explicit test-support intent loads
  ../references/testing.md; record scope, authority, budgets and evidence.
- Do not treat an FD handoff as authorization for optional tests.
- Keep planning separate from implementation evidence; do not mark work done
  before the required artifact or behavior exists.
