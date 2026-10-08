# R8-S13 Fix2 — Namespace Refactor

## Decision

The canonical Java/Maven namespace is now:

```text
me.asu
```

The application root package is therefore:

```text
me.asu.aidev
```

## Scope

This is a namespace-only refactor of the accepted R8-S13 Fix1 baseline.

Updated consistently:

- `src/main/java` package declarations and paths;
- `src/test/java` package declarations and paths;
- Java imports and fully-qualified references;
- Maven project `groupId`;
- documentation and release-validation references.

Unchanged:

- `artifactId = ai-dev-manager-r8`;
- REST API paths and payloads;
- Worker Protocol v1;
- `AIW_` environment/configuration prefix;
- database schema and Flyway migrations (`V1` through `V14`);
- runtime scheduling semantics;
- core/authority/audit table count.

## Canonical application class

```text
me.asu.aidev.AiDevManagerApplication
```

## Validation requirements

A release package must satisfy:

```text
legacy namespace residue = 0
Java package/path mismatches = 0
unresolved internal me.asu imports = 0
```

The authoritative build gate remains:

```bash
mvn clean verify
```
