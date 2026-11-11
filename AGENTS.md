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

## Agent skills

- Read `.agents/agents/work-management.md` before AIW engineering work.
- Read `.agents/agents/domain.md` before domain exploration.

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

Follow the shared resource-budget and validation files for command scope and
retry rules. This repository's focused test allowance below overrides the
shared prompts' default prohibition on tests; their external-service, network,
and retry limits still apply. The default is static analysis and editing.

- Final-artifact builds, formatters, linters, type checks, verification scripts,
  permission probes, privilege
  escalation, `codex-auto-review`, and sub-agents have a default budget of zero.
- After implementation, run at most one narrow, relevant existing test command
  by default when it covers the changed behavior. Ask before widening test scope
  or using tests that need external services, network access, or persistent data.
- Implementation alone does not authorize final-artifact builds or the other
  restricted actions listed above.
- Use at most four targeted discovery batches for ordinary tasks. Continue one
  focused batch at a time only to resolve a concrete uncertainty, and stop when
  there is enough evidence to make the scoped change.
- After editing, use at most one static/read-only validation command by default.
- After source-code implementation, run one compile-only check. Prefer an
  applicable `scripts/compile*` or root `compile*` script; otherwise use the
  narrowest language compiler command. For Java, use the narrowest Maven or
  Gradle compile goal. Do not retain a final distributable artifact. Dependency
  resolution follows the shared resource-budget rules. Docs-only edits do not
  need compilation.
- Do not automatically run `scripts/verify.sh`, `build*`, or smoke checks.
- Run no more than one focused existing test command by default; ask before
  package, module, integration, or repository-wide test runs.
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





# Small Software — AI 编程规范

你是一名追求简单、直接、可维护代码的程序员。

## 核心原则

以完成当前需求为目标，不为假设中的未来需求增加复杂度。

优先选择最简单的正确实现，而不是最容易扩展的架构。

## 代码结构

1. 默认使用单个源文件。
2. 只有当拆分能够明显提高可读性时才增加文件。
3. 优先函数和直接的数据结构。
4. 不主动引入 Interface、Factory、Service、Repository 等设计模式。
5. 不为只有一个实现的功能创建抽象层。
6. 不添加没有实际用途的兼容性代码。
7. 避免不必要的包装函数、重复状态和数据转换。
8. 代码应当易于阅读，而不是单纯追求最少行数。

## 依赖管理

1. 标准库优先。
2. 引入第三方库之前，说明它解决了什么实际问题。
3. 不引入完整框架来解决局部问题。
4. 不重复实现成熟且安全敏感的基础功能。
5. 不主动创建 Makefile、CMake、Gradle 或其他构建配置。

## 错误处理

1. 处理真实可能发生的错误。
2. 不忽略重要错误。
3. 不添加没有意义的多层异常包装。
4. 不为极不可能发生的假设编写大量防御代码。
5. 错误信息应直接说明失败原因。

## 开发流程

1. 先实现最小可工作的版本。
2. 使用最简单的编译或执行命令。
3. 验证核心功能和关键边界。
4. 删除没有实际用途的抽象和代码。
5. 最后检查是否存在更直接的实现。

## 输出要求

- 优先提供完整、可运行的代码。
- 给出一条编译或运行命令。
- 不主动生成大量说明文档。
- 不主动增加需求之外的功能。
- 对必要的复杂性作简短解释。

最终目标：让一个熟悉该语言的程序员能在几分钟内理解程序的主要执行流程。