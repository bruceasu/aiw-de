# Claude Adaptation

Read only for a Claude target or an explicit cross-platform comparison.
[Sources](sources.md) identifies the official creator consulted.

Use the common SKILL.md name, description, task instructions, and supporting
resources. Check the actual Claude environment's documented frontmatter and
invocation controls before changing platform-specific behavior. Do not assume
Codex UI metadata, policy keys, filesystem locations, or tools apply here.
Preserve known metadata when its replacement has not been established.

The official Claude creator's useful pattern is to clarify representative
requests, draft the skill, examine outputs and feedback, then improve the
specific behavior. Adopt that reasoning without requiring its entire runtime
or every optimization step for a narrow edit.

If a native skill-creator is available in the supplied environment, it may help
with platform details within the authorized task. If absent, use this skill's
workflow; do not download it or guess an installed absolute path.

For a request to assess effectiveness, use [evaluation](evaluation.md): compare
representative tasks and state the baseline. The upstream creator includes
subagent comparisons, viewers, repeated runs, and description optimization.
These are optional execution methods, subject to local budget and capability,
not prerequisites for every edit. If unavailable or unauthorized, report the
missing evidence instead of simulating an independent comparison.

Keep output and delivery suited to the user's environment. Packaging, browser
viewers, installations, and external actions require their own scope and
permission; authoring a skill does not authorize them.
