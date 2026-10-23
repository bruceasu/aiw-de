"""An explicitly selected goal projection. Unknown sends are queried, never replayed."""

import hashlib
import uuid

from platform_client import PlatformError
from store import encode


class SelectedGoals:
    def __init__(self, store, platform, enabled):
        self.store, self.platform, self.enabled = store, platform, enabled

    def preview(self, selected_goal, necessary_context="", acceptance=""):
        values = {"selectedGoal": selected_goal, "necessaryContext": necessary_context, "acceptance": acceptance}
        if any(not isinstance(v, str) or len(v) > 8000 or "\0" in v for v in values.values()) or not selected_goal.strip():
            raise ValueError("Select a bounded goal and only necessary context")
        raw = encode(values)
        digest = hashlib.sha256(raw.encode("utf-8")).hexdigest()
        request = str(uuid.uuid4())
        body = {"requestId": request, "projectionJson": raw, "digest": digest}
        if len(encode(body).encode("utf-8")) > 32768:
            raise ValueError("Selected goal envelope exceeds 32768 bytes")
        self.store.put("decision", "goal:" + request, {**body, "kind": "SELECTED_GOAL", "state": "PREVIEWED"}, 0)
        return body

    def _record(self, request):
        if str(uuid.UUID(request)) != request:
            raise ValueError("Canonical original request ID required")
        row = self.store.get("decision", "goal:" + request)
        if row is None or row.get("kind") != "SELECTED_GOAL":
            raise ValueError("Preview the exact selected projection first")
        return row

    def _identity(self):
        if not self.enabled():
            raise ValueError("Explicit local enablement required")
        identity = self.platform.request("/identity")
        if identity.get("ownerId") != self.store.owner or identity.get("workerId") != self.store.worker:
            raise ValueError("Authenticated private owner/Worker changed")
        self.store.bind_platform(identity["platformInstanceId"])
        return identity

    def _save(self, row, state, **extra):
        values = {k: v for k, v in row.items() if k not in {"id", "revision"}}
        return self.store.put("decision", row["id"], {**values, **extra, "state": state}, row["revision"])

    def _confirm(self, row, receipt):
        expected = {"requestId": row["requestId"], "digest": row["digest"], "ownerId": self.store.owner,
                    "workerId": self.store.worker, "platformInstanceId": row["platformInstanceId"], "state": "RECORDED"}
        if not isinstance(receipt, dict) or any(receipt.get(k) != v for k, v in expected.items()):
            raise ValueError("Selected goal receipt does not match the original source")
        return self._save(row, "RECORDED", receipt=receipt)

    def submit(self, request, projection_json, digest):
        row = self._record(request)
        if row["projectionJson"] != projection_json or row["digest"] != digest or hashlib.sha256(projection_json.encode("utf-8")).hexdigest() != digest:
            raise ValueError("Confirm the exact visible preview digest")
        if row["state"] == "RECORDED":
            return row
        if row["state"] != "PREVIEWED":
            raise ValueError("Send is unconfirmed; query the original request, never resend")
        identity = self._identity()
        # CAS commits before the one permitted POST, including concurrent button clicks.
        row = self._save(row, "SENDING", platformInstanceId=identity["platformInstanceId"])
        try:
            receipt = self.platform.request("/goal-proposals", {"requestId": request, "projectionJson": projection_json, "digest": digest})
            return self._confirm(row, receipt)
        except (PlatformError, ValueError, KeyError, TypeError):
            return self._save(row, "UNKNOWN", note="Query the original request; no automatic replay")

    def query(self, request):
        row = self._record(request)
        if row["state"] == "PREVIEWED":
            raise ValueError("This selected projection has not been sent")
        identity = self._identity()
        if row.get("platformInstanceId") != identity["platformInstanceId"]:
            raise ValueError("Original platform instance changed; keep the original outcome unresolved")
        if row["state"] == "SENDING":
            row = self._save(row, "UNKNOWN", note="Interrupted send requires receipt lookup")
        try:
            receipt = self.platform.request("/goal-proposals/" + request)
            return self._confirm(row, receipt)
        except (PlatformError, ValueError, KeyError, TypeError):
            # A 404 is not permission to POST again. It can be a different/stale source.
            if row["state"] == "RECORDED":
                return self._save(row, "RECORDED", note="Current lookup failed; retain the original verified receipt")
            return self._save(row, "UNKNOWN", note="Receipt not confirmed; only query this original request")
