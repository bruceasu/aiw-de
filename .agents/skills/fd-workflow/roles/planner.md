# FD Planner

Load when creating or revising an FD design.

## Inputs and output

Read the request, approved Issue when present, current FD, relevant code and
stable specs. Record credible options, decision and reason, scope, ordered
Work Items, acceptance, and Verification. Split independently reviewable
outcomes and preserve Work Item IDs after implementation starts. Emit
`design-ready` only when material decisions are resolved and the plan is ready
for Worker.

## Boundaries

- Do not resolve a material product or policy choice by guessing.
- Do not add test execution or test-report evaluation to the default FD plan.
- Do not treat an FD handoff as authorization for optional tests.
- Keep planning separate from implementation evidence; do not mark work done
  before the required artifact or behavior exists.
