"""Interactive platform binding without moving the owner-local private store."""
import base64
import json
import os
import re
import secrets
import threading
import urllib.parse
import uuid

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
        self.pairing_lock = threading.Lock()
        if isinstance(runtime.platform.backend, PlatformClient): runtime.store.bound = True
        self.path = runtime.store.path.parent / "platform-connection.json"
        self.local_path = runtime.store.path.parent / "local-enrollment.json"
        saved = read_private(self.path, {})
        self.legacy_connection = False
        if saved:
            try:
                value = self.validate(saved)
            except ValueError:
                # Keep legacy credentials as local history; never send them to the new protocol.
                self.legacy_connection = True
            else:
                existing = runtime.platform.backend
                if not (isinstance(existing, PlatformClient) and existing.base == value["platform"] + "/api/r1/authority"
                        and existing.worker == value["agentId"] and existing._credential == value["credential"]):
                    runtime.platform.backend = self.client(value)
                runtime.store.owner, runtime.store.worker = value["ownerId"], value["agentId"]
                runtime.store.bound = True
        self.startup()

    @staticmethod
    def local_origin(value):
        parsed = urllib.parse.urlsplit(value) if isinstance(value, str) else None
        if (parsed is None or parsed.username or parsed.password or parsed.query or parsed.fragment
                or parsed.path not in {"", "/"} or parsed.scheme != "http"
                or parsed.hostname not in {"127.0.0.1", "localhost", "::1"}):
            raise ValueError("本机自动连接只接受明确的 loopback HTTP 地址。")
        return value.rstrip("/")

    def startup(self):
        """Resume saved LOCAL connections; REMOTE still waits for its explicit process enablement."""
        runtime = self.runtime
        backend = runtime.platform.backend
        try:
            if isinstance(backend, PlatformClient) and backend.profile == "LOCAL":
                saved = read_private(self.path, {})
                if saved:
                    value = self.validate(saved)
                else:
                    value = self.validate({"platform": backend.base.removesuffix("/api/r1/authority"),
                        "ownerId": runtime.store.owner, "agentId": backend.worker, "credential": backend._credential,
                        "profile": "LOCAL", "clientCert": None, "clientKey": None, "serverCa": None})
                if value["profile"] != "LOCAL":
                    return
                if value["agentId"].startswith("a-local-"):
                    registered = PlatformClient.register_local(value["platform"], value["agentId"], value["credential"])
                    if registered["ownerId"] != value["ownerId"]:
                        raise ValueError("本机 Agent 已绑定到不同的默认管理员身份。")
                self.check()
            elif not isinstance(backend, PlatformClient):
                pending = read_private(self.local_path, {})
                if pending:
                    self._connect_local_once(pending.get("platform"), startup=True)
        except (PlatformError, OSError, ValueError):
            runtime.enabled = False
            runtime.platform.session_started = False
            runtime.platform_observed = None

    @staticmethod
    def validate(value):
        required = {"platform", "ownerId", "agentId", "credential", "profile", "clientCert", "clientKey", "serverCa"}
        if not isinstance(value, dict) or set(value) != required:
            raise ValueError("Agent connection settings require a new pairing; legacy credentials are not migrated")
        if any(not isinstance(value.get(key), str) or not value[key]
               for key in ("platform", "ownerId", "agentId", "credential", "profile")):
            raise ValueError("Agent connection settings are incomplete")
        if (not re.fullmatch(r"[A-Za-z0-9_-]{43,128}", value["credential"])
                or len(value["ownerId"]) > 64 or len(value["agentId"]) > 128):
            raise ValueError("Agent connection settings exceed the allowed limits")
        if value["profile"] not in {"LOCAL", "REMOTE"}:
            raise ValueError("Unknown Agent connection profile")
        if value.get("serverCa") is not None and (not isinstance(value["serverCa"], str) or not value["serverCa"].strip()):
            raise ValueError("Server CA path must be a non-empty string when configured")
        if value["profile"] == "REMOTE" and any(not isinstance(value.get(key), str) or not value[key]
                                                  for key in ("clientCert", "clientKey")):
            raise ValueError("Remote Agent connection requires certificate and private key paths")
        if value["profile"] == "LOCAL" and any(value.get(key) is not None for key in ("clientCert", "clientKey", "serverCa")):
            raise ValueError("Local Agent profile must not configure certificates or a server CA")
        parsed = urllib.parse.urlsplit(value["platform"])
        if parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in {"", "/"} or not parsed.hostname:
            raise ValueError("Use a Control Plane origin without credentials, path, or query")
        if value["profile"] == "LOCAL" and (parsed.scheme != "http" or parsed.hostname not in {"127.0.0.1", "localhost", "::1"}):
            raise ValueError("Local Agent profile requires an explicit loopback origin")
        if value["profile"] == "REMOTE" and parsed.scheme != "https":
            raise ValueError("Remote Agent profile requires HTTPS")
        return {**value, "platform": value["platform"].rstrip("/")}

    @staticmethod
    def client(value):
        parsed = urllib.parse.urlsplit(value["platform"])
        loopback = parsed.scheme == "http" and parsed.hostname in {"127.0.0.1", "localhost", "::1"}
        return PlatformClient(value["platform"], value["agentId"], value["credential"], loopback,
                              value.get("clientCert"), value.get("clientKey"),
                              os.environ.get("AIW_AGENT_SERVER_CA") or value.get("serverCa"))

    def connect(self, code):
        if not self.pairing_lock.acquire(blocking=False):
            raise ValueError("Agent pairing is already in progress; check the current connection before retrying")
        try:
            return self._connect_once(code)
        finally:
            self.pairing_lock.release()

    def connect_local(self, platform):
        if not self.pairing_lock.acquire(blocking=False):
            raise ValueError("Agent connection is already in progress; check the current connection before retrying")
        try:
            return self._connect_local_once(platform)
        finally:
            self.pairing_lock.release()

    def _connect_local_once(self, platform, startup=False):
        runtime = self.runtime
        origin = self.local_origin(platform)
        saved = read_private(self.path, {})
        client = None
        value = None
        pending_created = False
        if saved:
            value = self.validate(saved)
            if value["profile"] != "LOCAL" or value["platform"] != origin:
                raise ValueError("This secretary already has a different Control Plane connection; use a separate data directory to change identity")
            client = self.client(value)
        elif isinstance(runtime.platform.backend, PlatformClient):
            existing = runtime.platform.backend
            if existing.profile != "LOCAL" or existing.base != origin + "/api/r1/authority":
                raise ValueError("This secretary is already connected to a different Agent profile")
            value = self.validate({"platform": origin, "ownerId": runtime.store.owner, "agentId": existing.worker,
                "credential": existing._credential, "profile": "LOCAL", "clientCert": None, "clientKey": None, "serverCa": None})
            client = existing
        else:
            pending = read_private(self.local_path, {})
            if pending:
                if (not isinstance(pending, dict) or set(pending) != {"platform", "agentId", "credential"}
                        or self.local_origin(pending.get("platform")) != origin
                        or not isinstance(pending.get("agentId"), str) or not pending["agentId"].startswith("a-local-")
                        or not isinstance(pending.get("credential"), str)
                        or not re.fullmatch(r"[A-Za-z0-9_-]{43,128}", pending["credential"])):
                    raise ValueError("Saved local enrollment is invalid; keep the private data directory and reconnect with a separate identity")
            else:
                pending = {"platform": origin, "agentId": "a-local-" + str(uuid.uuid4()),
                           "credential": secrets.token_urlsafe(32)}
                write_private(self.local_path, pending)
                pending_created = True
            try:
                registered = PlatformClient.register_local(origin, pending["agentId"], pending["credential"])
            except PlatformError as error:
                raise ValueError("Unable to auto-enroll with the local Control Plane. Check that it is running on this computer") from error
            value = self.validate({"platform": origin, "ownerId": registered["ownerId"], "agentId": pending["agentId"],
                "credential": pending["credential"], "profile": "LOCAL", "clientCert": None, "clientKey": None, "serverCa": None})
            client = self.client(value)

        if value["agentId"].startswith("a-local-") and not pending_created:
            try:
                registered = PlatformClient.register_local(origin, value["agentId"], value["credential"])
            except PlatformError as error:
                raise ValueError("Unable to confirm this local Agent identity with the Control Plane") from error
            if registered["ownerId"] != value["ownerId"]:
                raise ValueError("This local Agent is bound to a different Control Plane administrator")
        if runtime.store.bound and runtime.store.owner != value["ownerId"]:
            raise ValueError("This private store is bound to a different human owner; use a separate data directory")
        try:
            if client.session_started:
                identity = client.request("/connection-state")
            else:
                identity = client.request("/sessions", {"agentBoot": client.boot})
                if identity.get("ownerId") != value["ownerId"] or identity.get("agentId") != value["agentId"]:
                    raise ValueError("Control Plane identity does not match the local Agent")
                client.session_started = True
                identity = client.request("/connection-state")
            if (identity.get("ownerId") != value["ownerId"] or identity.get("agentId") != value["agentId"]
                    or not identity.get("platformInstanceId") or identity.get("approvalState") != "APPROVED"
                    or identity.get("enabled") is not True):
                raise ValueError("The local Agent session is not fully authorized")
        except PlatformError as error:
            runtime.enabled = False
            runtime.platform.session_started = False
            runtime.platform_observed = None
            raise ValueError("Unable to confirm the local Control Plane session") from error
        runtime.store.bind_platform(identity["platformInstanceId"])
        runtime.store.owner, runtime.store.worker = value["ownerId"], value["agentId"]
        runtime.store.bound = True
        write_private(self.path, value)
        try:
            self.local_path.unlink(missing_ok=True)
        except OSError:
            pass
        runtime.platform.backend = client
        runtime.enabled = True
        runtime.platform_observed = {"enabled": True, "approvalState": "APPROVED", "agentBoot": client.boot,
                                     "generation": identity.get("generation")}
        self.legacy_connection = False
        return self.connected(value, client, identity)

    def _connect_once(self, code):
        if not isinstance(code, str) or not code.startswith("aiw-agent-v1.") or len(code) > 4096:
            raise ValueError("Use a current AIW Agent pairing code")
        try:
            encoded = code.split(".", 1)[1]
            invitation = json.loads(base64.b64decode(encoded + "=" * (-len(encoded) % 4), altchars=b"-_", validate=True))
        except (ValueError, UnicodeError):
            raise ValueError("Agent pairing code is invalid") from None
        if (not isinstance(invitation, dict) or set(invitation) != {"platform", "invitation", "profile", "ownerId"}
                or not isinstance(invitation.get("invitation"), str) or not isinstance(invitation.get("ownerId"), str)
                or invitation["profile"] not in {"LOCAL", "REMOTE"}):
            raise ValueError("Agent pairing code has invalid fields")
        runtime = self.runtime
        with runtime.daily.lock:
            saved = read_private(self.path, {})
            if runtime.store.bound or (isinstance(saved, dict) and saved.get("agentId") and saved.get("credential")):
                raise ValueError("This secretary already has an Agent identity. Check the existing connection; use a separate data directory for another Control Plane")
            if runtime.daily.busy or runtime.store.pending() or any(runtime.professional.pending(row["id"]) for row in runtime.store.list("work")):
                raise ValueError("Finish pending work before changing this Agent connection")
            if runtime.store.bound and runtime.store.owner != invitation["ownerId"]:
                raise ValueError("This private store is bound to a different human owner")
        client_cert = os.environ.get("AIW_AGENT_CLIENT_CERT")
        client_key = os.environ.get("AIW_AGENT_CLIENT_KEY")
        server_ca = os.environ.get("AIW_AGENT_SERVER_CA")
        if invitation["profile"] == "LOCAL":
            client_cert = client_key = server_ca = None
        paired = PlatformClient.pair(invitation["platform"], invitation["invitation"], invitation["profile"], client_cert, client_key, server_ca)
        value = self.validate({"platform": paired["serverOrigin"], "ownerId": paired["ownerId"], "agentId": paired["agentId"],
                               "credential": paired["credential"], "profile": paired["profile"],
                               "clientCert": client_cert, "clientKey": client_key, "serverCa": server_ca})
        with runtime.daily.lock:
            if runtime.daily.busy or runtime.store.pending() or any(runtime.professional.pending(row["id"]) for row in runtime.store.list("work")):
                raise ValueError("Finish pending work before changing this Agent connection")
            current = runtime.store
            if current.bound and current.owner != value["ownerId"]:
                raise ValueError("This private store is bound to a different human owner")
            write_private(self.path, value)
            client = self.client(value)
            saved = read_private(self.path, {})
            existing = runtime.platform.backend
            if saved == value and isinstance(existing, PlatformClient) and existing.session_started:
                return self.connected(value, existing)
            try:
                identity = client.request("/sessions", {"agentBoot": client.boot})
                if identity.get("ownerId") != value["ownerId"] or identity.get("agentId") != value["agentId"]:
                    raise ValueError("Control Plane identity does not match the paired Agent")
                client.session_started = True
                identity = client.request("/connection-state")
                if identity.get("ownerId") != value["ownerId"] or identity.get("agentId") != value["agentId"] or not identity.get("platformInstanceId"):
                    raise ValueError("Unable to confirm Control Plane Agent identity")
                current.bind_platform(identity["platformInstanceId"])
            except PlatformError:
                raise ValueError("Unable to connect to the Control Plane. Check its address, Agent certificate, and service state") from None
            write_private(self.path, value)
            current.owner, current.worker = value["ownerId"], value["agentId"]
            current.bound = True
            runtime.platform.backend = client
            runtime.enabled = False
            runtime.platform_observed = {"enabled": identity.get("enabled") is True, "approvalState": identity.get("approvalState"),
                                         "agentBoot": client.boot, "generation": identity.get("generation")}
            self.legacy_connection = False
            return self.connected(value, client, identity)

    def connected(self, value, client, state=None):
        state = state or client.request("/connection-state")
        approved = state.get("approvalState") == "APPROVED"
        return {"connected": True, "platform": value["platform"], "ownerId": value["ownerId"],
                "agentId": value["agentId"], "agentBoot": client.boot,
                "profile": value["profile"], "approvalState": state.get("approvalState"), "authorized": approved,
                "nextUrl": value["platform"] + "/ui/agents" if not approved else value["platform"] + "/ui/authority"}

    def check(self):
        runtime = self.runtime
        if not isinstance(runtime.platform.backend, PlatformClient):
            raise ValueError("请先通过连接向导连接专业平台。")
        try:
            if not runtime.platform.session_started:
                session = runtime.platform.request("/sessions", {"agentBoot": runtime.platform.boot})
                if session.get("ownerId") != runtime.store.owner or session.get("agentId") != runtime.store.worker:
                    raise ValueError("平台身份不一致，请重新连接已配对的 Agent。")
                runtime.platform.session_started = True
            state = runtime.platform.request("/connection-state")
            if state.get("ownerId") != runtime.store.owner or state.get("agentId") != runtime.store.worker:
                raise ValueError("平台身份不一致，请重新连接原秘书。")
            if (runtime.platform.profile == "LOCAL" and state.get("approvalState") == "APPROVED"
                    and state.get("enabled") is not True):
                session = runtime.platform.request("/sessions", {"agentBoot": runtime.platform.boot})
                if session.get("ownerId") != runtime.store.owner or session.get("agentId") != runtime.store.worker:
                    raise ValueError("平台身份不一致，请重新连接原秘书。")
                state = runtime.platform.request("/connection-state")
                if state.get("ownerId") != runtime.store.owner or state.get("agentId") != runtime.store.worker:
                    raise ValueError("平台身份不一致，请重新连接原秘书。")
            runtime.enabled = state.get("enabled") is True
            runtime.platform_observed = {"enabled": runtime.enabled, "approvalState": state.get("approvalState"),
                                         "agentBoot": runtime.platform.boot, "generation": state.get("generation")}
            if state.get("approvalState") == "PENDING":
                return {"missing": [{"code": "AGENT_APPROVAL_PENDING", "message": "请在 Control Plane 一次确认 Agent 身份和精确授权范围。"}]}
            notice = state.get("authorization") or {}
            if notice.get("id"):
                prior = runtime.store.get("decision", "platform-authorization-notice")
                if not prior or prior.get("notificationId") != notice["id"]:
                    runtime.store.put("decision", "platform-authorization-notice", {"kind": "PLATFORM_AUTHORIZATION_NOTICE", "notificationId": notice["id"],
                        "planId": notice.get("planId"), "createdAt": notice.get("createdAt"), "state": "RECEIVED"}, prior["revision"] if prior else 0)
            note = "本机 Control Plane 已自动登记并授权计划与任务管理。" if runtime.enabled and runtime.platform.profile == "LOCAL" else (
                "连接与进程授权已确认。" if runtime.enabled else "已连接，请在平台向导确认当前 Agent 及授权范围。")
            return {**self.public(), "note": note}
        except PlatformError:
            runtime.enabled = False
            runtime.platform.session_started = False
            runtime.platform_observed = None
            raise ValueError("连接状态未获确认，请检查平台服务并回到连接向导。") from None

    def request_certificate_rotation(self):
        backend = self.runtime.platform.backend
        if not isinstance(backend, PlatformClient) or backend.profile != "REMOTE":
            raise ValueError("Certificate rotation is available only for a paired remote Agent")
        if not backend.session_started:
            session = backend.request("/sessions", {"agentBoot": backend.boot})
            if session.get("ownerId") != self.runtime.store.owner or session.get("agentId") != self.runtime.store.worker:
                raise ValueError("Control Plane identity does not match this Agent")
            backend.session_started = True
        return backend.request("/agents/certificate-rotation/request", {})

    def diagnostics(self, plan_id=None):
        """Only GETs on the existing process session; never enroll or enable."""
        runtime = self.runtime
        if not isinstance(runtime.platform.backend, PlatformClient):
            return {"missing": [{"code": "PLATFORM_CONNECTION_REQUIRED", "message": "秘书尚未连接专业平台。"}]}
        if not runtime.platform.session_started:
            return {"missing": [{"code": "PROCESS_SESSION_REQUIRED", "message": "当前秘书尚未完成进程连接，请检查连接后再确认当前进程。"}]}
        state = runtime.platform.request("/connection-state")
        if state.get("ownerId") != runtime.store.owner or state.get("agentId") != runtime.store.worker:
            raise ValueError("平台身份与已配对 Agent 不一致，请重新连接原 Agent。")
        runtime.platform_observed = {"enabled": state.get("enabled") is True, "approvalState": state.get("approvalState"),
                                     "agentBoot": runtime.platform.boot, "generation": state.get("generation")}
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
        connected = isinstance(backend, PlatformClient) and backend.session_started
        origin = backend.base.removesuffix("/api/r1/authority") if connected else "http://127.0.0.1:8080"
        observed = self.runtime.platform_observed
        approval_state = observed.get("approvalState") if observed else None
        setup_path = "/ui/authority" if approval_state == "APPROVED" else "/ui/agents"
        return {"connected": connected, "platform": origin, "enabled": observed["enabled"] if observed else self.runtime.enabled,
                "localProcessingEnabled": self.runtime.enabled,
                "profile": backend.profile if connected else None,
                "approvalState": approval_state, "setupUrl": origin + setup_path}
