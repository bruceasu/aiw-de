---
name: resume-ext
description: List local Codex sessions for the current workspace as numbered options and prepare an interactive resume command. Use when the user wants to find, preview, choose, switch to, continue, or resume another Codex session without starting a nested Codex process.
---

# Resume Ext

Follow `skills/reviewed-skill-contract.md` and `skills/work-management.md`.
This Skill selects and reports a resumable Session only; it does not switch
Tasks, seize leases, or mutate worktrees automatically.

Use `aiw cxs` as the source of truth. Keep all prompts and option labels in Easy
English.

## Select a Session

1. Run:

   ```text
   aiw cxs list --current-workspace --json -n 20
   ```

2. Parse the JSON. Show a compact numbered list with alias, title, updated time,
   and `original_cwd`. Use `[no alias]` and `[unknown workspace]` when needed.
3. Ask the user to choose a number or alias. Offer `all` to rerun without
   `--current-workspace`, and `refresh` to rerun the same command.
4. Resolve the choice only from the most recently displayed result. If it is
   missing or ambiguous, show the options again.

## Prepare the Handoff

After selection, show:

```text
Session: <alias-or-session-id>
Directory: <original_cwd>
Command: codex resume <session-id>
```

If `original_cwd` is unknown or missing, explain that interactive resume cannot
be prepared safely.

Never execute `codex resume` from an active Codex session. Never start nested
Codex. Give the user a copyable command so they can run it in a terminal.
