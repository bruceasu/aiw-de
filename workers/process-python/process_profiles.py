from __future__ import annotations

import tomllib
from dataclasses import dataclass
from pathlib import Path


PROFILE_DIR = Path(__file__).resolve().parent / "profiles"
ROLE_FILES = {
    "goal-discussion": "goal-discussion",
    "requirements": "requirement-analyst",
    "architect": "solution-architect",
    "planner": "implementation-planner",
    "coder": "coding-executor",
    "integrator": "integration-executor",
    "reviewer": "code-reviewer",
    "stage-reviewer": "stage-reviewer",
    "verifier": "verifier",
    "integration-verifier": "integration-verifier",
    "repairer": "repair-executor",
}


@dataclass(frozen=True)
class ProcessProfile:
    name: str
    worker_id_prefix: str
    capabilities: tuple[str, ...]
    task_types: tuple[str, ...]
    protocol_skills: tuple[str, ...]
    primary_skill: str
    instruction: str
    writable: bool
    result_kind: str


def load_profile(path: Path) -> ProcessProfile:
    if path.suffix != ".toml":
        raise ValueError(f"profile must be a .toml file: {path}")
    with path.open("rb") as stream:
        data = tomllib.load(stream)
    required = {"name", "workerIdPrefix", "roleCapability", "allowedTaskTypes", "protocolSkills", "primarySkill", "instruction", "readOnly", "resultKind"}
    if set(data) != required:
        raise ValueError(f"invalid profile {path}: missing={sorted(required - set(data))}, unknown={sorted(set(data) - required)}")
    for key in ("name", "workerIdPrefix", "roleCapability", "primarySkill", "instruction"):
        if not isinstance(data[key], str) or not data[key].strip():
            raise ValueError(f"invalid profile {path}: {key} must be a nonempty string")
    for key in ("allowedTaskTypes", "protocolSkills"):
        if not isinstance(data[key], list) or not data[key] or any(not isinstance(item, str) or not item.strip() for item in data[key]):
            raise ValueError(f"invalid profile {path}: {key} must be a nonempty string array")
    if type(data["readOnly"]) is not bool:
        raise ValueError(f"invalid profile {path}: readOnly must be a boolean")
    if data["allowedTaskTypes"] != ["EXTERNAL_WORK"]:
        raise ValueError(f"invalid profile {path}: allowedTaskTypes must be [EXTERNAL_WORK]")
    if data["resultKind"] not in {"REPORT", "VERDICT", "WORKSPACE"}:
        raise ValueError(f"invalid profile {path}: unknown resultKind")
    if (data["resultKind"] == "WORKSPACE") != (not data["readOnly"]):
        raise ValueError(f"invalid profile {path}: resultKind conflicts with readOnly")
    return ProcessProfile(
        name=data["name"], worker_id_prefix=data["workerIdPrefix"],
        capabilities=(data["roleCapability"],), task_types=tuple(data["allowedTaskTypes"]),
        protocol_skills=tuple(data["protocolSkills"]), primary_skill=data["primarySkill"],
        instruction=data["instruction"], writable=not data["readOnly"], result_kind=data["resultKind"],
    )


PROFILES = {role: load_profile(PROFILE_DIR / f"{filename}.toml") for role, filename in ROLE_FILES.items()}


def get_profile(name: str, task_type: str | None = None) -> ProcessProfile:
    key = name.strip().lower()
    try:
        return PROFILES[key]
    except KeyError as exc:
        raise ValueError(f"unsupported AIW_PROCESS_ROLE={name!r}; choose one of: {', '.join(PROFILES)}") from exc
