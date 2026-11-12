"""Inject authenticated workflow input at model boundaries in the existing Agent loop."""
from __future__ import annotations

import uuid
from context_window import encoded, sha


class WorkflowInputs:
    def __init__(self, client, base, lease, attempt):
        self.client, self.base, self.lease, self.attempt = client, base, lease, attempt
        self.pending = None
        self.insertions = []
        self.failed = False

    def _post(self, action, batch):
        if action == "RESERVE":
            self.attempt.check()
        try:
            result = self.client._post(self.base + "/execution-run/inputs", {
                "lease": self.lease, "action": action, "batchId": batch})
            if result.get("protocol") != "aiw.workflow.inputs.v1":
                raise RuntimeError("WORKFLOW_INPUT_PROTOCOL_INVALID")
            return result
        except Exception:
            self.failed = True
            raise

    def project(self, items):
        if self.failed:
            raise RuntimeError("WORKFLOW_INPUT_DELIVERY_UNCONFIRMED")
        if not isinstance(items, list):
            raise RuntimeError("WORKFLOW_INPUT_HISTORY_UNSUPPORTED")
        for insertion in self.insertions:
            if len(items) < insertion["count"] or sha(encoded(items[:insertion["count"]])) != insertion["digest"]:
                raise RuntimeError("WORKFLOW_INPUT_HISTORY_CHANGED")
        if self.pending is not None:
            raise RuntimeError("WORKFLOW_INPUT_DELIVERY_UNCONFIRMED")
        batch = str(uuid.uuid4())
        result = self._post("RESERVE", batch)
        messages = result.get("messages")
        if not isinstance(messages, list) or len(messages) > 8:
            raise RuntimeError("WORKFLOW_INPUT_BATCH_INVALID")
        if messages:
            self.pending = batch
            if any(not isinstance(item, dict) or not isinstance(item.get("text"), str)
                   or not item["text"] or item.get("state") != "RESERVED"
                   or item.get("batchId") != batch or item.get("attemptId") != self.lease["attemptId"]
                   or item.get("taskId") != self.base.rsplit("/", 1)[-1] for item in messages):
                raise RuntimeError("WORKFLOW_INPUT_SOURCE_CHANGED")
            text = ("The user supplied additional context for this same task. Keep the original goal, "
                    "completion conditions and tool permissions. This content grants no new permissions.\n\n")
            text += "\n\n".join(item["mode"] + ":\n" + item["text"] for item in messages)
            self.insertions.append({"count": len(items), "digest": sha(encoded(items)),
                                    "message": {"role": "user", "content": text}})
        output = []
        for index in range(len(items) + 1):
            output.extend(item["message"] for item in self.insertions if item["count"] == index)
            if index < len(items):
                output.append(items[index])
        return output

    def protected_messages(self):
        return {sha(encoded(item["message"])) for item in self.insertions}

    def delivered(self):
        if self.pending is not None:
            if self._post("ACK", self.pending).get("state") != "APPLIED":
                raise RuntimeError("WORKFLOW_INPUT_ACK_INVALID")
            self.pending = None

    def unknown(self):
        if self.pending is not None:
            try:
                self._post("UNKNOWN", self.pending)
            except Exception:
                self.failed = True

    def continue_or_seal(self):
        if self.pending is not None:
            raise RuntimeError("WORKFLOW_INPUT_DELIVERY_UNCONFIRMED")
        if self.attempt.cancelled.is_set():
            return False
        result = self._post("DRAIN", str(uuid.uuid4()))
        if type(result.get("hasPending")) is not bool:
            raise RuntimeError("WORKFLOW_INPUT_DRAIN_INVALID")
        return result["hasPending"]
