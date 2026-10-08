"""Pure helpers for rendering and updating managed OpenSpec Issue content."""

import json
from pathlib import Path
from typing import Any, Dict, Iterable, Optional


START_MARKER = "<!-- aiw:openspec:start -->"
END_MARKER = "<!-- aiw:openspec:end -->"


def render_projection(
    change_id: str,
    goal: str,
    scope: str,
    requirements: Iterable[str],
    tasks: Iterable[str],
) -> str:
    requirement_lines = "\n".join(f"- {item}" for item in requirements) or "- None recorded"
    task_lines = "\n".join(tasks) or "- [ ] No tasks recorded"
    return "\n".join(
        [
            START_MARKER,
            f"## OpenSpec change: `{change_id}`",
            "",
            "## Goal",
            goal.strip(),
            "",
            "## Scope",
            scope.strip(),
            "",
            "## Requirements",
            requirement_lines,
            "",
            "## Tasks",
            task_lines,
            "",
            "OpenSpec is authoritative for local requirements, progress, and status.",
            END_MARKER,
        ]
    )


def replace_managed_block(existing: str, generated: str) -> str:
    start = existing.find(START_MARKER)
    end = existing.find(END_MARKER)
    if (start == -1) != (end == -1):
        raise ValueError("existing Issue body has an incomplete OpenSpec marker block")
    if start == -1:
        if not existing.strip():
            return generated
        return existing.rstrip() + "\n\n" + generated
    if end < start:
        raise ValueError("existing Issue body has reversed OpenSpec markers")
    suffix_start = end + len(END_MARKER)
    prefix = existing[:start].rstrip()
    suffix = existing[suffix_start:].lstrip()
    parts = [part for part in (prefix, generated.strip(), suffix) if part]
    return "\n\n".join(parts)


def load_mapping(path: Path) -> Optional[Dict[str, Any]]:
    if not path.exists():
        return None
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"invalid GitHub mapping: {path}: {exc}") from exc
    required = ("version", "repository", "issue_number", "url")
    if not isinstance(data, dict) or any(key not in data for key in required):
        raise ValueError(f"GitHub mapping is missing required fields: {path}")
    if data["version"] != 1 or not isinstance(data["issue_number"], int):
        raise ValueError(f"unsupported GitHub mapping: {path}")
    return data


def save_mapping(path: Path, mapping: Dict[str, Any]) -> None:
    required = ("version", "repository", "issue_number", "url")
    if any(key not in mapping for key in required):
        raise ValueError("GitHub mapping is missing required fields")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(mapping, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
