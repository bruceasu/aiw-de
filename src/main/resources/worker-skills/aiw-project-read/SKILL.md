---
name: aiw-project-read
description: Read AIW Issue records or list available AIW Skills using read-only task commands in a configured project workspace.
---

# Aiw Project Read

Use this Skill for a supplied Issue ID or to inspect the installed AIW tool's available Skill catalogue.

Request a task-scoped command for aiw issue show ISSUE-00001 --json, substituting the Issue ID, or aiw pkg skill list --json.

Run only the declared command through run_command.

Report the Issue ID, status and approval status, or the matching Skill metadata, from the JSON response.

If AIW is missing or the project cannot be identified, report the error.

Keep worktree changes, Skill installation and lifecycle transitions outside this read-only task.
