"""Optional Step choices. Invalid preferences retain the Worker's own configuration."""
from __future__ import annotations

import re
from typing import Any

_VALUE = re.compile(r"[A-Za-z0-9][A-Za-z0-9._:/-]{0,159}\Z")
_REJECTION = re.compile(r"(?:model|reasoning[_ .]effort|effort).{0,100}(?:not supported|unsupported|not found|does not exist|not available)|(?:unsupported|invalid).{0,30}(?:model|reasoning[_ .]effort)", re.I)


def preference_rejected(error: Any) -> bool:
    return bool(_REJECTION.search(str(error)))


def step_selection(task, runtime, baseline, model, effort):
    preferences = task.get("aiPreferences") or {}
    if not preferences.get("model") and not preferences.get("effort"):
        return baseline
    if task.get("aiGuidance"):
        for item in task["aiGuidance"]["allowedProfiles"]:
            p = item["profile"]
            if (p["provider"] == "openai" and p["runtime"] == runtime
                    and (not preferences.get("model") or preferences["model"] == p["model"])
                    and (not preferences.get("effort") or preferences["effort"] == p["effort"])
                    and not p.get("fast", False)
                    and p.get("effort") in ({"none", "minimal", "low", "medium", "high", "xhigh"}
                         if runtime == "codex-app-server" else {"none", "minimal", "low", "medium", "high"})):
                return item
        return baseline
    efforts = {"none", "minimal", "low", "medium", "high"}
    if runtime == "codex-app-server": efforts.add("xhigh")
    return {"profile": {"model": preferences["model"] if _VALUE.fullmatch(preferences.get("model") or "") else model,
                        "effort": preferences["effort"] if preferences.get("effort") in efforts else effort,
                        "fast": False}}
