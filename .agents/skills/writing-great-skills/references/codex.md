# Codex Adaptation

Read only for a Codex target or an explicit cross-platform comparison.
[Sources](sources.md) records the guidance used and its limits.

- Keep required `name` and `description` in SKILL.md. Describe the capability,
  useful activation cases, and material boundaries concisely. Do not remove
  description to implement explicit-only invocation.
- Codex supports `agents/openai.yaml` for UI metadata and invocation policy.
  `policy.allow_implicit_invocation: false` disables implicit selection while
  keeping explicit invocation available. Preserve an existing policy unless
  the user requests a change. Other frontmatter fields are host-specific;
  do not assume they replace this policy or govern every host.
- Treat discoverability, user invocation, and declared workflow loading as
  different mechanisms. Do not infer that a skill can never be loaded by a
  workflow merely because implicit selection is disabled.
- Follow the target repository's installed skill layout. Keep references
  relative and packaged with the skill or explicitly provided by its installer.
  Do not depend on a particular user's system skill directory.
- If the supplied catalog provides skill-creator, use its relevant authoring
  or metadata guidance when helpful. This skill still works without it.
  Avoid loading two complete copies of overlapping advice.
- A native initializer, validator, or UI generator is an executable helper,
  not an automatically permitted step. Inspect its scope and follow the
  repository's execution budget before running it. Preserve unrelated fields
  when editing metadata; do not regenerate a file just to change one value.

Host behavior can change. Recheck current official guidance when changing
host-specific semantics; mark unsupported or unverified claims explicitly.
