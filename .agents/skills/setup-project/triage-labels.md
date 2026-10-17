# Optional intake labels

Map existing tracker labels only when the repository uses them. They describe
queue routing, not a second Issue/FD lifecycle or implementation permission.
No mandatory category/state label pair or new label setup is required.

| Suggested role | Tracker label | Meaning |
| --- | --- | --- |
| needs-triage | needs-triage | Routing recommendation needed |
| needs-info | needs-info | Specific source information missing |
| ready-for-agent | ready-for-agent | Scoped next action described; not FD readiness |
| ready-for-human | ready-for-human | A human decision/action is named; not merge approval |
| wontfix | wontfix | Explicit rejection with rationale; not merely deferred |

Respect established names, category labels and priority conventions. A request
for queue advice does not authorize applying labels or closing issues. Conflicts
are reported with the proposed correction, never guessed. Existing authorization
for a concrete action suffices; do not add a repeated approval checkpoint.
