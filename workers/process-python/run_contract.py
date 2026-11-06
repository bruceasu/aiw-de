"""Local, metadata-only context observations. They grant no runtime capabilities."""
from __future__ import annotations

from dataclasses import asdict, dataclass
import hashlib
import json
import threading


PROTOCOL = "aiw.worker.loop.v1"


@dataclass(frozen=True)
class RuntimeCapabilities:
    request_loop_owner: str
    tool_executor: str
    context_visibility: str
    dispatch_boundary: str
    supplement_boundary: str
    session_reuse: str
    pause_boundary: str
    cancel_evidence: str
    usage_scope: str
    request_accounting_scope: str

    def __post_init__(self):
        choices = {
            "request_loop_owner": {"HOST", "BACKEND"}, "tool_executor": {"HOST", "BACKEND"},
            "context_visibility": {"PROVIDER_PAYLOAD", "WORKER_PROJECTION", "BACKEND_OPAQUE"},
            "dispatch_boundary": {"TOOL", "BATCH", "TURN", "NONE"},
            "supplement_boundary": {"REQUEST", "TURN", "NONE"}, "session_reuse": {"SAME_ATTEMPT", "NONE"},
            "pause_boundary": {"TOOL", "BATCH", "TURN", "NONE"}, "cancel_evidence": {"CONFIRMED", "BEST_EFFORT"},
            "usage_scope": {"REQUEST", "CUMULATIVE", "UNKNOWN"},
            "request_accounting_scope": {"REQUEST", "BACKEND_TURN", "UNKNOWN"},
        }
        if any(value not in choices[name] for name, value in asdict(self).items()):
            raise ValueError("Unsupported runtime capability")

    def payload(self):
        def camel(name):
            first, *rest = name.split("_")
            return first + "".join(part.title() for part in rest)
        return {camel(name): value for name, value in asdict(self).items()}


def _encoded(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False).encode("utf-8")


def _digest(raw):
    return hashlib.sha256(raw).hexdigest()


@dataclass(frozen=True)
class ContextProjection:
    context_id: str
    digest: str
    input_bytes: int
    runtime: str
    visibility: str
    request_scope: str
    purpose: str


class ContextLedger:
    """Observe worker submissions, without persisting prompt text or changing dispatch."""

    def __init__(self, evidence):
        self.evidence = evidence
        self.lock = threading.RLock()
        self.sequence = 0
        self.previous = {}
        self.materials = set()
        self.capabilities = {}

    def capabilities_observed(self, runtime, value):
        with self.lock:
            previous = self.capabilities.get(runtime)
            if previous is not None and previous != value:
                raise RuntimeError("RUNTIME_CAPABILITIES_CHANGED_WITHIN_ATTEMPT")
            if previous is None:
                controlled = value.pause_boundary != "NONE"
                self.evidence.record("runtime-observed", runtime=runtime, capabilities=value.payload(),
                                     phase="LEASE_CONTROLLED" if controlled else "OBSERVATION_ONLY", complete=False,
                                     unknown="backend-inner-state-and-cancellation-not-guaranteed" if controlled else "no-live-control-or-worker-loop-registration")
                self.capabilities[runtime] = value

    def material(self, kind, source_id, version, content):
        raw = content.encode("utf-8")
        identity = (kind, source_id, str(version), _digest(raw))
        with self.lock:
            if identity in self.materials:
                return
            # A bounded inventory, not proof that every source appears in a later request.
            if len(self.materials) >= 128:
                raise RuntimeError("CONTEXT_MATERIAL_INVENTORY_LIMIT")
            self.evidence.record("context-material", sourceKind=kind, path=source_id,
                                 version=str(version), digest=identity[3], bytes=len(raw),
                                 complete=False, unknown="inventory-not-per-request-attribution")
            self.materials.add(identity)

    def observe(self, runtime, projection, *, request_scope, purpose="task", parts=None):
        if request_scope not in {"REQUEST", "BACKEND_TURN", "UNKNOWN"}:
            raise ValueError("Invalid request accounting scope")
        raw = _encoded(projection)
        with self.lock:
            self.sequence += 1
            context_id = "context-" + str(self.sequence)
            observed = ContextProjection(context_id, _digest(raw), len(raw), runtime,
                                         "WORKER_PROJECTION", request_scope, purpose)
            self.evidence.record("context-projected", callId=context_id, runtime=runtime,
                                 digest=observed.digest, inputBytes=len(raw), purpose=purpose,
                                 visibility=observed.visibility, requestScope=request_scope,
                                 usageKnown=False, unknown="provider-added-context-and-input-token-attribution")
            components = parts if parts is not None else projection
            for kind, value in components.items():
                component = _encoded(value)
                digest = _digest(component)
                previous = self.previous.get((runtime, purpose, kind))
                self.evidence.record("context-component", parentCallId=context_id, sourceKind=kind,
                                     digest=digest, bytes=len(component), changed=previous != digest,
                                     count=len(value) if isinstance(value, list) else 1,
                                     complete=True, unknown="serialized-component-bytes-not-provider-tokens")
                self.previous[(runtime, purpose, kind)] = digest
            return observed


def ledger(attempt):
    # Attempt construction and model dispatch are single-owner; component writes are locked.
    value = getattr(attempt, "context_ledger", None)
    if value is None:
        value = ContextLedger(attempt.evidence)
        attempt.context_ledger = value
    return value


def observe_context(request, runtime, projection, *, request_scope, purpose=None):
    observed = ledger(request.attempt)
    api = runtime == "openai-agents"
    host = getattr(request, "context_purpose", "task") == "host-action"
    broker = getattr(request, "broker", None) is not None
    channel = getattr(request.attempt, "run_channel", None)
    capabilities = RuntimeCapabilities(
        "HOST" if host else "BACKEND", "HOST" if api or host or broker or getattr(request, "model_only", False) else "BACKEND",
        "WORKER_PROJECTION", "TOOL" if api or host or broker else "TURN",
        "REQUEST" if api and broker else "NONE",
        "SAME_ATTEMPT" if runtime == "codex-app-server:host-session" else "NONE",
        channel.capabilities.pause_boundary if channel is not None else "NONE",
        "BEST_EFFORT", "REQUEST" if api else "UNKNOWN", request_scope)
    if channel is not None and channel.capabilities.pause_boundary == "TURN":
        capabilities = channel.capabilities
    observed.capabilities_observed(runtime, capabilities)
    return observed.observe(runtime, projection, request_scope=request_scope,
        purpose=purpose or getattr(request, "context_purpose", "task"),
        parts=dict(request.context_parts) if getattr(request, "context_parts", ()) else None)


def register_material(kind, source_id, version, content):
    from worker_runtime import current_attempt
    attempt = current_attempt()
    if attempt is not None:
        ledger(attempt).material(kind, source_id, version, content)
