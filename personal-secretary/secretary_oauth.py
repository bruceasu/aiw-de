"""Fixed-origin local OAuth with PKCE, OIDC verification and bounded HTTP."""
from __future__ import annotations

import base64
import hashlib
import hmac
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import secrets
import threading
import time
from datetime import datetime
import urllib.error
import urllib.parse
import urllib.request


ISSUER = "https://auth.openai.com"
RESOURCE = "https://api.openai.com/v1"
SCOPES = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *_args, **_kwargs):
        return None


def request_json(url, form=None):
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != "https" or parsed.hostname != "auth.openai.com" or parsed.port is not None or parsed.username or parsed.password:
        raise ValueError("OAuth 端点来源无效。")
    data = urllib.parse.urlencode(form).encode() if form is not None else None
    request = urllib.request.Request(url, data=data, headers={"Content-Type": "application/x-www-form-urlencoded", "Accept": "application/json"})
    try:
        with urllib.request.build_opener(NoRedirect()).open(request, timeout=12) as response:
            chunks, size, deadline = [], 0, time.monotonic() + 12
            while True:
                if time.monotonic() >= deadline:
                    raise ValueError("OAuth 响应超过期限。")
                chunk = response.read1(8192)
                if not chunk:
                    break
                size += len(chunk)
                if size > 131072:
                    raise ValueError("OAuth 响应超过上限。")
                chunks.append(chunk)
            raw = b"".join(chunks)
            if response.status != 200:
                raise ValueError("OAuth 响应不完整或超过上限。")
        result = json.loads(raw)
        if not isinstance(result, dict):
            raise ValueError()
        return result
    except urllib.error.HTTPError as error:
        status = error.code
        error.close()
        raise ValueError("登录服务拒绝请求（HTTP " + str(status) + "）。请检查组织许可、账户或重新登录。") from None
    except (OSError, ValueError):
        raise ValueError("OAuth 响应未确认，请重新登录；未自动重试。") from None


def verify(token, audience, keys):
    try:
        import jwt
    except ImportError:
        raise ValueError("ChatGPT 登录需要可选依赖 PyJWT[crypto]；尚未执行令牌交换。") from None
    try:
        if not isinstance(token, str) or not 1 <= len(token) <= 32768:
            raise ValueError()
        header = jwt.get_unverified_header(token)
        if header.get("alg") != "RS256" or not header.get("kid") or header.get("crit"):
            raise ValueError()
        key = next(k for k in keys if k.get("kid") == header["kid"] and k.get("kty") == "RSA"
                   and k.get("use") in {None, "sig"} and k.get("alg") in {None, "RS256"}
                   and ("key_ops" not in k or "verify" in k["key_ops"]))
        signing_key = jwt.PyJWK.from_dict(key).key
        if signing_key.key_size < 2048:
            raise ValueError()
        return jwt.decode(token, signing_key, algorithms=["RS256"], audience=audience, issuer=ISSUER,
                          options={"require": ["iss", "aud", "sub", "exp", "iat"]}, leeway=30)
    except Exception:
        raise ValueError("令牌签名、来源、账户或有效期校验失败。") from None


def validate(tokens, client=None, nonce=None, subject=None):
    actual_client = tokens.get("client_id") or client
    if client not in {None, "dynamic_agent_client"} and actual_client != client:
        raise ValueError("签发的客户端与本次登录不一致。")
    client = actual_client
    if not isinstance(client, str) or not 1 <= len(client) <= 256 or client == "dynamic_agent_client":
        raise ValueError("没有取得实际签发的 OAuth 客户端标识。")
    if str(tokens.get("token_type", "")).lower() != "bearer":
        raise ValueError("登录服务没有签发受支持的令牌类型。")
    discovery = request_json(ISSUER + "/.well-known/openid-configuration")
    if discovery.get("issuer") != ISSUER:
        raise ValueError("OIDC 来源不匹配。")
    keys = request_json(discovery.get("jwks_uri", "")).get("keys", [])
    if not isinstance(keys, list) or len(keys) > 32:
        raise ValueError("验签公钥响应无效。")
    identity = verify(tokens.get("id_token"), client, keys)
    access = verify(tokens.get("access_token"), RESOURCE, keys)
    if (not identity.get("sub") or identity["sub"] != access.get("sub") or subject is not None and subject != identity["sub"]
            or nonce is not None and not hmac.compare_digest(str(identity.get("nonce", "")), nonce)):
        raise ValueError("登录返回的身份或 nonce 不匹配。")
    issued_client = access.get("client_id") or access.get("azp")
    if issued_client != client:
        raise ValueError("访问令牌的客户端与本次授权不一致。")
    if any(claims.get("azp") is not None and claims["azp"] != client for claims in (identity, access)):
        raise ValueError("令牌授权方与本次客户端不一致。")
    if isinstance(identity.get("aud"), list) and len(identity["aud"]) > 1 and identity.get("azp") != client:
        raise ValueError("多受众身份令牌缺少正确的授权方。")
    scopes = tokens.get("scope", "")
    if not isinstance(scopes, str) or not {"resource.invoke", "chatgpt.tokens.use.direct"}.issubset(scopes.split()):
        raise ValueError("已登录，但未授予使用 ChatGPT 套餐调用模型的权限；请检查组织外部访问设置。")
    signed_scopes = access.get("scope", "")
    if not isinstance(signed_scopes, str) or not set(scopes.split()).issubset(signed_scopes.split()):
        raise ValueError("访问令牌中的授权范围与返回值不一致。")
    refresh_token = tokens.get("refresh_token")
    if not isinstance(refresh_token, str) or not 1 <= len(refresh_token) <= 32768 or access["exp"] <= time.time() + 30:
        raise ValueError("登录凭据缺少可用刷新令牌或已经过期。")
    account_info = access.get("https://api.openai.com/auth", {})
    if not isinstance(account_info, dict):
        raise ValueError("访问令牌账户字段无效。")
    account = account_info.get("chatgpt_account_id") or access.get("chatgpt_account_id") or identity["sub"]
    if not isinstance(account, str) or not 1 <= len(account) <= 256:
        raise ValueError("访问令牌账户标识无效。")
    earliest = tokens.get("earliest_refresh_at")
    if earliest is None:
        earliest = 0
    if isinstance(earliest, str):
        try:
            parsed_time = datetime.fromisoformat(earliest.replace("Z", "+00:00"))
            if parsed_time.tzinfo is None:
                raise ValueError()
            earliest = parsed_time.timestamp()
        except ValueError:
            raise ValueError("令牌刷新时间必须包含明确时区。") from None
    if type(earliest) not in {int, float} or not 0 <= earliest <= access["exp"]:
        raise ValueError("令牌刷新时间无效。")
    return {"clientId": client, "subject": identity["sub"], "account": account,
            "label": str(identity.get("email", "ChatGPT"))[:100] if identity.get("email_verified") is True else "ChatGPT",
            "accessToken": tokens["access_token"], "refreshToken": refresh_token, "idToken": tokens["id_token"],
            "scope": scopes, "expiresAt": access["exp"], "earliestRefreshAt": earliest}


def refresh(record):
    result = request_json(ISSUER + "/api/accounts/oauth/token", {"grant_type": "refresh_token", "client_id": record["clientId"],
        "refresh_token": record["refreshToken"], "resource": RESOURCE})
    result.setdefault("scope", record["scope"])
    return validate(result, record["clientId"], subject=record["subject"])


class BrowserLogin:
    def __init__(self, host, save, client="dynamic_agent_client"):
        self.host, self.save, self.client = host, save, client
        self.state, self.nonce, self.verifier = (secrets.token_urlsafe(32) for _ in range(3))
        self.deadline = time.monotonic() + 180
        self.status, self.message = "WAITING", "请在秘书主机的系统浏览器中登录。"
        self.lock = threading.RLock()
        self.server = None

    def public(self):
        with self.lock:
            if self.status == "WAITING" and time.monotonic() >= self.deadline:
                self.cancel()
            return {"state": self.status, "message": self.message}

    def start(self):
        # Fail before starting callbacks if the verifier is unavailable.
        try:
            import jwt  # noqa: F401 -- capability check, never a credential import
            from cryptography.hazmat.primitives.asymmetric import rsa  # noqa: F401
        except ImportError:
            raise ValueError("ChatGPT 登录需要安装可选依赖 PyJWT[crypto]。") from None
        login = self

        class Callback(BaseHTTPRequestHandler):
            def log_message(self, *_args):
                pass

            def do_GET(self):
                parsed = urllib.parse.urlsplit(self.path)
                with login.lock:
                    valid = (parsed.path == "/auth/callback" and self.headers.get("Host") == "127.0.0.1:" + str(login.server.server_port)
                             and login.status == "WAITING" and time.monotonic() < login.deadline)
                    query = urllib.parse.parse_qs(parsed.query)
                    valid = valid and len(query.get("state", [])) == 1 and hmac.compare_digest(query["state"][0], login.state)
                    if not valid:
                        self.send_error(400)
                        return
                    login.status = "EXCHANGING"
                try:
                    if len(query.get("code", [])) != 1 or not 1 <= len(query["code"][0]) <= 8192:
                        raise ValueError("登录未取得授权码，请检查组织许可后重新发起。")
                    result = request_json(ISSUER + "/api/accounts/oauth/token", {"grant_type": "authorization_code", "client_id": login.client,
                        "redirect_uri": login.redirect, "code": query["code"][0], "code_verifier": login.verifier, "resource": RESOURCE})
                    issued = validate(result, login.client, nonce=login.nonce)
                    with login.lock:
                        if login.status != "EXCHANGING" or time.monotonic() >= login.deadline:
                            raise ValueError("原登录已经取消或过期，未保存返回的凭据。")
                        login.save(issued)
                        login.status, login.message = "SUCCEEDED", "ChatGPT 连接已保存，请回到设置明确选择该连接和模型。"
                except Exception as error:
                    with login.lock:
                        login.status, login.message = "FAILED", str(error)[:300] if isinstance(error, ValueError) else "登录失败，请重新发起；未保存未验证的令牌。"
                try:
                    self.send_response(200 if login.status == "SUCCEEDED" else 400)
                    self.send_header("Content-Type", "text/plain; charset=utf-8")
                    self.send_header("Cache-Control", "no-store")
                    self.end_headers()
                    self.wfile.write(login.message.encode())
                finally:
                    threading.Thread(target=login.server.shutdown, daemon=True).start()

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Callback)
        self.server.daemon_threads = True
        self.redirect = "http://127.0.0.1:" + str(self.server.server_port) + "/auth/callback"
        challenge = base64.urlsafe_b64encode(hashlib.sha256(self.verifier.encode()).digest()).decode().rstrip("=")
        params = {"client_id": self.client, "ext_agent_host_id": self.host,
            "response_type": "code", "redirect_uri": self.redirect, "scope": SCOPES, "resource": RESOURCE,
            "state": self.state, "nonce": self.nonce, "code_challenge_method": "S256", "code_challenge": challenge}
        if self.client == "dynamic_agent_client":
            params["agent_name_hint"] = "AIW Secretary"

        def serve():
            try:
                self.server.serve_forever(poll_interval=0.1)
            finally:
                self.server.server_close()

        threading.Thread(target=serve, daemon=True).start()
        timer = threading.Timer(180, self.cancel)
        timer.daemon = True
        timer.start()
        return {"authorizationUrl": ISSUER + "/api/accounts/authorize?" + urllib.parse.urlencode(params),
                "expiresIn": 180, "note": self.message}

    def cancel(self):
        with self.lock:
            if self.status in {"WAITING", "EXCHANGING"}:
                self.status, self.message = "CANCELLED", "登录已取消或过期，请重新发起。"
                if self.server:
                    threading.Thread(target=self.server.shutdown, daemon=True).start()
