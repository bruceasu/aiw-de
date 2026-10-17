---
name: codebase-design
description: Design module responsibilities, interfaces and test seams; use for scoped FD design, structural alternatives or shared design vocabulary, independently of code review.
---

# Codebase design

Follow `skills/reviewed-skill-contract.md`, `skills/work-management.md` and
repository rules. Use for a concrete interface, responsibility, state/error
contract or testability question, not as a mandatory step for every FD or
review. Return design guidance only: no implementation, Git writes or runtime
validation. This is an independent method, not a code-review subworkflow.

## Inputs and working vocabulary

Resolve the requested scope, actual callers, required behavior, dependency and
compatibility constraints, and relevant domain terms/ADRs. Missing business
rules are `%% NEEDS_INPUT: ...`, not an opportunity to infer acceptance from
the current implementation. Use project language; define these terms when
useful without prohibiting service, API, component or domain boundary:

- **Module:** a coherent behavior and its implementation, at an appropriate
  scale such as function, package or service.
- **Interface:** what a caller must know, including inputs/results, invariants,
  call ordering, errors, configuration and relevant performance constraints.
  A module may expose different interfaces to different roles.
- **Depth:** useful behavior behind an understandable interface, not a ratio
  of implementation lines to declarations or a count of public methods.
- **Seam:** where behavior can be substituted or observed for a specific need;
  distinguish this from a domain boundary when the distinction matters.
- **Adapter:** an implementation serving a dependency role at a seam.
- **Locality:** how much a change or rule can remain in one place.
- **Leverage:** how much useful behavior a caller gains without extra knowledge.

## Design decisions

Follow actual caller scenarios, including failure/recovery, rather than starting
with abstract layers. Identify which complexity the interface hides and which
facts still leak into every caller. Use the deletion thought experiment: does
removing a layer eliminate unnecessary complexity or spread necessary behavior
back into callers? A thin adapter can still earn its cost through isolation.

Prefer simple interfaces that preserve necessary information; fewer methods or
parameters are not inherently better if they hide invalid states or ordering.
Inject variable/side-effect dependencies where it improves control, but do not
add ports solely to satisfy a pattern. Multiple real adapters support a seam;
a single adapter can also be justified by protocol, ownership, isolation or an
observed verification need. Do not invent a second adapter as justification.

Use observable behavior at the chosen interface as a primary test surface.
Retain necessary internal, integration, contract and end-to-end checks for
risks that interface-level tests cannot detect. A test double does not prove
the real integration. Testability does not authorize creating/running tests,
deleting existing coverage or expanding the FD. Keep pure rules separate from
side effects when useful, while designing explicitly for unavoidable effects.

## Conditional methods

- Read [DEEPENING.md](DEEPENING.md) when comparing structural consolidation and
  dependency/test strategies. Do not load it for a simple signature question.
- Read [DESIGN-IT-TWICE.md](DESIGN-IT-TWICE.md) for materially different interface
  alternatives. Current-session comparison is the default. Only with explicit
  independent-analysis authority may the host delegate, at most two bounded
  static agent passes total for the comparison; no recursive delegation,
  tests, branches/worktrees or additional review rounds are granted.

## Output and completion

Return the proposed responsibility/interface, a representative caller and error
scenario, hidden complexity, alternatives and trade-offs, compatibility impact,
test seam/evidence limits and remaining questions. Scale detail to the actual
decision. In an FD, place confirmed engineering decisions in that FD and link
existing specs/ADRs; outside it, return guidance without requiring an FD.

Complete when the requested design question has a supported recommendation or
an explicit BLOCKED/INCOMPLETE conclusion and next action. Check caller fit,
constraints and source references statically. An illustrative sketch is not
implemented or verified code. Planner owns design readiness; fd-review owns
independent implementation acceptance. Do not emit events or change status.
