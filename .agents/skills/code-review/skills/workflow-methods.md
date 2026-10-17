# Shared workflow methods

Canonical methods for stage-specific use. The installer materializes this file
for entries that declare `skills/workflow-methods.md`. Read only the section
needed by the current role. This file grants no role, state, Git, runtime or
external publication authority; the active entry and shared contract own those.

## Domain clarification

Use when a term is overloaded, concept ownership/relationships are unclear,
or a state/rule has conflicting meanings. Reading established vocabulary alone
does not require a modeling exercise. Work from the current stage's authorized
sources; Requirement conversation methods use only the supplied snapshot.

For each relevant gap return the preferred term and definition, distinctions
from nearby concepts, relationships/ownership, relevant states/transitions and
invariants, one ordinary example and one boundary/counterexample. Omit dimensions
that genuinely do not apply. Cite confirmed sources; separate proposed meaning,
code-observed behavior and human-confirmed rules. Ask about material ambiguity,
not facts that authorized local evidence can settle. Never infer a business rule
from the implementation alone or silently change an agreed meaning.

Keep each fact in its owning artifact: Issue Plan owns user goal, proposed rules,
acceptance and unresolved business questions; CONTEXT.md owns concise confirmed
vocabulary and distinctions; stable specs own settled capability behavior;
the FD owns current engineering choices, dependencies and Work Items. An ADR
preserves a durable, non-obvious, costly-to-reverse trade-off only when useful.
Link between owners instead of copying a rule into all of them. Relationships,
states and invariants may be proposed in discussion, then linked to their settled
spec; a glossary is not another behavior specification. Use existing document
conventions; do not create empty context maps/ADRs or new approval stages.

Return missing definitions and their readiness impact as explicit questions.
Complete when relevant terms and boundaries are confirmed or the remaining
uncertainty and affected scope are clear. Stable writes still require scope
and the owning stage's authority; discovering a term is not write permission.

## Decision dependencies

Use for a large or uncertain Issue/FD with several dependent decisions, not
every request. Keep a small section in the current Issue Plan or FD: destination
and non-goals, question IDs, prerequisites, status, evidence/answer location and
the next actionable question. Use statuses as discussion notes (open, blocked,
resolved), not a second workflow state machine. Record loose in-scope unknowns
separately until a precise question can be stated; keep out-of-scope items apart.
Do not pre-slice unknown work into implementation tickets.

Choose questions whose prerequisites are settled; investigate accessible facts
within authorization and ask the human about material preferences/trade-offs.
Use the host's selected grilling/grill-with-docs method to discuss the current
question, not an additional full interview. Host question limits and confirmed
answers prevail. No automatic research/prototype subagents, branches or network;
invoke those capabilities only within independently authorized scope/budget.

After a decision, link its single authoritative answer, update only affected
dependencies and surface newly clear questions. On resume, read the destination,
decisions and current frontier first, then relevant details. Detect circular or
missing prerequisites and record the gate rather than guessing a resolution.
Do not repeat settled questions, force one ticket per session, or stop when the
current frontier is empty but material blocked decisions remain.

Keep user goal -> software rule/acceptance -> FD engineering decision traceable.
Business outcomes use existing child Issue rules; an unresolved question alone
does not justify an approved child deliverable. Engineering questions continue
in the same FD; Work Items remain the sole execution checklist. Link prior
business decisions instead of creating a second map, tracker or authoritative
brief. Critical unknowns block the affected approval/design readiness; independent
work may continue. Finish when material decisions are resolved or explicitly
deferred out of the current agreed scope, preserving history and remaining risk.

## Intake queue

Use to organize multiple reports, requests or incoming PRs. For each item record
source identity/link, user problem and desired outcome, known facts versus
assumptions, missing information, duplicate/related candidates, impact/priority
reason and next action/owner. Keep source links to preserve the original need
when it becomes a software requirement. Do not invent priority from age alone.

Search only relevant authorized records/evidence for existing capability or prior
decisions. Similar wording, existing implementation or an earlier rejection is
a candidate match, not automatic closure: verify the unmet need, behavior,
accessibility/docs and changed context. Distinguish duplicate, already satisfied,
rejected and deferred. Preserve earlier rationale; do not erase old decisions
when reconsidered. Static inspection does not establish runtime reproduction.

Return a queue summary and specific next action. Hand individual discovery and
business decisions to issue-management, engineering planning to fd-workflow and
PR correctness to independent review. A summary is input, not approval, an
execution specification or a merge verdict. Do not launch full interviews for
every item or require triage before ordinary Issue work. External reads need
network authorization; labels, comments, assignment, closing or messages need
explicit action authority. Existing authority is sufficient within scope; do
not ask again. Without it, provide local recommendations and missing inputs.

## Splitting principles

Assess outcome size, difficulty and uncertainty before approval or handoff.
Split by one reviewable result, with acceptance, dependencies and non-goals;
do not split solely by files or technical layers. Preserve IDs and lineage.
Map each original outcome once, including retained/deferred scope and shared
safety or release dependencies. Splitting does not remove uncertainty or risk.

Issue owns business scope: use child Issues for independently decidable and
acceptable user outcomes. A child needs its own approval; parent approval is
not inherited. Planner owns engineering scope: use the FD's Work Items for
implementation steps within an approved outcome, not another execution plan.
Keep inseparable outcomes together and explain the choice. Do not create a
second Task or OpenSpec tasks checklist. Unresolved scope/acceptance decisions
are explicit gates, not ready work. Use the active stage's write authority.

## Specification writing

Use when settled decisions change a stable capability rule or observable
behavior, or for an explicitly requested spec maintenance action. Read only
relevant stable specs, confirmed Issue evidence, the FD and an explicitly
linked change when present. Write requirements and observable success,
failure and boundary scenarios; do not prescribe incidental implementation.

The FD owns options, engineering decisions and Work Items; stable specs own
capability behavior. Identify which requirements change and why. Keep current
and proposed behavior distinguishable: link a proposed rule to its pending FD
and reconcile wording at completion. Do not represent unfinished behavior as
implemented. Do not update stable specs for a routine internal refactor.

Do not reopen settled decisions or create missing policy. Record material
unknowns as `%% NEEDS_INPUT` and report BLOCKED/INCOMPLETE. Return affected
requirements and FD items to Planner for reconciliation; keep existing IDs and
completed evidence. Create an OpenSpec change only on explicit user request.
If one exists, keep its relevant artifacts coherent without making tasks.md a
second plan. Missing OpenSpec tooling blocks only that requested operation.
Completion requires rules/scenarios consistent with confirmed scope, a clear
proposed/implemented status and no unresolved acceptance ambiguity. Report
changed files, evidence and gates. Runtime checks follow the repository budget.

## Implementation review

Review actual changed source and relevant surrounding call paths, not only a
report or checkbox. Pin the implementation revision and comparison base; read
the originating FD/Issue, applicable stable specs and documented repo rules.
For documentation/configuration-only work, review those implementations and
contracts directly; do not fabricate code findings or demand irrelevant tests.

Assess these dimensions while keeping findings distinct:

- Correctness: trace control/data flow, edge and failure paths, compatibility,
  affected interfaces, permissions and data integrity where relevant. Report
  concrete defects and consequences rather than hypothetical checklists.
- Standards: cite actual repository rules. Apply the smell heuristics below
  only where useful; tooling-enforced style is not a manual review target.
- Spec: identify missing/partial requirements, wrong behavior or scope creep
  against current approved acceptance. A code style pass does not prove the
  requested behavior, and a spec pass does not prove sound implementation.
- Evidence: check commands actually run, revision coverage, skipped checks and
  remaining risks. A waived check is not passed and does not waive behavior.

Smell heuristics (judgment calls, never automatic blockers; repo rules prevail):
unclear names; duplicated logic; feature envy; data clumps; primitive obsession;
repeated type switches; shotgun surgery; divergent change; speculative
generality; message chains; middle-man delegation; refused inheritance.
Suggest a local correction only when it improves the changed scope; do not
turn review into unrelated refactoring. Severity follows actual impact.

Each actionable finding names the file/location, current requirement or rule,
observed defect, consequence and minimal correction. Separate hard violations
from heuristics; label uncertainty instead of inventing evidence. State what
was inspected and what remains unverified. The active entry owns report
format, verdict and events. Loading this method never invokes another Skill,
spawns extra reviewers, runs tests or expands review rounds by itself.

Relevant optional test evidence may support review: inspect rule/oracle quality,
key assertions, tested versions and limits, not pass counts alone. Confirmed
acceptance defects remain actionable even when tests are optional. An exhausted
test effort stays diagnosis-only; review does not restart its repair/run budget.
