---
name: fd-package
description: After fd-workflow completes local delivery and Complete archive, identify changed programs, plugins and skills, recompile affected native targets, then ask before creating snap-version packages, publishing and installing them.
---

# FD Package

Use this skill as the host's follow-up to a fully completed fd-workflow, or
when the user asks to package a completed FD. It is a separate post-FD stage.
Do not run it for planning-only, implementation-only, review-only, partial,
no-merge, blocked, or failed FD workflows. A skill provides agent guidance;
it does not register a CLI hook or a background job.

Keep user-facing messages in the repository's required language. Use the
current repository's instructions and build/package documentation. Creating
this skill does not run its workflow. An ordinary FD request alone does not
authorize final builds, publishing or installation. When this follow-up is
requested or enabled by the user, it authorizes the focused local compilation
below; the publish/install decision is still required for each package batch.

## 1. Confirm completion and identify changes

- Resolve one FD from the current conversation. Read its archived FD,
  completion evidence and delivery metadata. Require successful local
  delivery, worktree cleanup, Complete archive and archive commit. For an
  explicitly approved primary-workspace FD, use its documented completion
  path. Do not close, repair or merge the FD from this skill.
- Work from the delivered parent checkout. Identify the FD's actual delivery
  commit and its first parent, or the exact focused implementation commits
  for primary-workspace work. Inspect that diff, including renames/deletions.
  Do not use the archive commit alone, the entire branch, or all dirty files
  as the implementation diff. If the range is unclear, stop and explain.
- Check the current checkout and HEAD. Do not include unrelated uncommitted
  changes or silently package unrelated later changes. Require a known,
  clean source revision covering the reviewed delivery; explain any
  difference before asking for package approval.
- Map changed files to their owning programs, plugins, skills and shared
  resource packages using the current package catalog, manifests and build
  definitions. Include consumers affected by shared native code or generated
  assets. Trace shared skill documents to their resource package owners.
  Deleted packages are removals, not targets to recreate or uninstall.
- Show a compact table: changed component, evidence, compile target or
  "no compilation", package target, and affected dependencies. Docs-only,
  Python plugin and Markdown skill changes usually need packaging only;
  inspect their actual build definitions before deciding.

## 2. Recompile only what needs compilation

For AIW, read `docs/usage/aiw-pkg.md`, `build.py`, and the relevant
`scripts/package_definitions.py` or individual package definitions. Prefer
the smallest supported component targets. Do not guess target names from
directory names or use `all` when a narrower target exists.

Run `python build.py compile <target>` only for affected native/generated
targets. Compilation writes the fixed `snapshot` output and provenance to
`bin`; it takes no version argument. Record the exact source revision and
commands. A changed package version does not require recompilation, but
changed native source does: snapshot reuse does not detect source edits.
For a skill or source-only plugin, report that no compilation is needed.

This is requested output preparation, separate from FD validation. Do not
add tests, linters, final package builds, downloads, network calls or broad
validation. Respect explicit command limits. If focused compilation needs
additional permission under active rules, stop that step and explain the
exact command. On failure, preserve results and report the failed stage;
do not publish/install or loop retries. If nothing packageable changed,
report that result and stop without asking to publish.

## 3. Present one concrete publish/install decision

Before asking, resolve the package targets, exact dependency/member closure,
configured publication repository and installation destination from local
configuration. Do not print secrets or invent a destination. Missing or
ambiguous configuration stops this stage until the user supplies it.

Choose one fresh package version such as `snap-20261019-032500123456Z`,
using the actual current UTC timestamp with sufficient precision. Use the
same explicit version for this batch and its matching dependency packages.
Never overwrite an existing version with changed contents. Keep native
compile version `snapshot`; do not change source version files or Git tags.

Show the source revision, changed packages, required dependency packages,
compile results, proposed version, publication repository, install root,
and exact planned build/publish/install commands. State if dependencies
must also be packaged or installed. Then ask one question equivalent to:
"Publish and install these packages as <version> at these locations?"

Wait for an explicit affirmative answer tied to this batch. A negative,
ambiguous or absent answer means stop: do not build final ZIPs, publish or
install. Do not poll or schedule reminders. A later explicit yes may resume
only after confirming that source, targets and destinations still match;
material changes require an updated decision. No answer is never approval.

## 4. Build, publish and install after yes

Run the reviewed commands sequentially, preserving the approved revision,
targets, version and destinations. For AIW, the supported command shapes are:

```text
python build.py build <target> --version <snap-version>
python build.py publish <target> <snap-version>
python build.py install <target> --dest <install-root>
```

Build each required package closure only as needed. Build may compile
missing native output automatically: inspect its closure before approval
and disclose any extra compilation. Publish consumes existing versioned
ZIPs. Use the configured local `AIW_PACKAGE_REPO`; do not switch to a remote
service or enable network as a fallback.

Install consumes an existing target release manifest. Immediately before
installation, inspect that manifest and require its version, package paths
and source batch to match the approved build. Do not accidentally install
a newer "latest" build. If this cannot be guaranteed, stop before install.

Installing a skill's canonical package and making it available to an agent
are distinct operations. Include `aiw pkg skill install <name> --scope
<project-or-user>` in the approved plan when agent skill installation is
needed, using the scope already configured or selected by the user. Inspect
its source selection so it installs the approved packaged skill. Do not
replace unrelated unmanaged skill copies.

Stop on the first failed build, publication or installation. Report any
earlier successful operations and remaining work; do not claim atomic
rollback, uninstall other packages, retry under a new version, or elevate
permissions. Publication approval does not authorize Git push, tags,
production deployment, tests, or unrelated packages.

Report changed components, compile decisions and results, package version,
artifact locations, publication/install destinations, commands actually run,
skipped checks, and any partial failure. If the user declines or does not
answer, report "stopped before packaging, publication and installation".
