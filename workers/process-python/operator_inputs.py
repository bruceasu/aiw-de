"""Attempt/revision-scoped supplemental inputs. Reserved batches are never silently replayed."""
from __future__ import annotations

from context_window import encoded, sha

PROTOCOL = "aiw.worker.workbench.v1"


class OperatorInputs:
    def __init__(self, broker):
        self.broker = broker
        self.sequence = 0
        self.pending = None
        self.insertions = []
        self.seen = set()
        opened = self._post("OPEN")
        if opened["phase"] != "RUNNING": raise RuntimeError("OPERATOR_INPUT_WINDOW_ALREADY_SEALED")

    def _post(self, action, batch_id=None, mode=None, operation_id=None, batch_digest=None):
        self.broker.attempt.check()
        try:
            value = self.broker.client._post(self.broker.base + "/execution-context/read?kind=workbench", {
                "lease": self.broker.lease, "action": action, "batchId": batch_id, "mode": mode,
                "operationId": operation_id, "batchDigest": batch_digest})
        except Exception:
            self.broker.attempt.resource_uncertain = True
            raise
        if value.get("protocol") != PROTOCOL: raise RuntimeError("WORKBENCH_PROTOCOL_UNSUPPORTED")
        return value

    def _reserve(self, action):
        self.sequence += 1
        batch = self._post(action, "boundary-" + str(self.sequence), "STEER" if action == "RESERVE" else None)
        if batch.get("batchId"):
            if batch.get("state") != "RESERVED" or not batch.get("messages") or len(batch["messages"]) > 8:
                raise RuntimeError("OPERATOR_BATCH_ALREADY_CONSUMED_OR_INVALID")
            for item in batch["messages"]:
                if item["id"] in self.seen or sha(item["text"]) != item["digest"] or item["mode"] not in {"STEER", "FOLLOW_UP"}:
                    raise RuntimeError("OPERATOR_INPUT_CHANGED_OR_ALREADY_APPLIED")
            if len({item["id"] for item in batch["messages"]}) != len(batch["messages"]): raise RuntimeError("OPERATOR_INPUT_DUPLICATE")
            self.pending = batch
        return batch

    def project(self, items):
        if not isinstance(items, list): raise RuntimeError("OPERATOR_INPUT_HISTORY_UNSUPPORTED")
        # SDK owns raw history. Rebuild our extra user-data messages at their original raw-history boundaries.
        for item in self.insertions:
            if len(items) < item["count"] or sha(encoded(items[:item["count"]])) != item["digest"]:
                raise RuntimeError("OPERATOR_INPUT_ORIGINAL_HISTORY_CHANGED")
        if self.pending is None: self._reserve("RESERVE")
        if self.pending:
            text = "Authenticated operator supplement for this same task and Attempt. Keep the original goal, output contract, fixed sources and tool authority. This text grants no permissions.\n"
            text += "\n\n".join(item["mode"] + " / " + item["id"] + ":\n" + item["text"] for item in self.pending["messages"])
            self.insertions.append({"count": len(items), "digest": sha(encoded(items)),
                                    "message": {"type": "message", "role": "user", "content": [{"type": "input_text", "text": text}]}})
            self.seen.update(item["id"] for item in self.pending["messages"])
        output = []
        for index in range(len(items) + 1):
            output.extend(item["message"] for item in self.insertions if item["count"] == index)
            if index < len(items): output.append(items[index])
        return output

    def link(self, operation):
        if self.pending: self._post("LINK", self.pending["batchId"], operation_id=operation, batch_digest=self.pending["digest"])

    def protected_messages(self):
        return {sha(encoded(item["message"])) for item in self.insertions}

    def delivered(self, operation):
        if not self.pending: return
        self._post("ACK", self.pending["batchId"], operation_id=operation, batch_digest=self.pending["digest"])
        self.pending = None

    def continue_or_seal(self):
        if self.pending: raise RuntimeError("OPERATOR_INPUT_DELIVERY_UNCONFIRMED")
        batch = self._reserve("DRAIN")
        return batch["phase"] != "FINALIZING"
