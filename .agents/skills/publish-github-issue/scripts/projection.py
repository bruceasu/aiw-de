"""Local projection helpers; no GitHub requests or workflow transitions."""

import json
import os
import re
import tempfile
from pathlib import Path
from typing import Any, Dict, Iterable, Optional


START_MARKER = "<!-- aiw:openspec:start -->"
END_MARKER = "<!-- aiw:openspec:end -->"


def source_markers(source_kind: str, source_id: str) -> tuple[str, str]:
    if not isinstance(source_kind, str) or source_kind not in {"issue", "fd", "openspec"}:
        raise ValueError("unsupported projection source kind")
    if not isinstance(source_id, str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}", source_id):
        raise ValueError("use a resolved canonical source ID, not a path")
    stem = f"<!-- aiw:source:{source_kind}:{source_id}:"
    return stem + "start -->", stem + "end -->"


def mapping_path(workspace: Path, source_kind: str, source_id: str) -> Path:
    """Select the mapping owner's stable location; does not resolve lineage."""
    source_markers(source_kind, source_id)
    if source_kind not in {"issue", "fd"}:
        raise ValueError("legacy OpenSpec mappings retain their existing location")
    root = workspace.resolve()
    target = root / "docs" / "external" / "github" / source_kind / f"{source_id}.json"
    current = root
    for part in target.relative_to(root).parts:
        current = current / part
        if current.is_symlink() or current.resolve() != current:
            raise ValueError(f"mapping path must not traverse links: {current}")
    return target


def render_source_projection(
    source_kind: str,
    source_id: str,
    goal: str,
    scope: str,
    requirements: Iterable[str],
    progress: Iterable[str],
    *,
    status: str = "Not supplied",
    verification: Iterable[str] = (),
    related: Iterable[str] = (),
) -> str:
    start, end = source_markers(source_kind, source_id)
    sections = [
        ("Goal", [goal]), ("Scope", [scope]),
        ("Acceptance / Requirements", list(requirements)),
        ("Progress", list(progress)), ("Verification", list(verification)),
        ("Related local sources", list(related)),
    ]
    values = [status] + [item for _, items in sections for item in items]
    if any(not isinstance(item, str) or "<!-- aiw:" in item for item in values):
        raise ValueError("projection data must be text without managed markers")
    lines = [start, f"## {source_kind.upper()}: `{source_id}`", "", f"Status: {status}"]
    for heading, items in sections:
        lines.extend(["", f"### {heading}", "\n".join(items) or "Not supplied"])
    lines.extend(["", "Local Issue/FD records own scope, progress and lifecycle; OpenSpec supplies specifications when present.", end])
    result = "\n".join(lines)
    if len(result.encode("utf-8")) > 65536:
        raise ValueError("projection exceeds 64 KiB; reduce the supplied summary")
    return result


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
            "Local Issue/FD records own scope, progress and lifecycle; OpenSpec supplies specifications when present.",
            END_MARKER,
        ]
    )


def _block_span(text: str, start_marker: str, end_marker: str) -> Optional[tuple[int, int]]:
    starts, ends = text.count(start_marker), text.count(end_marker)
    if starts == ends == 0:
        return None
    if starts != 1 or ends != 1:
        raise ValueError("managed block must have exactly one complete marker pair")
    start, end = text.index(start_marker), text.index(end_marker)
    if end < start:
        raise ValueError("managed block markers are reversed")
    return start, end + len(end_marker)


def _validate_source_blocks(text: str) -> None:
    tokens = list(re.finditer(r"<!-- aiw:source:([^:]+):([^:]+):(start|end) -->", text))
    if text.count("<!-- aiw:source:") != len(tokens):
        raise ValueError("malformed source markers")
    boundaries = []
    for token in tokens:
        kind, ident, boundary = token.groups()
        source_markers(kind, ident)
        boundaries.append((token.start(), (kind, ident), boundary))
    legacy_tokens = list(re.finditer(r"<!-- aiw:openspec:(start|end) -->", text))
    if text.count("<!-- aiw:openspec:") != len(legacy_tokens):
        raise ValueError("malformed legacy markers")
    for token in legacy_tokens:
        boundaries.append((token.start(), ("legacy", "openspec"), token.group(1)))
    active = None
    seen = set()
    # One ordered stream prevents a legacy replacement from swallowing another
    # source block, including valid source pairs nested inside legacy markers.
    for _, key, boundary in sorted(boundaries):
        if boundary == "start":
            if active is not None or key in seen:
                raise ValueError("nested or duplicate managed blocks")
            active = key
            seen.add(key)
        elif active != key:
            raise ValueError("reversed or mismatched managed markers")
        else:
            active = None
    if active is not None:
        raise ValueError("incomplete managed block")


def _bounded_body(body: str) -> str:
    if len(body.encode("utf-8")) > 65536:
        raise ValueError("Issue body exceeds 64 KiB; reduce summaries without truncating human content")
    return body


def replace_managed_block(
    existing: str, generated: str, *, source_kind: Optional[str] = None,
    source_id: Optional[str] = None, migrate_legacy: bool = False,
) -> str:
    if source_kind is not None or source_id is not None:
        _validate_source_blocks(existing)
        start_marker, end_marker = source_markers(source_kind, source_id)
        generated_span = _block_span(generated, start_marker, end_marker)
        if generated_span != (0, len(generated)):
            raise ValueError("generated text must be exactly the selected source block")
        interior = generated[len(start_marker):-len(end_marker)]
        if "<!-- aiw:" in interior:
            raise ValueError("generated block contains nested managed markers")
        span = _block_span(existing, start_marker, end_marker)
        legacy = _block_span(existing, START_MARKER, END_MARKER)
        if legacy is not None:
            if not migrate_legacy or span is not None:
                raise ValueError("legacy block needs explicit, unambiguous migration")
            span = legacy
        elif migrate_legacy:
            raise ValueError("no legacy block exists to migrate")
        if span is None:
            return _bounded_body(existing + ("\n\n" if existing else "") + generated)
        return _bounded_body(existing[:span[0]] + generated + existing[span[1]:])

    # Preserve the historical two-argument OpenSpec API and whitespace behavior.
    if migrate_legacy:
        raise ValueError("legacy migration needs a selected source")
    _block_span(existing, START_MARKER, END_MARKER)
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


def _validate_mapping(data: Any) -> Dict[str, Any]:
    required = ("version", "repository", "issue_number", "url")
    if not isinstance(data, dict) or any(key not in data for key in required):
        raise ValueError("GitHub mapping is missing required fields")
    repo, number = data["repository"], data["issue_number"]
    if type(data["version"]) is not int or data["version"] != 1:
        raise ValueError("unsupported GitHub mapping version")
    if not isinstance(repo, str) or not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repo):
        raise ValueError("invalid mapping repository")
    if any(part in {".", ".."} for part in repo.split("/")) or type(number) is not int or number <= 0:
        raise ValueError("invalid mapping target")
    expected = f"https://github.com/{repo}/issues/{number}"
    if not isinstance(data["url"], str) or data["url"].rstrip("/").casefold() != expected.casefold():
        raise ValueError("mapping URL does not match repository and issue number")
    if "source_kind" in data or "source_id" in data:
        source_markers(data.get("source_kind"), data.get("source_id"))
    if "publication_mode" in data:
        if not isinstance(data["publication_mode"], str) or data["publication_mode"] not in {"aggregate", "independent"}:
            raise ValueError("invalid publication mode")
        if data["publication_mode"] == "independent" and data.get("source_kind") != "fd":
            raise ValueError("independent mappings must be FD-owned")
    return data


def load_mapping(path: Path) -> Optional[Dict[str, Any]]:
    if path.is_symlink():
        raise ValueError("GitHub mapping must not be a symlink")
    if not path.exists():
        return None
    if path.stat().st_size > 65536:
        raise ValueError("GitHub mapping exceeds 64 KiB")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"invalid GitHub mapping: {path}: {exc}") from exc
    return _validate_mapping(data)


def save_mapping(path: Path, mapping: Dict[str, Any]) -> None:
    _validate_mapping(mapping)
    existing = load_mapping(path)
    if existing is not None:
        for key in ("repository", "issue_number", "source_kind", "source_id"):
            if key in existing and existing[key] != mapping.get(key):
                raise ValueError("refusing to silently change an existing mapping owner or target")
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(mapping, ensure_ascii=False, indent=2) + "\n"
    if len(payload.encode("utf-8")) > 65536:
        raise ValueError("GitHub mapping exceeds 64 KiB")
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=path.parent, prefix=path.name + ".", delete=False) as handle:
            temporary = Path(handle.name)
            handle.write(payload)
        os.replace(temporary, path)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()
