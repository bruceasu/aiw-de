"""Read bounded model metadata using the selected secretary-local connection."""

import json
import os
import threading
import urllib.error
import urllib.request

from secretary_credentials import endpoint, api_key_environment, missing_api_key_environment


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *_args, **_kwargs):
        return None


_probes = threading.BoundedSemaphore(2)


def list_models(settings, data):
    service = data.get("service")
    if service not in {"chatgpt", "openai", "deepseek", "responses", "chat"}:
        raise ValueError("当前方式没有可用的模型目录，请手动填写模型。")
    identity = data.get("connectionId") or ""
    key = data.get("key") or ""
    if data.get("credentialSource") == "environment" and identity:
        raise ValueError("请选择环境变量或已保存的连接，不要同时使用。")
    if not isinstance(key, str) or len(key) > 4096 or any(c in key for c in "\x00\r\n"):
        raise ValueError("模型密钥格式不正确。")
    if identity and key:
        raise ValueError("请选择已保存的连接或输入新密钥，不要同时使用。")
    binding = None
    if identity:
        mode = "chatgpt" if service == "chatgpt" else "api-key"
        binding = settings.connections.binding(identity, mode)
        access = settings.connections.access(identity, binding["connectionVersion"], mode)
        key, base = access["token"], access["baseUrl"]
    else:
        if service == "chatgpt":
            raise ValueError("请先登录并选择 ChatGPT 连接。")
        if service in {"responses", "chat"} and not data.get("endpoint"):
            raise ValueError("请先填写模型服务地址。")
        base = endpoint(data.get("endpoint"))
        saved = settings.value
        if data.get("credentialSource") == "environment":
            if key:
                raise ValueError("请选择环境变量或填写 API Key，不要同时使用。")
            key = os.environ.get(api_key_environment(base), "").strip()
            if not key:
                raise ValueError(missing_api_key_environment(base))
        elif not key and saved.get("endpoint", "").rstrip("/") == base:
            key = saved.get("key") or ""
    base = endpoint(base)
    if service == "openai" and base != "https://api.openai.com/v1":
        raise ValueError("OpenAI 连接地址不匹配，请选择对应的连接。")
    if service == "deepseek" and base not in {"https://api.deepseek.com", "https://api.deepseek.com/v1"}:
        raise ValueError("DeepSeek 连接地址不匹配，请选择对应的连接。")
    if service == "chatgpt" and base != "https://api.openai.com/v1":
        raise ValueError("ChatGPT 连接地址不匹配。")
    if not key and service in {"openai", "deepseek"}:
        raise ValueError("请先选择连接或填写 API Key。")
    if not _probes.acquire(blocking=False):
        raise ValueError("模型目录正在查询，请稍后重试。")
    try:
        headers = {"Accept": "application/json"}
        if key:
            headers["Authorization"] = "Bearer " + key
        catalog_url = "https://api.deepseek.com/models" if base in {"https://api.deepseek.com", "https://api.deepseek.com/v1"} else base + "/models"
        request = urllib.request.Request(catalog_url, headers=headers, method="GET")
        try:
            with urllib.request.build_opener(NoRedirect()).open(request, timeout=12) as response:
                raw = response.read(1_000_001)
            if len(raw) > 1_000_000:
                raise ValueError("模型目录过大，请手动填写模型。")
            body = json.loads(raw)
        except urllib.error.HTTPError as error:
            status = error.code
            error.close()
            if status in {401, 403}:
                raise ValueError("模型目录未接受当前连接或账户权限；请检查凭据，也可手动填写模型。") from None
            raise ValueError("模型目录请求失败（HTTP " + str(status) + "）；可手动填写模型。") from None
        except (urllib.error.URLError, OSError, TimeoutError, UnicodeError, json.JSONDecodeError):
            raise ValueError("无法读取模型目录，请检查连接；也可手动填写模型。") from None
        if binding and settings.connections.binding(identity, "chatgpt" if service == "chatgpt" else "api-key") != binding:
            raise ValueError("查询期间连接已变化，请重新选择。")
        rows = body.get("data") if isinstance(body, dict) else None
        if rows is None and isinstance(body, dict):
            rows = body.get("models")
        if not isinstance(rows, list):
            raise ValueError("模型目录格式不受支持，请手动填写模型。")
        models, seen = [], set()
        for row in rows:
            if not isinstance(row, dict) or row.get("visibility", "list") != "list":
                continue
            name = row.get("id") or row.get("slug")
            if not isinstance(name, str) or not 1 <= len(name) <= 200 or any(ord(c) < 32 for c in name) or name in seen:
                continue
            seen.add(name)
            models.append(name)
            if len(models) == 500:
                break
        return {"models": models, "truncated": len(rows) > 500,
                "note": "目录由服务商返回，模型是否支持当前工具和接口仍需确认。"}
    finally:
        _probes.release()
