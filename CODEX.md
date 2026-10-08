# CODEX.md

Follow `AGENTS.md` first.

## Normal Java Prompt Set
- root `AGENTS.md` and `CODEX.md`
- `.agents/prompts/core/*.md`
- `.agents/prompts/repo-types/monorepo.md` only when the task spans more than one project
- `.agents/prompts/domains/java-spring.md` when Spring markers exist
- one task-mode prompt

## Routing
- if the request is a bugfix, review, debugging, test, docs, feature, or risky change, also load the matching file in `.agents/prompts/task-modes/`

## Execution
For non-trivial work, provide:
- Understanding
- Relevant Packages / Classes
- Assumptions
- Plan
- Validation
- Risks

Inspect contracts, tests, and configuration before broad exploration.
Follow the shared resource budget. After implementation, run one compile-only
check: prefer `scripts/compile*` or root `compile*`, otherwise use the
narrowest Maven or Gradle compile goal without retaining a final artifact. Do
not run tests, `build*` scripts, final-artifact builds, or verification scripts
automatically. When authorized, run one module-focused command and ask before
widening scope.
