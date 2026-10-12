---
name: setup-project
description: Prepare a repository's AIW engineering conventions and agent instructions.
disable-model-invocation: true
---

# Setup Project

Follow `skills/reviewed-skill-contract.md` and `skills/work-management.md` when
present. Resolve repository-relative paths from the repository root.

## Trigger and boundaries

Use only when the user explicitly invokes this Skill or requests repository
setup for AIW engineering work. Do not infer setup authorization from requests
to inspect a repository, use another Skill, create an Issue or FD, or discuss
workflow conventions.

This Skill configures local project documentation and agent instructions. It
does not create Issues, Tasks, FDs, OpenSpec changes, external tracker
configuration, branches, worktrees, commits, or run tests. Create an OpenSpec
change only on a separate, explicit request.

## Inputs and inspection

Required input is the target repository. Inspect its existing `AGENTS.md`, AIW
markers and available CLI help, `openspec/changes/` and
`openspec/specs/`, `.agents/agents/`, `CONTEXT.md`, `CONTEXT-MAP.md`, ADR
directories, installed triage Skill, and clear monorepo boundaries. Treat
`.scratch` as legacy data. Read `skills/work-management.md` once when present.

Use repository evidence for existing conventions. Do not guess domain facts,
tracker choice, monorepo boundaries, or user preferences. Record missing facts
as `%% NEEDS_INPUT: <question or missing evidence>` in the proposal; ask only
for a decision that changes the proposed setup.

## Prepare and approve the proposal

Before writing any project files, present one concise proposal that names each
file to create or change, summarizes its exact intended content, and identifies
any unresolved choice. Include the proposed `## Agent skills` block. Wait for
the user's confirmation before applying these project configuration changes.
If no `AGENTS.md` exists, include creating it in the proposal. If a legacy
`CODEX.md` exists, treat it as migration input and move relevant rules into
`AGENTS.md` rather than keeping parallel instruction files.

Propose the AIW ownership contract in `.agents/agents/work-management.md`:

- AIW owns Issue and Task lifecycle, branch, worktree, Session, and handoff
  state.
- FD owns design decisions and ordered work items.
- OpenSpec owns stable capability specs; change artifacts are optional.
- External Issues are optional projections used only on explicit request.

Do not create a separate issue-tracker configuration for an AIW/OpenSpec
repository. If triage is installed, ask once whether to keep the default role
labels (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`,
`wontfix`). Propose `.agents/agents/triage-labels.md` only if the answer or
existing project conventions require it. Use root `CONTEXT.md` and `docs/adr/`
by default; propose a multi-context layout only when repository evidence shows
clear monorepo boundaries.

The Agent skills block points to `.agents/agents/work-management.md` and
`.agents/agents/domain.md` only when those files exist or are included in the
approved proposal. Point to `.agents/agents/triage-labels.md` only when triage is
configured. Preserve surrounding instructions and update a single existing
`## Agent skills` block instead of adding duplicates.

## Apply and finish

After confirmation, apply only the approved file changes. Preserve unrelated
user content. Do not overwrite existing project documentation wholesale; make
focused edits and leave unsupported domain details unresolved. If the user
changes scope, update the proposal and get confirmation for the changed files
before writing them.

Report each file actually changed, the resulting ownership split, evidence
inspected, and any unresolved setup decisions. State checks actually run and
checks skipped. Do not claim successful installation or validation without
evidence.

Setup is complete when every approved file change is applied, agent
instructions point only to available or approved documents, no unrelated
content was replaced, and the report accurately records remaining unknowns and
checks.
