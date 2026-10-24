"""Personal UI with an independent local session. No platform session handoff."""

from __future__ import annotations

import argparse
import hashlib
import hmac
import json
import os
import secrets
import socket
import sys
import threading
import time
import urllib.parse
import webbrowser
from http.cookies import SimpleCookie
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from platform_client import PlatformClient
from runtime import Runtime
from store import SQLiteStore, encode


def desktop_session_available():
    if any(os.environ.get(key) for key in ("SSH_CONNECTION", "SSH_CLIENT", "SSH_TTY")):
        return False
    if sys.platform == "win32":
        return os.environ.get("SESSIONNAME", "").lower() != "services"
    if sys.platform == "darwin":
        return True
    return bool(os.environ.get("DISPLAY") or os.environ.get("WAYLAND_DISPLAY"))


def serve(runtime, host, port, public_origin=None, open_browser=True):
    origin = public_origin or f"http://{'[' + host + ']' if ':' in host else host}:{port}"
    parsed = urllib.parse.urlsplit(origin)
    if parsed.scheme not in {"http", "https"} or parsed.username or parsed.password or parsed.path not in {"", "/"} or parsed.query or parsed.fragment:
        raise ValueError("Explicit UI origin without path or credentials required")
    if host not in {"127.0.0.1", "::1", "localhost"} and (public_origin is None or parsed.scheme != "https"):
        raise ValueError("Remote UI requires an explicit HTTPS reverse-proxy origin")
    origin = origin.rstrip("/")
    cookie_name = "AIW_PERSONAL_" + hashlib.sha256((runtime.store.owner + runtime.store.worker).encode()).hexdigest()[:16]
    pairing = secrets.token_urlsafe(24)
    pairing_expiry = time.monotonic() + 300
    attempts = 0
    sessions = {}
    stop = threading.Event()
    lock = threading.RLock()

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass  # No URL, credential, private request or reply logging.

        def respond(self, status, body, mime="application/json"):
            payload = body.encode() if isinstance(body, str) else encode(body).encode()
            self.send_response(status)
            self.send_header("Content-Type", mime + "; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

        def valid_host(self):
            return self.headers.get("Host", "").lower() == parsed.netloc.lower()

        def session(self):
            cookie = SimpleCookie()
            try:
                cookie.load(self.headers.get("Cookie", ""))
                key = cookie.get(cookie_name)
            except Exception:
                return None
            session = sessions.get(key.value) if key else None
            if session and session["expires"] > time.monotonic():
                return session
            return None

        def do_GET(self):
            if not self.valid_host():
                self.respond(403, {"error": "Invalid UI host"})
                return
            files = {"/": ("index.html", "text/html"), "/app.js": ("app.js", "application/javascript"),
                     "/console.css": ("console.css", "text/css"), "/pairing.js": ("pairing.js", "application/javascript"),
                     "/pairing.css": ("pairing.css", "text/css")}
            if self.path in files:
                name, mime = files[self.path]
                self.respond(200, (Path(__file__).parent / "ui" / name).read_text(encoding="utf-8"), mime)
                return
            with lock:
                session = self.session()
                if session is None:
                    self.respond(401, {"error": "Personal login required"})
                elif self.path == "/api/state":
                    self.respond(200, {"csrf": session["csrf"], "ownerId": runtime.store.owner, "workerId": runtime.store.worker,
                        "platformOrigin": runtime.platform.base.removesuffix("/api/r1/authority"),
                        "enabled": runtime.enabled, "work": runtime.store.list("work"), "messages": runtime.store.list("message"),
                        "policies": runtime.store.list("policy"), "drafts": runtime.store.list("draft"), "templates": runtime.store.list("template"),
                        "todos": runtime.store.list("todo"), "decisions": runtime.store.list("decision"),
                        "receipts": [{key: row[key] for key in ("request_id", "state", "receipt")} for row in runtime.store.pending()]})
                elif self.path == "/api/backup":
                    self.respond(200, runtime.store.backup())
                elif urllib.parse.urlsplit(self.path).path == "/api/history":
                    query = urllib.parse.parse_qs(urllib.parse.urlsplit(self.path).query)
                    self.respond(200, runtime.store.history(query.get("kind", [""])[0], query.get("id", [""])[0]))
                else:
                    self.respond(404, {"error": "Unknown personal route"})

        def do_POST(self):
            nonlocal attempts, pairing, pairing_expiry
            if not self.valid_host() or self.headers.get("Origin") != origin:
                self.respond(403, {"error": "Same-origin personal UI required"})
                return
            try:
                length = int(self.headers.get("Content-Length", "-1"))
                if length < 0 or length > 32768 or self.headers.get("Transfer-Encoding"):
                    self.respond(413, {"error": "Bounded JSON body required"})
                    return
                data = json.loads(self.rfile.read(length))
                if not isinstance(data, dict):
                    raise ValueError()
                with lock:
                    if self.path == "/api/login":
                        attempts += 1
                        if not pairing or attempts > 5 or time.monotonic() > pairing_expiry or not hmac.compare_digest(str(data.get("code", "")), pairing):
                            self.respond(401, {"error": "Pairing code invalid or expired; restart to request a new code"})
                            return
                        pairing = ""
                        key = secrets.token_urlsafe(32)
                        csrf = secrets.token_urlsafe(32)
                        sessions[key] = {"csrf": csrf, "expires": time.monotonic() + 12 * 3600}
                        self.send_response(200)
                        self.send_header("Set-Cookie", f"{cookie_name}={key}; Path=/; HttpOnly; SameSite=Strict" + ("; Secure" if parsed.scheme == "https" else ""))
                        self.send_header("Cache-Control", "no-store")
                        self.send_header("Content-Type", "application/json")
                        self.end_headers()
                        self.wfile.write(encode({"csrf": csrf}).encode())
                        return
                    session = self.session()
                    if session is None or not hmac.compare_digest(self.headers.get("X-CSRF-Token", ""), session["csrf"]):
                        self.respond(403, {"error": "Current personal session and CSRF token required"})
                        return
                    if self.path == "/api/pairing":
                        # Explicitly pair another UI without restarting this Worker or reusing a session.
                        pairing = secrets.token_urlsafe(24)
                        pairing_expiry = time.monotonic() + 300
                        attempts = 0
                        result = {"code": pairing, "expiresIn": 300}
                    elif self.path == "/api/work":
                        result = runtime.create_work(data["title"], data["body"], data.get("planId") or None)
                    elif self.path == "/api/chat":
                        result = runtime.chat(data["workId"], data["text"])
                    elif self.path == "/api/enable":
                        if type(data.get("enabled")) is not bool:
                            raise ValueError()
                        result = runtime.enable(data["enabled"])
                    elif self.path == "/api/schedule":
                        result = runtime.schedule(data["workId"], data["dueMs"], data["revision"], data.get("timeZone", "UTC"))
                    elif self.path == "/api/queue-action":
                        result = runtime.queue_action(data["grantId"])
                    elif self.path == "/api/questions":
                        result = runtime.questions(data["planId"])
                    elif self.path == "/api/goal-preview":
                        result = runtime.selected_goals.preview(data["selectedGoal"], data.get("necessaryContext", ""), data.get("acceptance", ""))
                    elif self.path == "/api/goal-submit":
                        result = runtime.selected_goals.submit(data["requestId"], data["projectionJson"], data["digest"])
                    elif self.path == "/api/goal-receipt":
                        result = runtime.selected_goals.query(data["requestId"])
                    elif self.path == "/api/question-preview":
                        result = runtime.question_projection(data["questionId"], data["requestVersion"], data["rawAnswer"], data.get("selectedGoal", ""), data.get("necessaryContext", ""))
                    elif self.path == "/api/question-reply":
                        result = runtime.queue_question_reply(data["grantId"], data["projectionJson"], data["digest"])
                    elif self.path == "/api/policy":
                        result = runtime.save_policy(data["id"], data["revision"], data["originalText"], data["grantId"], data.get("confirmed"), data.get("deny"), data.get("expiresAt"))
                    elif self.path == "/api/cancel" and data.get("kind") in {"work", "policy"}:
                        result = runtime.store.cancel_record(data["kind"], data["id"], data["revision"])
                    elif self.path == "/api/record" and data.get("kind") in {"draft", "template", "todo"}:
                        result = runtime.store.put(data["kind"], data["id"], data["data"], data["revision"])
                    elif self.path == "/api/delete-draft":
                        result = runtime.store.delete_draft(data["id"], data["revision"])
                    else:
                        self.respond(404, {"error": "Unknown personal route"})
                        return
                    self.respond(200, result)
            except Exception:
                self.respond(422, {"error": "Operation rejected or unconfirmed. Reload private state and query the original receipt; delegation was not expanded."})

    def tick():
        while not stop.wait(5):
            with lock:
                try:
                    runtime.tick()
                except Exception:
                    runtime.enabled = False
                    try:
                        runtime.report_background_error()
                    except Exception:
                        print("Background processing failed and work reports could not be saved. Local processing is paused; inspect private storage and platform receipts.", flush=True)

    class IPv6Server(ThreadingHTTPServer):
        address_family = socket.AF_INET6
    server = (IPv6Server if ":" in host else ThreadingHTTPServer)((host, port), Handler)
    print(f"Personal UI: {origin}\nOne-time pairing code (5 minutes): {pairing}", flush=True)
    thread = threading.Thread(target=tick, daemon=True)
    thread.start()
    try:
        if open_browser and desktop_session_available():
            def launch_browser():
                try:
                    opened = webbrowser.open(origin, new=2)
                except Exception:
                    opened = False
                if not opened:
                    print(f"Could not open a browser. Open Personal UI manually: {origin}", flush=True)
            threading.Thread(target=launch_browser, daemon=True).start()
        elif open_browser:
            print("No desktop session detected; automatic browser opening skipped. Use the Personal UI address above.", flush=True)
        server.serve_forever()
    finally:
        stop.set()
        runtime.cancel()
        server.server_close()
        thread.join(timeout=25)
        if not thread.is_alive():
            runtime.store.close()


def main():
    parser = argparse.ArgumentParser(description="Independent personal-secretary Worker")
    parser.add_argument("--owner-id", required=True)
    parser.add_argument("--worker-id", required=True)
    parser.add_argument("--platform", required=True)
    parser.add_argument("--data-root", type=Path, default=Path.home() / ".aiw-personal")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8766)
    parser.add_argument("--public-origin")
    parser.add_argument("--allow-loopback-http", action="store_true")
    parser.add_argument("--no-browser", action="store_true", help="Do not automatically open the personal UI")
    args = parser.parse_args()
    credential = os.environ.get("AIW_PERSONAL_CREDENTIAL", "")
    command = json.loads(os.environ["AIW_PERSONAL_MODEL_COMMAND"]) if os.environ.get("AIW_PERSONAL_MODEL_COMMAND") else None
    if command is not None and (not isinstance(command, list) or not command or any(not isinstance(item, str) or not item for item in command)):
        raise ValueError("Model command must be an explicit nonempty argv JSON array")
    platform = PlatformClient(args.platform, args.worker_id, credential, args.allow_loopback_http)
    identity = platform.request("/sessions", {"workerBoot": platform.boot})
    if identity.get("ownerId") != args.owner_id or identity.get("workerId") != args.worker_id:
        raise ValueError("Explicit private owner/Worker does not match the enrolled platform identity")
    print(f"Personal process boot: {platform.boot}. Confirm this process in the platform, then enable the private UI.", flush=True)
    store = SQLiteStore(args.data_root, args.owner_id, args.worker_id)
    serve(Runtime(store, platform, command), args.host, args.port, args.public_origin, open_browser=not args.no_browser)


if __name__ == "__main__":
    main()
