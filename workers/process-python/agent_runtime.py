"""Backend-neutral boundary for model execution in a claimed worker attempt."""
from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Protocol


from process_profiles import ProcessProfile
from worker_runtime import Attempt


@dataclass(frozen=True)
class AgentRunRequest:
    attempt: Attempt
    workspace: str
    profile: ProcessProfile
    instruction: str
    model: str | None
    reasoning_effort: str
    network_access: bool
    service_tier: str | None
    sandbox: str
    approval_mode: str


class AgentRuntime(Protocol):
    """Executes one prompt and reports the session needed by lease heartbeats."""

    @property
    def type(self) -> str: ...

    def run(self, client: Any, request: AgentRunRequest, on_session: Any) -> str: ...
