# Authoring Principles

Read for creating, improving, or reviewing the content of a skill.

## Contract before structure

Define the useful outcome and representative requests before choosing folders
or steps. State meaningful exclusions, necessary inputs, outputs, decision
boundaries, and completion. Use concrete acceptance examples for ambiguous
rules. Do not invent a universal process from one incident or preference.

## Instructions that earn their place

Include guidance that changes a decision, supplies non-obvious context, or
protects an actual boundary. Prefer clear verbs and examples over slogans.
Match specificity to risk: allow judgement for exploration; prescribe exact
operations only when deviation has a concrete consequence. Distinguish a
known defect from a hypothesis about model behavior.

Compress duplicated meaning only when the replacement preserves it. A familiar
keyword can help, but cannot replace required precision or a checkable result.
State the desired action clearly; keep explicit prohibitions for real limits.
Treat claims that wording is redundant or harmful as hypotheses when there
is no observed evidence. Do not prune required authorization or compatibility.

## Ownership and loading

Keep essential task decisions in SKILL.md. Put substantial conditional detail
in references with explicit read conditions. Split into another skill only
when it has an independently useful trigger or owner. Do not create routers,
resource folders, scripts, or glossaries without a concrete use.

Keep task semantics platform-neutral where practical. Platform adapters own
host-specific metadata and tools. A shared rule has one maintained owner;
references link to it instead of carrying competing copies. Inspect callers
before moving or deleting resources, and preserve contracts unless changed
within the user's request.

## Usable completion

Choose checks that establish a meaningful result. Static inspection can find
broken references or contradictory instructions; it cannot by itself establish
trigger accuracy or task improvement. Report actual evidence and uncertainty.
A small, justified edit may finish without a new scaffold or runtime eval.
