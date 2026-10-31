# FD-066: Isolate optional personal secretary features

**Status:** Complete
**Revision:** 6
**Review policy:** Self
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

The personal-secretary desktop experience and Knowledge Base client are mixed into the service root. This makes optional feature boundaries hard to locate and means a missing Knowledge Base module can prevent the secretary from starting. Isolate desktop code under `personal-secretary/pet/` and Knowledge Base integration under `personal-secretary/knowledge/`, while preserving existing desktop launch commands and allowing the service to run when the Knowledge Base package is absent.

## Options and decision

1. **Use feature packages with compatibility boundaries (selected).** Desktop implementation, desktop-only dependencies/assets and full launch scripts live under `pet/`; the Knowledge Base API client lives under `knowledge/`. Small root launchers and a guarded client compatibility module preserve existing entrypoints. Missing Knowledge Base code disables only that feature.
2. Move everything and remove root entrypoints. This is simpler internally but breaks documented commands and user scripts.
3. Redesign startup as installed Python console packages. This expands packaging and environment changes beyond the requested directory organization.

## Solution

Create `personal-secretary/pet/` as the desktop package. Move the Tk/Qt windows, desktop client/startup/Win32/pet/i18n modules, stop-service implementation, Qt requirements, assets and full desktop launch scripts there. Keep root compatibility launchers for documented entrypoints and keep the root requirements file as an include shim. Move environment setup, compile-only and Windows packaging scripts into `personal-secretary/scripts/`, with root compatibility launchers where they existed. The Windows packaging script creates a one-folder service-plus-Qt desktop build using only an already installed PyInstaller and downloads no dependencies. Move the Knowledge Base HTTP client into `personal-secretary/knowledge/` and leave a small guarded root compatibility module for existing service imports. When that package is absent, settings report it unavailable, keep it disabled in the public state, return a non-error connection status and omit knowledge tools from model context. Preserve existing service and desktop contracts.

## Scope

**In scope**

- The `personal-secretary/pet/` package, implementation files, desktop launch scripts, desktop requirements and desktop assets.
- The `personal-secretary/knowledge/` package and optional availability handling in the settings/tool boundary.
- Environment setup, compile-only and Windows one-folder packaging scripts under `personal-secretary/scripts/`.
- Minimal root forwarding entrypoints and client compatibility module that preserve documented commands/imports.
- Dependency setup, compile-only script and documentation path updates.

**Out of scope**

- Changes to desktop behavior, UI, Knowledge Base API protocol, service lifecycle or data storage.
- Installing packages, generating a distributable during this change, changing Web/browser behavior or moving shared service modules.

## Work items

- [x] 1.1 Move desktop implementation, desktop-only dependencies and assets into `personal-secretary/pet/`; preserve the package import boundary.
- [x] 1.2 Preserve desktop launch and stop compatibility; relocate environment setup and compile-only scripts into `personal-secretary/scripts/`.
- [x] 2.1 Move the Knowledge Base client into `personal-secretary/knowledge/` and make package absence a supported disabled state.
- [x] 2.2 Update settings status and assistant context so unavailable Knowledge Base tools are not exposed and normal startup continues.
- [x] 3.1 Add a Windows one-folder packaging script for the service and Qt desktop; require an already installed PyInstaller.
- [x] 3.2 Update documentation and perform compile-only plus static path/import review.

## Acceptance

- Desktop implementation modules, Qt dependency declaration and desktop assets are located under `personal-secretary/pet/`.
- Existing documented root launch commands and direct root Python entrypoints continue forwarding arguments to the relocated implementation; the implementation can also be launched from `pet/`.
- The personal web service remains importable without Qt, and Qt remains optional unless desktop dependencies are explicitly installed.
- The Knowledge Base client implementation is located under `personal-secretary/knowledge/`; if that directory/package is absent, the service starts, reports Knowledge Base unavailable, keeps it disabled, and does not expose its tools.
- Setup and compile-only scripts reference the relocated files; docs contain no stale implementation paths.
- The Windows packaging script targets the service-plus-Qt desktop entrypoint, installs no dependencies, and writes an onedir build to its configured output directory.
- Static import/path review and the repository's desktop compile-only check pass. No GUI/service/runtime or dependency installation is implied by compile success.

## Verification

- Feature packages and compatibility shims preserve existing desktop entrypoints and Knowledge Base imports; no protocol or persistent-data changes were made.
- Passed: `powershell -NoProfile -ExecutionPolicy Bypass -File personal-secretary/scripts/compile-secretary-desktop.ps1` (Python in-memory compile, Node syntax checks, PowerShell parsing only).
- Passed: `git diff --check`, `git diff --cached --check`, and targeted stale-path/import search. Remaining root `knowledge_client` and desktop asset references are intentional compatibility paths or relocated implementation references.
- Not run: tests, application/service launch, GUI, PyInstaller packaging, dependency installation, network access or distributable generation. The standalone package's runtime behavior remains unverified until the script is run with the required local dependencies.

## Sources

- `personal-secretary/README.md`
- `docs/DESKTOP-STARTUP.md`
- `docs/APPEARANCE.md`
- Issue: none

**Completed:** 2026-10-31
