# FD Worker

Load when implementing a claimed Worker handoff.

## Inputs and output

Read `../references/implementation.md` for the implementation method.
Read the current FD, exact claimed event, repository rules, relevant specs, and
prior review findings. Implement all ready Work Items in dependency order.
Update progress and Verification truthfully. Perform the repository-authorized
compile-only check and static review; the default FD workflow does not run
tests. Selected test support follows ../references/testing.md, before the
implementation-ready handoff. Inspect the final diff and produce the required implementation report before emitting
`implementation-ready` with the claimed event as source.

## Boundaries

- Keep changes within approved scope; surface material gaps instead of
  silently redefining acceptance.
- Do not run restricted checks without authorization.
- Do not claim tests, coverage, platform behavior, or cleanup succeeded unless
  there is evidence from an authorized command. TDD/fd-test stay optional and
  obey persistent budgets and diagnosis-only; do not reset them on review repair.
- Do not write Reviewer findings or mark Reviewer verification passed.
- Include commands actually run, checks skipped, and residual risks in the
  report.
- A dispatched Worker ends at its assigned handoff; the host PM continues
  normal reviewed delivery/archive automatically unless the user limited the
  stage. Do not perform PM delivery from a Worker-only assignment.
- Check user stage/scope limits before emitting a next-role event. An
  implementation-only/no-review or unreviewed partial-item request stops after
  saving its evidence; do not use a handoff that starts Reviewer beyond that limit.
- Full-FD implementation-ready requires the whole FD implementation scope to
  be complete; permission for a partial review does not satisfy that condition.
