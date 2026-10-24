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

## Local work and proposed delegation
- A role can use several Skills in one Worker task. Keep a coherent outcome and shared
  decisions together; do not turn every Skill step into an independently dispatched node.
- Isolate a bounded investigation when extensive exploration can return a concise answer
  and exact evidence references. Keep dependent debugging hypotheses in one context.
- Independent review has a separate purpose even for small inputs. Propose it when the
  assigned process requires independent judgment; do not impose a new review gate.
- Parallel proposals need explicit inputs, completion/output contracts, decision ownership
  and joins. Shared writable state or hidden design dependencies need coordination first.
- Explain each proposed execution boundary in existing task/item instruction fields. State
  the consumer, allowed tools, source revisions, evidence gaps and minimal useful handoff.
  The platform adopts the graph and creates tasks; this method cannot dispatch them.
- Choose from supplied approved profiles or state capability needs. Never invent model
  references, change an active task's model, or use fixed vendor/token thresholds as policy.
- Use actual supplied consumption and result records for comparison. Missing startup cost,
  quality scores or savings stay unmeasured; a self-reported PASS is not independent acceptance.

## Changed acceptance and handoff
- Compare a supplied change with the exact accepted requirement and architecture.
  Identify affected outputs, dependency edges, checks and completed evidence; preserve
  unaffected work with a reason instead of restarting the whole plan.
- Distinguish a changed requirement from a defect against the existing requirement.
  Propose the needed decision before planning executable work for unresolved policy.
- Keep one owner for each outcome and shared rule. Link the formal scope and proposed
  verification in existing plan fields; do not maintain a competing FD or task list.
- For a proposed handoff, name required inputs, expected output and observable finish.
  State missing contracts, authorization or evidence explicitly. A small item is not
  ready merely because its estimated size is small.
- Check that any optional experiment answers a decision that blocks real work. Do not
  make a prototype, test suite or release gate mandatory when the task did not ask for it.
- Return sizing, coverage and readiness proposals. The Control Plane assigns IDs,
  accepts the plan and dispatches roles; the planner does not grant itself those actions.
