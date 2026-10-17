# Sources and Maintenance

Read when checking provenance, revising platform guidance, or reconciling an
upstream change. Routine authoring does not require a fetch or download.

## Inputs used for this revision

Reviewed in this conversation on 2026-10-17 (Asia/Tokyo):

- Existing AIW writing-great-skills and GLOSSARY at repository baseline
  `8cbec0a`: retained conditional references, ownership, and completion criteria;
  revised the primacy of predictability and absolute invocation/prompting claims.
- The environment-supplied Codex skill-creator and its UI metadata reference:
  adopted concise, risk-proportionate guidance, progressive disclosure, and
  preservation of user scope. It is a source, not a runtime filesystem dependency.
- [Official OpenAI Build skills](https://learn.chatgpt.com/docs/build-skills):
  used required metadata, progressive loading, and Codex invocation policy.
- [Official Anthropic skill-creator](https://github.com/anthropics/skills/blob/main/skills/skill-creator/SKILL.md):
  adopted representative examples, output feedback, explicit baselines, and
  optional evaluation as methods, not its automatic execution sequence.

Upstream commits and the supplied Codex package version were not pinned in
this session. These are dated observations, not a claim of permanent host
behavior or an exact upstream snapshot. No upstream scripts or full skill
files are vendored by this revision.

## Maintain deliberately

Keep general authoring principles here independent of host metadata. Before
changing platform semantics, check an authoritative current source when
network access is permitted and record the retrieved revision or date.
If unavailable, preserve known behavior and mark the unresolved claim.

An upstream update is review input, not permission to overwrite local rules.
Adopt only relevant changes and document the reason. Before redistributing
upstream code or files, inspect their license, attribution, dependencies,
and side effects. Do not import a complete evaluation runtime just to borrow
an authoring principle, and never fetch upstream content during routine use.
