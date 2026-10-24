---
name: prototype
description: Build a small runnable UI or logic prototype to answer an in-scope design question, as an optional Skill on an existing AI Worker.
---

# Prototype

Use when the assigned task asks for a prototype or permits one to resolve an
in-scope uncertainty. This is an added method, not a new Worker role. Keep the
primary method, frozen task, repository scope and result contract unchanged.
Do not start a prototype for unrelated work or treat it as production acceptance.

## Choose the useful form

State the question, intended user, observable result, smallest scope and finish
criterion. Use the supplied context to select the form:

- For state transitions, rules, API shape or data behavior, read
  [Logic prototype](references/logic.md).
- For layout, information hierarchy or interaction, read
  [UI prototype](references/ui.md).

Read only the relevant branch from the supplied Skill files. If a missing decision
changes the form or scope, use the existing question/gap path. Reuse known answers.

## Build and check within the task

1. Inspect nearby code, components and available tools before choosing an approach.
2. Put the smallest prototype in the authorized workspace and clearly label it as
   a prototype. Use the project's existing language, dependencies and conventions.
3. Use in-memory or stub data by default. Real persistence, services or external
   effects require explicit task scope and their existing permissions.
4. Give one concrete entry point or run command, expected interaction and reset/stop
   instructions. Run it only through an available authorized adapter. File creation
   needs write authority; preview, browser and commands need their own authority.
5. Record actual observations against the question. If a check was not run, provide
   the files and instructions and say what remains unverified. A missing write or
   execution permission is a gap, not proof that the prototype was made or tested.

## Deliver the answer

Use the primary task's existing report/output format. Include the question, artifact
paths, entry point, scenarios or variants, checks actually run, observed result,
recommendation and remaining limits. Provide a preview URL only when it is known;
do not invent a running server, screenshot, user choice or successful interaction.

Keep the prototype available through normal task delivery. Promotion, shared-library
changes, branch/worktree operations, deletion and release need their existing task
authority. Do not automatically merge a winner, install packages or run the host FD
workflow. Prototype evidence supports a design decision, not production readiness.
