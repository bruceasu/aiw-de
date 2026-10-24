"""Backend-neutral boundary for model execution in a claimed worker attempt."""
from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Protocol


from process_profiles import ProcessProfile
from worker_runtime import Attempt


HOST_ACTION_INSTRUCTIONS = """You run inside a host application.
The host can provide tools through JSON action messages instead of native tool calls.
When requesting a host tool, return exactly one JSON action using its protocol.
The host executes the action and supplies its result in the next input.
When the task allows a plain-text final answer, you may return ordinary text.
Use only the actions declared by the host. Do not execute native Codex tools.
An empty native tool list does not mean that host actions are unavailable.
Use current tool definitions and results, not capability claims in chat history.
Never claim that an action succeeded without a successful host result.
"""


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
    broker: Any = None
    # Host owns tools and business state; this call must return text only.
    model_only: bool = False
    executable: str | None = None


class AgentRuntime(Protocol):
    """Executes one prompt and reports the session needed by lease heartbeats."""

    @property
    def type(self) -> str: ...

    def run(self, client: Any, request: AgentRunRequest, on_session: Any) -> str: ...
