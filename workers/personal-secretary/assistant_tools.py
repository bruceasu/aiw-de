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
    def __init__(self, settings, user_text, output_path=None):
        self.settings = settings
        self.selected = {Path(item).expanduser().resolve() for item in explicit_paths(user_text)}
        if output_path: self.selected.add(Path(output_path).expanduser().resolve())
        self.written, self.sources = [], []

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
