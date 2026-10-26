# Dependency and path contract

Read once when first loading this skill in a repository, and again if its
installation or dependency resolution changes. This package is not standalone:
it supplies workflow instructions, not the `aiw` executable or shared contracts.

## Path resolution

- Markdown links resolve relative to the containing file.
- Bare `roles/...` and `references/...` paths in SKILL.md are skill-root-relative.
- Bare `skills/...` names denote installed shared resources, not a directory
  bundled inside this skill. Resolve them using the repository's documented
  installation/catalog first. Record the verified paths; do not infer a root
  from the current working directory or silently substitute unrelated files.
- Repository artifacts (`docs/...`, `.ai/...`, `.wt/...`) are relative to the
  applicable repository/worktree root. Workspace selection follows preflight.
- Bare skill names refer to separately installed skills, not files in this ZIP.

## Required for the applicable workflow

| Dependency | Load/use condition |
|---|---|
| Repository instructions | Before any repository work |
| `skills/reviewed-skill-contract.md` | Before workflow execution |
| `skills/work-management.md` | Before workflow execution; lifecycle, ownership and authorization |
| `skills/workflow-methods.md` | Only relevant splitting, specification, review, domain or decision section |
| `skills/testing-methods.md` | Only requested test support, relevant sections |
| Installed `aiw` CLI | Before FD event, status, worktree, delivery or archive operations |
| `docs/templates/TEMPLATE.md` | Before creating a new FD |
| Blocker Markdown/JSON templates named in blockers.md | Before producing a blocker pair |
| `fd-review` | Before independent implementation review |
| `fd-test`, `tdd` | Selected independent testing / suitable TDD scope |
| `resolving-merge-conflicts` | Actual delivery conflict, before bounded recovery |
| `fd-release-gate` | A real release-readiness request, never routine local completion |

Planning/testing references also name conditional design/diagnosis methods;
load them only under their documented conditions. Their existing unavailable-
skill fallbacks remain applicable and must not be described as an invocation.

If a required contract, executable, template or independent capability is
missing, ambiguous or incompatible, identify the exact dependency and stop the
dependent operation; continue only permitted independent analysis. Do not
invent a contract, synthesize receipts, create permissive fallback authority,
or install/run missing tools without applicable permission. A blocker report
cannot require pretending an unavailable template or schema was validated.

The repository's narrower authority remains binding. A documentation/CLI
mismatch is a Gate to diagnose, not permission to select the more permissive
behavior. This revision does not verify the external contracts or CLI.
