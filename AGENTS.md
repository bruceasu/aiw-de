Always respond in Chinese.

# AGENTS.md

## Purpose

This is the root rule file for mixed-language repositories.
Keep the active instruction set and execution budget small.

## Load Order

1. Apply this file.
2. Prefer the nearest local `AGENTS.md` or `CODEX.md`.
3. Load `.agents/prompts/core/resource-budget.md`,
   `.agents/prompts/core/universal-principles.md`,
   `.agents/prompts/core/validation.md`, and `.agents/prompts/core/communication.md`.
4. Add at most one repo-type prompt, one domain prompt, and one task-mode prompt.
5. Use `project-local > domain > language > repo-type > root` precedence.

Do not load the whole prompt library.

## Resource Guard

- Static analysis and editing are the default.
- Tests, final-artifact builds, formatters, linters, type checks, verification
  scripts, network calls, permission probes, privilege escalation,
  `codex-auto-review` has a default budget of zero.
- After implementation, run one compile-only check: prefer `scripts/compile*`
  or root `compile*`, otherwise use the narrowest language-level compiler
  command. The command must not retain a final distributable artifact.
- Implementation does not imply authorization to run the restricted commands.
- Use no more than three targeted discovery batches before editing unless a
  concrete blocker remains.
- After editing, use at most one static/read-only validation command by default.
- Do not repeat unchanged failed commands; a compile-only check may be retried
  once after a relevant source fix.

Follow `.agents/prompts/core/resource-budget.md` and `.agents/prompts/core/validation.md` for
authorization and retry rules.

## Working Rules

- Plan first for non-trivial work.
- Inspect the nearest code, tests, config, and docs.
- Expand only when current evidence is insufficient.
- Change only what the task needs.
- Preserve current contracts unless explicitly changed.
- Keep diffs small, local, and reviewable.
- Update nearby docs when behavior or workflow changes.

## UI Style

- For UI work, treat the existing Web Console visual style as a required contract.
- Inspect nearby pages and shared styles first; reuse their colors, typography,
  spacing, components, and interaction patterns. Any game or technology motif
  must fit that style rather than introduce a separate one.

## Stop And Ask

Pause when:

- local instructions conflict;
- external behavior is unclear;
- designs have materially different blast radius;
- migration, rollout, or compatibility policy is missing;
- runtime evidence is decisive but not already authorized.

## High-Risk Areas

Call out risk before changing dependencies, auth, permissions, billing, schema,
migrations, persistence, public APIs, events, CLI contracts, CI/CD, deployment,
concurrency, retries, timeouts, or shutdown behavior.

## Prompt Routing

- Mixed-language repository: `.agents/prompts/repo-types/monorepo.md`
- Python: `python/AGENTS.md` or `python/CODEX.md`
- Java: `java/AGENTS.md` or `java/CODEX.md`
- Go: `go/AGENTS.md` or `go/CODEX.md`
- Prompt changes: `.agents/prompts/domains/prompt-authoring.md`
- Task mode: one matching file under `.agents/prompts/task-modes/`

## Final Report

Include the change, reason, static evidence, commands actually run, checks not
run, residual risks, and optional focused checks that require authorization.

<!-- aiw-prompts:java:agents begin -->
Always respond in Chinese.
Write prompts in Easy English when asked to draft prompts.

# AGENTS.md

## Purpose

This is the root rule file for mixed-language repositories.
Keep the active instruction set and execution budget small.

## Load Order

1. Apply this file.
2. Prefer the nearest local `AGENTS.md` or `CODEX.md`.
3. Load `.agents/prompts/core/resource-budget.md`,
   `.agents/prompts/core/universal-principles.md`,
   `.agents/prompts/core/validation.md`, and `.agents/prompts/core/communication.md`.
4. Add at most one repo-type prompt, one domain prompt, and one task-mode prompt.
5. Use `project-local > domain > language > repo-type > root` precedence.

Do not load the whole prompt library.

## Resource Guard

- Static analysis and editing are the default.
- Tests, final-artifact builds, formatters, linters, type checks, verification
  scripts, network calls, permission probes, privilege escalation,
  `codex-auto-review` has a default budget of zero.
- After implementation, run one compile-only check: prefer `scripts/compile*`
  or root `compile*`, otherwise use the narrowest language-level compiler
  command. The command must not retain a final distributable artifact.
- Implementation does not imply authorization to run the restricted commands.
- Use no more than three targeted discovery batches before editing unless a
  concrete blocker remains.
- After editing, use at most one static/read-only validation command by default.
- Do not repeat unchanged failed commands; a compile-only check may be retried
  once after a relevant source fix.

Follow `.agents/prompts/core/resource-budget.md` and `.agents/prompts/core/validation.md` for
authorization and retry rules.

## Working Rules

- Plan first for non-trivial work.
- Inspect the nearest code, tests, config, and docs.
- Expand only when current evidence is insufficient.
- Change only what the task needs.
- Preserve current contracts unless explicitly changed.
- Keep diffs small, local, and reviewable.
- Update nearby docs when behavior or workflow changes.

## Stop And Ask

Pause when:

- local instructions conflict;
- external behavior is unclear;
- designs have materially different blast radius;
- migration, rollout, or compatibility policy is missing;
- runtime evidence is decisive but not already authorized.

## High-Risk Areas

Call out risk before changing dependencies, auth, permissions, billing, schema,
migrations, persistence, public APIs, events, CLI contracts, CI/CD, deployment,
concurrency, retries, timeouts, or shutdown behavior.

## Prompt Routing

- Mixed-language repository: `.agents/prompts/repo-types/monorepo.md`
- Python: `python/AGENTS.md` or `python/CODEX.md`
- Java: `java/AGENTS.md` or `java/CODEX.md`
- Go: `go/AGENTS.md` or `go/CODEX.md`
- Prompt changes: `.agents/prompts/domains/prompt-authoring.md`
- Task mode: one matching file under `.agents/prompts/task-modes/`

## Final Report

Include the change, reason, static evidence, commands actually run, checks not
run, residual risks, and optional focused checks that require authorization.

---

# AGENTS.md

## Purpose
This is the default local rule file for Java subtrees.
Use it when the task is mostly Java or the current directory contains Java markers.

## Inspect First
- `pom.xml`, `build.gradle*`, and wrapper files
- controllers or handlers near the touched path
- services, repositories, DTOs, and config classes
- tests near the touched code

## Detect The Java Domain
- Spring markers:
  `spring-boot`, `@RestController`, `application.yml`, `src/main/java`
  - also load `.agents/prompts/domains/java-spring.md`

## Keep Stable
- controller, service, repository, and config boundaries
- request and response contracts
- transaction boundaries
- dependency injection and build-tool conventions

## High-Risk Java Areas
- dependency upgrades
- auth, schema, or migration changes
- transaction changes
- public API or event contract changes

## Validation Options
Use static review by default. After implementation, run one compile-only check:
prefer `scripts/compile*` or root `compile*`, otherwise use the narrowest
Maven or Gradle compile goal without retaining a final distributable artifact.
Do not run `scripts/verify.sh`, tests, `build*` scripts, or final-artifact
builds.

When the shared resource budget authorizes runtime validation, choose one
smallest relevant command:
- `mvn -pl module -am test`
- `./gradlew :module:test`

Ask before repository-wide commands. Rerun only after a relevant change.

## Escalation
If a deeper subtree has its own `AGENTS.md` or `CODEX.md`, prefer that local file.
<!-- aiw-prompts:java:agents end -->
