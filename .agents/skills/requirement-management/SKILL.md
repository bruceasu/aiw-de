---
name: requirement-management
description: Continue a legacy AIW Requirement record as an Issue. Use issue-management for new discovery; preserve existing REQ IDs and artifacts.
disable-model-invocation: true
---

# Requirement Compatibility

Requirement is the former name for an AIW Issue. Use
`skills/issue-management/SKILL.md` for discovery, decisions, and promotion.
The existing `aiw req` command, `REQ` IDs, `docs/requirements/<id>/` records,
and Session evidence remain readable and writable for compatibility. Do not
rename or move them as part of a conversation.

For a linked Task, read `skills/work-management.md`. Its FD is the engineering
plan; an OpenSpec change is optional. Preserve existing approved scope and
handoff lineage. Do not repeat promotion or overwrite an authored FD.
