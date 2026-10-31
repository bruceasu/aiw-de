"""Knowledge Base actions exposed through the secretary's closed tool set."""

import hashlib
import json
import re
import uuid

from .client import KnowledgeError


def perform(host, action):
    self = host
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

