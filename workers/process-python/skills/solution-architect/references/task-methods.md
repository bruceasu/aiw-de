# Architecture decision methods

Use for a concrete boundary, interface, failure contract, or structural obstacle.
Keep accepted requirements and repository permissions unchanged.

## Compare useful options
- Start from actual callers, data flow, current friction, and supplied contracts.
  Explain what the caller must know: inputs/results, invariants, ordering, errors,
  authority, and relevant performance constraints.
- Compare keeping the current design with a local improvement; add a broader alternative
  only when evidence warrants it. State where complexity moves and which rules it hides.
- Ask what happens if a layer is removed: does complexity disappear or spread to callers?
  A thin adapter can be justified by isolation; line count is not a design score.
- Keep pure rules separate from side effects where useful. Reuse an existing stable test
  boundary; do not add an abstraction, service, or dependency only to make mocks easier.
- Record compatibility, migration, partial failure, cancellation, and concurrency effects
  only where relevant. A small change can still depend on an unresolved high-risk decision.

## Reduce uncertainty
- Use supplied primary sources and relevant domain distinctions. Separate confirmed
  requirements from code-observed behavior and proposed architectural meaning.
- For multiple dependent decisions, identify prerequisites and the next decision that
  can be resolved. Return unresolved business policy to its owner instead of guessing.
- If a prototype would settle a material question, propose one question, bounded
  scope, expected observation, and exit criterion. Do not create or execute it without
  the task's write/runtime authority; prototype output is not production evidence.
- Describe testing at the observable contract and disclose what doubles cannot validate.
  Return out-of-scope structural work as a follow-up proposal, not an automatic refactor.

## Check
Trace the recommendation to callers, accepted behavior, and evidence. Use existing
architecture/contract proposal fields, with explicit unresolved trade-offs and limits.
