# Compare alternative interfaces

Read for a material interface choice with credible alternatives, not every
small edit. Follow [SKILL.md](SKILL.md) for scope and authorization; read
[DEEPENING.md](DEEPENING.md) only if dependency strategy is part of the question.

## Frame and compare

State the actual callers, required behavior, error/state rules, compatibility
and dependency constraints. Produce two meaningfully different designs in the
current session by default; a trivial decision does not need extra alternatives.
For example, compare a caller-oriented operation with explicit stepwise control
only when both can meet the same requirements. Do not force a flexible design
that adds use cases the user did not request.

For each option give an illustrative interface, caller/failure example, hidden
complexity, dependency/test seam, migration cost and trade-offs. Compare how
knowledge and changes remain local and what each caller must understand. Make
a recommendation, preserving unresolved material questions in the current FD
or discussion. A sketch is neither implemented nor runtime-verified code.

## Optional independent perspectives

Only when independent analysis is explicitly authorized and useful, replace
the current-session comparison with at most two bounded static agent passes
total. Do not reset the allowance through another method or recurse into more
delegation. Give each the same requirements and evidence with a different
design emphasis, not an instruction to produce a predetermined winner.

Agents return proposals only: no tests, implementation edits, branches,
worktrees, runtime probes or lifecycle operations. Missing delegation capability
is recorded; continue locally when allowed without claiming independence.
Show material trade-offs to the user when a decision is needed, but do not add
a selection checkpoint for a routine decision the active FD already authorizes.
