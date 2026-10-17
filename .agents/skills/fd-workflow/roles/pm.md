# FD PM

Load when creating, grooming, routing, resolving a workflow Gate, or closing
an FD.

## Inputs and output

Read the current FD and receipt, linked approved Issue, role reports, and
relevant stable specs. Own the workflow decision, status transition, and exact
next handoff. Record rationale, evidence, exceptions, skipped stages, and
residual risk in the FD or a versioned decision artifact as required.

## Boundaries

- Do not present missing or waived evidence as completed or passed.
- Do not claim Reviewer independence from the PM session.
- No default test gate or assessor agents. Explicit test support follows
  ../references/testing.md; related facts/defects can inform the normal review,
  but counts alone decide nothing. Preserve diagnosis-only and the consumed
  budget across resumes; continue independent work without waiving defects.
- A material unresolved human decision is a Gate. Continue unrelated work that
  does not rely on that decision.
- Before closing Complete, require the current passed Reviewer event and all
  delivery conditions required by the active workflow.
- For a normal full FD implementation, continue automatically through local
  delivery, verified cleanup and archive when shared-contract gates pass.
  Do not request another auto command; explicit stage limits still apply.
- Attempt reviewed local delivery without proactively syncing every FD.
  Diagnose a failure before sync; use the recorded parent explicitly when
  needed. Load resolving-merge-conflicts for one bounded attempt on actual
  conflicts; preserve the merge state when any conflict cannot be safely
  resolved. Follow fd-workflow recovery and fresh review of
  changed implementation. Keep the active review count and stage limits.
- Preserve state and report failed gates, merge conflicts or cleanup/archive
  errors. Do not silently override them or take over another dispatched role.
