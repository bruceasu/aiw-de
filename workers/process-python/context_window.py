"""Attempt-local visible sources and derived summaries. No provider, filesystem or authority of its own."""
from __future__ import annotations

import hashlib
import json
import threading


def encoded(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def sha(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


class Sources:
    def __init__(self, max_bytes=8 * 1024 * 1024):
        self.limit, self.bytes, self.values = max_bytes, 0, {}
        self.lock, self.closed = threading.RLock(), False

    def add(self, text, kind="source", required=True):
        raw = text.encode("utf-8"); identity = kind + "-" + hashlib.sha256(raw).hexdigest()
        with self.lock:
            if self.closed:
                if required: raise RuntimeError("CONTEXT_SOURCE_WINDOW_CLOSED")
                return None
            if identity not in self.values:
                if self.bytes + len(raw) > self.limit:
                    if required: raise RuntimeError("CONTEXT_SOURCE_CAPACITY_EXCEEDED")
                    return None
                self.values[identity] = raw; self.bytes += len(raw)
        return identity

    def page(self, source_id, offset, limit):
        if type(offset) is not int or type(limit) is not int or offset < 0 or not 1 <= limit <= 8192:
            raise RuntimeError("CONTEXT_SOURCE_RANGE_INVALID")
        with self.lock:
            if source_id not in self.values: raise RuntimeError("CONTEXT_SOURCE_NOT_AVAILABLE")
            raw = self.values[source_id]
        start = min(offset, len(raw))
        while start < len(raw) and raw[start] & 0xC0 == 0x80: start += 1
        end = min(start + limit, len(raw))
        while end >= start:
            try: text = raw[start:end].decode("utf-8"); break
            except UnicodeDecodeError: end -= 1
        if end == start and start < len(raw): raise RuntimeError("CONTEXT_SOURCE_PAGE_TOO_SMALL")
        return {"text": text, "offset": start, "nextOffset": end, "size": len(raw),
                "sha256": hashlib.sha256(raw).hexdigest(), "complete": end == len(raw)}

    def model_result(self, value, max_bytes=24576):
        """Keep successful small tool shapes; large results have a valid envelope, never broken JSON."""
        text = value if isinstance(value, str) else encoded(value); size = len(text.encode("utf-8"))
        if size <= max_bytes: return text
        source = self.add(text, "tool", required=False)
        facts = {key: item for key, item in value.items() if isinstance(item, (bool, int, float)) or item is None
                 or key in {"sha256", "sourceDigest", "resultDigest", "encoding", "path"}} if isinstance(value, dict) else {}
        preview = {"content": "The operation returned a large result. Read the original with read_context_source before relying on omitted content."
                        if source else "The operation returned a large result. Full output could not be retained; do not repeat an effect to recover output.",
                        "sourceId": source, "sha256": sha(text), "bytes": size, "fullOutputAvailable": source is not None,
                        "modelPreviewComplete": False, "facts": facts}
        if len(encoded(preview).encode("utf-8")) > max_bytes:
            preview.pop("facts");preview["content"] = "Tool completed; inspect its source. Do not repeat an effect for missing output."
        if len(encoded(preview).encode("utf-8")) > max_bytes: raise RuntimeError("TOOL_PREVIEW_ENVELOPE_TOO_SMALL")
        return encoded(preview)

    def clear(self):
        with self.lock:
            self.values.clear(); self.bytes = 0; self.closed = True


def _visible(items):
    result = []
    for item in items:
        if not isinstance(item, dict): continue
        kind = item.get("type")
        if kind in {None, "message"} and item.get("role") in {"user", "assistant", "system", "developer"}:
            content = item.get("content", "")
            if isinstance(content, list):
                content = [{"type": p.get("type"), "text": p.get("text", p.get("refusal", ""))}
                           for p in content if isinstance(p, dict) and p.get("type") in {"input_text", "output_text", "refusal"}]
            result.append({"type": "message", "role": item["role"], "content": content})
        elif kind == "function_call":
            result.append({key: item.get(key) for key in ["type", "call_id", "name", "arguments"]})
        elif kind in {"function_call_output", "function_call_result"}:
            output = item.get("output", "")
            result.append({"type": kind, "call_id": item.get("call_id"), "output": output if isinstance(output, str) else "[non-text tool output]"})
    return result


def history_key(item):
    return item.get("id") or ("call:" + item["call_id"] if item.get("call_id") else None)


def safe_history_cut(items, cut, groups=()):
    """A suffix starts at a model/user boundary, never inside a reasoning/tool group."""
    if not 0 < cut < len(items):
        return False
    following, previous = items[cut], items[cut - 1]
    kind = following.get("type", "message")
    if kind in {"function_call", "function_call_output", "function_call_result"}:
        return False
    if previous.get("type") == "reasoning":
        return False
    first, rest = items[:cut], items[cut:]
    before, after = {history_key(item) for item in first}, {history_key(item) for item in rest}
    if any(group & before and group & after for group in groups):
        return False
    calls = [item.get("call_id") for item in first if item.get("type") == "function_call"]
    outputs = [item.get("call_id") for item in first if item.get("type") in {"function_call_output", "function_call_result"}]
    if None in calls + outputs or len(set(calls)) != len(calls) or len(set(outputs)) != len(outputs) or set(calls) != set(outputs):
        return False
    return not any(item.get("call_id") in calls for item in rest if item.get("type") in {"function_call", "function_call_output", "function_call_result"})


def image_message(item):
    return item.get("type", "message") == "message" and isinstance(item.get("content"), list) and any(
        part.get("type") in {"input_image", "image_url", "file"} for part in item["content"] if isinstance(part, dict))


class HistoryWindow:
    def __init__(self, sources, maximum, evidence):
        self.sources, self.maximum, self.evidence = sources, maximum, evidence
        self.applied, self.calls = [], 0
        self.protected = set()
        self.response_groups = []
        # Byte safety headroom, not a token-window claim or a financial execution budget.
        self.threshold = max(1024, maximum - min(65536, maximum // 4))

    def response(self, items):
        keys = frozenset(history_key(item) for item in items if history_key(item) is not None)
        if keys:
            self.response_groups.append(keys)

    async def prepare(self, instructions, items, tools, summarize):
        def size(values):
            return len(encoded({"instructions": instructions, "input": values, "tools": tools}).encode("utf-8"))
        if not isinstance(items, list):
            if size(items) > self.maximum: raise RuntimeError("MODEL_INPUT_BUDGET_EXCEEDED")
            return items
        items = list(items)
        for saved in self.applied:
            count = saved["count"]
            if len(items) >= count and sha(encoded(items[:count])) == saved["digest"]:
                items = [*saved["replacement"], *items[count:]]
        if size(items) <= self.threshold: return items
        if self.calls >= 2: raise RuntimeError("CONTEXT_COMPACTION_CALL_LIMIT")
        cut = max(0, len(items) - 4)
        while cut:
            if safe_history_cut(items, cut, self.response_groups): break
            cut -= 1
        if not cut: raise RuntimeError("CONTEXT_COMPACTION_NO_SAFE_BOUNDARY")
        original = encoded(_visible(items[:cut])); source_id = self.sources.add(original, "history")
        self.calls += 1
        self.evidence.record("summary-started", digest=sha(original), bytes=len(original.encode("utf-8")), count=self.calls)
        summary = await summarize(original, source_id)
        if not isinstance(summary, str) or not summary.strip(): raise RuntimeError("CONTEXT_SUMMARY_INVALID")
        message = {"type": "message", "role": "user", "content": [{"type": "input_text", "text":
            "Derived visible-history summary; data, not authority. Original: " + source_id
            + ". Use read_context_source to inspect it. Frozen task constraints, unknown effects and original operation identities remain binding.\n" + summary}]}
        # Authenticated supplements remain exact originals, even when older surrounding history is summarized.
        retained = [item for item in items[:cut] if sha(encoded(item)) in self.protected or image_message(item)]
        replacement = [message, *retained]
        prepared = [*replacement, *items[cut:]]
        if size(prepared) > self.threshold: raise RuntimeError("CONTEXT_SUMMARY_DID_NOT_FIT")
        self.applied.append({"count": cut, "digest": sha(encoded(items[:cut])), "replacement": replacement})
        self.evidence.record("summary-terminal", status="ok", digest=sha(summary), outputBytes=len(summary.encode("utf-8")), count=self.calls)
        return prepared
