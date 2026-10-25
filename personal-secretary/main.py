"""Personal UI with an independent local session. No platform session handoff."""

from __future__ import annotations

import argparse
import atexit
import getpass
import hashlib
import hmac
import ipaddress
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

from platform_client import PlatformClient, PlatformError, LocalPlatform
from private_config import read_private, write_private, PrivateProcessLock
from runtime import Runtime, PersonalChatError
from store import SQLiteStore, encode
from business_actions import BusinessActions
from desktop_startup import launch_desktop_pet


def desktop_session_available():
    if any(os.environ.get(key) for key in ("SSH_CONNECTION", "SSH_CLIENT", "SSH_TTY")):
        return False
    if sys.platform == "win32":
        return os.environ.get("SESSIONNAME", "").lower() != "services"
    if sys.platform == "darwin":
        return True
    return bool(os.environ.get("DISPLAY") or os.environ.get("WAYLAND_DISPLAY"))


def loopback_host(host):
    if host == "localhost":
        return True
    try:
        address = ipaddress.ip_address(host)
        return address.is_loopback or bool(getattr(address, "ipv4_mapped", None) and address.ipv4_mapped.is_loopback)
    except ValueError:
        return False


def serve(runtime, host, port, public_origin=None, open_browser=True, open_desktop_pet=True):
    origin = public_origin or f"http://{'[' + host + ']' if ':' in host else host}:{port}"
    parsed = urllib.parse.urlsplit(origin)
    if parsed.scheme not in {"http", "https"} or parsed.username or parsed.password or parsed.path not in {"", "/"} or parsed.query or parsed.fragment:
        raise ValueError("Explicit UI origin without path or credentials required")
    if not loopback_host(host) and (public_origin is None or parsed.scheme != "https"):
        raise ValueError("Remote UI requires an explicit HTTPS reverse-proxy origin")
    origin = origin.rstrip("/")
    cookie_name = "AIW_PERSONAL_" + hashlib.sha256((runtime.store.owner + runtime.store.worker).encode()).hexdigest()[:16]
    pairing = secrets.token_urlsafe(24)
    pairing_expiry = time.monotonic() + 300
    attach_token = secrets.token_urlsafe(32)
    local_attach = loopback_host(host) and loopback_host(parsed.hostname)
    attach_path = Path.home() / ".aiw-personal" / "desktop-attach" / (hashlib.sha256(origin.encode()).hexdigest() + ".json")
    attempts = 0
    session_path = runtime.store.path.parent / "ui-sessions.json"
    sessions = read_private(session_path, {})
    sessions = {key: value for key, value in sessions.items() if isinstance(value, dict) and value.get("expires", 0) > time.time()}
    stop = threading.Event()
    lock = threading.RLock()
    session_lock = threading.RLock()

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass  # No URL, credential, private request or reply logging.

        def respond(self, status, body, mime="application/json"):
            payload = body if isinstance(body, bytes) else body.encode() if isinstance(body, str) else encode(body).encode()
            self.send_response(status)
            self.send_header("Content-Type", mime + "; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")
            self.send_header("Content-Length", str(len(payload)))
            if getattr(self, "session_cookie", None):
                self.send_header("Set-Cookie", self.session_cookie)
                self.session_cookie = None
            self.end_headers()
            self.wfile.write(payload)

        def valid_host(self):
            authority = self.headers.get("Host", "").lower()
            if authority == parsed.netloc.lower():
                return True
            try:
                target = urllib.parse.urlsplit(f"{parsed.scheme}://{authority}")
                return (local_attach and loopback_host(target.hostname) and not target.username
                        and not target.password and not target.path and not target.query and not target.fragment
                        and (target.port or (443 if parsed.scheme == "https" else 80))
                        == (parsed.port or (443 if parsed.scheme == "https" else 80)))
            except ValueError:
                return False

        def request_origin(self):
            return f"{parsed.scheme}://{self.headers.get('Host', '')}"

        def local_request(self):
            # A public reverse proxy must not turn its loopback backend into local trust.
            return (local_attach and loopback_host(self.client_address[0])
                    and not any(name.lower().startswith("x-forwarded-") or name.lower() == "forwarded"
                                for name in self.headers))

        def new_session(self):
            with session_lock:
                return self.create_session()

        def create_session(self):
            key = secrets.token_urlsafe(32)
            value = {"csrf": secrets.token_urlsafe(32), "expires": time.time() + 12 * 3600}
            for expired in [token for token, item in sessions.items() if item["expires"] <= time.time()]:
                del sessions[expired]
            sessions[key] = value
            if len(sessions) > 64:
                del sessions[next(iter(sessions))]
            write_private(session_path, sessions)
            self.session_cookie = f"{cookie_name}={key}; Path=/; Max-Age=43200; HttpOnly; SameSite=Strict" + ("; Secure" if parsed.scheme == "https" else "")
            return value

        def session(self):
            cookie = SimpleCookie()
            try:
                cookie.load(self.headers.get("Cookie", ""))
                key = cookie.get(cookie_name)
            except Exception:
                return None
            with session_lock:
                session = sessions.get(key.value) if key else None
            if session and session["expires"] > time.time():
                return session
            return None

        def do_GET(self):
            if (not self.valid_host() or self.headers.get("Sec-Fetch-Site") == "cross-site"
                    or self.headers.get("Origin") not in {None, self.request_origin()}):
                self.respond(403, {"error": "Invalid UI host"})
                return
            files = {"/": ("index.html", "text/html"), "/app.js": ("app.js", "application/javascript"),
                     "/console.css": ("console.css", "text/css"), "/pairing.js": ("pairing.js", "application/javascript"),
                     "/pairing.css": ("pairing.css", "text/css"),
                     "/appearance.js": ("appearance.js", "application/javascript"),
                     "/appearance.css": ("appearance.css", "text/css"),
                     "/i18n.js": ("i18n.js", "application/javascript")}
            appearance = {"/appearance-secretary.png": "image/png", "/appearance-hud.png": "image/png", "/appearance-rpg.png": "image/png",
                          "/appearance-hud-wink.png": "image/png", "/appearance-rpg-wink.png": "image/png",
                          "/appearance-avatar-hud.png": "image/png", "/appearance-avatar-rpg.png": "image/png",
                          "/appearance-avatar-lady.png": "image/png"}
            if self.path in appearance:
                asset = Path(__file__).parent / "ui" / "assets" / self.path[1:]
                if not asset.is_file():
                    self.respond(404, {"error": "Secretary appearance asset is missing"})
                    return
                self.respond(200, asset.read_bytes(), appearance[self.path])
                return
            if self.path in files:
                name, mime = files[self.path]
                self.respond(200, (Path(__file__).parent / "ui" / name).read_text(encoding="utf-8"), mime)
                return
            if self.path == "/api/service/control":
                # Local session bootstrap must not queue behind a long business
                # request or read private work/chat just to stop the listener.
                if not self.local_request():
                    self.respond(403, {"error": "Direct local connection required"})
                    return
                session = self.session() or self.new_session()
                self.respond(200, {"csrf": session["csrf"], "state": "STOPPING" if stop.is_set() else "RUNNING"})
                return
            if stop.is_set():
                self.respond(503, {"error": "Personal service is stopping"})
                return
            with lock:
                session = self.session()
                if session is None and self.path == "/api/state" and self.local_request():
                    session = self.new_session()
                if session is None:
                    self.respond(401, {"error": "Personal login required"})
                elif self.path == "/api/state":
                    self.respond(200, {"csrf": session["csrf"], "ownerId": runtime.store.owner, "workerId": runtime.store.worker,
                        "platformOrigin": runtime.platform.base.removesuffix("/api/r1/authority"),
                        "serviceVersion": "daily-assistant-v1", "assistant": runtime.settings.public(),
                        "enabled": not runtime.settings.value.get("paused", False), "platformEnabled": runtime.enabled,
                        "modelConfigured": runtime.settings.configured(), "work": runtime.store.list("work"), "messages": runtime.store.chat_messages(limit=60),
                        "messageCount": sum(runtime.store.chat_counts().values()), "conversations": runtime.conversations.list(), "memories": runtime.store.list("memory"),
                        "platformConnection": runtime.platform_setup.public(),
                        "backgroundError": runtime.background_error,
                        "policies": runtime.store.list("policy"), "drafts": runtime.store.list("draft"), "templates": runtime.store.list("template"),
                        "todos": runtime.store.list("todo"), "decisions": runtime.store.list("decision"),
                        "receipts": [{key: row[key] for key in ("request_id", "state", "receipt")} for row in runtime.store.pending()]})
                elif self.path == "/api/backup":
                    self.respond(200, runtime.store.backup())
                elif urllib.parse.urlsplit(self.path).path == "/api/history":
                    query = urllib.parse.parse_qs(urllib.parse.urlsplit(self.path).query)
                    self.respond(200, runtime.store.history(query.get("kind", [""])[0], query.get("id", [""])[0]))
                elif urllib.parse.urlsplit(self.path).path == "/api/platform/diagnostics":
                    query = urllib.parse.parse_qs(urllib.parse.urlsplit(self.path).query)
                    try:
                        self.respond(200, runtime.platform_setup.diagnostics(query.get("planId", [None])[0]))
                    except PlatformError as error:
                        self.respond(502, {"code": "PLATFORM_DIAGNOSTICS_FAILED", "status": error.status})
                    except ValueError as error:
                        self.respond(422, {"code": "PLATFORM_DIAGNOSTICS_INVALID", "message": str(error)})
                else:
                    self.respond(404, {"error": "Unknown personal route"})

        def do_POST(self):
            nonlocal attempts, pairing, pairing_expiry
            if not self.valid_host() or self.headers.get("Origin") != self.request_origin():
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
                if self.path == "/api/service/shutdown":
                    # Native local service control: never available through a
                    # reverse proxy, an unauthenticated request, or a model tool.
                    session = self.session()
                    if (not self.local_request() or session is None
                            or not hmac.compare_digest(self.headers.get("X-CSRF-Token", ""), session["csrf"])):
                        self.respond(403, {"error": "Local personal session and CSRF token required"})
                        return
                    stop.set()
                    try:
                        self.respond(200, {"state": "STOPPING"})
                    finally:
                        # A lost response must not leave the service half-stopped.
                        # Stop the accept loop even if model cancellation fails.
                        # It must run outside the serve_forever thread.
                        threading.Thread(target=server.shutdown, daemon=True).start()
                        runtime.cancel()
                    return
                if stop.is_set():
                    self.respond(503, {"error": "Personal service is stopping"})
                    return
                with lock:
                    if stop.is_set():
                        self.respond(503, {"error": "Personal service is stopping"})
                        return
                    if self.path in {"/api/login", "/api/attach"}:
                        attaching = self.path == "/api/attach"
                        if not attaching: attempts += 1
                        valid = self.local_request() and hmac.compare_digest(str(data.get("token", "")), attach_token) if attaching else bool(pairing) and attempts <= 5 and time.monotonic() <= pairing_expiry and hmac.compare_digest(str(data.get("code", "")), pairing)
                        if not valid:
                            self.respond(401, {"error": "Pairing code invalid or expired; restart to request a new code"})
                            return
                        if not attaching: pairing = ""
                        self.respond(200, {"csrf": self.new_session()["csrf"]})
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
                    elif self.path == "/api/settings":
                        try:
                            result = runtime.settings.save(data)
                        except ValueError as error:
                            raise PersonalChatError("SETTINGS_INVALID", str(error)) from None
                    elif self.path == "/api/settings/check":
                        try:
                            runtime.model("Reply with OK only. Do not use tools or perform any action.")
                        except ValueError as error:
                            raise PersonalChatError("MODEL_CONNECTION_FAILED", str(error)) from None
                        result = {"note": "模型连接正常，可以直接聊天或安排工作。"}
                    elif self.path == "/api/assistant/control":
                        try:
                            result = runtime.settings.pause(data.get("paused"))
                        except ValueError as error:
                            raise PersonalChatError("SETTINGS_INVALID", str(error)) from None
                    elif self.path == "/api/assistant/confirm":
                        try:
                            result = runtime.daily.confirm(data["id"], data["revision"], data["accepted"])
                        except ValueError as error:
                            raise PersonalChatError("CONFIRMATION_INVALID", str(error)) from None
                    elif self.path == "/api/work":
                        result = runtime.create_work(data["title"], data["body"], data.get("planId") or None, data.get("outputPath") or None)
                    elif self.path in {"/api/platform/connect", "/api/platform/check", "/api/authorization/decide"}:
                        try:
                            if self.path.endswith("/connect"):
                                result = runtime.platform_setup.connect(data.get("code"))
                            elif self.path.endswith("/check"):
                                result = runtime.platform_setup.check()
                            else:
                                result = runtime.authorizations.decide(data.get("id"), data.get("revision"), data.get("accepted"))
                        except ValueError as error:
                            raise PersonalChatError("PLATFORM_SETUP_REQUIRED", str(error)) from None
                    elif self.path == "/api/chat":
                        work_id = data.get("workId")
                        if not work_id:
                            if not isinstance(data.get("text"), str) or not data["text"].strip() or len(data["text"]) > 4000:
                                raise PersonalChatError("CHAT_INVALID_INPUT", "请输入 1–4000 字的消息。")
                            if not runtime.settings.configured() or runtime.settings.value.get("paused", False):
                                raise PersonalChatError("CHAT_NOT_READY", "请先连接模型并继续工作，再开始对话。")
                            work = runtime.conversations.start(data["text"])
                            work_id = work["id"]
                        result = runtime.chat(work_id, data["text"])
                    elif self.path in {"/api/conversation/page", "/api/conversation/summary", "/api/conversation/delete", "/api/memory/delete"}:
                        try:
                            if self.path.endswith("/page"):
                                result = runtime.conversations.page(data.get("id"), data.get("before"))
                            elif self.path.endswith("/summary"):
                                result = runtime.conversations.request_summary(data["id"], data["revision"])
                            elif self.path == "/api/memory/delete":
                                result = runtime.conversations.delete_memory(data["id"], data["revision"])
                            else:
                                result = runtime.conversations.delete(data["id"], data["revision"], data.get("deleteMemories"))
                        except ValueError as error:
                            raise PersonalChatError("CONVERSATION_INVALID", str(error)) from None
                    elif self.path == "/api/run-work":
                        work = runtime.store.get("work", data["id"])
                        if not work or work["revision"] != data["revision"]:
                            raise PersonalChatError("WORK_CHANGED", "这项工作已变化，请刷新后重新选择。")
                        result = runtime.daily.schedule(work, int(time.time() * 1000), time.tzname[0], execution_mode="IMMEDIATE")
                    elif self.path == "/api/professional/recheck":
                        try:
                            result = runtime.professional.recheck(data.get("id"), data.get("revision"))
                        except ValueError as error:
                            raise PersonalChatError("PROFESSIONAL_RECHECK_FAILED", str(error)) from None
                    elif self.path == "/api/work-trash":
                        work = runtime.store.get("work", data["id"])
                        if not work or work["revision"] != data["revision"]:
                            raise PersonalChatError("WORK_CHANGED", "这项工作已变化，请刷新后再移到已删除列表。")
                        value = {key: value for key, value in work.items() if key not in {"id", "revision"}}
                        value["state"] = "TRASHED"
                        result = runtime.store.put("work", work["id"], value, work["revision"])
                        runtime.daily.dismiss(work["id"])
                    elif self.path == "/api/work-restore":
                        work = runtime.store.get("work", data["id"])
                        if not work or work["state"] != "TRASHED" or work["revision"] != data["revision"]:
                            raise PersonalChatError("WORK_CHANGED", "这项工作已恢复或发生变化，请刷新后查看。")
                        result = runtime.report_work(work, "DRAFT", "RESTORED", "已恢复工作，可直接处理或重新安排。")
                    elif self.path == "/api/work-delete":
                        try:
                            result = runtime.conversations.delete_work(data.get("id"), data.get("revision"), data.get("deleteMemories"))
                        except ValueError as error:
                            raise PersonalChatError("WORK_DELETE_NOT_READY", str(error)) from None
                    elif self.path == "/api/enable":
                        if type(data.get("enabled")) is not bool:
                            raise ValueError()
                        result = runtime.enable(data["enabled"])
                    elif self.path == "/api/schedule":
                        result = runtime.schedule(data["workId"], data["dueMs"], data["revision"], data.get("timeZone", "UTC"))
                    elif self.path == "/api/business-permissions":
                        result = BusinessActions(runtime).permissions()
                    elif self.path == "/api/business-questions":
                        result = BusinessActions(runtime).questions(data["permissionId"], data["planId"])
                    elif self.path == "/api/business-action":
                        result = BusinessActions(runtime).queue(data)
                    elif self.path == "/api/business-action-status":
                        result = BusinessActions(runtime).status(data["requestId"])
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
                        if data["kind"] == "work": runtime.daily.dismiss(data["id"])
                    elif self.path == "/api/record" and data.get("kind") in {"draft", "template", "todo"}:
                        result = runtime.store.put(data["kind"], data["id"], data["data"], data["revision"])
                    elif self.path == "/api/delete-draft":
                        result = runtime.store.delete_draft(data["id"], data["revision"])
                    else:
                        self.respond(404, {"error": "Unknown personal route"})
                        return
                    self.respond(200, result)
            except PersonalChatError as error:
                self.respond(422, {"code": error.code, "message": str(error)})
            except PlatformError as error:
                message = "专业平台尚未连接。日常聊天和本机工作仍可使用；如需专业平台功能，请在启动时使用平台提供的连接信息。" if error.status == "PLATFORM_NOT_CONNECTED" else "专业平台没有接受这次请求。请在高级功能中检查平台连接和权限；日常助理不需要这些配置。"
                self.respond(422, {"code": "PLATFORM_REQUEST_FAILED", "message": message})
            except Exception:
                messages = {"/api/schedule": "安排未保存：请选择一项工作和未来时间。若工作刚被修改，请刷新后重新安排。",
                    "/api/record": "资料未保存：内容需要是有效的 JSON 对象；若版本已变化，请重新打开资料后保存。",
                    "/api/delete-draft": "草稿未删除：可能已被修改或删除，请刷新资料列表再操作。",
                    "/api/chat": "对话未开始：请打开“设置”检查模型连接，再重新发送。若仍失败，请重启个人服务以加载新版本。"}
                self.respond(422, {"code": "OPERATION_NOT_COMPLETED", "message": messages.get(self.path, "这次操作未完成。请刷新当前列表后重试；需要继续时，界面会列出具体待确认内容。")})

    def tick():
        while not stop.wait(5):
            with lock:
                if stop.is_set(): break
                try:
                    runtime.tick()
                    if runtime.enabled: runtime.background_error = None
                except Exception as error:
                    runtime.enabled = False
                    runtime.background_error = {"code": "BACKGROUND_PROCESSING_FAILED", "type": type(error).__name__,
                                                "message": "后台处理发生异常，已停止本地平台操作；这不表示平台撤销了进程授权。"}
                    try:
                        runtime.report_background_error()
                    except Exception:
                        print("Background processing failed and work reports could not be saved. Local processing is paused; inspect private storage and platform receipts.", flush=True)

    class IPv6Server(ThreadingHTTPServer):
        address_family = socket.AF_INET6
    server = (IPv6Server if ":" in host else ThreadingHTTPServer)((host, port), Handler)
    if local_attach:
        write_private(attach_path, {"origin": origin, "token": attach_token})
    print(f"Personal UI: {origin}", flush=True)
    if local_attach:
        print("Local loopback access connects automatically; no pairing code is required.", flush=True)
    else:
        print(f"One-time pairing code (5 minutes): {pairing}", flush=True)
    thread = threading.Thread(target=tick, daemon=True)
    thread.start()
    try:
        if open_desktop_pet and local_attach and desktop_session_available():
            try:
                if not launch_desktop_pet(origin):
                    print("Desktop pet not started: no supported interactive desktop or GUI dependency. The service remains available.", flush=True)
            except OSError:
                print("Could not launch the desktop pet. The service and browser UI remain available.", flush=True)
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
        # Release the HTTP listener before waiting for background cleanup.
        # Cleanup errors must never leave the listening socket open.
        server.server_close()
        try:
            runtime.cancel()
            thread.join(timeout=25)
            if not thread.is_alive() and lock.acquire(timeout=1):
                try:
                    if not runtime.daily.busy:
                        runtime.store.close()
                finally:
                    lock.release()
        finally:
            if local_attach:
                attach_path.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description="Independent personal-secretary Worker")
    parser.add_argument("--owner-id", help="Optional professional platform owner")
    parser.add_argument("--worker-id", help="Optional professional platform Worker")
    parser.add_argument("--platform", help="Optional professional platform origin")
    parser.add_argument("--data-root", type=Path, default=Path.home() / ".aiw-personal")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8766)
    parser.add_argument("--public-origin")
    parser.add_argument("--allow-loopback-http", action="store_true")
    parser.add_argument("--no-browser", action="store_true", help="Do not automatically open the personal UI")
    parser.add_argument("--no-desktop-pet", action="store_true", help="Do not automatically launch the desktop companion")
    args = parser.parse_args()
    profile_path = args.data_root.expanduser() / "connection.json"
    try:
        profile = read_private(profile_path, {})
    except (OSError, ValueError):
        raise SystemExit("无法读取已保存的连接设置。请检查个人数据目录的访问权限，不要删除私人数据库。") from None
    local_identity = hashlib.sha256((getpass.getuser() + "\n" + socket.gethostname()).encode()).hexdigest()[:16]
    owner = args.owner_id or profile.get("ownerId") or "local-" + local_identity
    worker = args.worker_id or profile.get("workerId") or "personal-secretary"
    platform_url = args.platform or profile.get("platform")
    credential = os.environ.get("AIW_PERSONAL_CREDENTIAL") or profile.get("credential", "")
    command = json.loads(os.environ["AIW_PERSONAL_MODEL_COMMAND"]) if os.environ.get("AIW_PERSONAL_MODEL_COMMAND") else None
    if command is not None and (not isinstance(command, list) or not command or any(not isinstance(item, str) or not item for item in command)):
        raise ValueError("Model command must be an explicit nonempty argv JSON array")
    store = SQLiteStore(args.data_root, owner, worker)
    instance = PrivateProcessLock(store.path.parent / "service.lock")
    try:
        instance.acquire()
    except OSError:
        store.close()
        raise SystemExit("这份秘书数据已有服务在运行，或私库目录不能锁定。请连接已有服务；如需重启，请先关闭原服务。") from None
    atexit.register(instance.release)
    platform = LocalPlatform()
    if platform_url and credential:
        allow_http = args.allow_loopback_http or profile.get("allowLoopbackHttp", False) and platform_url == profile.get("platform")
        try:
            connected = PlatformClient(platform_url, worker, credential, allow_http)
            platform = connected
            if args.platform or args.owner_id or args.worker_id:
                identity = connected.request("/sessions", {"workerBoot": connected.boot})
                if identity.get("ownerId") != owner or identity.get("workerId") != worker:
                    raise SystemExit("平台返回的身份与所选个人数据身份不一致，已停止启动。请使用平台提供的正确连接信息。")
                connected.session_started = True
                write_private(profile_path, {"ownerId": owner, "workerId": worker, "platform": platform_url,
                    "credential": credential, "allowLoopbackHttp": bool(allow_http)})
        except PlatformError as error:
            platform = LocalPlatform()
            print(f"专业平台暂未连接（{error.status}）。本机日常助理仍可使用；专业功能请检查平台身份和凭证。", flush=True)
        except ValueError:
            platform = LocalPlatform()
            print("专业平台连接设置不完整或地址不符合要求。本机日常助理仍可使用；专业连接可稍后重新配置。", flush=True)
    print("秘书已启动。首次使用请在“设置”连接模型；以后会恢复设置，无需再次启用日常工作。", flush=True)
    try:
        runtime = Runtime(store, platform, command)
    except (OSError, ValueError):
        store.close()
        raise SystemExit("无法读取个人设置或恢复私人状态。请检查个人数据目录的访问权限；不要删除私人数据库。") from None
    if not profile.get("ownerId") and not profile.get("workerId"):
        write_private(profile_path, {"ownerId": owner, "workerId": worker})
    try:
        serve(runtime, args.host, args.port, args.public_origin, open_browser=not args.no_browser, open_desktop_pet=not args.no_desktop_pet)
    finally:
        instance.release()


if __name__ == "__main__":
    main()
