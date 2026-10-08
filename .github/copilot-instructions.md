# .github/copilot-instructions.md

Read `AGENTS.md` first.

## Prompt Set

- load the four files in `.agents/prompts/core/`
- prefer the nearest local instructions
- add at most one repo-type, one domain, and one task-mode prompt
- do not load the whole prompt library

## Defaults

- use static analysis and minimal edits
- use at most three targeted discovery batches before editing unless blocked
- keep command output narrow
- after implementation, run one compile-only check using a `compile*` script or
    the narrowest language-level compiler command; do not retain a final artifact
- do not run tests, `build*` scripts, final-artifact builds, formatters,
    linters, type checks, vet, verification scripts, network calls, permission
    probes, escalation, `codex-auto-review`, or sub-agents without authorization
- use at most one static/read-only post-edit command by default
- do not repeat equivalent commands

## Report

State the change, static evidence, exact commands, skipped runtime checks,
remaining risks, and optional checks requiring authorization.

<!-- aiw-prompts:java:copilot begin -->
# .github/copilot-instructions-for-java.md

Read `AGENTS.md` first, then `java/AGENTS.md`.

## Java Routing
- Spring markers:
  `spring-boot`, `@RestController`, `application.yml`, `src/main/java`
  - also load `.agents/prompts/domains/java-spring.md`
- bugfix, feature, review, debugging, test, docs, or risky work:
  also load the matching file in `.agents/prompts/task-modes/`

## Defaults
- inspect contracts, tests, and config first
- respect controller, service, and repository boundaries
- treat DTO, schema, and transaction changes as risk-sensitive
- use static review by default
- do not run Maven, Gradle, tests, builds, verification scripts, network calls,
  or permission probes without resource-budget authorization
- when authorized, run one module-focused command and ask before widening
<!-- aiw-prompts:java:copilot end -->
