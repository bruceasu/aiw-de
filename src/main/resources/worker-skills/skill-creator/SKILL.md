---
name: skill-creator
description: Create a new reusable Agent Skill from a defined task and examples. Use when asked to draft a Skill or decide which files its package needs.
---

# Skill Creator

Adapted for AIW from the OpenAI Codex skill-creator Skill. The bundled license
is in LICENSE.txt.

## Define the job

1. Identify the outcome, target runtime, typical requests, needed inputs, and
   a result the user can check. Ask about an unknown only when it changes the
   Skill's behavior, compatibility, or authority.
2. Choose a short lowercase name with hyphens. Write a description that says
   what the Skill does and when to use it. Add a boundary when a nearby request
   could otherwise trigger it by mistake.
3. Write a focused SKILL.md with name and description in YAML front matter.
   Explain the decisions, steps, inputs, and completion checks that matter.
   Keep optional detail out of the main path.

## Build the smallest useful package

- Keep all guidance in SKILL.md when it fits. Add a reference only when a
  separate document helps a specific branch of the work. Link that document
  and say when to read it.
- Add a script only when a repeatable operation needs it and the target runtime
  has a permitted way to run it. State its inputs, expected output, and failure
  behavior. Supply assets only when they are used in the result.
- Platform Skills saved through the catalogue editor contain a single
  SKILL.md. Keep such drafts self-contained. Confirm package support before
  proposing separate files for another delivery path.
- A Skill describes work. It does not grant file access, tools, permissions,
  installation, or publication rights.

## Check and deliver

Check the front matter, links, required inputs, output, and stopping
conditions. Choose a representative request that should use the Skill and
one nearby request that should not. If authorized, try them and record the
observations. Report structural checks separately from runtime results.

Return the draft and any necessary file contents in the assigned output
format. Write to a workspace only when the task grants that access.
