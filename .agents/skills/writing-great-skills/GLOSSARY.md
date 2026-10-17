# Skill Authoring Glossary

Read this file only when a term used by [Writing Great Skills](SKILL.md) is
unclear. These definitions support decisions; they are not mandatory labels
for every skill or a substitute for examples and evidence.

## Skill

Reusable guidance for a defined class of tasks, with useful activation,
execution, resource, and completion boundaries. It may contain instructions,
references, scripts, or assets when those resources serve the task.

## Discoverability and invocation

Discoverability is whether a host exposes a skill to an agent or user.
Implicit selection is a host choosing it for a matching request. Explicit
invocation is the user requesting it. Workflow loading is a declared process
reading its instructions or reference. These mechanisms depend on the host;
metadata presence alone does not define all of them.

## Predictability

Consistent decisions and reliable boundaries where the task requires them.
It does not require identical reasoning paths or prove useful results.

## Progressive disclosure and context pointer

Keep universally needed decisions in the entrypoint; put conditional detail
in a reference. A context pointer is a link that states when the agent should
read that material. Moving text out is useful only if the right run finds it.

## Branch and granularity

A branch is a distinct use of one skill. Granularity is how much independently
useful work belongs to one skill. Split for distinct ownership or activation;
use references when only conditional detail differs. A router is worthwhile
only when it reduces real selection or maintenance difficulty.

## Completion criterion

An observable condition for the requested scope. It should prevent premature
completion without demanding unrelated work or unauthorized validation.

## Single source of truth

One maintained owner for each requirement or decision. References and
platform adapters should not create competing copies of the same rule.

## No-op, leading word, and negation

A no-op is guidance that adds no useful decision beyond the agent's existing
behavior; this is often a hypothesis until observed. A leading word is a
short familiar cue. Use one when it conveys the required meaning accurately,
not as a replacement for a formula, boundary, or acceptance condition.
Positive instructions can clarify the desired behavior; explicit prohibitions
remain appropriate for concrete permission, compatibility, or safety limits.

## Evidence

Structural evidence concerns files and instruction consistency. Behavioral
evidence concerns observed selection and execution. Comparative evidence
concerns whether a version improves representative task outcomes and cost.
State which exists, how it was obtained, and what remains untested.
