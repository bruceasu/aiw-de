"""Exact-task access tokens in memory. No refresh token, credential file or environment mutation."""
from __future__ import annotations

import copy
import hashlib
import os
import re
import threading
from urllib.parse import quote, urlsplit


def capability():
    reference = os.environ.get("AIW_MODEL_CREDENTIAL_REF", "")
    version = os.environ.get("AIW_MODEL_CREDENTIAL_VERSION", "")
    if not re.fullmatch(r"MC-[0-9a-f-]{36}", reference) or not re.fullmatch(r"[1-9][0-9]*", version):
        raise RuntimeError("MODEL_CONNECTION_REQUIRED")
    return "model-auth:" + hashlib.sha256((reference + ":" + version).encode()).hexdigest()


class ModelCredentialProvider:
    def __init__(self, client, config, task, attempt, model):
        capability()
        endpoint = urlsplit(client.base)
        if not client.token or endpoint.scheme != "https" and endpoint.hostname not in {"127.0.0.1", "localhost", "::1"}:
            raise RuntimeError("MODEL_AUTH_SECURE_PLANE_REQUIRED")
        if not getattr(client, "model_proof", ""):
            raise RuntimeError("MODEL_WORKER_PROOF_REQUIRED")
        self.client = copy.copy(client)
        self.client.timeout = min(self.client.timeout, 25)
        self.task, self.attempt, self.model = task, attempt, model
        self.worker = config.worker_id
        self.reference = os.environ["AIW_MODEL_CREDENTIAL_REF"]
        self.version = int(os.environ["AIW_MODEL_CREDENTIAL_VERSION"])
        self.lock = threading.Lock()
        self.generation = None

    def access(self, kind, force=False):
        with self.lock:
            self.attempt.check()
            request = {"lease": {"workerId": self.worker, "attemptId": self.task["attemptId"], "leaseToken": self.task["leaseToken"]},
                       "connectionId": self.reference, "connectionVersion": self.version, "model": self.model,
                       "rejectedGeneration": self.generation if force else None}
            try:
                result = self.client._post("/tasks/" + quote(self.task["taskId"], safe="") + "/model-credential", request)
            except Exception:
                raise RuntimeError("MODEL_AUTH_UNAVAILABLE") from None
            self.attempt.check()
            if (not isinstance(result, dict) or result.get("kind") != kind or not isinstance(result.get("accessToken"), str)
                    or not result["accessToken"] or type(result.get("generation")) is not int
                    or type(result.get("expiresAt")) is not int
                    or self.generation is not None and result["generation"] < self.generation):
                raise RuntimeError("MODEL_CREDENTIAL_RESPONSE_INVALID")
            self.generation = result["generation"]
            return result

    def codex_login(self, session):
        token = self.access("CODEX_APP_SERVER")
        result = session.rpc("account/login/start", {"type": "chatgptAuthTokens", "accessToken": token["accessToken"],
                              "chatgptAccountId": token["accountId"], "chatgptPlanType": token.get("planType")})
        if result.get("type") != "chatgptAuthTokens":
            raise RuntimeError("CODEX_EXTERNAL_AUTH_UNCONFIRMED")

    def codex_refresh(self, message):
        token = self.access("CODEX_APP_SERVER", force=True)
        previous = message.get("params", {}).get("previousAccountId")
        if previous is not None and previous != token.get("accountId"):
            raise RuntimeError("CODEX_EXTERNAL_ACCOUNT_CHANGED")
        return {"id": message["id"], "result": {"accessToken": token["accessToken"],
                "chatgptAccountId": token["accountId"], "chatgptPlanType": token.get("planType")}}
