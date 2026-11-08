"""Secretary-local protected model connections; one refresh owner per token set."""
from __future__ import annotations

from contextlib import contextmanager
import os
from pathlib import Path
import threading
import time
import urllib.parse
import uuid
import json

from private_config import PrivateProcessLock, read_private, write_private


class ModelAuthRequired(ValueError):
    """Authentication cannot be repaired by retrying a task or its tools."""


def endpoint(value):
    value = (value or "https://api.openai.com/v1").strip().rstrip("/")
    parsed = urllib.parse.urlsplit(value)
    if (not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment
            or parsed.scheme != "https" and not (parsed.scheme == "http" and parsed.hostname in {"127.0.0.1", "localhost", "::1"})):
        raise ValueError("模型地址必须使用 HTTPS；本机回环服务可使用 HTTP。")
    return value


class ModelConnections:
    def __init__(self, directory):
        self.directory = Path(directory)
        self.path = self.directory / "model-connections.json"
        self.lock = threading.RLock()
        self.login = None

    @contextmanager
    def locked(self):
        with self.lock:
            self.directory.mkdir(parents=True, exist_ok=True, mode=0o700)
            process = PrivateProcessLock(self.directory / "model-connections.lock")
            try:
                process.acquire()
            except OSError:
                raise ValueError("模型连接正在由另一进程更新，请稍后重试。") from None
            try:
                value = read_private(self.path, None) or {"format": "secretary-model-connections-v1", "hostId": str(uuid.uuid4()), "connections": []}
                if value.get("format") != "secretary-model-connections-v1" or not isinstance(value.get("connections"), list):
                    raise ValueError("模型连接存储格式不受支持。")
                if any(not isinstance(record, dict) or not isinstance(record.get("id"), str) or type(record.get("version")) is not int
                       or record.get("kind") not in {"api-key", "chatgpt"} for record in value["connections"]):
                    raise ValueError("模型连接记录格式不受支持，未覆盖原存储。")
                yield value
            finally:
                process.release()

    def persist(self, value):
        if len(json.dumps(value, allow_nan=False).encode()) > 512000:
            raise ValueError("模型连接存储达到上限，原文件未覆盖。请先停用不再使用的连接，再重新登录。")
        write_private(self.path, value)

    def public(self):
        try:
            with self.locked() as value:
                safe = [{key: r.get(key) for key in ("id", "version", "kind", "label", "status", "account", "expiresAt", "baseUrl")}
                        for r in value["connections"]]
            return {"connections": safe, "persistentOAuth": os.name == "nt", "login": self.login.public() if self.login else None}
        except (OSError, ValueError):
            return {"connections": [], "persistentOAuth": False, "error": "本机模型连接无法读取，请检查当前系统账户和原存储；未覆盖旧凭据。"}

    @staticmethod
    def find(value, identity):
        result = next((r for r in value["connections"] if r.get("id") == identity), None)
        if result is None:
            raise ValueError("找不到所选本机模型连接。")
        return result

    def create_api_key(self, key, base_url, label="API Key"):
        if not isinstance(key, str) or not key.strip() or len(key) > 4096 or any(c in key for c in "\x00\r\n"):
            raise ValueError("请提供有效的 API Key。")
        with self.locked() as value:
            record = {"id": "mc-" + str(uuid.uuid4()), "version": 1, "generation": 1, "kind": "api-key", "label": label[:100],
                      "status": "READY", "baseUrl": endpoint(base_url), "key": key.strip()}
            value["connections"].append(record)
            self.persist(value)
        return {"id": record["id"], "version": record["version"]}

    def binding(self, identity, kind):
        with self.locked() as value:
            record = self.find(value, identity)
            if record["kind"] != kind or record["status"] != "READY":
                raise ValueError("所选连接的认证方式不匹配或需要重新登录。")
            return {"connectionId": identity, "connectionVersion": record["version"], "endpoint": record.get("baseUrl", "https://api.openai.com/v1")}

    def access(self, identity, version, kind):
        with self.locked() as value:
            record = self.find(value, identity)
            if record["version"] != version or record["kind"] != kind or record["status"] != "READY":
                raise ModelAuthRequired("模型连接已变化或登录失效；请修复登录后明确重新安排，原运行不会改用其它账户。")
            if kind == "api-key":
                return {"token": record["key"], "baseUrl": record["baseUrl"], "account": identity}
            if record["expiresAt"] <= time.time() + 60:
                if record.get("earliestRefreshAt", 0) > time.time():
                    raise ValueError("暂未到服务允许的刷新时间；连接保持原状态，请稍后再试。")
                from secretary_oauth import refresh
                record["status"] = "REFRESHING"
                self.persist(value)
                try:
                    replacement = refresh(record)
                    if replacement["account"] != record["account"] or replacement["clientId"] != record["clientId"]:
                        raise ValueError("刷新后的账户或客户端与原连接不一致。")
                    record.update(replacement, status="READY", generation=record["generation"] + 1)
                    self.persist(value)
                except Exception:
                    record["status"] = "REAUTH_REQUIRED"
                    # A failed persistence leaves REFRESHING on disk; either
                    # state refuses a second use of the rotating refresh token.
                    self.persist(value)
                    raise ModelAuthRequired("令牌刷新未确认，已停止使用旧刷新令牌。请重新登录。") from None
            return {"token": record["accessToken"], "baseUrl": "https://api.openai.com/v1", "account": record["account"]}

    def disconnect(self, identity, version):
        if type(version) is not int:
            raise ValueError("请选择连接的实际版本。")
        with self.locked() as value:
            record = self.find(value, identity)
            if record["version"] != version:
                raise ValueError("连接已变化，请刷新后核对。")
            record.update(status="DISCONNECTED", version=version + 1)
            for key in ("key", "accessToken", "refreshToken", "idToken"):
                record.pop(key, None)
            self.persist(value)
        return {"note": "本机连接已停用；没有声称远端授权已撤销。", "remoteRevocationConfirmed": False}

    def begin_login(self):
        if os.name != "nt":
            raise ValueError("当前系统尚未配置受保护的 OAuth 存储，请使用 API Key 或本机 Codex。")
        from secretary_oauth import BrowserLogin
        with self.lock:
            if self.login and self.login.status in {"WAITING", "EXCHANGING"}:
                raise ValueError("已有登录等待完成，请先取消原登录。")
            with self.locked() as value:
                # Persist the local host registration before opening a browser.
                self.persist(value)
                host, client = value["hostId"], value.get("clientId", "dynamic_agent_client")
            self.login = BrowserLogin(host, self.accept_login, client)
            try:
                return self.login.start()
            except Exception:
                self.login.status, self.login.message = "FAILED", "登录尚未启动，请检查本机可选依赖和系统配置。"
                raise

    def accept_login(self, issued):
        with self.locked() as value:
            record = {"id": "mc-" + str(uuid.uuid4()), "version": 1, "generation": 1,
                      "kind": "chatgpt", "label": "ChatGPT", "status": "READY", **issued}
            value["connections"].append(record)
            value["clientId"] = issued["clientId"]
            self.persist(value)

    def cancel_login(self):
        login = self.login
        if login:
            login.cancel()
        return {"note": "原登录已取消，请重新发起登录。"}

    def import_metadata(self, data):
        source = data.get("registration")
        if not isinstance(source, dict) or set(source) - {"issuer", "client_id", "label"}:
            raise ValueError("仅接受连接注册元数据；不导入或复制其它应用的令牌。")
        if source.get("issuer") != "https://auth.openai.com" or not isinstance(source.get("client_id"), str) or not 1 <= len(source["client_id"]) <= 256:
            raise ValueError("连接来源或客户端标识无效。")
        return {"note": "注册信息格式已检查；未验证或复用来源客户端，未复制任何令牌。请在此秘书主机独立登录，以取得自己的会话和刷新令牌。", "requiresLogin": True}
