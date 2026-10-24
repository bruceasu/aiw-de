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

## Receive and hand off
- On entry or continuation, identify the assigned task, exact input/output revisions,
  repository/base, accepted decisions, scope and remaining authority from the supplied
  formal context. A prior summary helps navigation; it cannot replace those facts.
- Check whether supplied evidence still covers the current change. Code, requirement,
  test or configuration changes can make an old conclusion stale. Reuse only the
  unaffected part with a reason; never describe an old run as a run of new content.
- Use existing summary/blocker/evidence fields for delivered outcomes, pending work,
  confirmed decisions, exact output references, open questions and the next action/role.
  Separate work that can continue from acceptance that remains blocked.
- Preserve original request/operation identities and known, failed or unknown outcomes.
  If a write or external effect is unconfirmed, request reconciliation through the
  existing contract. A handoff does not authorize replay, a new identity or a retry.
- Include relevant attempted commands and consumed/remaining execution authority.
  A new session, role or command spelling does not renew a spent budget.
- Do not invent missing current state, revisions, hashes or another role's approval.
  Return the exact missing fact and its effect on the assigned result.
- Link authoritative records instead of creating another registry. Use plain language
  and concise evidence; do not copy private conversations or unrelated logs.

## Completion and reuse
- Trace the claimed outcome to the assigned acceptance and actual output. Distinguish
  implementation, self-review, independent review, acceptance and release evidence.
  The Control Plane owns acceptance and transitions; a Worker reports only its role.
- Reusable guidance belongs in a Skill only when that is the assigned artifact scope.
  Otherwise propose the learning through existing fields; do not update a shared
  library, policy or requirement because one task produced a useful observation.

## Check
Check source/claim alignment and agreement between human text and structured fields.
Return only the assigned result; do not add a top-level schema, state, or handoff event.
