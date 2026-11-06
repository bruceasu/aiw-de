"""Local, metadata-only context observations. They grant no runtime capabilities."""
from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json
import threading


PROTOCOL = "aiw.worker.loop.v1"


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
    return ledger(request.attempt).observe(runtime, projection, request_scope=request_scope,
        purpose=purpose or getattr(request, "context_purpose", "task"),
        parts=dict(request.context_parts) if getattr(request, "context_parts", ()) else None)


def register_material(kind, source_id, version, content):
    from worker_runtime import current_attempt
    attempt = current_attempt()
    if attempt is not None:
        ledger(attempt).material(kind, source_id, version, content)
