---
name: publish-github-issue
description: Publish one OpenSpec change as a managed GitHub Issue projection when the user explicitly requests it.
disable-model-invocation: true
---

# Publish OpenSpec to GitHub Issue

External publication is explicit opt-in. Follow
`skills/reviewed-skill-contract.md` and `skills/work-management.md`; publication
failure must not mutate Task status, archive, merge, or clean up resources.

Use this Skill only when the user explicitly asks to publish or update an
OpenSpec change on GitHub Issues. Normal planning and implementation remain
local.

Read `skills/work-management.md` and resolve exactly one AIW Task and matching
OpenSpec change before writing. If the session/worktree does not resolve one
uniquely, stop and ask for the Task ID.

## Process

1. Read the resolved change's `proposal.md`, `design.md` when present,
   capability specs, `tasks.md`, and `notes.md` when relevant. Do not treat an
   existing `.scratch` file or a remote Issue as the local source of truth.
2. Resolve the GitHub repository from the user's explicit `owner/repo` argument
   or the current Git `origin` using `aiw-github`. Require `GITHUB_TOKEN` before
   attempting a request.
3. Use `skills/publish-github-issue/scripts/projection.py` (or equivalent
   behavior) to render a bounded Markdown projection with these managed markers:

   ```markdown
   <!-- aiw:openspec:start -->
   ...generated OpenSpec summary...
   <!-- aiw:openspec:end -->
   ```

   Include the change ID, Goal, Scope, key requirement summaries, task progress,
   and a statement that AIW owns lifecycle state while OpenSpec owns requirement
   and checklist content. Keep detailed design notes and temporary findings
   local unless the user asks for them.
4. Read `openspec/changes/<change-id>/external/github.json` when it exists.
   It MUST be a versioned JSON object containing `version`, `repository`,
   `issue_number`, and `url`. If it is malformed or the mapped Issue cannot be
   validated, stop and report the problem; never create a replacement silently.
5. Without a mapping, call `aiw github --json create-issue` with the rendered
   title and a body file, then save the returned repository, Issue number, URL,
   and publication timestamp/content hash to `external/github.json`.
6. With a valid mapping, call `aiw github --json get-issue` first, use the
   projection helper to replace only the managed marker block in the fetched
   body, preserve all content outside the markers, and call
   `aiw github --json update-issue --body-file`.
7. Report the Issue URL and local mapping path. Do not close the Issue, update
   local task status, or import remote comments unless the user separately asks.

## Projection rules

- AIW is authoritative for lifecycle status.
- The approved Issue and FD own scope and planned work; OpenSpec owns stable capability specs when applicable.
- Publication is one-way and explicit.
- The mapping lives at `external/github.json`, not in `task.toml`.
- Use `--body-file` or stdin for Markdown bodies; do not pass a large generated
  document as a shell-quoted argument.
- Preserve human-authored content outside the managed marker block.
