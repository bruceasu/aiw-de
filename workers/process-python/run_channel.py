"""Lease-scoped metadata/control transport. It never grants tool authority."""
from __future__ import annotations

import copy
import hashlib
import json
import os
import threading
import time

PROTOCOL = "aiw.worker.loop.v1"
EVENTS = {"context-projected", "context-component", "guard-intent", "guard-start", "guard-finished", "native-turn-started",
          "host-batch-validated", "validation-failed", "validation-passed", "execution-quiescent", "delivery-pending"}
FIELDS = set("bytes inputBytes outputBytes count durationMs inputTokens outputTokens cachedTokens complete truncated changed usageKnown visibility requestScope purpose status phase tool digest operation callId parentCallId sourceKind code".split())
SOURCE_KINDS = set("instructions messages tools fixed_header observations new_observations task knowledge upstream skill configuration".split())


def encoded(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False).encode("utf-8")


class RunChannel:
    def __init__(self, broker, capabilities, configuration_capability=None):
        self.broker, self.attempt = broker, broker.attempt
        self.capabilities = capabilities
        self.configuration_digest = None
        self.quiescent_sequence = None
        self.client = copy.copy(broker.client)
        self.client.timeout = min(self.client.timeout, 2)
        self.base = broker.base + "/execution-run"
        self.lock, self.io = threading.RLock(), threading.Lock()
        self.stopped = threading.Event()
        self.active = 0
        self.paused = False
        self.failed = None
        self.sequence = self.ack = 0
        self.queue = []
        self.pending_receipts = []
        self.revision = 0
        self.phase = "OPENING"
        self.last_contact = 0
        opening = {"protocol": PROTOCOL, "instructionRevision": broker.task["instructionRevision"], "capabilities": capabilities.payload()}
        if configuration_capability is not None:
            opening["configurationCapability"] = configuration_capability
        opened = self._post("open", opening)
        self._accept(opened)
        self.attempt.evidence.observers.append(self.observe)
        self.thread = threading.Thread(target=self._watch, name="worker-run-control", daemon=True)
        self.thread.start()

    def _post(self, action, value):
        if action not in {"events", "control"}:
            self.attempt.check()
        payload = {"lease": self.broker.lease, **value}
        if len(encoded(payload)) > 65536:
            raise RuntimeError("RUN_ENVELOPE_LIMIT")
        from worker_runtime import attempt_scope
        # Cleanup metadata may be confirmed after cancellation; no model/tool call uses this exception.
        with attempt_scope(None):
            return self.client._post(self.base + "/" + action, payload)

    def _accept(self, value):
        if value.get("protocol") != PROTOCOL or value.get("attemptId") != self.broker.lease["attemptId"]:
            raise RuntimeError("RUN_IDENTITY_CHANGED")
        with self.lock:
            self.revision = value["revision"]
            self.phase = value["phase"]
            self.configuration_digest = value.get("configurationDigest", self.configuration_digest)
            self.last_contact = time.monotonic()
            if self.phase in {"BROKEN", "SETTLED"}:
                self.failed = "RUN_SESSION_UNAVAILABLE"
            self.paused = self.phase in {"PAUSING", "PAUSED", "STOPPING", "YIELDING"}
            if self.phase == "STOPPING":
                self.attempt.cancel("stop-requested")
            ack = value["acknowledgedSequence"]
            if type(ack) is not int or ack < self.ack or ack > self.sequence:
                raise RuntimeError("RUN_ACK_INVALID")
            self.ack = ack
            self.queue = [item for item in self.queue if item["sequence"] > ack]
            receipts = []
            for command in value["commands"]:
                status, action, sequence = command["status"], command["action"], command["sequence_no"]
                if status == "ACCEPTED":
                    if action in {"PAUSE", "CANCEL"}:
                        self.paused = True
                    if action == "CANCEL":
                        self.attempt.cancel("stop-requested")
                    receipts.append({"sequence": sequence, "status": "RECEIVED"})
                elif status == "RECEIVED" and action != "CANCEL" and self.active == 0 and not self.attempt.resource_uncertain and (self.capabilities.pause_boundary != "TURN" or self.quiescent_sequence is not None and self.ack >= self.quiescent_sequence):
                    receipts.append({"sequence": sequence, "status": "APPLIED"})
            self.pending_receipts = receipts

    def observe(self, event, values):
        if event not in EVENTS:
            return
        payload = {key: value for key, value in values.items() if key in FIELDS}
        if payload.get("sourceKind") not in SOURCE_KINDS:
            payload.pop("sourceKind", None)
        # Never copy path, instruction, model output, arguments, credentials or free-form errors.
        with self.lock:
            if self.phase == "FINALIZING":
                return
            if len(self.queue) >= 512:
                self.failed = "RUN_SPOOL_LIMIT"
                raise RuntimeError(self.failed)
            self.sequence += 1
            if event == "native-turn-started":
                self.quiescent_sequence = None
            elif event == "execution-quiescent" and payload.get("complete") is True and payload.get("count") == 0:
                self.quiescent_sequence = self.sequence
            self.queue.append({"sequence": self.sequence, "eventType": event, "occurredAt": int(time.time() * 1000),
                               "payload": payload, "payloadDigest": hashlib.sha256(encoded(payload)).hexdigest()})

    def _ready(self, admit):
        while True:
            self.attempt.check()
            with self.lock:
                if self.failed:
                    raise RuntimeError(self.failed)
                if self.phase == "FINALIZING":
                    raise RuntimeError("RUN_ALREADY_FINALIZING")
                if not self.paused and time.monotonic() - self.last_contact <= 5:
                    if admit:
                        self.active += 1
                    return
            self.stopped.wait(0.05)

    def enter(self):
        self._ready(True)

    def leave(self):
        with self.lock:
            if self.active < 1:
                raise RuntimeError("RUN_ACTIVITY_UNDERFLOW")
            self.active -= 1

    def _exchange(self):
        with self.io:
            with self.lock:
                receipts = list(self.pending_receipts)
            self._accept(self._post("control", {"receipts": receipts}))
            with self.lock:
                events = []
                for item in self.queue[:64]:
                    if len(encoded({"lease": self.broker.lease, "events": [*events, item]})) > 65536:
                        break
                    events.append(item)
                sealed = self.phase == "FINALIZING"
            if events and not sealed:
                self._accept(self._post("events", {"events": events}))

    def _watch(self):
        while not self.stopped.wait(1):
            try:
                self._exchange()
            except Exception as error:
                # Idempotent sequence/receipt queries may be retried. Never replay a tool.
                status = getattr(error, "status", 0)
                if status in {401, 403, 409, 422}:
                    with self.lock:
                        self.failed = "RUN_CHANNEL_REJECTED"
                if self.attempt.cancelled.is_set():
                    return

    def seal(self, batch_id):
        while True:
            self._ready(False)
            self._exchange()
            with self.io:
                with self.lock:
                    if self.active or self.queue or self.paused:
                        continue
                    revision, ack = self.revision, self.ack
                result = self._post("seal", {"expectedRunRevision": revision, "acknowledgedSequence": ack, "batchId": batch_id})
                self._accept(result["run"])
                if "batch" in result and result["batch"].get("batchId"):
                    return result["batch"]
                if self.phase == "FINALIZING":
                    return result.get("batch")

    def flush(self):
        deadline = time.monotonic() + 10
        while True:
            with self.lock:
                if not self.queue:
                    return
                if self.failed:
                    raise RuntimeError(self.failed)
            if time.monotonic() >= deadline:
                raise RuntimeError("RUN_FINAL_EVIDENCE_UNCONFIRMED")
            self._exchange()

    def close(self):
        self.stopped.set()
        self.thread.join(timeout=5)
        self.attempt.evidence.observers = [item for item in self.attempt.evidence.observers if item != self.observe]
        if self.thread.is_alive():
            self.attempt.resource_uncertain = True
            raise RuntimeError("RUN_CHANNEL_CLOSE_UNCONFIRMED")


def bind(request, runtime):
    enabled = os.environ.get("AIW_WORKER_LOOP_CONTROL", "false")
    if enabled not in {"true", "false"}:
        raise RuntimeError("WORKER_LOOP_CONTROL_SETTING_INVALID")
    if enabled == "false" or getattr(request.attempt, "run_channel", None) is not None:
        return
    if request.broker is None:
        if "worker-loop-v1" in getattr(request.attempt, "required_capabilities", ()):
            raise RuntimeError("WORKER_LOOP_CONTROL_REQUIRES_GUARDED_TASK")
        return  # Ordinary native tasks retain their actual NONE pause boundary.
    from run_contract import RuntimeCapabilities
    api = runtime == "openai-agents"
    host = runtime == "host-action"
    if not api and not host:
        raise RuntimeError("WORKER_LOOP_CONTROL_BACKEND_UNSUPPORTED")
    if host and os.environ.get("AIW_HOST_ACTION_PROTOCOL", "v1") != "v2":
        raise RuntimeError("WORKER_LOOP_CONTROL_REQUIRES_HOST_V2")
    capabilities = RuntimeCapabilities("HOST" if host else "BACKEND", "HOST", "WORKER_PROJECTION", "TOOL",
        "REQUEST" if api else "NONE", "SAME_ATTEMPT" if host and os.environ.get("AIW_CODEX_HOST_SESSION_REUSE") == "true" else "NONE",
        "TOOL", "BEST_EFFORT", "REQUEST" if api else "UNKNOWN", "REQUEST" if api else "BACKEND_TURN")
    request.attempt.run_channel = RunChannel(request.broker, capabilities)
