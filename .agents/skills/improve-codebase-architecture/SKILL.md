---
name: improve-codebase-architecture
description: Assess scoped architectural friction and compare worthwhile structural improvements; use for architecture exploration or a concrete structural obstacle in FD design.
disable-model-invocation: true
---

# Improve codebase architecture

Follow `skills/reviewed-skill-contract.md`, `skills/work-management.md` and
repository rules. Use for requested architecture exploration, or a structural
obstacle routed by the current FD. Ordinary changes and passing tests do not
require an architecture scan. Produce recommendations, not implementation or
approval. Keep the standalone entry; loading a method does not start agents,
rewrite domain records, create an FD or expand existing work.

## Focus the investigation

Start with the named module, affected FD paths or observed problem: repeated
cross-module changes, leaked rules, complex call ordering, hidden side effects,
or a behavior that cannot be observed at a suitable test seam. If scope is
missing, use bounded existing history to choose a focus or ask for it; do not
widen to the entire codebase simply because a hot spot is not obvious.

Read relevant domain terms and ADRs when present. Use codebase-design for actual
interface/seam decisions if available; retain the project's names such as
service, API or boundary and clarify ambiguous terms instead of banning them.
If a supporting Skill is missing, record that limitation and use the available
evidence; do not pretend it ran or block a straightforward analysis solely for
that reason. Static inspection by the current agent is the default.

## Compare proportionate options

Describe the concrete friction with file/caller evidence and its effect on
correctness, change cost or testability. Compare keeping the current design,
a local improvement and a broader redesign only when justified. For each
credible option identify:

- what callers must know and what complexity moves or becomes hidden;
- behavior/contracts to preserve, affected dependencies and failure paths;
- benefit, migration effort, compatibility and verification risks;
- existing tests/evidence that remain valuable and any real coverage gap.

Apply the deletion thought experiment: if a wrapper disappears, does complexity
vanish or return across callers? Do not use line count or the number of
interfaces/adapters as an architectural score. A thin adapter can still provide
useful isolation. A fake alone does not prove integration behavior. Do not
recommend a broad refactor merely to make tests easier.

Give a recommendation with evidence strength and unresolved questions. Revisit
an ADR only with a concrete changed assumption or cost, not a theoretical
preference. A small routine decision can proceed within the active FD; ask the
user only for material business ambiguity, scope or trade-offs that evidence
cannot settle. Grilling is optional for those questions, not a mandatory loop.

## Output and optional presentation

Default to concise Markdown in conversation or the existing FD/design report.
Do not create a second plan or report for every candidate. An in-scope approved
design stays in the current FD; an out-of-scope redesign remains a proposed
follow-up for the owning Issue/Planner, without automatically creating it.

When the user requests an HTML presentation, read
[HTML-REPORT.md](HTML-REPORT.md). Write one unique offline file under
`<workspace>/.ai/tmp/architecture-review-<timestamp>.html`; report its absolute
path. Do not load CDN resources, use the OS temporary directory, open a browser
or install dependencies automatically. File output does not grant validation or
implementation authority.

If independent alternative design is explicitly authorized, codebase-design's
optional method permits at most two bounded static agent passes within the
host's budget. Otherwise compare alternatives in the current session. Lack of
delegation does not require a fabricated independent result.

## Completion and boundaries

Complete when the focused friction, options, recommendation, scope and next
action are clear with sources and uncertainty. Use `%% NEEDS_INPUT: ...` and
BLOCKED/INCOMPLETE for a material missing decision; unaffected options may still
be discussed. Review references and scope statically. Do not run tests/builds,
network, formatters or validation commands to produce recommendations.

Return design decisions to Planner and diagnostic evidence to Worker. Domain
model changes use domain-modeling only for a real terminology/rule gap and
within the owning artifact's authority. fd-review retains the independent
implementation verdict; this Skill is neither a release nor delivery gate.
