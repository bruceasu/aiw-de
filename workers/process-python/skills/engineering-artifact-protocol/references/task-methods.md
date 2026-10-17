# Evidence and continuation methods

Apply these methods when reporting evidence, research, a blocker, or a handoff.
Keep the role's output schema and Control Plane authority unchanged.

## Evidence
- Prefer supplied primary sources: accepted requirements, contracts, exact code revisions,
  and raw execution output. Cite the owner, revision, and relevant location for a claim.
- Separate confirmed rules, observed behavior, inference, and unknowns. Current code is
  evidence of implementation, not proof of the intended rule.
- Distinguish observed status (passed, failed, blocked, not run) from cause
  (product, test, environment, or undetermined). A failure alone does not identify its cause.
- Tie evidence to the actual implementation and supplied dirty snapshot, when any,
  command, environment, and output. Missing identity is a limitation, not an invented hash.
- Remote research, experiments, and new artifact writes still require task authority.
  If evidence is inaccessible, state the exact missing source and affected conclusion.

## Continuation
- In existing summary/blocker fields, record the delivered outcome, exact inputs and
  outputs, important decisions, unresolved question, next action, and responsible role.
- Include relevant attempted commands, remaining authorized budget, and preserved
  failure evidence. A new session does not renew a spent budget or expand permission.
- Link authoritative records rather than copying their rules into a new registry.
  Summaries and explanations are derived context, not accepted requirements or approvals.
- Explain conclusions in plain language with the evidence needed to assess them.
  Keep unrelated narrative, repeated logs, and speculative follow-up out of the report.

## Check
Check source/claim alignment and agreement between human text and structured fields.
Return only the assigned result; do not add a top-level schema, state, or handoff event.
