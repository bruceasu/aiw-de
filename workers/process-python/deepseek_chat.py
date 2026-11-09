"""DeepSeek Chat wire history and bounded streams, behind the public SDK Model seam."""
from __future__ import annotations

import json
import uuid


def plain(value):
    if hasattr(value, "model_dump"):
        return value.model_dump(exclude_none=True)
    if isinstance(value, list):
        return [plain(item) for item in value]
    return value


def content(parts):
    if isinstance(parts, str) or parts is None:
        return parts
    result = []
    for part in parts:
        kind = part.get("type")
        if kind in {"input_text", "output_text", "text"}:
            result.append({"type": "text", "text": part["text"]})
        elif kind == "input_image" and part.get("image_url"):
            result.append({"type": "image_url", "image_url": {"url": part["image_url"], "detail": part.get("detail", "auto")}})
        elif kind == "input_image" and part.get("file_id"):
            result.append({"type": "file", "file_id": part["file_id"]})
        else:
            raise RuntimeError("DEEPSEEK_CHAT_CONTENT_UNSUPPORTED")
    return result


def messages(instructions, items):
    """Rebuild a complete assistant message, including reasoning and all its calls."""
    rows = [{"role": "system", "content": instructions}] if instructions else []
    if isinstance(items, str):
        return [*rows, {"role": "user", "content": items}]
    assistant, reasoning, pending, seen = None, None, set(), set()

    def flush():
        nonlocal assistant
        if assistant is not None:
            rows.append(assistant)
            assistant = None

    def start():
        nonlocal assistant, reasoning
        if assistant is None:
            assistant = {"role": "assistant", "content": None}
        if reasoning is not None:
            assistant["reasoning_content"] = reasoning
            reasoning = None

    for raw in items:
        item = plain(raw)
        kind = item.get("type", "message")
        if kind == "reasoning":
            if pending:
                raise RuntimeError("DEEPSEEK_CHAT_HISTORY_UNPAIRED")
            flush()
            text = "".join(part["text"] for part in item.get("content", []) if part.get("type") == "reasoning_text")
            reasoning = (reasoning or "") + text
        elif kind == "message":
            role = item.get("role")
            if role == "assistant":
                flush(); start()
                parts = content(item.get("content"))
                if isinstance(parts, list):
                    if any(part.get("type") != "text" for part in parts):
                        raise RuntimeError("DEEPSEEK_CHAT_ASSISTANT_IMAGE_UNSUPPORTED")
                    parts = "".join(part["text"] for part in parts)
                assistant["content"] = parts
            elif role in {"user", "system", "developer"}:
                flush()
                if reasoning is not None or pending:
                    raise RuntimeError("DEEPSEEK_CHAT_HISTORY_UNPAIRED")
                rows.append({"role": "system" if role == "developer" else role, "content": content(item.get("content", ""))})
            else:
                raise RuntimeError("DEEPSEEK_CHAT_HISTORY_ROLE_UNSUPPORTED")
        elif kind == "function_call":
            call_id = item.get("call_id")
            if not call_id or call_id in seen:
                raise RuntimeError("DEEPSEEK_CHAT_HISTORY_UNPAIRED")
            seen.add(call_id); pending.add(call_id); start()
            assistant.setdefault("tool_calls", []).append({"id": call_id, "type": "function", "function": {
                "name": item["name"], "arguments": item["arguments"]}})
        elif kind in {"function_call_output", "function_call_result"}:
            flush()
            call_id = item.get("call_id")
            if call_id not in pending:
                raise RuntimeError("DEEPSEEK_CHAT_HISTORY_UNPAIRED")
            pending.remove(call_id)
            output = item.get("output", "")
            rows.append({"role": "tool", "tool_call_id": call_id, "content": output if isinstance(output, str) else json.dumps(output, ensure_ascii=False)})
        else:
            raise RuntimeError("DEEPSEEK_CHAT_HISTORY_ITEM_UNSUPPORTED")
    flush()
    if pending or reasoning is not None:
        raise RuntimeError("DEEPSEEK_CHAT_HISTORY_UNPAIRED")
    return rows


class ChatStream:
    def __init__(self, maximum):
        self.maximum, self.size = maximum, 0
        self.text, self.reasoning, self.calls = [], [], {}
        self.finish = None
        self.response_id = None
        self.usage = {}

    def add(self, raw):
        chunk = plain(raw)
        identity = chunk.get("id")
        if identity:
            if not isinstance(identity, str) or len(identity) > 512 or self.response_id is not None and self.response_id != identity:
                raise RuntimeError("DEEPSEEK_CHAT_RESPONSE_ID_CHANGED")
            self.response_id = identity
        self.size += len(json.dumps(chunk, ensure_ascii=False, allow_nan=False).encode("utf-8"))
        if self.size > self.maximum * 8:
            raise RuntimeError("DEEPSEEK_CHAT_STREAM_LIMIT")
        if chunk.get("usage") is not None:
            self.usage = chunk["usage"]
        choices = chunk.get("choices", [])
        if not isinstance(choices, list) or len(choices) > 1:
            raise RuntimeError("DEEPSEEK_CHAT_STREAM_INVALID")
        if not choices:
            return
        choice = choices[0]
        if choice.get("index", 0) != 0:
            raise RuntimeError("DEEPSEEK_CHAT_STREAM_INVALID")
        delta = choice.get("delta") or {}
        if self.finish is not None:
            raise RuntimeError("DEEPSEEK_CHAT_DUPLICATE_TERMINAL")
        for field, target in (("content", self.text), ("reasoning_content", self.reasoning)):
            value = delta.get(field)
            if value is not None:
                if not isinstance(value, str):
                    raise RuntimeError("DEEPSEEK_CHAT_STREAM_INVALID")
                target.append(value)
        for call in delta.get("tool_calls") or []:
            index = call.get("index")
            if type(index) is not int or not 0 <= index < 16:
                raise RuntimeError("DEEPSEEK_CHAT_TOOL_LIMIT")
            current = self.calls.setdefault(index, {"id": None, "name": "", "arguments": ""})
            if call.get("id"):
                if current["id"] is not None and current["id"] != call["id"]:
                    raise RuntimeError("DEEPSEEK_CHAT_TOOL_ID_CHANGED")
                current["id"] = call["id"]
            if call.get("type", "function") != "function":
                raise RuntimeError("DEEPSEEK_CHAT_TOOL_UNSUPPORTED")
            function = call.get("function") or {}
            for field in ("name", "arguments"):
                value = function.get(field)
                if value is not None:
                    if not isinstance(value, str):
                        raise RuntimeError("DEEPSEEK_CHAT_STREAM_INVALID")
                    current[field] += value
        if choice.get("finish_reason") is not None:
            self.finish = choice["finish_reason"]

    def complete(self):
        if self.finish not in {"stop", "tool_calls"}:
            raise RuntimeError("DEEPSEEK_CHAT_STREAM_INCOMPLETE")
        if bool(self.calls) != (self.finish == "tool_calls"):
            raise RuntimeError("DEEPSEEK_CHAT_STREAM_INVALID")
        ids = set()
        for call in self.calls.values():
            if not call["id"] or call["id"] in ids or not call["name"]:
                raise RuntimeError("DEEPSEEK_CHAT_TOOL_INVALID")
            ids.add(call["id"])
        result = {"reasoning": "".join(self.reasoning), "text": "".join(self.text),
                  "calls": [self.calls[index] for index in sorted(self.calls)]}
        if len(json.dumps(result, ensure_ascii=False).encode("utf-8")) > self.maximum:
            raise RuntimeError("DEEPSEEK_CHAT_OUTPUT_LIMIT")
        if not result["text"] and not result["calls"]:
            raise RuntimeError("DEEPSEEK_CHAT_OUTPUT_EMPTY")
        return result


def create_model(client, model, config, attempt):
    from agents.models.interface import Model
    from agents.items import ModelResponse
    from agents.usage import Usage
    from openai.types.responses import ResponseReasoningItem, ResponseFunctionToolCall, ResponseOutputMessage, ResponseOutputText
    probe = ResponseReasoningItem(type="reasoning", id="rs_contract", summary=[],
        content=[{"type": "reasoning_text", "text": "history-contract"}])
    contract = ModelResponse(output=[probe], usage=Usage(requests=0, input_tokens=0, output_tokens=0, total_tokens=0), response_id="contract")
    replay = getattr(contract, "to_input_items", None)
    rows = replay() if callable(replay) else []
    parts = rows[0].get("content", []) if rows else []
    if not any(part.get("type") == "reasoning_text" and part.get("text") == "history-contract" for part in parts):
        raise RuntimeError("DEEPSEEK_CHAT_SDK_REASONING_HISTORY_UNSUPPORTED")

    class DeepSeekChatModel(Model):
        last_usage = None

        async def get_response(self, system_instructions, input, model_settings, tools, output_schema, handoffs, tracing, **kwargs):
            attempt.check()
            if kwargs.get("previous_response_id") or kwargs.get("conversation_id") or handoffs or output_schema is not None:
                raise RuntimeError("DEEPSEEK_CHAT_FEATURE_UNSUPPORTED")
            declarations = []
            for tool in tools:
                if not hasattr(tool, "params_json_schema"):
                    raise RuntimeError("DEEPSEEK_CHAT_TOOL_UNSUPPORTED")
                declarations.append({"type": "function", "function": {"name": tool.name,
                    "description": tool.description, "parameters": tool.params_json_schema, "strict": False}})
            extra = model_settings.extra_args or {}
            effort = extra.get("reasoning_effort")
            if effort is not None and effort not in {"none", "low", "high", "max"}:
                raise RuntimeError("DEEPSEEK_REASONING_EFFORT_INVALID")
            params = {"model": model, "messages": messages(system_instructions, input), "stream": True,
                      "stream_options": {"include_usage": True}}
            if effort is not None:
                params["reasoning_effort"] = effort
                params["extra_body"] = {"thinking": {"type": "disabled" if effort == "none" else "enabled"}}
            if declarations:
                params["tools"] = declarations
            choice = model_settings.tool_choice
            if isinstance(choice, str) and choice in {"none", "auto", "required"}:
                params["tool_choice"] = choice
            elif choice is not None:
                raise RuntimeError("DEEPSEEK_CHAT_TOOL_CHOICE_UNSUPPORTED")
            if model_settings.max_tokens:
                params["max_tokens"] = model_settings.max_tokens
            stream = await client.chat.completions.create(**params)
            collected = ChatStream(config.max_input_bytes)
            try:
                async for chunk in stream:
                    attempt.check(); collected.add(chunk)
            finally:
                await stream.close()
            result = collected.complete()
            raw = collected.usage
            cached = raw.get("prompt_cache_hit_tokens", (raw.get("prompt_tokens_details") or {}).get("cached_tokens"))
            self.last_usage = {"input_tokens": raw.get("prompt_tokens"), "output_tokens": raw.get("completion_tokens"),
                               "input_tokens_details": {"cached_tokens": cached}}
            incoming, outgoing = raw.get("prompt_tokens"), raw.get("completion_tokens")
            known = all(type(value) is int and value >= 0 for value in (incoming, outgoing))
            # Zero is only an SDK counter placeholder; published usage comes from last_usage.
            usage = Usage(requests=1, input_tokens=incoming if known else 0, output_tokens=outgoing if known else 0,
                          total_tokens=incoming + outgoing if known else 0)
            output = [ResponseReasoningItem(type="reasoning", id="rs_" + uuid.uuid4().hex, summary=[],
                content=[{"type": "reasoning_text", "text": result["reasoning"]}])]
            if result["text"]:
                output.append(ResponseOutputMessage(type="message", id="msg_" + uuid.uuid4().hex, role="assistant", status="completed",
                    content=[ResponseOutputText(type="output_text", text=result["text"], annotations=[])]))
            output.extend(ResponseFunctionToolCall(type="function_call", id="fc_" + uuid.uuid4().hex,
                call_id=call["id"], name=call["name"], arguments=call["arguments"], status="completed") for call in result["calls"])
            return ModelResponse(output=output, usage=usage, response_id=collected.response_id)

        def stream_response(self, *args, **kwargs):
            raise RuntimeError("DEEPSEEK_CHAT_USE_ROUND_RUNNER")

    return DeepSeekChatModel()
