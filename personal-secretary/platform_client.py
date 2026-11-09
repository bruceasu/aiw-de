"""A closed HTTP boundary; no shared database, redirects or automatic effect retries."""

import json
import re
import ssl
import urllib.error
import urllib.parse
import urllib.request
import uuid

from store import encode


class PlatformError(RuntimeError):
    def __init__(self, status, code=None, details=None):
        super().__init__(f"Platform request rejected or unconfirmed ({status}); query the original receipt")
        self.status = status
        self.code = code
        self.details = details or {}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, newurl):
        raise PlatformError(code)


class LocalPlatform:
    """Daily tools do not need a platform identity or a professional Worker."""
    base = ""
    boot = "local"

    def request(self, *_args, **_kwargs):
        raise PlatformError("PLATFORM_NOT_CONNECTED")

    def management(self, *_args):
        raise PlatformError("PLATFORM_NOT_CONNECTED")


class PlatformClient:
    def __init__(self, base_url, worker, credential, allow_loopback_http=False, client_cert=None, client_key=None, server_ca=None):
        parsed = urllib.parse.urlsplit(base_url)
        if parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ("", "/"):
            raise ValueError("Use an explicit platform origin without credentials, path or query")
        if parsed.scheme != "https" and not (allow_loopback_http and parsed.scheme == "http" and parsed.hostname in {"127.0.0.1", "::1", "localhost"}):
            raise ValueError("HTTPS is required except explicitly enabled loopback HTTP")
        if not parsed.hostname or not credential:
            raise ValueError("Platform origin and dedicated credential required")
        self.base = base_url.rstrip("/") + "/api/r1/authority"
        self.profile = "REMOTE" if parsed.scheme == "https" else "LOCAL"
        self.worker, self._credential = worker, credential
        if parsed.scheme == "https" and (not client_cert or not client_key):
            raise ValueError("Remote Agent connection requires a CA-issued client certificate and private key")
        if bool(client_cert) != bool(client_key):
            raise ValueError("Both Agent client certificate and private key are required")
        self.boot = str(uuid.uuid4())
        self.session_started = False
        direct = (allow_loopback_http and parsed.scheme == "http"
                  and parsed.hostname in {"127.0.0.1", "::1", "localhost"})
        self.transport = self._transport(client_cert, client_key, server_ca, direct=direct)

    @staticmethod
    def _transport(client_cert=None, client_key=None, server_ca=None, direct=False):
        handlers = [NoRedirect()]
        if direct:
            handlers.insert(0, urllib.request.ProxyHandler({}))
        if client_cert or server_ca:
            context = ssl.create_default_context()
            if server_ca:
                context.load_verify_locations(cafile=server_ca)
            if client_cert and client_key:
                context.load_cert_chain(certfile=client_cert, keyfile=client_key)
            handlers.insert(0, urllib.request.HTTPSHandler(context=context))
        return urllib.request.build_opener(*handlers)

    @staticmethod
    def pair(base_url, invitation, profile, client_cert=None, client_key=None, server_ca=None):
        parsed = urllib.parse.urlsplit(base_url)
        if parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ("", "/"):
            raise ValueError("Use an explicit Control Plane origin without credentials, path or query")
        loopback = parsed.hostname in {"127.0.0.1", "::1", "localhost"}
        if profile == "LOCAL":
            if parsed.scheme != "http" or not loopback:
                raise ValueError("Local Agent pairing requires an explicit loopback origin")
        elif profile == "REMOTE":
            if parsed.scheme != "https":
                raise ValueError("Remote Agent pairing requires HTTPS")
            if not client_cert or not client_key:
                raise ValueError("Remote Agent pairing requires a CA-issued client certificate and private key")
        else:
            raise ValueError("Unknown Agent connection profile")
        payload = encode({"invitation": invitation}).encode()
        if len(payload) > 4096:
            raise ValueError("Agent invitation exceeds the fixed request limit")
        request = urllib.request.Request(base_url.rstrip("/") + "/api/r1/authority/agents/pair", data=payload,
            headers={"Content-Type": "application/json"}, method="POST")
        transport = PlatformClient._transport(client_cert, client_key, server_ca, direct=profile == "LOCAL")
        try:
            with transport.open(request, timeout=20) as response:
                raw = response.read(32769)
                if len(raw) > 32768:
                    raise ValueError("Agent pairing response too large")
                result = json.loads(raw)
        except urllib.error.HTTPError as error:
            raise PlatformError(error.code) from None
        except (urllib.error.URLError, TimeoutError, OSError):
            raise PlatformError("pairing-unconfirmed") from None
        if (not isinstance(result, dict) or result.get("profile") != profile or
                not isinstance(result.get("agentId"), str) or not isinstance(result.get("ownerId"), str) or
                not isinstance(result.get("credential"), str)):
            raise PlatformError("invalid-pairing-response")
        return result

    @staticmethod
    def register_local(base_url, agent_id, credential):
        parsed = urllib.parse.urlsplit(base_url)
        if (parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ("", "/")
                or parsed.scheme != "http" or parsed.hostname not in {"127.0.0.1", "::1", "localhost"}):
            raise ValueError("Automatic Agent enrollment requires an explicit loopback HTTP Control Plane origin")
        if (not isinstance(agent_id, str) or not agent_id.startswith("a-local-")
                or not isinstance(credential, str) or not re.fullmatch(r"[A-Za-z0-9_-]{43,128}", credential)):
            raise ValueError("Local Agent identity or credential is invalid")
        payload = encode({"agentId": agent_id, "credential": credential}).encode()
        if len(payload) > 8192:
            raise ValueError("Local Agent enrollment request too large")
        request = urllib.request.Request(base_url.rstrip("/") + "/api/r1/authority/agents/local/register", data=payload,
            headers={"Content-Type": "application/json"}, method="POST")
        try:
            with PlatformClient._transport(direct=True).open(request, timeout=20) as response:
                raw = response.read(32769)
                if len(raw) > 32768:
                    raise ValueError("Local Agent enrollment response too large")
                result = json.loads(raw)
        except urllib.error.HTTPError as error:
            raise PlatformError(error.code) from None
        except (urllib.error.URLError, TimeoutError, OSError):
            raise PlatformError("local-enrollment-unconfirmed") from None
        if (not isinstance(result, dict) or result.get("profile") != "LOCAL" or result.get("agentId") != agent_id
                or not isinstance(result.get("ownerId"), str) or result.get("approvalState") != "APPROVED"):
            raise PlatformError("invalid-local-enrollment-response")
        return result

    def request(self, path, body=None):
        operation = path.split("?", 1)[0]
        goal_receipt = operation.startswith("/goal-proposals/") and operation == "/goal-proposals/" + str(uuid.UUID(operation.removeprefix("/goal-proposals/")))
        plan_receipt = operation.startswith("/personal-plans/") and operation == "/personal-plans/" + str(uuid.UUID(operation.removeprefix("/personal-plans/")))
        permission_receipt = operation.startswith("/permission-actions/") and operation != "/permission-actions/prepare" and operation == "/permission-actions/" + str(uuid.UUID(operation.removeprefix("/permission-actions/")))
        professional_receipt = operation.startswith("/professional-tasks/") and not operation.endswith("/continue") and operation not in {"/professional-tasks/options", "/professional-tasks/diagnostics"} and operation == "/professional-tasks/" + str(uuid.UUID(operation.removeprefix("/professional-tasks/")))
        professional_continue = operation.startswith("/professional-tasks/") and operation.endswith("/continue")
        message_ack = operation.startswith("/messages/") and operation.endswith("/ack")
        if message_ack:
            identity = operation.removeprefix("/messages/").removesuffix("/ack").rstrip("/")
            message_ack = identity == str(uuid.UUID(identity))
        certificate_rotation = operation == "/agents/certificate-rotation/request"
        if professional_continue:
            identity = operation.removeprefix("/professional-tasks/").removesuffix("/continue")
            professional_continue = identity == str(uuid.UUID(identity)) and body == {}
        if operation not in {"/professional-tasks", "/professional-tasks/options", "/professional-tasks/diagnostics", "/sessions", "/connection-state", "/identity", "/grants", "/action-permissions", "/permission-questions", "/permission-actions/prepare", "/state", "/management-state", "/actions", "/coordination", "/restrictions", "/questions", "/question-replies", "/goal-proposals", "/personal-plans", "/messages"} and not operation.startswith("/receipts/") and not permission_receipt and not goal_receipt and not plan_receipt and not professional_receipt and not professional_continue and not message_ack and not certificate_rotation:
            raise ValueError("Unknown platform operation")
        payload = None if body is None else encode(body).encode()
        if payload is not None and len(payload) > 32768:
            raise ValueError("Platform action exceeds the fixed envelope limit")
        request = urllib.request.Request(self.base + path, data=payload,
            headers={"Authorization": "Bearer " + self._credential, "X-AIW-Agent": self.worker, "X-AIW-Agent-Boot": self.boot, "Content-Type": "application/json"},
            method="GET" if body is None else "POST")
        try:
            with self.transport.open(request, timeout=20) as response:
                raw = response.read(2 * 1024 * 1024 + 1)
                if len(raw) > 2 * 1024 * 1024:
                    raise ValueError("Platform response too large")
                return json.loads(raw)
        except urllib.error.HTTPError as error:
            if error.code == 409:
                try:
                    raw = error.read(32769)
                    result = json.loads(raw) if len(raw) <= 32768 else {}
                except (ValueError, OSError):
                    result = {}
                if isinstance(result, dict) and result.get("code") == "PROFESSIONAL_PLAN_NOT_DRAFT":
                    details = {key: value for key in ("planId", "planState")
                               if isinstance((value := result.get(key)), str) and len(value) <= 128}
                    raise PlatformError(409, result["code"], details) from None
            if error.code == 422:
                raw = error.read(32769)
                if len(raw) <= 32768:
                    result = json.loads(raw)
                    if isinstance(result, dict) and result.get("state") == "REJECTED":
                        return result
            raise PlatformError(error.code) from None
        except (urllib.error.URLError, TimeoutError, OSError):
            raise PlatformError("transport-unconfirmed") from None

    def state(self, grant):
        return self.request("/state?" + urllib.parse.urlencode({"grantId": grant}))

    def management(self, plan):
        return self.request("/management-state?" + urllib.parse.urlencode({"planId": plan}))

    def receipt(self, request):
        return self.request("/receipts/" + urllib.parse.quote(request, safe=""))
