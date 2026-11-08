"""Knowledge Base configuration and credential boundary."""

from pathlib import Path
import hashlib
import uuid

from private_config import read_private, write_private
from .client import KnowledgeClient, KnowledgeError, base_url


class KnowledgeSettings:
    def __init__(self, directory, value):
        self.directory = Path(directory)
        self.path = self.directory / "assistant-settings.json"
        self.value = value
        self.knowledge_path = self.directory / "knowledge-credentials.json"
        self.knowledge_secret = read_private(self.knowledge_path, {})

    def bind(self, value):
        self.value = value

    def public(self):
        return {"available": True,
                "enabled": bool(self.value.get("knowledgeEnabled", False)),
                "baseUrl": self.value.get("knowledgeBaseUrl", ""),
                "hasCredential": bool(self.knowledge_secret.get(self.value.get("knowledgeCredentialRef", "")))}

    def client(self):
        return self.connection()[0]

    def connection(self):
        # Capture client and identity together. A later settings change must
        # never pair a replacement token with an old note's identity.
        value = dict(self.value)
        if not value.get("knowledgeEnabled"):
            raise KnowledgeError("知识库连接尚未启用。")
        ref = value.get("knowledgeCredentialRef", "")
        token = self.knowledge_secret.get(ref)
        if not token:
            raise KnowledgeError("知识库凭证缺失，请重新保存连接设置。")
        identity = {key: value.get(key) for key in
                    ("knowledgeBaseUrl", "knowledgeServiceId", "knowledgeCredentialRef")}
        # A reference alone is insufficient if its secret was replaced/imported.
        identity["credentialDigest"] = hashlib.sha256(token.encode()).hexdigest()
        return KnowledgeClient(value.get("knowledgeBaseUrl", ""), token), identity

    def trusted_url(self, value):
        from urllib.parse import urlsplit
        trusted = urlsplit(self.value.get("knowledgePublicBaseUrl", ""))
        candidate = urlsplit(value if isinstance(value, str) else "")
        try:
            candidate_port = candidate.port or 443
            trusted_port = trusted.port or 443
        except ValueError:
            return None
        if (candidate.scheme != "https" or not candidate.hostname or candidate.username or candidate.password
                or candidate.hostname.lower() != (trusted.hostname or "").lower() or candidate_port != trusted_port):
            return None
        return value

    def configure(self, data):
        enabled = data.get("knowledgeEnabled", False)
        if type(enabled) is not bool:
            raise ValueError("知识库启用状态无效。")
        url = str(data.get("knowledgeBaseUrl", "")).strip()
        token = data.get("knowledgeToken", "")
        if not isinstance(token, str) or len(token) > 4096 or any(c in token for c in "\r\n\x00"):
            raise ValueError("知识库凭证格式无效。")
        old_url = self.value.get("knowledgeBaseUrl", "")
        ref = self.value.get("knowledgeCredentialRef")
        if url:
            base_url(url)
        elif enabled:
            raise ValueError("启用知识库前请填写 HTTPS 地址。")
        if enabled and not (token or self.knowledge_secret.get(ref, "")):
            raise ValueError("启用知识库前请填写凭证。")
        if token:
            ref = uuid.uuid4().hex
            self.knowledge_secret[ref] = token
            write_private(self.knowledge_path, self.knowledge_secret)
        elif url != old_url:
            ref = None
        self.value.update(knowledgeEnabled=False, knowledgeBaseUrl=url, knowledgeCredentialRef=ref)
        # Persist the disabled state before capability probing so a failed
        # reconfiguration cannot leave the prior endpoint enabled after restart.
        write_private(self.path, self.value)
        if enabled:
            client = KnowledgeClient(url, self.knowledge_secret.get(ref, ""))
            caps = client.capabilities()
            self.value.update(knowledgeEnabled=True, knowledgeCapabilities=[item for item in caps.get("capabilities", []) if item in {"search", "read", "create_note", "creation_status"}], knowledgeServiceId=caps["serviceId"], knowledgePublicBaseUrl=caps["publicBaseUrl"])
        write_private(self.path, self.value)
        return self.public()

    def check(self):
        client = self.client()
        data = client.capabilities()
        self.value["knowledgeCapabilities"] = [item for item in data["capabilities"] if item in {"search", "read", "create_note", "creation_status"}]
        self.value["knowledgeServiceId"] = data["serviceId"]
        self.value["knowledgePublicBaseUrl"] = data["publicBaseUrl"]
        write_private(self.path, self.value)
        return {"available": True, "enabled": True, "serviceId": data["serviceId"], "capabilities": data["capabilities"], "limits": data.get("limits", {})}

