"""Bounded HTTPS client for the independent knowledge API v1.0."""

import http.client
from email.utils import parsedate_to_datetime
import json
import socket
import ssl
import time
import urllib.parse
import uuid


class KnowledgeError(ValueError):
    def __init__(self, message, code=None):
        super().__init__(message)
        self.code = code


def base_url(value):
    parsed = urllib.parse.urlsplit(value if isinstance(value, str) else "")
    if (parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password
            or parsed.path not in {"", "/"} or parsed.query or parsed.fragment):
        raise KnowledgeError("知识库地址必须是仅含 HTTPS 主机和可选端口的 base URL。")
    try:
        port = parsed.port or 443
    except ValueError:
        raise KnowledgeError("知识库端口无效。") from None
    return urllib.parse.urlunsplit(("https", parsed.netloc, "", "", "")), parsed.hostname, port


class KnowledgeClient:
    def __init__(self, url, token, timeout=15):
        self.url, self.host, self.port = base_url(url)
        if not isinstance(token, str) or not token or len(token) > 4096 or "\n" in token or "\r" in token:
            raise KnowledgeError("知识库凭证无效。")
        self.token, self.timeout = token, timeout

    def request(self, method, path, body=None, key=None, read_only=False):
        parsed_path = urllib.parse.urlsplit(path)
        if not path.startswith("/") or parsed_path.scheme or parsed_path.netloc or parsed_path.fragment:
            raise KnowledgeError("知识库请求路径无效。")
        payload = None if body is None else json.dumps(body, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        if payload is not None and len(payload) > 256 * 1024:
            raise KnowledgeError("知识库请求超过 256 KiB 限制。")
        attempts = 2 if read_only or method == "GET" else 1
        deadline = time.monotonic() + 15
        for attempt in range(attempts):
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise KnowledgeError("知识库请求超过 15 秒总时限。")
            connection = http.client.HTTPSConnection(self.host, self.port, timeout=min(3, remaining), context=ssl.create_default_context())
            try:
                headers = {"Authorization": "Bearer " + self.token, "Accept": "application/json", "User-Agent": "AIW-Personal-Secretary/1.0"}
                if payload is not None:
                    headers["Content-Type"] = "application/json; charset=utf-8"
                if key:
                    headers["Idempotency-Key"] = key
                connection.request(method, "/api/v2/secretary" + parsed_path.path + ("?" + parsed_path.query if parsed_path.query else ""), body=payload, headers=headers)
                remaining = deadline - time.monotonic()
                if connection.sock and remaining > 0:
                    connection.sock.settimeout(remaining)
                response = connection.getresponse()
                transient = response.status in {429, 503, 504}
                retry_delay = None
                if transient and (read_only or method == "GET") and attempt + 1 < attempts:
                    retry_after = response.getheader("Retry-After")
                    try:
                        retry_delay = max(0.0, float(retry_after)) if retry_after is not None else 0.0
                    except ValueError:
                        try:
                            retry_delay = max(0.0, (parsedate_to_datetime(retry_after).timestamp() - time.time()))
                        except (TypeError, ValueError, OverflowError):
                            retry_delay = 0.0
                    remaining = deadline - time.monotonic()
                    if retry_delay >= remaining:
                        raise KnowledgeError("知识库要求延后重试，已达到本次请求时限。")
                    time.sleep(retry_delay)
                    continue
                raw = response.read(1_048_577)
                if len(raw) > 1_048_576:
                    raise KnowledgeError("知识库响应超过 1 MiB 限制。")
                try:
                    envelope = json.loads(raw.decode("utf-8"))
                except (UnicodeError, json.JSONDecodeError):
                    raise KnowledgeError("知识库返回了无法识别的响应。") from None
                if (not isinstance(envelope, dict) or envelope.get("apiVersion") != "1.0"
                        or not isinstance(envelope.get("requestId"), str) or not envelope["requestId"].strip()):
                    raise KnowledgeError("知识库 API 版本或响应格式不匹配。")
                if response.status in {301, 302, 303, 307, 308}:
                    raise KnowledgeError("知识库拒绝重定向；请检查 API 地址。")
                if response.status >= 400:
                    error = envelope.get("error", {})
                    code = error.get("code") if isinstance(error, dict) else ""
                    messages = {"UNAUTHENTICATED": "知识库凭证无效，请检查连接设置。", "FORBIDDEN": "知识库凭证缺少所需权限。", "NOT_FOUND": "知识库资料不存在或当前凭证无权读取。", "REVISION_CHANGED": "资料版本已变化，请重新搜索。", "CONTENT_NOT_READY": "资料仍在处理中，请稍后重试。", "UNSUPPORTED_CONTENT": "知识库暂不支持读取此资料。", "RATE_LIMITED": "知识库请求过于频繁，请稍后重试。", "UNAVAILABLE": "知识库暂不可用。"}
                    raise KnowledgeError(messages.get(code, "知识库请求未完成（" + str(code or response.status) + "）。"), code)
                data = envelope.get("data")
                if not isinstance(data, dict):
                    raise KnowledgeError("知识库响应缺少 data 对象。")
                return data
            except (OSError, socket.timeout, http.client.HTTPException, ssl.SSLError) as error:
                if attempt + 1 < attempts:
                    continue
                raise KnowledgeError("知识库连接失败或超时；请检查地址和服务状态。") from None
            finally:
                connection.close()

    def capabilities(self):
        data = self.request("GET", "/capabilities")
        if (not isinstance(data.get("serviceId"), str) or not data["serviceId"].strip()
                or not isinstance(data.get("capabilities"), list)
                or any(not isinstance(item, str) for item in data["capabilities"])):
            raise KnowledgeError("知识库能力响应不完整。")
        if not isinstance(data.get("limits"), dict):
            raise KnowledgeError("知识库能力限制格式无效。")
        for field in ("maxSearchLimit", "maxExcerptChars", "maxReadChars", "maxNoteChars"):
            if type(data["limits"].get(field)) is not int or data["limits"][field] < 1:
                raise KnowledgeError("知识库能力限制字段无效。")
        origin = data.get("publicBaseUrl", "")
        parsed = urllib.parse.urlsplit(origin)
        if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:
            raise KnowledgeError("知识库阅读入口无效，已拒绝显示来源链接。")
        return data

    def search(self, query, limit=10):
        if not isinstance(query, str) or not query.strip() or len(query) > 2000:
            raise KnowledgeError("搜索词不能为空且不能超过 2000 字符。")
        data = self.request("POST", "/search", {"query": query.strip(), "limit": max(1, min(int(limit), 20)), "excerptChars": 1200, "filters": {}}, read_only=True)
        if not isinstance(data.get("items"), list) or type(data.get("complete")) is not bool or type(data.get("hasMore")) is not bool or not isinstance(data.get("diagnostics"), list):
            raise KnowledgeError("知识库搜索响应字段无效。")
        for item in data["items"]:
            if not isinstance(item, dict) or any(not isinstance(item.get(key), str) or not item[key] for key in ("documentId", "revision", "title", "format", "updatedAt", "excerpt")):
                raise KnowledgeError("知识库搜索结果缺少资料 ID、revision 或标题。")
            if not isinstance(item.get("collectionIds"), list) or not isinstance(item.get("tags"), list):
                raise KnowledgeError("知识库搜索结果集合或标签格式无效。")
            if item.get("freshness") not in {"current", "stale", "unknown"} or not isinstance(item.get("source"), dict) or not isinstance(item.get("location"), dict):
                raise KnowledgeError("知识库搜索结果来源、位置或 freshness 无效。")
            if not isinstance(item["source"].get("url"), str) or not item["source"]["url"] or not isinstance(item["location"].get("label"), str) or not item["location"]["label"]:
                raise KnowledgeError("知识库搜索结果缺少来源链接或位置标签。")
            if item["location"].get("kind") not in {"document", "page", "section", "lines"} or item["source"].get("kind") not in {"import", "note", "external_reference"}:
                raise KnowledgeError("知识库搜索结果来源或位置类型无效。")
        if any(not isinstance(item, dict) or not isinstance(item.get("code"), str) or not isinstance(item.get("message"), str) for item in data["diagnostics"]):
            raise KnowledgeError("知识库诊断字段无效。")
        return data

    def read(self, document_id, revision, cursor=None):
        if not isinstance(document_id, str) or not document_id or len(document_id) > 200 or not isinstance(revision, str) or not revision:
            raise KnowledgeError("请从知识库搜索结果中选择资料。")
        params = {"revision": revision, "maxChars": "12000"}
        if cursor:
            params["cursor"] = str(cursor)
        path = "/documents/" + urllib.parse.quote(document_id, safe="") + "?" + urllib.parse.urlencode(params)
        data = self.request("GET", path)
        if any(not isinstance(data.get(key), str) or not data[key] for key in ("documentId", "revision", "title", "content")):
            raise KnowledgeError("知识库正文响应缺少必需字段。")
        if any(not isinstance(data.get(key), str) or not data[key] for key in ("format", "updatedAt")) or not isinstance(data.get("collectionIds"), list) or not isinstance(data.get("tags"), list):
            raise KnowledgeError("知识库正文元数据字段无效。")
        if data.get("contentFormat") not in {"text", "markdown"} or data.get("freshness") not in {"current", "stale", "unknown"} or type(data.get("truncated")) is not bool:
            raise KnowledgeError("知识库正文格式或 freshness 无效。")
        if (data["truncated"] and not isinstance(data.get("nextCursor"), str)) or (not data["truncated"] and data.get("nextCursor") is not None):
            raise KnowledgeError("知识库正文 cursor 状态不一致。")
        if (not isinstance(data.get("source"), dict) or not isinstance(data.get("location"), dict)
                or not isinstance(data.get("diagnostics"), list)
                or data["source"].get("kind") not in {"import", "note", "external_reference"}
                or data["location"].get("kind") not in {"document", "page", "section", "lines"}):
            raise KnowledgeError("知识库正文来源、位置或诊断字段无效。")
        if any(not isinstance(item, dict) or not isinstance(item.get("code"), str) or not isinstance(item.get("message"), str)
               for item in data["diagnostics"]):
            raise KnowledgeError("知识库正文诊断字段无效。")
        if not isinstance(data["source"].get("url"), str) or not data["source"]["url"] or not isinstance(data["location"].get("label"), str) or not data["location"]["label"]:
            raise KnowledgeError("知识库正文缺少来源链接或位置标签。")
        return data

    def create_note(self, key, body):
        return self._creation(self.request("POST", "/notes", body, key=key), key)

    def creation(self, key):
        return self._creation(self.request("GET", "/note-creations/" + urllib.parse.quote(key, safe="")), key)

    @staticmethod
    def _creation(data, key):
        if (data.get("idempotencyKey") != key or not isinstance(data.get("operationId"), str) or not data["operationId"]
                or data.get("state") not in {"pending", "succeeded", "failed"}
                or data.get("indexState") not in {"pending", "ready", "failed", "unsupported"}):
            raise KnowledgeError("知识库笔记创建状态字段无效。")
        if data["state"] == "succeeded" and any(not isinstance(data.get(field), str) or not data[field] for field in ("documentId", "revision", "url")):
            raise KnowledgeError("知识库成功状态缺少持久化结果。")
        if data["state"] == "failed" and (not isinstance(data.get("error"), dict) or not isinstance(data["error"].get("code"), str) or not isinstance(data["error"].get("message"), str)):
            raise KnowledgeError("知识库失败状态缺少受控错误详情。")
        return data
