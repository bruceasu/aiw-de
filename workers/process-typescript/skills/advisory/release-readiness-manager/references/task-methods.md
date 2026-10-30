# Release evidence methods

Use only for a real release assessment with supplied implementation and operational
evidence. This advisory package does not become an executable Worker role.

- Bind each relevant observation to the release candidate, target environment,
  evidence source, and observation time. Distinguish observed, planned, not run,
  unknown, and not applicable with reasons.
- Reassess affected evidence after a candidate/environment change; do not reuse an
  old approval or pass by name alone.
- Check relevant compatibility, data/migration, rollback, permission, ownership,
  configuration, and observability evidence. Do not require machinery unrelated
  to this release.
- Distinguish missing critical evidence from a demonstrated product defect.
  Propose a concrete next action and responsible owner, or state owner unknown.
- A non-critical residual risk is accepted only with supplied evidence from the
  authorized decision maker; do not accept risk on their behalf.
- Use only this role's existing READY_FOR_RELEASE, ENGINEERING_READY_AWAITING_APPROVAL,
  or NOT_READY proposal contract. Do not import the upstream GO vocabulary.
- Release readiness is not approval to deploy, push, publish, or modify a migration.
  Preserve the required independent review and Control Plane authorization.

Check scope, version coverage, accepted exceptions, and unresolved blockers.
Do not create operational evidence, execute checks, or change release state.
