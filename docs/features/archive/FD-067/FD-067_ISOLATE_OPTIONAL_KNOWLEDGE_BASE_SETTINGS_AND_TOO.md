# FD-067: Isolate optional Knowledge Base settings and tools

**Status:** Complete
**Revision:** 2
**Review policy:** Self
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

Knowledge Base configuration and action handling still live in the secretary-wide settings and tools modules. The new `knowledge/` package contains only the HTTP client, so the feature remains difficult to locate and its host adapters still know the implementation details. Continue FD-066 by placing Knowledge Base settings and tool behavior inside the optional package without changing the settings API or private data shape.

## Options and decision

1. **Move feature logic behind host adapters (selected).** Put configuration, capability checks and API tool execution under `knowledge/`; keep small adapters in the secretary-wide modules so existing runtime and UI contracts remain stable.
2. Keep all logic in `assistant_settings.py` and `assistant_tools.py`. This leaves the requested feature boundary incomplete.
3. Make the whole secretary import the Knowledge Base package. This would make an optional feature a startup dependency.

## Solution

Move Knowledge Base settings behavior to `knowledge/settings.py` and search/read/note behavior to `knowledge/tools.py`. `AssistantSettings` and `DailyTools` delegate through thin methods. Import feature logic lazily at action time; if `knowledge/` is absent, the existing compatibility boundary reports unavailable, keeps the feature disabled and allows other secretary functions to run. Preserve settings keys, credential storage, API routes and tool action schemas.

## Scope

**In scope**

- `knowledge/settings.py` and `knowledge/tools.py`.
- Thin delegation in `assistant_settings.py` and `assistant_tools.py`.
- Availability guard and imports required to keep the package optional.

**Out of scope**

- Knowledge Base API or settings schema changes.
- UI redesign, service lifecycle, data migration, dependencies or runtime packaging.

## Work items

- [x] 1.1 Define the first independently reviewable outcome and its acceptance evidence.
- [x] 1.2 Move Knowledge Base settings/configuration implementation under `knowledge/` and delegate from `AssistantSettings`.
- [x] 1.3 Move Knowledge Base action execution under `knowledge/` and delegate from `DailyTools`.
- [x] 1.4 Compile and statically review optional import behavior, settings persistence boundaries and action delegation.

## Acceptance

- Knowledge-specific configuration and tool implementation are located under `personal-secretary/knowledge/`.
- Existing `/api/settings`, `/api/knowledge/check`, credential file and model tool schemas remain compatible.
- When `knowledge/` is absent, service imports succeed, knowledge reports unavailable and no knowledge actions are offered; other settings and tools continue to work.
- The local compile-only command and targeted static review pass. No runtime behavior is inferred from static evidence.

## Verification

- `personal-secretary/scripts/compile-secretary-desktop.ps1` plus one static path/import review.
- Do not run tests, service/GUI, network or packaging actions without separate authorization.
- Passed: `powershell -NoProfile -ExecutionPolicy Bypass -File personal-secretary/scripts/compile-secretary-desktop.ps1` (Python in-memory compile, Node syntax checks and PowerShell parsing only).
- Static review confirmed the existing settings keys, credential file, settings/check routes and tool action names are preserved behind adapters; the root compatibility module degrades to unavailable when package imports are missing.
- Not run: unit/integration tests, missing-package runtime simulation, service/GUI launch, network or actual PyInstaller package generation. Package runtime behavior remains unverified.
- Review policy Self: the change preserves existing settings and HTTP/tool contracts; compile-only and static evidence found no unresolved compatibility question.

## Sources

- `docs/features/archive/FD-066/FD-066_MOVE_PERSONAL_SECRETARY_DESKTOP_FEATURES_UNDER_P.md`
- `personal-secretary/assistant_settings.py`
- `personal-secretary/assistant_tools.py`
- Issue: none

**Completed:** 2026-10-31
