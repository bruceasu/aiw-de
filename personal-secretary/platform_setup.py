"""Interactive platform binding without moving the owner-local private store."""
import base64
import json
import re
import urllib.parse

from platform_client import PlatformClient, PlatformError
from private_config import read_private, write_private


class PrivateIdentity:
    """Keep physical storage ownership; expose the explicitly bound platform identity."""
    def __init__(self, store):
        self.local = store
        self.owner, self.worker = store.owner, store.worker
        with store.lock:
            self.bound = store.db.execute("SELECT platform_instance FROM personal_identity WHERE id=1").fetchone()[0] is not None

    def __getattr__(self, name):
        return getattr(self.local, name)


class PlatformConnection:
    def __init__(self, backend):
        self.backend = backend

    def __getattr__(self, name):
        return getattr(self.backend, name)

    @property
    def session_started(self):
        return getattr(self.backend, "session_started", False)

    @session_started.setter
    def session_started(self, value):
        self.backend.session_started = value


class PlatformSetup:
    def __init__(self, runtime):
        self.runtime = runtime
        if isinstance(runtime.platform.backend, PlatformClient): runtime.store.bound = True
        self.path = runtime.store.path.parent / "platform-connection.json"
        saved = read_private(self.path, {})
        if saved:
            value = self.validate(saved)
            existing = runtime.platform.backend
            # A startup handshake may already have registered this exact client.
            # Keep its process boot instead of replacing it with a fresh session.
            if not (isinstance(existing, PlatformClient) and existing.base == value["platform"] + "/api/r1/authority"
                    and existing.worker == value["workerId"] and existing._credential == value["credential"]):
                runtime.platform.backend = self.client(value)
            runtime.store.owner, runtime.store.worker = value["ownerId"], value["workerId"]
            runtime.store.bound = True

    @staticmethod
    def validate(value):
        if not isinstance(value, dict) or set(value) != {"platform", "ownerId", "workerId", "credential"}:
            raise ValueError("连接信息格式不正确，请重新从平台连接向导获取。")
        if any(not isinstance(item, str) or not item for item in value.values()):
            raise ValueError("连接信息不完整，请重新获取。")
        if not re.fullmatch(r"[A-Za-z0-9_-]{43,128}", value["credential"]) or len(value["ownerId"]) > 64 or len(value["workerId"]) > 128:
            raise ValueError("连接信息超出允许范围。")
        parsed = urllib.parse.urlsplit(value["platform"])
        if parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in {"", "/"} or not parsed.hostname:
            raise ValueError("连接向导需要完整的平台地址，不接受带路径或登录信息的地址。")
        return {**value, "platform": value["platform"].rstrip("/")}

    @staticmethod
    def client(value):
        parsed = urllib.parse.urlsplit(value["platform"])
        loopback = parsed.scheme == "http" and parsed.hostname in {"127.0.0.1", "localhost", "::1"}
        return PlatformClient(value["platform"], value["workerId"], value["credential"], loopback)

    def connect(self, code):
        if not isinstance(code, str) or not code.startswith("aiw-secretary-v1.") or len(code) > 4096:
            raise ValueError("请通过平台连接向导发送连接信息，或粘贴完整连接码。")
        try:
            encoded = code.split(".", 1)[1]
            value = self.validate(json.loads(base64.b64decode(encoded + "=" * (-len(encoded) % 4), altchars=b"-_", validate=True)))
        except (ValueError, UnicodeError):
            raise ValueError("连接码无效，请从平台连接向导重新获取。") from None
        runtime = self.runtime
        with runtime.daily.lock:
            if runtime.daily.busy or runtime.store.pending() or any(runtime.professional.pending(row["id"]) for row in runtime.store.list("work")):
                raise ValueError("仍有工作或平台请求正在处理，请完成核对后再更改连接。")
            current = runtime.store
            if current.bound and (current.owner, current.worker) != (value["ownerId"], value["workerId"]):
                raise ValueError("此私库已关联另一位平台秘书；请在平台选择原秘书重新连接，避免混用身份。")
            client = self.client(value)
            saved = read_private(self.path, {})
            existing = runtime.platform.backend
            if saved == value and isinstance(existing, PlatformClient) and existing.session_started:
                return self.connected(value, existing)
            try:
                identity = client.request("/sessions", {"workerBoot": client.boot})
                if identity.get("ownerId") != value["ownerId"] or identity.get("workerId") != value["workerId"]:
                    raise ValueError("平台身份与连接信息不一致，未保存连接。")
                client.session_started = True
                identity = client.request("/connection-state")
                if identity.get("ownerId") != value["ownerId"] or identity.get("workerId") != value["workerId"] or not identity.get("platformInstanceId"):
                    raise ValueError("无法确认平台身份，未保存连接。")
                current.bind_platform(identity["platformInstanceId"])
            except PlatformError:
                raise ValueError("暂时无法连接平台。请检查平台地址、登录授权和服务状态，再回到连接向导检查。") from None
            write_private(self.path, value)
            current.owner, current.worker = value["ownerId"], value["workerId"]
            current.bound = True
            runtime.platform.backend = client
            runtime.enabled = False
            return self.connected(value, client)

    def connected(self, value, client):
        return {"connected": True, "platform": value["platform"], "ownerId": value["ownerId"],
                "workerId": value["workerId"], "workerBoot": client.boot,
                "nextUrl": value["platform"] + "/ui/secretary-setup?secretarySetup=" + urllib.parse.quote(value["workerId"])}

    def check(self):
        runtime = self.runtime
        if not isinstance(runtime.platform.backend, PlatformClient):
            raise ValueError("请先通过连接向导连接专业平台。")
        try:
            if not runtime.platform.session_started:
                session = runtime.platform.request("/sessions", {"workerBoot": runtime.platform.boot})
                if session.get("ownerId") != runtime.store.owner or session.get("workerId") != runtime.store.worker:
                    raise ValueError("平台身份不一致，请重新连接原秘书。")
                runtime.platform.session_started = True
            state = runtime.platform.request("/connection-state")
            if state.get("ownerId") != runtime.store.owner or state.get("workerId") != runtime.store.worker:
                raise ValueError("平台身份不一致，请重新连接原秘书。")
            runtime.enabled = state.get("enabled") is True
            runtime.platform_observed = {"enabled": runtime.enabled, "workerBoot": runtime.platform.boot, "generation": state.get("generation")}
            notice = state.get("authorization") or {}
            if notice.get("id"):
                prior = runtime.store.get("decision", "platform-authorization-notice")
                if not prior or prior.get("notificationId") != notice["id"]:
                    runtime.store.put("decision", "platform-authorization-notice", {"kind": "PLATFORM_AUTHORIZATION_NOTICE", "notificationId": notice["id"],
                        "planId": notice.get("planId"), "createdAt": notice.get("createdAt"), "state": "RECEIVED"}, prior["revision"] if prior else 0)
            return {**self.public(), "note": "连接与进程授权已确认。" if runtime.enabled else "已连接，请在平台向导确认当前秘书及代理范围。"}
        except PlatformError:
            runtime.enabled = False
            raise ValueError("连接状态未获确认，请检查平台服务并回到连接向导。") from None

    def diagnostics(self, plan_id=None):
        """Only GETs on the existing process session; never enroll or enable."""
        runtime = self.runtime
        if not isinstance(runtime.platform.backend, PlatformClient):
            return {"missing": [{"code": "PLATFORM_CONNECTION_REQUIRED", "message": "秘书尚未连接专业平台。"}]}
        if not runtime.platform.session_started:
            return {"missing": [{"code": "PROCESS_SESSION_REQUIRED", "message": "当前秘书尚未完成进程连接，请检查连接后再确认当前进程。"}]}
        state = runtime.platform.request("/connection-state")
        if state.get("ownerId") != runtime.store.owner or state.get("workerId") != runtime.store.worker:
            raise ValueError("平台身份与原秘书不一致，请重新连接原秘书。")
        runtime.platform_observed = {"enabled": state.get("enabled") is True, "workerBoot": runtime.platform.boot, "generation": state.get("generation")}
        result = {"processEnabled": state.get("enabled") is True, "localProcessingEnabled": runtime.enabled,
                  "backgroundError": runtime.background_error, "missing": []}
        if plan_id:
            if not isinstance(plan_id, str) or len(plan_id) > 128: raise ValueError("请选择有效计划。")
            details = runtime.platform.request("/professional-tasks/diagnostics?" + urllib.parse.urlencode({"planId": plan_id}))
            result.update(details)
        elif not result["processEnabled"]:
            result["missing"] = [{"code": "PROCESS_ENABLE_REQUIRED", "message": "平台已连接，但当前秘书进程尚未启用。"}]
        return result

    def public(self):
        backend = self.runtime.platform.backend
        connected = isinstance(backend, PlatformClient)
        origin = backend.base.removesuffix("/api/r1/authority") if connected else "http://127.0.0.1:8080"
        observed = self.runtime.platform_observed
        return {"connected": connected, "platform": origin, "enabled": observed["enabled"] if observed else self.runtime.enabled,
                "localProcessingEnabled": self.runtime.enabled,
                "setupUrl": origin + "/ui/secretary-setup?secretarySetup=" + urllib.parse.quote(self.runtime.store.worker if connected else "new")}
