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


class CodexAgentRuntime:
    """Codex SDK implementation of the AgentRuntime SPI."""

    type = "codex-python-sdk"

    def run(self, client: Any, request: AgentRunRequest, on_session: Any) -> str:
        from openai_codex import ApprovalMode, Sandbox

        attempt = request.attempt
        model_config = {
            "model_reasoning_effort": request.reasoning_effort,
            "sandbox_workspace_write": {"network_access": request.network_access},
        }
        if request.service_tier is not None:
            model_config["service_tier"] = request.service_tier
        thread = attempt.run_operation(lambda: client.thread_start(
            ephemeral=True,
            approval_mode=_approval_mode(ApprovalMode, request.approval_mode),
            cwd=request.workspace,
            model=request.model,
            sandbox=_sandbox(Sandbox, request.profile, request.sandbox),
            config=model_config,
        ))
        session_id = thread.id
        metadata = attempt.run_operation(lambda: thread.read(include_turns=False)).thread
        if metadata.ephemeral is not True or metadata.path is not None:
            raise RuntimeError("SESSION_EPHEMERAL_NOT_CONFIRMED")
        attempt.check()
        turn = attempt.run_operation(lambda: thread.turn(request.instruction))
        on_session(session_id, turn)
        attempt.check()
        result = attempt.run_turn(turn)
        attempt.check()
        final_response = (result.final_response or "").strip()
        if not final_response:
            raise RuntimeError("CODEX_OUTPUT_EMPTY")
        attempt.evidence.record("model-terminal", status="sdk-run-returned", usageKnown=False,
                                unknown="turn-usage-and-inner-tool-statuses")
        return final_response


def _sandbox(sandbox_type: Any, profile: ProcessProfile, value: str) -> Any:
    if not profile.writable:
        return sandbox_type.read_only
    mapping = {"read-only": sandbox_type.read_only, "workspace-write": sandbox_type.workspace_write,
               "full-access": sandbox_type.full_access}
    key = value.strip().lower().replace("_", "-")
    if key not in mapping:
        raise ValueError(f"unsupported AIW_CODEX_SANDBOX: {value}")
    return mapping[key]


def _approval_mode(approval_type: Any, value: str) -> Any:
    mapping = {"auto-review": approval_type.auto_review, "deny-all": approval_type.deny_all}
    key = value.strip().lower().replace("_", "-")
    if key not in mapping:
        raise ValueError(f"unsupported AIW_CODEX_APPROVAL_MODE: {value}")
    return mapping[key]
