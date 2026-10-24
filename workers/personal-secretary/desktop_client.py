"""In-memory desktop session for the existing personal service, never the platform."""

from __future__ import annotations

import http.cookiejar
import ipaddress
import json
import urllib.error
import urllib.parse
import urllib.request


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

    def request(self, route, data=None):
        headers = {"Accept": "application/json", "Origin": self.origin}
        payload = None
        if data is not None:
            payload = json.dumps(data, ensure_ascii=False, allow_nan=False).encode("utf-8")
            if len(payload) > 32768:
                raise PersonalError("本次内容过长，请缩短后再提交。")
            headers.update({"Content-Type": "application/json", "X-CSRF-Token": self.csrf})
        request = urllib.request.Request(self.origin + "/api/" + route, data=payload, headers=headers)
        try:
            with self.opener.open(request, timeout=135) as response:
                raw = response.read(8 * 1024 * 1024 + 1)
                if len(raw) > 8 * 1024 * 1024:
                    raise PersonalError("私人状态超过桌面显示上限，请在 Web 中查看并备份。")
                return json.loads(raw)
        except urllib.error.HTTPError as error:
            error.close()
            if error.code == 401:
                message = "配对码无效、已使用或已过期，或当前会话已失效。请在 Web 的“状态与备份”中生成新的配对码。"
            elif error.code == 403:
                message = "会话或服务地址校验失败。请先刷新状态，并核对地址与启动终端一致。"
            elif error.code == 422:
                message = "操作未获确认。请刷新状态，检查当前版本及授权，并核对原请求回执。"
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

    def operate(self, route, data):
        result = self.request(route, data)
        try:
            return result, self.state(), ""
        except PersonalError:
            return result, None, "服务已返回结果，但状态刷新失败。请先刷新状态并核对原回执，避免重复提交。"
