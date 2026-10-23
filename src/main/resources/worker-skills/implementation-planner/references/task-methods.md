# Work decomposition and readiness methods

Use while creating or revising the assigned plan proposal. Java still owns durable IDs,
DAG acceptance, readiness, scheduling, and permission.

## Size and uncertainty
- Give each item one independently reviewable outcome, clear non-goals, dependencies,
  completion criteria, and expected evidence. Do not split only by files or layers.
- Assess size and difficulty. Aim for small, low/medium-difficulty implementation items;
  half a day is a rough sizing aid, not a deadline or an acceptance requirement.
- Split items with independent outcomes or mechanisms. Keep shared logic in one owner
  item. Do not duplicate a contract or safety obligation across unrelated items.
- Small does not mean ready: a missing external contract, security policy, migration
  decision, or platform strategy remains a gap. Propose the deciding question and owner.
- Propose a bounded research/prototype item only when it resolves a concrete uncertainty;
  name its question, output, exit criterion, and decision consumer.

## Coverage and change
- Map each acceptance outcome to an item and its evidence. Explain retained, deferred,
  and excluded scope so splitting does not lose requirements.
- Preserve one writable primary repository per executable implementation item and
  the existing cross-repository verification joins.
- Use real dependency edges; a shared repository alone does not require sequential work.
  Do not mark tasks READY or dispatch the proposed graph.
- Preserve completed work and existing identities. Propose explicit supersession and
  impact when acceptance changes; never reuse IDs or erase prior results.
- Choose verification proportional to changed behavior and the task's authorization.
  Planning a test or naming a check does not authorize running it.

## Check
Review coverage, cycles, oversized items, hidden decisions, and evidence gaps. Put extra
sizing rationale in existing descriptive fields; do not add unsupported schema fields.
