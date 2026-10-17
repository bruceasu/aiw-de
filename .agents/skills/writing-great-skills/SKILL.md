---
name: writing-great-skills
description: Create, improve, review, or adapt Agent Skills with clear scope, usable instructions, platform compatibility, and honest evidence of effectiveness.
disable-model-invocation: true
---

# Writing Great Skills

A Skill is reusable guidance for a defined class of tasks. It helps an agent
recognize when it applies, choose the necessary methods and resources, work
within authorization, and produce results that can be assessed. Judge it by
task quality, appropriate cost, and maintained boundaries. Predictability is
useful when it supports those outcomes; exploration may need different paths.

Use this Skill to create, change, review, or adapt skill instructions and their
supporting files. A general task request does not require creating a Skill.
For installing an existing skill or changing unrelated application code, use
the relevant workflow instead.

For AIW-managed engineering edits, follow [work management](skills/work-management.md).
The [reviewed-skill contract](skills/reviewed-skill-contract.md) defines shared
artifact and evidence boundaries. These contracts do not replace the target
repository's instructions or grant additional execution rights.

## Inputs and modes

Start from the requested outcome, existing skill when present, target host(s),
representative user requests, known failures, authorized write scope, and
execution budget. Reuse supplied evidence; ask only when a missing answer
changes behavior or compatibility. Use `%% NEEDS_INPUT` for unresolved facts.
A host-neutral draft can proceed while platform-specific decisions stay open.

Choose the requested mode:

- **Create:** produce a focused skill and only the resources it needs.
- **Improve:** inspect the current instructions and a demonstrated problem;
  make the smallest coherent change, preserving unrelated behavior.
- **Review:** report source-backed findings and limits; remain read-only unless
  the user also requested fixes.
- **Adapt:** preserve task semantics while changing only host-specific details;
  record unsupported features rather than silently dropping them.

A creation or editing request authorizes skill-file changes within its scope,
subject to repository workflow. It does not authorize external publication,
installation elsewhere, Git operations, downloads, or running helper scripts.

## Workflow

Use only the steps needed for the mode. A narrow edit need not restart discovery.

1. **Define the task contract.** Identify useful outcomes, activation and
   exclusion cases, inputs, outputs, decision boundaries, and completion.
   Resolve material conflicts or name the missing decision before encoding it.
   Done when each proposed behavior has a stated purpose or evidence gap.
2. **Design the guidance.** Apply [authoring principles](references/authoring-principles.md).
   Keep essential decisions in the entrypoint and load branch-specific material
   through explicit links. Retain existing names and interfaces unless change
   is requested. Done when each instruction or resource has a clear owner and use.
3. **Draft, revise, or review.** Write concrete instructions and examples that
   affect decisions. For host-specific work, read only the relevant
   [Codex](references/codex.md) or [Claude](references/claude.md) reference;
   read both only for a cross-platform request. A supplied native creator may
   help with platform details when available and permitted, but this workflow
   must remain usable without it or a machine-specific path.
   Done when the requested files or actionable review findings exist.
4. **Check and report.** Inspect scope, required metadata, referenced files,
   instruction consistency, authorization, and unresolved placeholders.
   Read [evaluation guidance](references/evaluation.md) only when behavior or
   quality needs evaluation. Execute checks only under the host's authorization
   and budget. Done when the result, actual evidence, skipped checks, and
   remaining uncertainty are explicit.

Use the [glossary](GLOSSARY.md) only for unclear terminology. Consult
[sources and maintenance](references/sources.md) when reconciling upstream
advice, updating platform rules, or recording adopted material; routine use
requires no upstream fetch.

## Completion and output

Return the changed files or review findings, the problem addressed, preserved
contracts, actual checks and observations, and residual risks. For an
unresolved material requirement, report `INCOMPLETE` or `BLOCKED` and the exact
`%% NEEDS_INPUT` question; finish independent work that does not depend on it.

Separate structural correctness, observed behavior, and comparative task
quality. Static inspection supports a structural judgement, not a claim of
better trigger accuracy or output quality. Record unrun evaluation as not run.
Do not infer publication, approval, installation, or effect from a file edit.
