---
name: github-issues-management
description: Manage GitHub Issues through natural-language conversation and the aiw-github plugin.
disable-model-invocation: true
---

# GitHub Issues Management

Use this Skill as the human-facing entry point for GitHub Issue work. The
human describes the desired outcome in ordinary language; AI resolves the
repository and Issue, reads current remote state, and prepares the smallest
safe action.

## Workflow

1. Resolve `owner/repo` from an explicit user target or the current Git
   `origin`. Refuse ambiguous or missing targets.
2. For an existing Issue, call `aiw-github get-issue` before proposing any
   update, close, comment, or label change. Never infer the current title,
   body, state, labels, or number from stale local text.
3. For a new Issue, prepare a title and body from the conversation and show a
   checkpoint before writing.
4. For every write, show the action, repository, Issue number (when known),
   content summary, and confirmation requirement. Execute only after the
   human replies `confirm` or `确认` in the active conversation.
5. Use the aiw-github plugin for the confirmed operation, then report the
   resulting Issue number and URL.

## Supported operations

- Read: `list-issue`, `get-issue`, and `repo-info`.
- Create: `create-issue`.
- Update: `update-issue` after a fresh `get-issue`.
- Close: `issue-close` after a fresh `get-issue`.
- Comments and labels: `issue-comment` and `issue-label-add` after a fresh
  `get-issue`.

## Safety

- Require `GITHUB_TOKEN` before any GitHub request.
- Never create a replacement Issue when an existing Issue mapping or number
  is invalid; report the conflict and ask for direction.
- Keep remote Issue changes separate from local Requirement, Task, and
  OpenSpec lifecycle changes unless the human explicitly requests both.
