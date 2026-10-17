---
name: to-spec
description: Write or update stable capability rules and scenarios from confirmed Issue or FD decisions; maintain an OpenSpec change only when explicitly requested.
disable-model-invocation: true
---

# To Spec

Read `skills/reviewed-skill-contract.md`, `skills/work-management.md`, and only
Specification writing in `skills/workflow-methods.md`. Use for independent
spec maintenance requests; Planner uses the same method within fd-workflow.
Do not implement code, split engineering work, dispatch roles or change FD
state. A spec-only request does not require an FD implementation lifecycle.

Resolve the relevant capability, confirmed Issue/FD decisions and requested
write scope. Apply the method's requirements/scenarios and proposed/current
status rules. Return changed rules and affected FD items to Planner rather
than silently changing the plan. A missing decision remains BLOCKED with
`%% NEEDS_INPUT`; do not infer it from a template.

Complete with coherent scoped specs, changed paths, static evidence and any
remaining gates. Standalone use does not authorize tests, commits, merge,
archive or publication. The owning full FD lifecycle may commit its scoped
spec changes under the shared contract. Never create an OpenSpec change merely
because a stable spec changes; require an explicit request for that workflow.
