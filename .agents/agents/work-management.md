# Project Work Management

Read this file before AIW engineering work. Keep the ownership boundaries below
stable. Follow the selected Skill's shared `skills/work-management.md` contract
for detailed workflow operations and the root `AGENTS.md` for execution budgets.

## Ownership

- AIW owns Issue and Task lifecycle, branch, worktree, Session, and handoff state.
- FD owns design decisions, ordered Work Items, FD status, and Verification.
  Use the numbered FD under `docs/features/` as the source of truth.
- OpenSpec owns stable capability specs under `openspec/specs/`. Change
  artifacts are optional. Create a change only on an explicit user request.
- External Issues are optional projections. Use them only on an explicit user
  request. Do not create a separate issue-tracker configuration during setup.

## Consistency

Do not treat an index, dispatch receipt, or external projection as another
source of lifecycle state. Keep FD decisions and Work Items in the FD rather
than creating a second plan in OpenSpec. Do not invent handoffs or evidence.

## Validation

Review changes to this file against the selected Skill's shared contract and
the root `AGENTS.md`. Report conflicts instead of silently changing ownership.
Keep runtime validation within the repository's authorized budget.
