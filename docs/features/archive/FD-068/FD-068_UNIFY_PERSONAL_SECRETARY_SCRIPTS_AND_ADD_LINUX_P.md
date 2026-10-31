# FD-068: Unify personal secretary scripts and add Linux packaging

**Status:** Complete
**Revision:** 4
**Review policy:** Self
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

Personal Secretary launch, stop, setup and packaging entry points are split
between its root, `pet/` and `scripts/`. Temporary compatibility wrappers keep
old paths alive after the feature code moved, so users cannot tell which path
is canonical. A Linux one-directory package script is also missing.

## Options and decision

Keep the wrappers, move only desktop commands, or make `scripts/` the only
script entry directory. The user explicitly chose one canonical location and no
compatibility wrappers. Put all service/desktop start and stop commands,
environment setup, compile checks and platform packaging in `scripts/`; keep
desktop implementation under `pet/` and application code at the package root.
Add a Linux PyInstaller one-directory script parallel to the existing Windows
script. Both packaging scripts use already-installed tools and never install
dependencies.

## Solution

Relocate the service launchers (`start.ps1`, `start.sh`), desktop launchers
(`start-desktop.ps1`, `start-desktop.sh`), Windows stop launcher and Python stop
implementation to `personal-secretary/scripts/`. Update their root-relative
paths to resolve from `scripts/`. Remove the root and `pet/` compatibility
wrappers. Add `package-linux.sh` with Linux `--add-data` separators and the same
one-directory service-plus-Qt entry point as Windows. Update docs, UI help text,
and the compile-only script to name the canonical commands.

This deliberately changes script invocation paths. The application Python
package/API and private data locations stay unchanged.

## Scope

In scope: script moves/removals, path fixes, Linux package script, compiler
coverage and related usage documentation. Out of scope: Python feature changes,
dependency changes/downloads, actually producing packages, installing packages,
and running the application or tests.

## Work items

- [x] 1.1 Consolidate canonical setup, service/desktop start and stop scripts under `personal-secretary/scripts/`; remove obsolete wrappers and fix path resolution.
- [x] 1.2 Add the Linux one-directory PyInstaller packaging script and update all current usage references and compile-only coverage.

## Acceptance

All supported setup/start/stop/package commands are located in
`personal-secretary/scripts/`; no compatibility script remains at the
personal-secretary root or in `pet/`. Invoking the moved launchers resolves the
secretary root and `pet/` assets correctly by construction. The Linux script
checks for installed PyInstaller and Qt WebEngine and fails with a clear message
without installing anything. Windows and Linux package scripts target the same
one-directory service-plus-Qt application. Documentation and the compile-only
script refer only to canonical paths.

## Verification

- Passed: `powershell -NoProfile -ExecutionPolicy Bypass -File personal-secretary/scripts/compile-secretary-desktop.ps1` (Python in-memory compilation, Node syntax checks, PowerShell parsing and Bash `-n`; exit 0).
- Passed: `git diff --cached --check`; reviewed moved script paths and current usage references. No canonical start/setup/stop script remains in the secretary root or `pet/`, and generated console commands point under `personal-secretary/scripts/`.
- Not run: tests, desktop/service runtime, PyInstaller package build, or Linux GUI execution. The Linux bundle's runtime library requirements and actual launch remain unverified; old invocation paths intentionally stop working.
- Self review: script locations intentionally change at the user's request;
  script options and service APIs stay the same, and current usage references
  are updated to the single canonical directory.

## Sources

- Issue: none

**Completed:** 2026-10-31
