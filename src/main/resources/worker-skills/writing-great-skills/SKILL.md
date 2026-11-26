---
name: writing-great-skills
description: Review, improve, or adapt an Agent Skill for clear triggers, useful instructions, runtime compatibility, and testable results.
---

# Writing Great Skills

Adapted from the AIW repository's writing-great-skills guide.

## Choose the mode

- **Review:** Report concrete findings from the supplied Skill and give
  specific changes. Keep the supplied Skill unchanged unless asked to edit it.
- **Improve:** Fix the demonstrated problem and preserve unrelated behavior.
- **Adapt:** Preserve the task and result while changing platform-specific
  parts. Call out any required capability the target platform lacks.

## Make the instructions useful

1. Read the supplied Skill and relevant references. Identify the trigger,
   inputs, output, authority limits, and completion conditions. Use examples
   of real requests to spot ambiguous boundaries.
2. Keep instructions that change decisions, carry useful context, or protect
   a real constraint. Match the level of detail to the risk. Replace vague
   advice with concrete actions and keep significant exceptions.
3. Keep routing and shared constraints in the entry file. Move substantial
   conditional material to a reference when the delivery format supports it.
   Say when to read each reference. Keep a catalogue-only Skill self-contained.
4. Preserve required metadata, valid references, and real permissions. Mark
   unresolved facts and unsupported features instead of guessing.

## Report what was checked

For each finding, identify the relevant passage, its effect on work, and a
specific change. For an edit, include the revised content. Check scope,
metadata, references, permissions, and completion evidence.

When authorized, compare the revised Skill on a normal request and a nearby
request where the Skill should not trigger. Record actual observations and
checks still pending. Static review can establish structure and consistency.
Claims about execution quality or trigger accuracy need runtime evidence.
