---
name: ask-asu
description: Navigate AIW workflow state and next steps by default; optionally obtain one bounded second opinion through an authorized host agent or aiw ask.
---

# Ask Asu

Read `skills/reviewed-skill-contract.md`, `skills/work-management.md` and
[references/development-workflow.md](references/development-workflow.md).
Use for workflow navigation, or a specific second-opinion request. Default to
navigation: inspect available Issue/FD/evidence, current event/owner and stage
limits, then recommend one next action. Do not execute that action, mutate
files/events, dispatch a role or invoke an extra model merely to navigate.

An ordinary implementation request belongs to fd-workflow; a question about
sources/research or a bug belongs to its relevant method. Do not expand Ask Asu
into another implementation lifecycle. Missing IDs need resolution only when
material to the recommendation; ordinary questions do not require an FD.

## Default path

1. For a new bug, feature, or modification with unclear scope, recommend
   `issue-management`. A clear engineering request may start with
   `fd-workflow` and `aiw fd new` without an Issue.
2. For a Design FD, recommend Planner through `fd-workflow`, which records options,
   decisions, Work Items, acceptance, and Verification before `design-ready`.
3. For an Open FD or a review correction, route to the Worker stage of
   `fd-workflow`, which loads `references/implementation.md`. Worker handles
   all ready Work Items in order and emits `implementation-ready` when done.
4. For Pending Verification, route to `fd-review` in an independent Reviewer
   session to inspect
   actual diffs and evidence. A failed review returns to Worker; a passed
   review records `verification-passed`.
5. For a reviewed complete FD, route the host PM to fd-workflow completion
   under the existing request and stage limits. A normal full-FD implementation
   continues local delivery/archive automatically; a standalone commit or
   review does not authorize it. Push and release remain separate requests.

An explicit `needs-decision` or authorization gap prevents execution. Recommend
the smallest user question that resolves it. A pending event may be continued
with `aiw fd resume`; a dispatched event with unknown result must be checked
against its original session or log before any retry.

## Optional consultation

Use consultation only for a concrete requested second opinion, or a material
uncertainty covered by an existing consultation authorization. Read
[references/consultation.md](references/consultation.md) before choosing a channel.
Loading this Skill alone does not authorize agents, model requests, network,
new files or changes to model configuration. If authority is missing, prepare
the question and proposed scope; ask only for the missing authority, not again
for an already authorized consultation.

Choose one channel for one bounded consultation attempt: one read-only host
subagent, or one single-turn aiw ask invocation with a known fixed provider.
Do not combine channels, recursively delegate, switch models or retry after
failure without renewed explicit scope and authority. If a consultation result
is unknown or the provider may fan out through auto fallback, stop and report
the limitation rather than treating a new command as a fresh allowance.

Frame goal, exact question, constraints/acceptance, options already considered,
necessary source excerpts/revisions and unknowns. Request evidence-supported
trade-offs, assumptions and a recommendation using only the permitted material.
Do not imply that a fresh model inherits the conversation. Exclude secrets and
unrelated personal/project material; grant only the required context/access.

The host's model-selection capability determines whether a particular model
or reasoning setting can be used. A subagent, profile name or CLI invocation is
not proof of a stronger model or deeper reasoning. Record actual known settings
and limits; do not invent model identity or change persistent configuration.

Consultation is advisory: compare the result with current local facts and
return it to this discussion/FD. It neither records a human decision nor
substitutes for fd-review, independent testing or release approval. It claims
no FD event, performs no implementation and does not reset any test/repair/
review budget. An existing diagnosis-only analysis is referenced, not repeated
through a second opinion to restart the affected effort. Record a useful future
consultation as a proposal if it is outside the remaining authority.

## Output

State the current stage, evidence, one recommended next Skill or command, the
expected handoff artifact, and any real blocker. Mark unknown material facts
with `%% NEEDS_INPUT: ...`. Distinguish implementation from verification and
verification from delivery. Do not claim an unrun check passed.

For consultation also state the question/context boundary, authorization basis,
channel, actual known provider/model settings, attempt/result status and evidence
limits. Distinguish a supported suggestion from a confirmed fact and not-run
from failed/unknown. A non-ok or invalid answer is not a successful decision.
Reuse the current conversation/report; do not create a file just to navigate.

Navigation is complete when its current-state recommendation and limitations
are clear. Consultation is complete when the one authorized attempt is reported
accurately, or the missing authority/capability is explicit as BLOCKED/INCOMPLETE
with `%% NEEDS_INPUT: ...`. Review sources and scope statically; do not run the
recommended commands to validate a recommendation or claim underlying work done.
