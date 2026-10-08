# Repository Change Guidelines

## Before editing

Inspect:

- repository-level instructions and contributor docs;
- module/build structure;
- dependency-management conventions;
- existing tests for similar behavior;
- public contracts touched by the WP;
- migration/versioning conventions;
- generated-code rules.

## During editing

Prefer established project idioms. Avoid creating a second abstraction, error model, serialization convention, or configuration mechanism for the same concern.

Keep commits/change sets conceptually cohesive even if multiple files are required.

## Compatibility

Treat these as compatibility-sensitive:

- public APIs;
- database schemas and persistent formats;
- event/message schemas;
- configuration keys/formats;
- CLI commands and flags;
- authentication/authorization semantics;
- externally consumed logs/metrics only when explicitly contractual.

When changing them, identify old/new behavior and migration or coexistence expectations.

## Secrets and diagnostics

Do not introduce secrets, credentials, tokens, personal data, or production values into source or test fixtures. Remove temporary debug output before handoff unless it is intentionally part of observability.

## Generated artifacts

Do not hand-edit generated files unless repository policy requires it. Change the source definition and regenerate. Include generated output only when the repository normally versions it.
