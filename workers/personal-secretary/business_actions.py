"""Concrete action selections use durable originals and the existing immutable outbox."""
from __future__ import annotations

import hashlib
import json
import urllib.parse
import uuid

from platform_client import PlatformError
from store import encode


class BusinessActions:
    def __init__(self, runtime):
        self.runtime = runtime
        self.store = runtime.store
        self.platform = runtime.platform

    def _enabled(self):
        if not self.runtime.enabled:
            raise ValueError("Explicit local enablement required")

    def permissions(self):
        self._enabled()
        return {"permissions": self.platform.request("/action-permissions"), "requests": [
            {"requestId": row["id"], "state": row["phase"], "permissionId": row["selection"]["permissionId"], "targetId": row["selection"]["targetId"]}
            for row in self.store.list("decision") if row.get("kind") == "BUSINESS_ACTION_SELECTION"]}

    def questions(self, permission, plan):
        self._enabled()
        return self.platform.request("/permission-questions?" + urllib.parse.urlencode({"permissionId": permission, "planId": plan}))

    def _save(self, request, value, revision=0):
        clean = {key: item for key, item in value.items() if key not in {"id", "revision"}}
        return self.store.put("decision", request, clean, revision)

    def _original(self, request):
        value = self.store.get("decision", request)
        if value is None or value.get("kind") != "BUSINESS_ACTION_SELECTION":
            raise ValueError("Original business selection not found")
        if value.get("ownerId") != self.store.owner or value.get("workerId") != self.store.worker:
            raise ValueError("Selection belongs to another private identity")
        return value

    def status(self, request):
        value = self._original(request)
        if value["phase"] != "QUEUED" or not self.runtime.enabled:
            return {"requestId": request, "state": value["phase"]}
        try:
            return self.platform.receipt(request)
        except PlatformError:
            return {"requestId": request, "state": "QUEUED", "message": "原操作仍待核对，尚无可确认回执；不会生成新请求。"}

    def queue(self, data):
        self._enabled()
        if data.get("projectionJson") is not None and hashlib.sha256(data["projectionJson"].encode()).hexdigest() != data.get("digest"):
            raise ValueError("Confirm the exact selected projection")
        request = data.get("requestId")
        if request is not None and str(uuid.UUID(request)) != request:
            raise ValueError("Canonical request UUID required")
        if request is None:
            matches = [row for row in self.store.list("decision") if row.get("kind") == "BUSINESS_ACTION_SELECTION"
                       and all(row["selection"].get(key) == data.get(key) for key in ("permissionId", "targetId", "step", "projectionJson"))
                       and row["selection"]["parameters"] == (data.get("parameters") or {})
                       and (data.get("sourceTaskId") is None or row["selection"]["sourceTaskId"] == data["sourceTaskId"])]
            if len(matches) > 1:
                raise ValueError("Select the original request from the recorded request list")
            if matches:
                request = matches[0]["id"]
        original = self.store.get("decision", request) if request else None
        if original is not None:
            original = self._original(request)
            selection = original["selection"]
            # A request-only call explicitly resumes the persisted original.
            if set(data) != {"requestId"}:
                supplied = {key: data.get(key) for key in ("permissionId", "targetId", "step", "sourceTaskId", "parameters", "projectionJson")}
                supplied["parameters"] = supplied["parameters"] or {}
                if supplied["projectionJson"] is not None and supplied["sourceTaskId"] is None:
                    supplied["sourceTaskId"] = selection["sourceTaskId"]
                if encode({**supplied, "requestId": request}) != encode(selection):
                    raise ValueError("Original business selection cannot change")
            if original["phase"] == "QUEUED":
                return self.status(request)
        else:
            projection = data.get("projectionJson")
            source = data.get("sourceTaskId")
            if projection is not None:
                if not isinstance(projection, str) or hashlib.sha256(projection.encode()).hexdigest() != data.get("digest"):
                    raise ValueError("Confirm the exact selected projection")
                reply = json.loads(projection)["reply"]
                visible = self.questions(data["permissionId"], data["targetId"])
                question = next((q for q in visible["questions"] if q["questionId"] == reply["questionId"] and q["requestVersion"] == reply["requestVersion"]), None)
                if question is None:
                    raise ValueError("Current selected question required")
                source = question["source"].get("taskId")
                if question["kind"] == "AUTHORIZATION":
                    fixed = question["authorization"]["requestId"]
                    if request is not None and request != fixed:
                        raise ValueError("Use the original authorization request UUID")
                    request = fixed
            request = request or str(uuid.uuid4())
            selection = {"requestId": request, "permissionId": data["permissionId"], "targetId": data["targetId"], "step": data.get("step"),
                         "sourceTaskId": source, "parameters": data.get("parameters") or {}, "projectionJson": projection}
            if self.store.get("decision", request) is not None:
                return self.queue({**data, "requestId": request, "sourceTaskId": source})
            original = self._save(request, {"kind": "BUSINESS_ACTION_SELECTION", "ownerId": self.store.owner,
                "workerId": self.store.worker, "phase": "NEW", "selection": selection})
        try:
            if original["phase"] == "NEW":
                prepared = self.platform.request("/permission-actions/prepare", selection)
            else:
                try:
                    prepared = self.platform.request("/permission-actions/" + request)
                except PlatformError as error:
                    if error.status != 404 or original["phase"] == "PREPARED":
                        raise
                    # This queue call is an explicit retry after reading the missing original.
                    prepared = self.platform.request("/permission-actions/prepare", selection)
        except (PlatformError, ValueError) as error:
            phase = "PREPARATION_REJECTED" if isinstance(error, PlatformError) and error.status in {400, 401, 403, 409, 413, 422} else "PREPARATION_UNKNOWN"
            self._save(request, {**original, "phase": phase}, original["revision"])
            return {"requestId": request, "state": phase, "message": "原选择已保存，请按此请求核对；不会换标识或自动重新操作。"}
        if prepared.get("state") != "PREPARED":
            self._save(request, {**original, "phase": "STALE"}, original["revision"])
            return {"requestId": request, "state": "STALE", "message": "原进程或平台世代已变化，原候选不再排队执行。"}
        envelope = prepared["envelope"]
        if envelope.get("requestId") != request or prepared.get("permissionId") != selection["permissionId"] or prepared.get("questionProjection", "") != (selection["projectionJson"] or ""):
            raise ValueError("Original preparation identity or projection changed")
        self.store.bind_platform(prepared["platformInstanceId"])
        original = self._save(request, {**original, "phase": "PREPARED", "prepared": prepared}, original["revision"])
        path = "/question-replies" if selection["projectionJson"] is not None else "/actions"
        body = {"envelope": envelope, "projectionJson": selection["projectionJson"]} if path == "/question-replies" else envelope
        self.store.queue(request, path, body)
        self._save(request, {**original, "phase": "QUEUED"}, original["revision"])
        return {"requestId": request, "state": "PENDING"}
