"""Read-only, on-demand access to the personal secretary's own Skills."""

from __future__ import annotations

import hashlib
import re
from pathlib import Path


SKILLS_ROOT = Path(__file__).resolve().parent / "skills"
MAX_SKILL_BYTES = 1_048_576
MAX_READ_BYTES = 8192
_SAFE_PART = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,95}\Z")


def _inside(path: Path, root: Path) -> bool:
    try:
        path.relative_to(root)
        return True
    except ValueError:
        return False


def _markdown_files(directory: Path) -> list[Path]:
    result = []
    for path in directory.rglob("*.md"):
        if path.is_symlink():
            continue
        resolved = path.resolve()
        if _inside(resolved, directory.resolve()) and resolved.is_file():
            result.append(resolved)
    return sorted(result, key=lambda item: item.relative_to(directory.resolve()).as_posix())


def _description(entry: str) -> str:
    lines = entry.replace("\r\n", "\n").split("\n")
    if not lines or lines[0] != "---":
        return ""
    try:
        end = lines.index("---", 1)
    except ValueError:
        return ""
    values = [line[len("description:"):].strip() for line in lines[1:end]
              if line.startswith("description:")]
    if len(values) != 1:
        return ""
    value = values[0]
    if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
        value = value[1:-1]
    return value[:640]


class PersonalSkills:
    """Expose personal-assistant Skill markdown without granting new tools."""

    def __init__(self, root: Path = SKILLS_ROOT):
        self.root = root.resolve()

    def _packages(self) -> list[tuple[str, Path]]:
        if not self.root.is_dir():
            raise ValueError("Personal Skill catalog is unavailable.")
        packages = []
        for entry in self.root.rglob("SKILL.md"):
            if entry.is_symlink():
                continue
            skill_file = entry.resolve()
            if not _inside(skill_file, self.root) or not skill_file.is_file():
                continue
            name = skill_file.parent.relative_to(self.root).as_posix()
            if all(_SAFE_PART.fullmatch(part) for part in name.split("/")):
                packages.append((name, skill_file.parent))
        return sorted(packages)

    def list_skills(self) -> dict:
        packages = []
        for name, directory in self._packages():
            skill_file = directory / "SKILL.md"
            if skill_file.stat().st_size > MAX_SKILL_BYTES:
                continue
            entry = skill_file.read_text(encoding="utf-8")
            packages.append({
                "name": name,
                "description": _description(entry),
                "markdownFiles": [path.relative_to(directory).as_posix()
                                  for path in _markdown_files(directory)],
            })
        return {"skills": packages}

    def read_skill(self, action: dict) -> dict:
        name, relative = action.get("name"), action.get("path")
        offset, limit = action.get("offset", 0), action.get("limit", MAX_READ_BYTES)
        if (not isinstance(name, str) or not isinstance(relative, str)
                or not all(_SAFE_PART.fullmatch(part) for part in name.split("/"))
                or not relative or len(relative) > 240
                or not all(_SAFE_PART.fullmatch(part) for part in relative.split("/"))
                or not relative.lower().endswith(".md")
                or type(offset) is not int or offset < 0
                or type(limit) is not int or not 1 <= limit <= MAX_READ_BYTES):
            raise ValueError("Invalid Personal Skill file request.")

        package = self.root.joinpath(*name.split("/"))
        package_real = package.resolve()
        if not _inside(package_real, self.root) or package_real not in {directory for _, directory in self._packages()}:
            raise ValueError("Personal Skill is not available.")
        target = package.joinpath(*relative.split("/"))
        if target.is_symlink():
            raise ValueError("Personal Skill file is not available.")
        target_real = target.resolve()
        if not _inside(target_real, package_real) or target_real not in _markdown_files(package_real):
            raise ValueError("Personal Skill Markdown file is not declared.")
        raw = target_real.read_bytes()
        if len(raw) > MAX_SKILL_BYTES:
            raise ValueError("Personal Skill file exceeds the read limit.")

        start = min(offset, len(raw))
        while start < len(raw) and raw[start] & 0b1100_0000 == 0b1000_0000:
            start += 1
        end = min(start + limit, len(raw))
        while end > start:
            try:
                text = raw[start:end].decode("utf-8", errors="strict")
                break
            except UnicodeDecodeError as error:
                if error.reason != "unexpected end of data":
                    raise ValueError("Personal Skill file is not valid UTF-8.") from None
                end = start + error.start
        else:
            text = ""
        return {"name": name, "path": relative, "text": text,
                "sha256": hashlib.sha256(raw).hexdigest(), "size": len(raw),
                "offset": start, "nextOffset": end, "complete": end == len(raw)}

    def perform(self, action: dict) -> dict:
        kind = action.get("action")
        if kind == "list_skills" and set(action) == {"action"}:
            return self.list_skills()
        if kind == "read_skill" and set(action) <= {"action", "name", "path", "offset", "limit"}:
            return self.read_skill(action)
        raise ValueError("Invalid Personal Skill action.")
