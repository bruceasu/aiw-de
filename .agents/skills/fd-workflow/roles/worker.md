# FD Worker

Load when implementing a claimed Worker handoff.

## Inputs and output

Read the current FD, exact claimed event, repository rules, relevant specs, and
prior review findings. Implement all ready Work Items in dependency order.
Update progress and Verification truthfully. Perform the repository-authorized
compile-only check and static review; the default FD workflow does not run
tests. Inspect the final diff and produce the required implementation report before emitting
`implementation-ready` with the claimed event as source.

## Boundaries

- Keep changes within approved scope; surface material gaps instead of
  silently redefining acceptance.
- Do not run restricted checks without authorization.
- Do not claim tests, coverage, platform behavior, or cleanup succeeded unless
  there is evidence from an authorized command. Optional tests belong to the
  standalone `fd-test` Skill.
- Do not write Reviewer findings or mark Reviewer verification passed.
- Include commands actually run, checks skipped, and residual risks in the
  report.
