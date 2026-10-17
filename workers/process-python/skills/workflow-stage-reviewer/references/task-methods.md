# Stage acceptance methods

Use only for the assigned completed step. Preserve the exact final AIW_VERDICT line
and the Control Plane's repair limits and acceptance authority.

## Evidence by output
- Identify each stated criterion and the exact supplied output/revision that could
  support it. Do not invent extra acceptance rules from a general checklist.
- For analysis reports check source/claim alignment, confirmed facts versus assumptions,
  whether the goal is answered, and whether unresolved decisions are honestly identified.
- For plans check outcome coverage, real dependencies, bounded items, and unresolved
  policy/design choices hidden as executable work. Do not mark a DAG ready yourself.
- For architecture check caller fit, contract ownership, compatibility, and failure
  behavior against accepted inputs rather than preference for a particular pattern.
- For code inspect the actual supplied diff and relevant call paths: correctness,
  repository rules, accepted behavior, and evidence are separate review dimensions.
- For verification check expected-result derivation, meaningful assertions, actual
  tested revisions and outcomes. Passed command counts alone are insufficient.

## Verdict
- An unmet criterion or decisive missing evidence needs a concrete correction tied
  to that criterion. Distinguish a defect from an environmental or evidence gap.
- A waived check is not passed and does not waive behavior; use current authority.
- Do not reject solely for an unrelated style smell, missing optional experiment,
  missing FD/ADR document, or a test that the task did not require.
- A prose claim of independent review is not proof of separate execution. Use supplied
  provenance and report its limits; never invent identities or run another reviewer.
- Preserve exact upstream IDs, output versions, and the required existing verdict format.

## Check
Ensure the explanation supports PASS or REPAIR_REQUIRED for this step alone.
No code edits, test execution without authority, next-role dispatch, or new report schema.
