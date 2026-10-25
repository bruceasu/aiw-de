"""In-memory desktop session for the existing personal service, never the platform."""

from __future__ import annotations

import http.cookiejar
import ipaddress
import json
import hashlib
from pathlib import Path
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from private_config import read_private


class PersonalError(Exception):
    def __init__(self, message, status=0):
        super().__init__(message)
        self.status = status


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *_args, **_kwargs):
        raise PersonalError("个人服务发生了重定向，连接已停止。请核对服务地址。")


def personal_origin(value):
    try:
        parsed = urllib.parse.urlsplit(value.strip())
        host = parsed.hostname
        local = host == "localhost" or bool(host and ipaddress.ip_address(host).is_loopback)
        if (not local or parsed.scheme not in {"http", "https"} or parsed.username is not None
                or parsed.password is not None or parsed.path not in {"", "/"} or parsed.query or parsed.fragment
                or not parsed.port or any(char.isspace() for char in value)):
            raise ValueError()
    except ValueError:
        raise PersonalError("请输入本机个人服务地址，例如 http://127.0.0.1:8766；地址和端口须与启动终端一致。") from None
    return urllib.parse.urlunsplit((parsed.scheme, parsed.netloc, "", "", ""))


class PersonalClient:
    def __init__(self, origin):
        self.origin = personal_origin(origin)
        self.csrf = ""
        self.opener = urllib.request.build_opener(
            urllib.request.ProxyHandler({}), NoRedirect(),
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def request(self, route, data=None, timeout=135):
        headers = {"Accept": "application/json", "Origin": self.origin}
        payload = None
        if data is not None:
            payload = json.dumps(data, ensure_ascii=False, allow_nan=False).encode("utf-8")
            if len(payload) > 32768:
                raise PersonalError("本次内容过长，请缩短后再提交。")
            headers.update({"Content-Type": "application/json", "X-CSRF-Token": self.csrf})
        request = urllib.request.Request(self.origin + "/api/" + route, data=payload, headers=headers)
        try:
            with self.opener.open(request, timeout=timeout) as response:
                raw = response.read(8 * 1024 * 1024 + 1)
                if len(raw) > 8 * 1024 * 1024:
                    raise PersonalError("私人状态超过桌面显示上限，请在 Web 中查看并备份。")
                return json.loads(raw)
        except urllib.error.HTTPError as error:
            details = {}
            try:
                if error.code == 422:
                    raw = error.read(8193)
                    if len(raw) <= 8192:
                        try:
                            details = json.loads(raw)
                        except (ValueError, UnicodeError):
                            pass
            finally:
                error.close()
            if error.code == 401:
                message = "配对码无效、已使用或已过期，或当前会话已失效。请在 Web 的“状态与备份”中生成新的配对码。"
            elif error.code == 403:
                message = "会话或服务地址校验失败。请先刷新状态，并核对地址与启动终端一致。"
            elif error.code == 422:
                if isinstance(details, dict) and details.get("code") and isinstance(details.get("message"), str):
                    message = details["message"][:500]
                elif route == "chat":
                    message = "对话失败。请检查个人服务是否已配置 AIW_PERSONAL_MODEL_COMMAND，以及模型程序能否正常运行；更新并重启服务后可显示具体原因。"
                else:
                    message = "这次操作没有完成，服务可能仍是旧版本。请重启个人服务并刷新界面；新版本会说明具体原因。"
            else:
                message = f"个人服务返回 {error.code}。请核对原回执，避免重复提交。"
            raise PersonalError(message, error.code) from None
        except (urllib.error.URLError, TimeoutError, OSError):
            raise PersonalError("无法连接个人服务或等待超时。请检查服务是否运行；已提交操作先查原回执，勿直接重发。") from None
        except (ValueError, UnicodeError):
            raise PersonalError("个人服务返回内容无法读取。请刷新状态并核对原请求回执。") from None

    def state(self):
        state = self.request("state")
        if not isinstance(state, dict) or not isinstance(state.get("csrf"), str):
            raise PersonalError("个人服务状态格式不符。请确认连接的是秘书服务。")
        self.csrf = state.pop("csrf")
        return state

    def login(self, code):
        # Keep the accepted session even if the subsequent state read fails.
        return self.operate("login", {"code": code.strip()})

    def auto_connect(self, cancelled=None):
        def check_cancelled():
            if cancelled is not None and cancelled.is_set():
                raise PersonalError("桌面正在退出，已取消启动本地服务。")
        check_cancelled()
        def attach():
            path = Path.home() / ".aiw-personal" / "desktop-attach" / (hashlib.sha256(self.origin.encode()).hexdigest() + ".json")
            try:
                value = read_private(path, {})
            except (OSError, ValueError):
                raise PersonalError("无法读取本机连接信息。请检查当前系统账号，或在连接窗口输入配对码。", 401) from None
            if value.get("origin") != self.origin or not value.get("token"):
                raise PersonalError("当前服务尚未提供本机自动连接。请更新并重启个人服务，或填写一次性配对码。", 401)
            try:
                self.request("attach", {"token": value["token"]})
            except PersonalError as error:
                if error.status == 404:
                    raise PersonalError("正在运行的服务是旧版本，请重启个人服务后再连接。", 404) from None
                raise
            return {}, self.state(), ""
        try:
            state = self.state()
        except PersonalError as error:
            if error.status:
                return attach()
        else:
            return {}, state, ""
        parsed = urllib.parse.urlsplit(self.origin)
        if parsed.scheme != "http":
            raise PersonalError("个人服务尚未运行。HTTPS 服务请先在已配置的反向代理后启动。")
        try:
            check_cancelled()
            subprocess.Popen([sys.executable, str(Path(__file__).with_name("main.py")), "--host", parsed.hostname,
                "--port", str(parsed.port), "--no-browser", "--no-desktop-pet"], stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        except OSError:
            raise PersonalError("无法启动个人服务。请在秘书目录运行 start.ps1 后再连接。") from None
        for _ in range(50):
            check_cancelled()
            time.sleep(0.2)
            try:
                state = self.state()
            except PersonalError as error:
                if error.status:
                    try: return attach()
                    except PersonalError as attach_error:
                        if attach_error.status not in {0, 401}: raise
            else:
                return {}, state, ""
        raise PersonalError("个人服务没有完成启动。请运行 start.ps1 查看终端中的具体原因，再重新连接。")

    def shutdown_service(self):
        """Request authenticated graceful shutdown; never kill a process."""
        import errno
        import socket
        # Refresh only local control credentials, without loading private state,
        # waiting for the business lock, or ever auto-starting a service.
        try:
            control = self.request("service/control", timeout=5)
        except PersonalError as error:
            if not error.status:
                target = urllib.parse.urlsplit(self.origin)
                try:
                    with socket.create_connection((target.hostname, target.port), timeout=0.3):
                        pass
                except ConnectionRefusedError:
                    self.csrf = ""
                    return  # Already stopped; never auto-start to stop it.
                except OSError:
                    pass
            if error.status in {401, 404}:
                raise PersonalError("运行中的旧服务不支持正常退出。请手动关闭并更新服务后再使用此入口。") from None
            raise
        if not isinstance(control, dict) or not isinstance(control.get("csrf"), str) or not control["csrf"]:
            raise PersonalError("服务没有确认停止请求，请核对状态。")
        self.csrf = control["csrf"]
        try:
            result = self.request("service/shutdown", {}, timeout=5)
        except PersonalError as error:
            if error.status in {401, 404}:
                raise PersonalError("运行中的旧服务不支持正常退出。请手动关闭并更新服务后再使用此入口。") from None
            raise
        if not isinstance(result, dict) or result.get("state") != "STOPPING":
            raise PersonalError("服务没有确认停止请求，请核对状态。")
        parsed = urllib.parse.urlsplit(self.origin)
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            try:
                with socket.create_connection((parsed.hostname, parsed.port), timeout=0.3):
                    pass
            except ConnectionRefusedError:
                self.csrf = ""
                return
            except OSError as error:
                if error.errno in {errno.ECONNREFUSED, 10061}:
                    self.csrf = ""
                    return
                raise PersonalError("停止请求已接受，但无法确认本地服务是否关闭。") from None
            time.sleep(0.2)
        raise PersonalError("停止请求已接受，服务仍在清理。请稍后查看状态，不要强制结束正在保存的操作。")

    def operate(self, route, data):
        result = self.request(route, data)
        try:
            return result, self.state(), ""
        except PersonalError:
            return result, None, "服务已返回结果，但状态刷新失败。请先刷新状态并核对原回执，避免重复提交。"
