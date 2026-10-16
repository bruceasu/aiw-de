# Domain Documentation

Read this file before domain exploration. Keep established domain terms and
recorded architectural decisions stable unless the user requests a change.

## Read Relevant Context

- Use root `CONTEXT.md` as the default domain context.
- If root `CONTEXT-MAP.md` exists, follow it and read only the contexts relevant
  to the current work.
- Read relevant decisions under `docs/adr/`. If the context map identifies
  context-specific ADR directories, read the relevant decisions there too.
- If these documents do not exist, continue without creating placeholders.
  Add domain documents only when actual terms or decisions have been resolved
  within an authorized task.

## Use Established Terms

Use the glossary's terms in proposals, code explanations, and other outputs.
Avoid synonyms that the glossary rejects. If a needed term is missing, record
the uncertainty in the current task rather than inventing a domain fact.

## Surface Decision Conflicts

If a proposal conflicts with an ADR, name the ADR and explain the conflict.
Do not silently override the decision.

## Validation

Check domain statements against the relevant context and ADRs. Separate facts,
assumptions, and unverified behavior. Keep runtime validation within the
repository's authorized budget.
