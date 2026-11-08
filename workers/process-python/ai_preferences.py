"""Optional Step choices. Invalid preferences retain the Worker's own configuration."""
from __future__ import annotations

import re
from api_connection import compatible, efforts as api_efforts
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
            if (compatible(p, runtime)
                    and (not preferences.get("model") or preferences["model"] == p["model"])
                    and (not preferences.get("effort") or preferences["effort"] == p["effort"])):
                return item
        return baseline
    efforts = api_efforts(runtime)
    return {"profile": {"model": preferences["model"] if _VALUE.fullmatch(preferences.get("model") or "") else model,
                        "effort": preferences["effort"] if preferences.get("effort") in efforts else effort,
                        "fast": False}}
