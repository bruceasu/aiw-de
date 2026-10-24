# Implementation and diagnosis methods

Use during the assigned Work Package, especially for a bug, failed check, or authorized
conflict repair. Preserve frozen inputs, repository scope, and the existing result contract.

## Implement
- Check the outcome, accepted rule, dependencies, and available evidence before edits.
  A local implementation choice is allowed within scope; missing policy or architecture
  returns to the owning role as a gap.
- Make a coherent, reviewable change. Record acceptance coverage and the highest-risk
  paths for review. Do not treat edited files or a successful commit as correctness proof.
- Reuse existing interfaces. Propose structural work outside the package instead of
  adding an unrelated refactor or independently executing other planned items.

## Local steps inside one work package
- For non-trivial work, outline a few steps by observable result: inspect the relevant
  path, implement a coherent change, perform authorized checks and self-review.
  A simple change may use one step; do not plan a separate item per file or tool call.
- Relate each step to the assigned acceptance, its dependencies and completion evidence.
  Use supplied item IDs when present. Local labels are notes, not new WorkItems,
  accepted scope or permission to implement other items in the feature.
- Keep completed, pending and blocked work distinct in existing progress or handoff
  text. Mark a step done only when its output exists; record unrun verification
  separately. A successful edit or commit alone is not proof of behavior.
- Update the note when new evidence changes the approach. Preserve completed outcomes
  and explain superseded work. Do not rewrite the accepted rule to fit the code.
- Before continuing from a summary, recheck the supplied baseline, current changes,
  prior operation outcomes and remaining authority. Unknown effects need the existing
  reconciliation path, not a fresh request or an automatic repeat.
- Return a concise continuation in the required report if work stops: exact outputs,
  next unfinished step, blocker, failed checks and responsible role. Do not create an
  ungranted plan file or claim these notes are a durable execution state machine.
- Self-review is part of implementation evidence. Independent review and acceptance
  are separate platform work; do not run another role or an FD lifecycle yourself.

## Diagnose before repeated repair
- Separate the symptom from its cause. Follow the affected input and call path using
  preserved logs, exact revisions, and source.
- Distinguish product, test/oracle, fixture, environment, and unknown causes.
  State supporting and contradictory evidence for a small set of useful hypotheses.
- Select a runtime experiment only when it distinguishes a specific hypothesis and
  the task already authorizes it. Compilation failure is not behavioral reproduction.
- Respect the existing retry budget and diagnosis-only state. Do not renew authority
  by switching commands or sessions, weaken assertions, or delete failure evidence.
- TDD applies only when selected and authorized. Expected values come from confirmed
  rules, not the implementation; never claim red/green phases that were not observed.

## Conflict repair
- Only for an actual, assigned conflict: inspect the common base, both intended changes,
  operation state, and current contract. Preserve both behaviors where evidence supports it.
- If intent is unclear, preserve the operation and report the conflicting paths and
  missing decision. No blind whole-side choice, reset, abort, or speculative cleanup.
- Commit/continue only under explicit operation authority. Return the changed revision
  and review needs; do not merge to the delivery parent or create Reviewer events.

## Check
Report the exact change, actual checks, skipped checks, unresolved hypotheses, and
remaining authority in existing execution evidence. Do not rewrite acceptance to fit code.
