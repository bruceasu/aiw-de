# FD-130: Improve console task and workflow usability

**Status:** Complete
**Revision:** 3
**Priority:** Medium
**Review policy:** Self
**Evidence policy:** Markdown

## Problem

The console exposes useful capabilities through unclear labels and cramped forms. Users cannot easily tell how to select Skills, reuse workflows, provide run-specific input, configure commands, or distinguish stable team members from Worker processes. The Wish Pool is not the first navigation item, and several visible labels/help texts are not translated consistently.

## Options and decision

Keep the existing APIs and execution rules, and make the current UI easier to understand. Use direct task-specific text for a custom method when needed. Do not add a network Skill installer until a trusted source and validation contract are established.

## Solution

Improve the Wish Pool, simple-task, workflow editor, Agent Factory/team-member, and professional-task preparation pages. Use existing platform Skills and saved workflow definitions. Make graph editing direct for common operations, clarify optional model defaults versus allowed model profiles, and add concise Chinese, English, and Japanese labels/help. Keep Worker as the execution process and Team member as the persistent team identity.

## Scope

UI templates, CSS, and browser JavaScript only. Preserve API payloads, saved workflow semantics, tool permissions, member settings, and execution behavior. External Skill downloads and new server-side Skill storage are excluded until their trust and validation behavior is defined.

## Work items

- [x] 1.1 Put Wish Pool first and improve its page layout; complete missing localization in touched UI.
- [x] 1.2 Make Skill selection visibly multi-selectable and explain installed Skills; allow writing a task-specific method without uploading a file. Clarify why arbitrary Skill downloads are not offered.
- [x] 1.3 Replace the hidden step form workflow with double-click node editing, drag-to-add and drag-to-connect, and clear node deletion; clarify run input, reusable definitions, and command profiles.
- [x] 1.4 Clarify Team member identity versus Worker process, explain Create member and optional model defaults/allowed profiles, and add spacing to Agent Factory panels.
- [x] 1.5 Rename and explain professional-task preparation so users know when it is useful and when the Secretary handles it.

## Acceptance

- Wish Pool is the first task-facing navigation entry and its main content uses available width cleanly.
- Users can select multiple Skills, enter a task-specific method directly, and understand the difference between a task method and an installed/shared Skill.
- A workflow node opens an editor on double-click; nodes can be added and connected by dragging; a selected node can be deleted. Saved definitions remain reusable, and each run still uses its frozen definition.
- Forms explain run-specific input, saved reference material, and optional restricted command profiles in plain language.
- Agent Factory explains that creating a member creates a stable identity. Model and effort are optional defaults; allowed profiles define the switching range. Worker continues to mean an execution process.
- Professional-task preparation is clearly named, localized, and explains its manual setup/diagnostic purpose.
- Changed UI text has Chinese, English, and Japanese translations. No API, permission, or persistence contract changes.

## Verification

- PowerShell loop ran `node --check <file>` on `workflows.js`, `team-members.js`, and `console-translations.js` — all passed; then `git diff --check` passed.
- `git diff --check` — passed; Git reported a line-ending normalization warning for `workers.html` only.
- Static self-review confirmed workflow payloads and permission checks are unchanged; node editing reuses the existing `next` and `branches` fields. Review policy is Self because this changes only UI presentation and client-side editing of existing fields.
- Tests, application/package builds, browser interaction checks, and network access were not run.

## Sources

- User feedback in the current conversation.

**Completed:** 2026-11-16
