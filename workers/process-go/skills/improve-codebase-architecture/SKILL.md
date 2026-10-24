---
name: improve-codebase-architecture
description: Investigate concrete structural friction in a bounded code area and propose a practical improvement with compatibility and migration costs.
---

# Improve Codebase Architecture

Use when an assigned task asks why changes are hard across a specific code area
or asks for structural improvement options. Do not scan the whole repository
because this Skill is present. Keep the primary task, role, read-only setting,
available tools and existing result contract.

## Find the friction

Take the named area, example change or repeated problem, constraints and desired
outcome as inputs. Use permitted code, history or supplied examples to locate
the friction. If no real example is available, label the concern as a hypothesis
and state what evidence would confirm it; do not invent maintenance history.

1. Trace a representative change through the affected modules. Record where
   the same rule is repeated, private details leak, state has competing owners,
   or a caller must know hidden ordering to use an API safely.
2. Separate a structural cause from naming, formatting or a local bug. Explain
   the cost in concrete terms: repeated edits, inconsistent behavior, difficult
   failure handling, or an unclear test boundary.
3. Map only the responsibilities and dependencies needed to explain that cost.
   Name the boundary that could hide the rule and the callers it would simplify.
   Check whether moving code would also move complexity to those callers.

## Compare useful changes

Compare keeping the current structure with the smallest local improvement. Add
a broader option only if the local option cannot solve the observed problem.
For each option, describe:

- which rule or state becomes owned in one place and what callers stop knowing;
- affected interfaces, existing behavior and error paths;
- implementation effort, migration sequence, compatibility and rollback cost;
- how an observable scenario would distinguish improvement from code movement.

Prefer a change that hides meaningful complexity behind a clear interface. More
interfaces, fewer lines or a fashionable pattern are not proof of better design.
Keep existing conventions unless evidence shows why they cause this problem.
Do not infer runtime speed or reliability from static structure alone.

## Recommend and hand back

Return a concise diagnosis, evidence locations, options, recommendation, smallest
next slice, risks and unresolved questions in the primary task's existing output.
Separate observed problems, proposed benefits and unverified outcomes. Completion
means the proposal explains how a representative change and failure case improve,
with an affordable migration path; a generic list of design principles is not enough.

This method does not authorize refactoring, dependency changes, new work items,
agents, previews, benchmarks or repository-wide checks. Implement only the
already assigned slice when write authority exists. Otherwise deliver the
proposal; report a material scope or contract gap through the normal task path.
