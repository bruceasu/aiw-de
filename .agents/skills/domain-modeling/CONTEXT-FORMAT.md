# Vocabulary format

Load only for an authorized glossary edit, never for snapshot Requirement mode.
Prefer the repository's existing format. Keep a term to a short definition,
its distinctions/avoided synonyms, and links to rules or decisions where useful.

```markdown
# Context name

## Language

**Delivery**: The reviewed FD changes have been placed on the recorded parent.
Distinguish from release readiness and production deployment.
Rule: [delivery contract](relative-path-to-owning-spec).
```

Use only terms relevant to this domain; no generic programming dictionary.
Relationships can clarify identity/ownership in a definition, but state machines,
invariants and acceptance rules belong in stable specs or proposed Issue rules,
not copied into CONTEXT.md. Keep ordinary/boundary examples in the relevant
Issue/FD discussion or owning spec; link instead of duplicating them.

If CONTEXT-MAP.md already exists, use it to locate the relevant context and
preserve distinct meanings across contexts. Otherwise prefer the existing root
glossary. Missing files are not a problem: create a glossary/map only within
approved scope when actual confirmed terms or multiple contexts justify it.
Ambiguous ownership is a question, not permission to create a new context.
