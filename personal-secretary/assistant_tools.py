"""Closed daily tools. No shell, authenticated browser, payment or publishing tools."""

import hashlib
from html.parser import HTMLParser
import http.client
import ipaddress
import json
from pathlib import Path
import re
import shutil
import socket
import ssl
import tempfile
import os
import urllib.parse
import uuid
import hashlib
from knowledge_client import KnowledgeError


class ConfirmationRequired(Exception):
    def __init__(self, message, action):
        super().__init__(message)
        self.action = action


class TextPage(HTMLParser):
    def __init__(self):
        super().__init__()
        self.hidden = 0
        self.parts, self.links = [], []

    def handle_starttag(self, tag, attrs):
        if tag in {"script", "style"}: self.hidden += 1
        if tag == "a" and len(self.links) < 30:
            href = dict(attrs).get("href", "")
            if href.startswith("https://"): self.links.append(href)

    def handle_endtag(self, tag):
        if tag in {"script", "style"}: self.hidden = max(0, self.hidden - 1)

    def handle_data(self, data):
        if not self.hidden and data.strip(): self.parts.append(data.strip())


def fetch_page(url):
    for _ in range(4):
        parsed = urllib.parse.urlsplit(url)
        if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password or parsed.fragment or len(url) > 4096 or parsed.port not in {None, 443}:
            raise ValueError("网页读取仅支持无登录信息的公开 HTTPS 地址。")
        addresses = socket.getaddrinfo(parsed.hostname, 443, type=socket.SOCK_STREAM)
        if not addresses or any(not ipaddress.ip_address(item[4][0]).is_global for item in addresses):
            raise ValueError("日常网页工具不访问本机或内网地址；如有需要，请单独说明授权范围。")
        # Connect to a checked address, retaining the original TLS identity.
        connection = http.client.HTTPSConnection(parsed.hostname, timeout=20)
        try:
            connection.sock = ssl.create_default_context().wrap_socket(
                socket.create_connection((addresses[0][4][0], 443), timeout=20), server_hostname=parsed.hostname)
            path = urllib.parse.urlunsplit(("", "", parsed.path or "/", parsed.query, ""))
            connection.request("GET", path, headers={"User-Agent": "AIW-Personal-Assistant/1.0", "Accept-Encoding": "identity"})
            response = connection.getresponse()
            if response.status in {301, 302, 303, 307, 308}:
                url = urllib.parse.urljoin(url, response.getheader("Location", ""))
                continue
            if response.status != 200:
                raise ValueError(f"网页返回 {response.status}，请换一个公开来源。")
            mime = response.getheader("Content-Type", "").lower()
            if not any(kind in mime for kind in ("text/", "application/json", "application/xhtml")):
                raise ValueError("该网页不是可读取的文本内容。")
            raw = response.read(512_001)
            if len(raw) > 512_000:
                raise ValueError("网页内容过大，请选择更具体的页面。")
            charset = re.search(r"charset=([\w-]+)", mime)
            try:
                text = raw.decode(charset.group(1) if charset else "utf-8", errors="replace")
            except LookupError:
                text = raw.decode("utf-8", errors="replace")
            parser = TextPage()
            parser.feed(text)
            return {"url": url, "text": "\n".join(parser.parts)[:14000], "links": parser.links}
        finally:
            connection.close()
    raise ValueError("网页重定向次数过多，已停止读取。")


def explicit_paths(text):
    text = re.sub(r"https?://\S+", "", text)
    return [match.rstrip(".,;，。；）)") for match in re.findall(r"(?:[A-Za-z]:[\\/][^\s\"<>|，。；]+|(?<![:/])/(?!/)[^\s\"<>|，。；]+)", text)]


class DailyTools:
    def __init__(self, settings, user_text, output_path=None, store=None, user_message=None):
        self.settings = settings
        self.store = store
        self.user_message = user_message if isinstance(user_message, str) else user_text
        self.selected = {Path(item).expanduser().resolve() for item in explicit_paths(user_text)}
        if output_path: self.selected.add(Path(output_path).expanduser().resolve())
        self.written, self.sources = [], []
        self.knowledge_counts = {"search": 0, "read": 0, "searchChars": 0, "readChars": 0}

    def knowledge(self, action):
        client = self.settings.knowledge_client()
        if action.get("action") == "knowledge_note_status":
            identity = action.get("requestId")
            if not isinstance(identity, str) or not identity or len(identity) > 200:
                raise KnowledgeError("本地笔记请求标识无效。")
            record = self.store.get("decision", "knowledge-note:" + identity)
            if not record or record.get("kind") != "KNOWLEDGE_NOTE":
                raise KnowledgeError("找不到这条本地笔记请求记录。")
            if record.get("serviceId") != self.settings.value.get("knowledgeServiceId"):
                raise KnowledgeError("知识库服务身份已变化，已停止核对或重放待处理笔记。")
            try:
                if record.get("state") in {"pending", "unknown"}:
                    try:
                        result = client.creation(record["key"])
                    except KnowledgeError as status_error:
                        if status_error.code != "CREATION_NOT_FOUND": raise
                        result = client.create_note(record["key"], record["body"])
                else:
                    result = record.get("result") or {}
            except KnowledgeError:
                updated = {key: value for key, value in record.items() if key not in {"id", "revision", "result"}}
                updated["state"] = "unknown"
                self.store.put("decision", "knowledge-note:" + identity, updated, record["revision"])
                raise KnowledgeError("笔记状态仍未知；已保留同一幂等键和原请求，未改写内容。") from None
            updated = {key: value for key, value in record.items() if key not in {"id", "revision"}}
            updated.update(state=result.get("state"), result=result)
            self.store.put("decision", "knowledge-note:" + identity, updated, record["revision"])
            return {"state": result.get("state"), "indexState": result.get("indexState"), "note": "笔记已保存" if result.get("state") == "succeeded" else "笔记创建失败，未确认保存" if result.get("state") == "failed" else "笔记仍在处理中；保存结果尚未最终确认"}
        if action.get("action") == "knowledge_search":
            if self.knowledge_counts["search"] >= 1: raise KnowledgeError("本次请求的知识库搜索次数已用完。")
            self.knowledge_counts["search"] += 1
            caps = self.settings.value.get("knowledgeCapabilities", [])
            if "search" not in caps: raise KnowledgeError("当前知识库凭证未开放搜索能力。")
            data = client.search(action.get("query", ""), action.get("limit", 10))
            items = data.get("items", [])
            output = []
            remaining = 30000 - self.knowledge_counts["searchChars"] - self.knowledge_counts["readChars"]
            omitted = False
            for item in items[:20]:
                source = item.get("source", {})
                url = self.settings.trusted_knowledge_url(source.get("url", ""))
                if not url: raise KnowledgeError("知识库返回了不受信任的来源链接，已拒绝使用该结果。")
                excerpt = item.get("excerpt", "")
                if not isinstance(excerpt, str): raise KnowledgeError("知识库摘要格式无效。")
                result = {key: item.get(key) for key in ("documentId", "revision", "title", "excerpt", "freshness", "location", "tags", "updatedAt")} | {"source": {"displayName": source.get("displayName"), "url": url}}
                size = len(json.dumps(result, ensure_ascii=False))
                if size > remaining:
                    omitted = True
                    break
                output.append(result)
                remaining -= size
                self.knowledge_counts["searchChars"] += size
            diagnostics = []
            for item in data.get("diagnostics", [])[:5]:
                diagnostic = {"code": item["code"][:100], "message": item["message"][:200]}
                size = len(json.dumps(diagnostic, ensure_ascii=False))
                if size > remaining: omitted = True; break
                diagnostics.append(diagnostic)
                remaining -= size
                self.knowledge_counts["searchChars"] += size
            if omitted:
                diagnostics.append({"code": "CONTEXT_BUDGET", "message": "Some results were omitted to stay within the request context budget."})
            return {"items": output, "complete": data.get("complete") and not omitted, "hasMore": data.get("hasMore") or omitted, "diagnostics": diagnostics}
        if action.get("action") == "knowledge_read":
            if self.knowledge_counts["read"] >= 3: raise KnowledgeError("本次请求的知识库正文读取次数已用完。")
            self.knowledge_counts["read"] += 1
            if "read" not in self.settings.value.get("knowledgeCapabilities", []): raise KnowledgeError("当前知识库凭证未开放正文读取能力。")
            data = client.read(action.get("documentId"), action.get("revision"), action.get("cursor"))
            source = data.get("source", {})
            url = self.settings.trusted_knowledge_url(source.get("url", ""))
            if not url: raise KnowledgeError("知识库返回了不受信任的来源链接，已拒绝使用该正文。")
            content = data.get("content", "")
            if not isinstance(content, str): raise KnowledgeError("知识库正文格式无效。")
            diagnostics = [{"code": item["code"][:100], "message": item["message"][:300]} for item in data.get("diagnostics", [])[:5] if isinstance(item.get("code"), str) and isinstance(item.get("message"), str)]
            result = {key: data.get(key) for key in ("documentId", "revision", "title", "content", "contentFormat", "freshness", "location", "truncated", "nextCursor")} | {"diagnostics": diagnostics, "source": {"displayName": source.get("displayName"), "url": url}}
            size = len(json.dumps(result, ensure_ascii=False))
            self.knowledge_counts["readChars"] += size
            if self.knowledge_counts["readChars"] + self.knowledge_counts["searchChars"] > 30000: raise KnowledgeError("知识库正文已达到本次上下文字符上限。")
            self.sources.append(url)
            return result
        if action.get("action") == "knowledge_save_note":
            if not re.search(r"保存|存入|写入|记到|入库|save|store|add to (?:the )?knowledge", self.user_message, re.I):
                raise KnowledgeError("只有用户当前消息明确要求保存时才能创建知识库笔记。")
            if "create_note" not in self.settings.value.get("knowledgeCapabilities", []): raise KnowledgeError("当前知识库凭证未开放笔记创建能力。")
            title, content = action.get("title"), action.get("content")
            if not isinstance(title, str) or not title.strip() or len(title.strip()) > 200 or not isinstance(content, str) or not content.strip() or len(content) > 100000:
                raise KnowledgeError("笔记标题或正文格式无效。")
            citations = action.get("citations", [])
            tags = action.get("tags", [])
            collection_id = action.get("collectionId")
            if not isinstance(tags, list) or len(tags) > 20 or any(not isinstance(tag, str) or not tag.strip() or len(tag) > 100 for tag in tags): raise KnowledgeError("笔记标签格式无效。")
            if collection_id is not None and (not isinstance(collection_id, str) or not collection_id or len(collection_id) > 200): raise KnowledgeError("笔记集合标识无效。")
            if not isinstance(citations, list) or len(citations) > 20 or any(not isinstance(cite, dict) or any(not isinstance(cite.get(key), str) or not cite[key] for key in ("documentId", "revision")) for cite in citations): raise KnowledgeError("引用数量或字段格式无效。")
            body = {"title": title.strip(), "content": content, "format": "markdown", "tags": tags, "collectionId": collection_id, "citations": citations}
            digest = hashlib.sha256(json.dumps(body, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
            identity = action.get("requestId")
            if not isinstance(identity, str) or not identity: identity = uuid.uuid4().hex
            record = self.store.get("decision", "knowledge-note:" + identity)
            if record:
                if record.get("digest") != digest: raise KnowledgeError("此请求键已有不同笔记正文，已停止重放。")
                if record.get("serviceId") != self.settings.value.get("knowledgeServiceId"): raise KnowledgeError("知识库服务身份已变化，已停止重放待核对笔记。")
            else:
                key = str(uuid.uuid4())
                record = self.store.put("decision", "knowledge-note:" + identity, {"kind":"KNOWLEDGE_NOTE", "key":key, "body":body, "digest":digest, "serviceId":self.settings.value.get("knowledgeServiceId"), "state":"pending"}, 0)
            try:
                if record.get("state") in {"pending", "unknown"}:
                    try:
                        result = client.creation(record["key"])
                    except KnowledgeError as status_error:
                        if status_error.code != "CREATION_NOT_FOUND":
                            raise
                        result = client.create_note(record["key"], record["body"])
                else:
                    result = record.get("result") or {}
            except KnowledgeError:
                updated = {key: value for key, value in record.items() if key not in {"id", "revision", "result"}}
                updated["state"] = "unknown"
                record = self.store.put("decision", "knowledge-note:" + identity, updated, record["revision"])
                raise KnowledgeError("笔记保存结果未知；已保留原请求，须先按同一幂等键核对。") from None
            self.store.put("decision", "knowledge-note:" + identity, {key:value for key,value in record.items() if key not in {"id","revision"}} | {"state":result.get("state"), "result":result}, record["revision"])
            state = result.get("state")
            note = "笔记已保存" if state == "succeeded" else "笔记创建失败，未确认保存" if state == "failed" else "笔记正在处理；保存结果尚未最终确认"
            return {"state": state, "indexState": result.get("indexState"), "documentId": result.get("documentId"), "url": self.settings.trusted_knowledge_url(result.get("url", "")) if result.get("url") else None, "note": note}

    def path(self, action, approved=False):
        raw = action.get("path")
        if not isinstance(raw, str) or not raw or len(raw) > 4096:
            raise ValueError("请指定要处理的文件位置。")
        if os.name == "nt" and (raw.startswith(chr(92) * 2) or raw.startswith("//")):
            raise ValueError("日常文件工具仅支持本机普通路径，不访问网络共享或 Windows 设备路径。")
        path = Path(raw).expanduser()
        if not path.is_absolute():
            raise ValueError("文件位置需要使用完整路径。")
        path = path.resolve()
        private_roots = {self.settings.directory.parent.resolve(), (Path.home() / ".aiw-personal").resolve()}
        if any(path == private or path.is_relative_to(private) or private.is_relative_to(path) for private in private_roots):
            raise ValueError("秘书的密钥、会话和私库不能通过日常文件工具访问。")
        if path == Path(path.anchor) or path == Path.home().resolve():
            raise ValueError("请指定具体文件，不能对整个磁盘或用户目录执行此操作。")
        roots = [Path(root).resolve() for root in self.settings.value.get("roots", [])]
        allowed = path in self.selected or any(path.is_relative_to(root) for root in roots)
        code_file = path.suffix.lower() in {".exe", ".dll", ".ps1", ".bat", ".cmd", ".sh", ".py", ".js", ".java", ".go", ".ts"}
        sensitive = any(part.lower() in {".ssh", ".aws", ".codex", ".agents"} for part in path.parts) or path.name.lower() in {".env", "credentials", "auth.json", "id_rsa", "id_ed25519"}
        if not approved and (not allowed or sensitive or action["action"] == "write_file" and code_file):
            raise ConfirmationRequired("这项文件操作超出日常授权范围，请确认具体文件及内容。", {**action, "path": str(path)})
        return path

    def perform(self, action, approved=False):
        kind = action.get("action")
        if kind in {"knowledge_search", "knowledge_read", "knowledge_save_note", "knowledge_note_status"}:
            return self.knowledge(action)
        if kind == "search":
            query = action.get("query", "")
            if not isinstance(query, str) or not query.strip() or len(query) > 1000:
                raise ValueError("搜索内容为空或过长。")
            result = fetch_page("https://search.yahoo.co.jp/search?" + urllib.parse.urlencode({"p": query}))
            self.sources.append(result["url"])
            return result
        if kind == "read_url":
            result = fetch_page(action.get("url", ""))
            self.sources.append(result["url"])
            return result
        if kind not in {"read_file", "write_file", "delete_file"}:
            if kind in {"payment", "publish", "command"}:
                raise ValueError("当前没有连接付款、发布或命令执行工具。这项操作未执行，请选择对应服务后单独授权。")
            raise ValueError("模型请求了不支持的操作，这项操作未执行。")
        path = self.path(action, approved or kind == "delete_file")
        if kind == "read_file":
            if not path.is_file() or path.stat().st_size > 512000:
                raise ValueError("文件不存在或超过 512 KB 读取上限。")
            text = path.read_text(encoding="utf-8-sig")
            return {"path": str(path), "text": text[:30000], "truncated": len(text) > 30000}
        if kind == "delete_file":
            if not path.is_file() or path.stat().st_size > 16_000_000:
                raise ValueError("仅支持删除明确选择的、不超过 16 MB 的单个文件。")
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            if not approved:
                raise ConfirmationRequired("将不可恢复地删除这个文件，请确认。", {"action": kind, "path": str(path), "expectedDigest": digest})
            if action.get("expectedDigest") != digest:
                raise ValueError("待删除文件已变化，请重新核对后确认。")
            path.unlink()
            return {"path": str(path), "deleted": True}
        text = action.get("text")
        if not isinstance(text, str) or len(text.encode("utf-8")) > 512000:
            raise ValueError("保存内容不是文本或超过 512 KB。")
        if path.exists():
            if not path.is_file() or path.stat().st_size > 16_000_000:
                raise ValueError("目标不是普通文件或超过备份上限，请换一个文件位置。")
            backup = self.settings.directory / "file-backups" / (uuid.uuid4().hex + path.suffix)
            backup.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
            shutil.copyfile(path, backup)
            os.chmod(backup, 0o600)
        path.parent.mkdir(parents=True, exist_ok=True)
        descriptor, temporary = tempfile.mkstemp(prefix=".assistant-", dir=path.parent)
        try:
            with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
                stream.write(text)
            os.replace(temporary, path)
        finally:
            Path(temporary).unlink(missing_ok=True)
        self.written.append(str(path))
        return {"path": str(path), "saved": True}
