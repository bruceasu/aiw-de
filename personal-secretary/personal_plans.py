"""Create one inert platform draft per explicitly scheduled private work."""

from platform_client import PlatformError
from store import encode


class PersonalPlans:
    def __init__(self, store, platform):
        self.store, self.platform = store, platform

    def ensure(self, work):
        identity = self.platform.request("/identity")
        if identity.get("ownerId") != self.store.owner or identity.get("workerId") != self.store.worker:
            raise ValueError("Personal platform identity changed")
        self.store.bind_platform(identity["platformInstanceId"])
        key = "plan:" + work["id"]
        row = self.store.get("decision", key)
        if row is None:
            body = {"requestId": work["id"], "title": work["title"], "body": work["body"]}
            if len(encode(body).encode("utf-8")) > 32768:
                raise ValueError("Selected personal work exceeds the platform envelope limit")
            row = self.store.put("decision", key, {"kind": "PERSONAL_PLAN", "state": "PREPARED",
                "body": body, "platformInstanceId": identity["platformInstanceId"]}, 0)
        if row["platformInstanceId"] != identity["platformInstanceId"]:
            raise ValueError("Original platform instance changed")
        if row["state"] == "CREATED":
            return row
        if row["state"] == "PREPARED":
            row = self._save(row, "SENDING")
            try:
                receipt = self.platform.request("/personal-plans", row["body"])
            except PlatformError:
                return self._save(row, "UNKNOWN")
        else:
            # An interrupted or unconfirmed POST is only queried, never resent.
            try:
                receipt = self.platform.request("/personal-plans/" + work["id"])
            except PlatformError:
                return self._save(row, "UNKNOWN")
        expected = {"requestId": work["id"], "ownerId": self.store.owner, "workerId": self.store.worker,
                    "platformInstanceId": row["platformInstanceId"]}
        if not isinstance(receipt, dict) or any(receipt.get(key) != value for key, value in expected.items()) or not isinstance(receipt.get("planId"), str) or not receipt["planId"].startswith("FP-"):
            return self._save(row, "UNKNOWN")
        return self._save(row, "CREATED", receipt=receipt)

    def _save(self, row, state, **extra):
        value = {key: value for key, value in row.items() if key not in {"id", "revision"}}
        return self.store.put("decision", row["id"], {**value, **extra, "state": state}, row["revision"])
