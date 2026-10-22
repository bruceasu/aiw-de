"""A closed HTTP boundary; no shared database, redirects or automatic effect retries."""

import json
import urllib.error
import urllib.parse
import urllib.request
import uuid

from store import encode


class PlatformError(RuntimeError):
    def __init__(self, status):
        super().__init__(f"Platform request rejected or unconfirmed ({status}); query the original receipt")
        self.status = status


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, newurl):
        raise PlatformError(code)


class PlatformClient:
    def __init__(self, base_url, worker, credential, allow_loopback_http=False):
        parsed = urllib.parse.urlsplit(base_url)
        if parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ("", "/"):
            raise ValueError("Use an explicit platform origin without credentials, path or query")
        if parsed.scheme != "https" and not (allow_loopback_http and parsed.scheme == "http" and parsed.hostname in {"127.0.0.1", "::1", "localhost"}):
            raise ValueError("HTTPS is required except explicitly enabled loopback HTTP")
        if not parsed.hostname or not credential:
            raise ValueError("Platform origin and dedicated credential required")
        self.base = base_url.rstrip("/") + "/api/r1/authority"
        self.worker, self._credential = worker, credential
        self.boot = str(uuid.uuid4())
        self.transport = urllib.request.build_opener(NoRedirect())

    def request(self, path, body=None):
        operation = path.split("?", 1)[0]
        if operation not in {"/sessions", "/identity", "/grants", "/state", "/management-state", "/actions", "/coordination", "/restrictions", "/questions", "/question-replies"} and not operation.startswith("/receipts/"):
            raise ValueError("Unknown platform operation")
        payload = None if body is None else encode(body).encode()
        if payload is not None and len(payload) > 32768:
            raise ValueError("Platform action exceeds the fixed envelope limit")
        request = urllib.request.Request(self.base + path, data=payload,
            headers={"Authorization": "Bearer " + self._credential, "X-AIW-Personal-Worker": self.worker, "X-AIW-Personal-Boot": self.boot, "Content-Type": "application/json"},
            method="GET" if body is None else "POST")
        try:
            with self.transport.open(request, timeout=20) as response:
                raw = response.read(2 * 1024 * 1024 + 1)
                if len(raw) > 2 * 1024 * 1024:
                    raise ValueError("Platform response too large")
                return json.loads(raw)
        except urllib.error.HTTPError as error:
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
