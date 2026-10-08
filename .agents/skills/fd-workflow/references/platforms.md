# Platform compatibility

## Common recommendation

Install the repository-level skill at:

```text
.agents/skills/fd-workflow/SKILL.md
```

This is the preferred shared source tree for Codex-style agent skills and is also supported by GitHub Copilot CLI.

Keep repository-wide durable project instructions in:

```text
AGENTS.md
```

Both CLIs can use this convention, so FD lifecycle rules do not need to be duplicated in `CODEX.md` or vendor-specific files.

## OpenAI Codex CLI

- Treat `.agents/skills/<name>/SKILL.md` as the repository-level portable skill location.
- Invoke explicitly with the runtime's skill invocation syntax when desired, or rely on the skill description for automatic matching.
- Do not encode a model name or reasoning effort in the skill. Model/effort are runtime/session concerns.
- Do not depend on Claude `allowed-tools`, `Task`, `Glob`, `Grep`, or `Read` names. Express required capabilities semantically: read files, search text, inspect git, edit files, run commands, delegate if available.
- `AGENTS.md` is the canonical repository instruction file.

Suggested user prompts:

```text
$fd-workflow init
$fd-workflow new Add export pagination
$fd-workflow status
$fd-workflow deep Investigate why export memory grows with large datasets
$fd-workflow verify FD-012
$fd-workflow request-review FD-012 Because something is changed.
$fd-workflow close FD-012 complete
```

If explicit `$name` invocation is unavailable in a particular frontend/version, use natural language: `Use the fd-workflow skill to ...`.

## GitHub Copilot CLI

Copilot CLI supports repository skills under `.github/skills`, `.claude/skills`, or `.agents/skills`; `.agents/skills` is preferred here because it can be shared with Codex.

Copilot CLI also reads `AGENTS.md` as repository instructions. It can use custom agents and subagents, but this skill must not require them. Delegate when useful and available; otherwise execute sequentially.

Suggested user prompts:

```text
Use the fd-workflow skill to initialize FD tracking.
Use fd-workflow to create a new FD for export pagination.
Use fd-workflow to show status.
Use fd-workflow to deeply analyze the export memory problem.
Use fd-workflow to verify FD-012.
Use fd-workflow request-review FD-012 because something is changed
Use fd-workflow to close FD-012 as complete.
```

Avoid editing the same workflow independently in both locations. If compatibility commands are kept, make them thin wrappers that instruct the agent to use `fd-workflow` with the corresponding operation.
