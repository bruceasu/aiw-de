# Repository Instructions

## Purpose and Communication

This file is the root instruction source for this repository.
Keep the active instruction set and execution budget small.

- Always respond in Chinese.
- Write prompts in Easy English when asked to draft or edit prompts.
- State facts, assumptions, and unverified behavior separately.

## Instruction Loading

1. Apply this file and the nearest applicable subtree `AGENTS.md` or `CODEX.md`.
2. Load these four shared files:
   - `.agents/prompts/core/resource-budget.md`
   - `.agents/prompts/core/universal-principles.md`
   - `.agents/prompts/core/validation.md`
   - `.agents/prompts/core/communication.md`
3. Add at most one repo-type prompt, one domain prompt, and one task-mode prompt.

Use `project-local > domain > language > repo-type > root` precedence for
repository rules. Explicit user instructions take priority over repository
rules. Ask only if a material conflict remains after applying precedence.
Do not load the whole prompt library or use a wildcard to load all core files.
Root `CODEX.md` only points here; it adds no separate rules.

## Prompt Routing

Load only files that exist and apply to the task:

- Use `.agents/prompts/repo-types/monorepo.md` when work spans multiple projects.
- For language subtrees, prefer their local `AGENTS.md` or `CODEX.md`, such as
  those under `python/`, `java/`, or `go/`; do not assume these paths exist.
- For Java work with Spring markers (`spring-boot`, `@RestController`,
  `application.yml`, or `src/main/java`), use
  `.agents/prompts/domains/java-spring.md`.
- For prompt changes, use `.agents/prompts/domains/prompt-authoring.md`.
- Choose one matching file under `.agents/prompts/task-modes/` for feature,
  bugfix, debugging, review, test, docs, or risky-change work.

## Working Rules

- Plan first for non-trivial work. Briefly state the goal, relevant files or
  classes, assumptions, steps, validation, and material risks.
- Inspect nearby code, contracts, tests, configuration, and docs before
  expanding the search. Expand only when current evidence is insufficient.
- Change only what the task needs. Keep diffs small, local, and reviewable.
- Preserve existing contracts unless the user explicitly requests a change.
- Update nearby docs when behavior or workflow changes.

For Java work, inspect the build file and wrappers, nearby handlers or
controllers, services, repositories, DTOs, configuration, and tests. Preserve
layer boundaries, request/response contracts, transaction boundaries,
dependency injection, and build-tool conventions unless explicitly changed.

## Resource and Validation Budget

Follow the shared resource-budget and validation files for authorization,
command scope, and retry rules. The default is static analysis and editing.

- Tests, final-artifact builds, formatters, linters, type checks, verification
  scripts, network calls, dependency downloads, permission probes, privilege
  escalation, `codex-auto-review`, and sub-agents have a default budget of zero.
- Implementation alone does not authorize these restricted actions.
- Use at most three targeted discovery batches before editing, unless a
  concrete blocker requires more evidence.
- After editing, use at most one static/read-only validation command by default.
- After source-code implementation, run one compile-only check. Prefer an
  applicable `scripts/compile*` or root `compile*` script; otherwise use the
  narrowest language compiler command. For Java, use the narrowest Maven or
  Gradle compile goal. Do not retain a final distributable artifact or download
  dependencies without authorization. Docs-only edits do not need compilation.
- Do not automatically run `scripts/verify.sh`, `build*`, tests, or smoke checks.
- Do not repeat an unchanged failed command. A compile-only check may be retried
  once after a relevant source fix; follow the shared rules for other retries.
- When runtime validation is authorized, run one smallest relevant command.
  Ask before expanding to repository-wide validation. Static evidence does
  not count as a runtime pass.

## UI Contract

For UI work, inspect nearby pages and shared styles first. Reuse the existing
Web Console colors, typography, spacing, components, and interaction patterns.
Any game or technology motif must fit this visual style.

## Risk and Decision Boundaries

Call out material risk before changing dependencies, auth, permissions,
billing, schema, migrations, persistence, transactions, public APIs, events,
CLI contracts, CI/CD, deployment, concurrency, retries, timeouts, or shutdown.

Pause the affected work and ask when an unresolved instruction conflict,
unclear external contract, materially different design impact, or missing
migration/rollout/compatibility policy prevents a sound decision. Also ask
when decisive runtime evidence is required but execution is not authorized.
Continue independent work that is already authorized.

## Final Report

State what changed and why, the static evidence reviewed, commands actually
run, checks not run, and remaining risks. Mention optional focused checks
requiring authorization only when they help resolve a remaining uncertainty.
