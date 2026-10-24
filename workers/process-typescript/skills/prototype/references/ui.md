# UI prototype

Read for a question about layout, information hierarchy or interaction. Keep the
primary task/output and the prototype Skill's workspace and tool boundaries.

## Pick the surface
- State the user's decision and the interaction needed to judge it. Inspect nearby
  pages and shared styles. Reuse the project's typography, colors and components.
- When the task permits, compare variants within the existing page's development
  surface so real content density and surrounding navigation remain visible.
  Otherwise use an isolated, clearly named prototype file or development route
  that follows existing conventions. Do not add a production entry just to preview.
- Use representative sample data or permitted read-only data. Keep authentication
  and data scope intact; send mutation actions to an in-memory stub by default.
  Do not wire the prototype to real deletion, payments or other external effects.

## Build something the user can try
- For a layout comparison, normally show up to three meaningfully different
  variants. Change hierarchy, arrangement or interaction, not just colors.
  A narrow interaction question may need only one prototype.
- Keep variants on one surface with a visible development-only switcher and label.
  Where supported, a variant query parameter makes the chosen view shareable and
  stable after reload. Keyboard shortcuts must not capture input-editing keys.
- Make the relevant action observable: show a stub save result, validation state,
  expanded details or next step. Add only the behavior needed to answer the question.
  Label mocked behavior so it cannot be mistaken for a real backend operation.
- Prefer the project's existing preview command or a self-contained local artifact.
  New servers, ports, browser sessions, dependencies and background processes still
  require the supplied task/tool authority; a Skill cannot grant those capabilities.

## Compare and deliver
- When preview is authorized, use the actual available preview/browser mechanism
  and record which views and interactions were checked. Otherwise return runnable
  files and exact entry instructions with preview explicitly marked not run.
- Give the actual artifact/route, variant keys, interaction steps, stub behavior
  and reset/stop instructions. Record a running preview's known location and
  lifetime only if it was actually started. Do not invent screenshots or approval.
- Explain the trade-off each variant tests and what remains unverified. Ask for a
  choice through the existing question/handoff contract when a human must decide.
- Keep normal rendering and authentication intact. Prototype controls and losing
  variants are not production features; promotion and cleanup need the normal
  task decision and write scope, not an automatic merge or deletion.
