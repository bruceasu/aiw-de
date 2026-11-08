"""SDK-owned tool loop with host-owned authority, history and completed responses."""
from __future__ import annotations

import asyncio
import json
import os
import math
import time
import urllib.error
import urllib.request
import uuid
import inspect
import importlib.metadata
import hashlib

from secretary_runs import RunYield
from secretary_credentials import ModelAuthRequired


MAX_INPUT = 240_000
MAX_OUTPUT = 32_000


def normalized(value):
    if hasattr(value, "model_dump"):
        return value.model_dump(exclude_none=True)
    if isinstance(value, list):
        return [normalized(v) for v in value]
    if isinstance(value, dict):
        return {k: normalized(v) for k, v in value.items()}
    return value


def object_json(text):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError("模型工具参数有重复字段。")
            result[key] = value
        return result
    def nonfinite(_value):
        raise ValueError("模型工具参数不能包含非有限数值。")
    def finite(value):
        parsed = float(value)
        if not math.isfinite(parsed):
            raise ValueError("工具参数数值超出有限范围。")
        return parsed
    value = json.loads(text, object_pairs_hook=pairs, parse_constant=nonfinite, parse_float=finite)
    if not isinstance(value, dict):
        raise ValueError("模型工具参数必须是对象。")
    return value


def matches(value, schema):
    kind = schema.get("type")
    if "enum" in schema and value not in schema["enum"]:
        return False
    if kind == "object":
        return (isinstance(value, dict) and not set(schema.get("required", [])) - set(value)
                and (schema.get("additionalProperties", True) or not set(value) - set(schema.get("properties", {})))
                and all(k not in schema.get("properties", {}) or matches(v, schema["properties"][k]) for k, v in value.items()))
    if kind == "array":
        return isinstance(value, list) and len(value) <= schema.get("maxItems", 100) and all(matches(v, schema.get("items", {})) for v in value)
    if kind == "string":
        return isinstance(value, str) and len(value) <= schema.get("maxLength", 100000)
    if kind == "integer":
        return type(value) is int and schema.get("minimum", 0) <= value <= schema.get("maximum", 1000000)
    if kind == "boolean":
        return type(value) is bool
    return kind is None


def definitions(knowledge=False):
    text = {"type": "string", "maxLength": 100000}
    short = {"type": "string", "maxLength": 4096}
    number = {"type": "integer", "minimum": 0, "maximum": 1000000}
    obj = {"type": "object"}
    declarations = {
        "search": ({"query": short}, ["query"]), "read_url": ({"url": short}, ["url"]),
        "read_file": ({"path": short, "offset": number, "limit": number}, ["path"]),
        "create_file": ({"path": short, "text": text}, ["path", "text"]),
        "prepare_edit": ({"path": short, "expectedVersion": short, "edits": {"type": "array", "maxItems": 32,
            "items": {"type": "object", "properties": {"old": text, "new": text}, "required": ["old", "new"], "additionalProperties": False}}}, ["path", "expectedVersion", "edits"]),
        "apply_edit": ({"editId": short, "expectedVersion": short}, ["editId", "expectedVersion"]),
        "restore_file": ({"operationId": short, "expectedCurrentVersion": short}, ["operationId", "expectedCurrentVersion"]),
        "list_skills": ({}, []), "read_skill": ({"name": short, "path": short, "offset": number, "limit": number}, ["name", "path"]),
        "professional_options": ({}, []), "platform_permissions": ({}, []),
        "platform_questions": ({"permissionId": short, "planId": short}, ["permissionId", "planId"]),
        "platform_action": ({"permissionId": short, "targetId": short, "parameters": obj}, ["permissionId", "targetId", "parameters"]),
        "platform_answer": ({"permissionId": short, "targetId": short, "questionId": short, "requestVersion": number, "answer": text},
            ["permissionId", "targetId", "questionId", "requestVersion", "answer"]),
        "create_work": ({"title": short, "description": text}, ["title", "description"]),
        "delegate_task": ({key: short if key != "instruction" else text for key in ("planId", "repositoryRef", "baseRevision", "role", "aiProfileId", "instruction")},
            ["planId", "repositoryRef", "baseRevision", "role", "aiProfileId", "instruction"]),
        "read_run_source": ({"sourceId": short, "offset": number}, ["sourceId"]),
    }
    if knowledge:
        declarations.update({
            "knowledge_search": ({"query": short, "limit": number}, ["query"]),
            "knowledge_read": ({"documentId": short, "revision": short, "cursor": short}, ["documentId", "revision"]),
            "knowledge_save_note": ({"title": short, "content": text, "tags": {"type": "array", "items": short, "maxItems": 20},
                "citations": {"type": "array", "items": obj, "maxItems": 20}, "collectionId": short}, ["title", "content"]),
            "knowledge_note_status": ({"requestId": short}, ["requestId"]),
        })
    return {name: {"type": "object", "properties": props, "required": required, "additionalProperties": False}
            for name, (props, required) in declarations.items()}


def paired_history(history, missing_results=None):
    calls = {r["call_id"] for r in history if r.get("type") == "function_call"}
    results = {r["call_id"] for r in history if r.get("type") == "function_call_output"}
    return history + [{"type": "function_call_output", "call_id": call,
        "output": json.dumps((missing_results or {}).get(call, {"status": "NOT_EXECUTED", "note": "Run stopped before this call; inspect host receipts before requesting any effect again."}), ensure_ascii=False)}
        for call in sorted(calls - results)]


def project(history, run_id):
    result = []
    for item in normalized(history):
        item = dict(item)
        if item.get("type") == "function_call_output" and isinstance(item.get("output"), str) and len(item["output"]) > 8000:
            item["output"] = json.dumps({"truncated": True, "preview": item["output"][:2000],
                "sourceId": run_id + ":source:" + item["call_id"],
                "note": "The complete tool result is private host data. Use read_run_source only if needed."}, ensure_ascii=False)
        result.append(item)
    if len(json.dumps(result, ensure_ascii=False).encode()) > MAX_INPUT:
        raise ValueError("上下文超过本次上限，原始历史已保留，请缩小请求范围。")
    return result


def response_events(response, check):
    """Bound the full stream deadline, including peers that drip partial lines."""
    buffer, data, wire, deadline = b"", [], 0, time.monotonic() + 120
    while True:
        check()
        if time.monotonic() >= deadline:
            raise ValueError("模型响应超过本次期限。")
        chunk = response.read1(8192)
        if not chunk:
            raise ValueError("模型响应未完整结束，没有执行其中的工具请求。")
        wire += len(chunk)
        if wire > 8 * 1024 * 1024:
            raise ValueError("模型响应超过上限。")
        buffer += chunk
        if len(buffer) > 262144:
            raise ValueError("模型事件行超过上限。")
        while b"\n" in buffer:
            line, buffer = buffer.split(b"\n", 1)
            line = line.rstrip(b"\r")
            if not line:
                if data:
                    try:
                        event = object_json("\n".join(data))
                    except (ValueError, UnicodeError):
                        raise ValueError("模型事件格式无效。") from None
                    data = []
                    yield event
            elif line.startswith(b"data:"):
                data.append(line[5:].decode("utf-8").strip())
                if sum(map(len, data)) > 262144:
                    raise ValueError("模型事件超过上限。")


def oauth_text(token, model, instruction, history, check, on_completed=None, reasoning_effort=""):
    """Public Responses text transport, not a second agent loop or native tools."""
    from secretary_oauth import NoRedirect
    payload = {"model": model, "instructions": instruction, "input": [{"role": "user", "content": json.dumps(history, ensure_ascii=False)}],
               "store": False, "stream": True}
    if reasoning_effort and reasoning_effort != "none":
        payload["reasoning"] = {"effort": reasoning_effort}
    raw = json.dumps(payload, ensure_ascii=False).encode()
    if len(raw) > MAX_INPUT:
        raise ValueError("模型输入超过上限。")
    request = urllib.request.Request("https://api.openai.com/v1/responses", raw,
        headers={"Authorization": "Bearer " + token, "Content-Type": "application/json", "Accept": "text/event-stream"})
    try:
        with urllib.request.build_opener(NoRedirect()).open(request, timeout=15) as response:
            if response.status != 200 or "text/event-stream" not in response.headers.get("Content-Type", ""):
                raise ValueError("模型没有返回有效事件流。")
            for event in response_events(response, check):
                if event.get("type") in {"response.failed", "response.incomplete", "error"}:
                    raise ValueError("模型响应失败，没有执行其中的工具请求。")
                if event.get("type") == "response.completed":
                    actual = event.get("response", {})
                    output = actual.get("output", [])
                    if actual.get("status") != "completed" or not isinstance(output, list) or any(item.get("type") not in {"message", "reasoning"} for item in output):
                        raise ValueError("模型响应终态不受支持。")
                    text = "".join(part.get("text", "") for item in output if item.get("type") == "message" for part in item.get("content", []) if part.get("type") == "output_text")
                    if not text.strip() or len(text.encode()) > MAX_OUTPUT:
                        raise ValueError("模型没有返回有效的有限文本。")
                    if on_completed is not None:
                        on_completed(actual)
                    return text, actual.get("usage", {}), actual.get("id")
    except urllib.error.HTTPError as error:
        status = error.code
        error.close()
        if status in {401, 403}:
            raise ModelAuthRequired("ChatGPT 登录或组织许可被拒绝，请检查外部访问和套餐使用权限，再重新登录；未切换账户。") from None
        raise ValueError("模型请求被拒绝（HTTP " + str(status) + "）。请检查登录、组织许可、模型和额度；未切换账户。") from None


def run(settings, instructions, context, execute, journal, identity, after_tool=None):
    return asyncio.run(_run(settings, instructions, context, execute, journal, identity, after_tool))


async def _run(settings, instructions, context, execute, journal, identity, after_tool):
    os.environ.setdefault("OPENAI_AGENTS_DONT_LOG_MODEL_DATA", "1")
    os.environ.setdefault("OPENAI_AGENTS_DONT_LOG_TOOL_DATA", "1")
    try:
        from agents import Agent, FunctionTool, ModelSettings, RunConfig, Runner, set_tracing_disabled
        from agents.models.interface import Model
        from agents.items import ModelResponse
        from agents.usage import Usage
        from openai import AsyncOpenAI, DefaultAsyncHttpxClient
        from openai.types.responses import ResponseFunctionToolCall, ResponseOutputMessage, ResponseOutputText
    except ImportError:
        raise ValueError("秘书的 SDK 执行需要安装 requirements.txt 中的可选依赖；未自动改用 Codex。") from None
    set_tracing_disabled(True)
    required = ((FunctionTool, {"name", "params_json_schema", "on_invoke_tool", "strict_json_schema"}),
                (ModelResponse, {"output", "usage", "response_id"}), (Runner.run, {"run_config", "max_turns"}))
    if any(not names.issubset(inspect.signature(target).parameters) for target, names in required):
        raise ValueError("当前 Agents SDK 不具备秘书需要的工具/回合接口，请检查 Python 环境及依赖版本；未开始模型调用。")
    config = dict(settings.value)
    try:
        sdk_version = importlib.metadata.version("openai-agents")
    except importlib.metadata.PackageNotFoundError:
        sdk_version = "unknown"
    journal.save(journal.get(identity), sdkVersion=sdk_version, historyFormat="responses-items-v1")
    schemas = definitions(settings.value.get("knowledgeEnabled") and settings.knowledge_available())
    capabilities = set(settings.value.get("knowledgeCapabilities", []))
    for name, capability in {"knowledge_search": "search", "knowledge_read": "read", "knowledge_save_note": "create_note", "knowledge_note_status": "creation_status"}.items():
        if capability not in capabilities:
            schemas.pop(name, None)
    pending, client, client_binding = [], None, None

    def save_history(history):
        row = journal.get(identity)
        journal.save(row, history=history)

    class GuardedModel(Model):
        async def get_response(self, system_instructions, input, model_settings, tools, output_schema, handoffs, tracing, **kwargs):
            nonlocal client, client_binding
            journal.check(identity)
            if kwargs.get("previous_response_id") or kwargs.get("conversation_id"):
                raise ValueError("本地会话不能同时使用服务端历史续接。")
            journal.count(identity, "modelCalls", 12)
            history = normalized(input) if not isinstance(input, str) else [{"role": "user", "content": input}]
            save_history(history)
            history = project(history, identity)
            if len(json.dumps({"instructions": system_instructions, "input": history, "tools": schemas}, ensure_ascii=False).encode()) > MAX_INPUT:
                raise ValueError("模型指令、工具和历史的总输入超过本次上限。")
            journal.event(identity, "model-start")
            if config.get("authMode") == "chatgpt":
                access = settings.connections.access(config["connectionId"], config["connectionVersion"], "chatgpt")
                header = (system_instructions or "") + "\nThe model channel has no native tools. Return one JSON object: "
                header += '{"action":"tool","name":"declared tool","arguments":{}} or {"action":"finish","text":"final answer","completed":true}. '
                header += "Tool schemas: " + json.dumps(schemas, ensure_ascii=False)
                def completed(actual):
                    journal.store.put("runtime", identity + ":wire:" + uuid.uuid4().hex,
                        {"kind": "SECRETARY_WIRE", "runId": identity, "response": actual}, 0)
                text, usage, response_id = await asyncio.to_thread(oauth_text, access["token"], config["model"], header, history,
                    lambda: journal.check(identity), completed, config.get("effort", ""))
                action = object_json(text)
                if action.get("action") == "tool":
                    name, arguments = action.get("name"), action.get("arguments")
                    if set(action) != {"action", "name", "arguments"} or name not in schemas or not matches(arguments, schemas[name]):
                        raise ValueError("模型工具请求不符合声明接口，没有执行。")
                    output = [ResponseFunctionToolCall(type="function_call", id="fc_" + uuid.uuid4().hex, call_id="call_" + uuid.uuid4().hex,
                        name=name, arguments=json.dumps(arguments, ensure_ascii=False), status="completed")]
                elif action.get("action") == "finish" and isinstance(action.get("text"), str) and type(action.get("completed")) is bool:
                    output = [ResponseOutputMessage(type="message", id="msg_" + uuid.uuid4().hex, role="assistant", status="completed",
                        content=[ResponseOutputText(type="output_text", text=json.dumps(action, ensure_ascii=False), annotations=[])])]
                else:
                    raise ValueError("模型没有返回有效的完成结果或工具请求。")
                incoming, outgoing = usage.get("input_tokens"), usage.get("output_tokens")
                known = type(incoming) is int and type(outgoing) is int and incoming >= 0 and outgoing >= 0
                if not known:
                    incoming, outgoing = 0, 0  # SDK accounting placeholder, never published as measured usage.
                response = ModelResponse(output=output, usage=Usage(requests=1, input_tokens=incoming, output_tokens=outgoing, total_tokens=incoming + outgoing), response_id=response_id)
                journal.event(identity, "model-end", status="completed", usageKnown=known,
                    **({"inputTokens": incoming, "outputTokens": outgoing} if known else {}))
            else:
                if config.get("connectionId"):
                    access = settings.connections.access(config["connectionId"], config["connectionVersion"], "api-key")
                    key, base = access["token"], access["baseUrl"]
                else:
                    key = config.get("key") or (os.environ.get("OPENAI_API_KEY", "").strip() if config.get("credentialSource") == "environment" else None)
                    base = config.get("endpoint")
                if not key:
                    raise ValueError("请明确配置 API Key 连接。")
                binding = (hashlib.sha256(key.encode()).hexdigest(), base)
                if client is None:
                    client = AsyncOpenAI(api_key=key, base_url=base or "https://api.openai.com/v1", timeout=120, max_retries=0,
                        http_client=DefaultAsyncHttpxClient(follow_redirects=False))
                    client_binding = binding
                elif binding != client_binding:
                    raise ModelAuthRequired("运行中的连接凭据发生变化，未自动替换；请重新安排。")
                try:
                    parameters = {"model": config["model"], "instructions": system_instructions, "input": history,
                        "tools": [{"type": "function", "name": name, "description": "Use the declared secretary tool within the current request.",
                                   "parameters": schema, "strict": False} for name, schema in schemas.items()],
                        "store": False, "stream": False, "parallel_tool_calls": False}
                    if config.get("effort") and config["effort"] != "none":
                        parameters["reasoning"] = {"effort": config["effort"]}
                    actual = await asyncio.wait_for(client.responses.create(**parameters), timeout=120)
                except Exception as error:
                    status = getattr(error, "status_code", None)
                    if status in {401, 403}:
                        raise ModelAuthRequired("API Key 或模型权限被拒绝，请检查所选连接；未切换账户。") from None
                    if status == 429:
                        raise ValueError("模型额度或限流阻止了请求，请检查账户用量；未自动重试或更换付费来源。") from None
                    raise ValueError("模型请求未完成，请检查所选模型、服务和 SDK 配置；没有执行未完成响应中的工具。") from None
                if getattr(actual, "status", None) != "completed":
                    raise ValueError("模型响应整体未完成，没有执行其中的工具请求。")
                usage = getattr(actual, "usage", None)
                incoming, outgoing = getattr(usage, "input_tokens", None), getattr(usage, "output_tokens", None)
                known = type(incoming) is int and incoming >= 0 and type(outgoing) is int and outgoing >= 0
                response = ModelResponse(output=actual.output, response_id=actual.id, usage=Usage(requests=1,
                    input_tokens=incoming if known else 0, output_tokens=outgoing if known else 0,
                    total_tokens=incoming + outgoing if known else 0))
                journal.event(identity, "model-end", status="completed", usageKnown=known,
                    **({"inputTokens": incoming, "outputTokens": outgoing} if known else {}))
            # SDK receives actionable output only after a complete model result.
            journal.check(identity)
            output = normalized(response.output)
            if not isinstance(output, list):
                raise ValueError("模型输出无效。")
            if sum(item.get("type") == "function_call" for item in output) > 1:
                raise ValueError("当前秘书每回合只接受一个工具调用，以保留可靠的暂停和确认边界；本批工具未执行。")
            call_ids = {item.get("call_id") for item in history if item.get("type") == "function_call"}
            for item in output:
                if item.get("type") not in {"message", "reasoning", "function_call"}:
                    raise ValueError("模型返回了未声明的工具或输出类型。")
                if item.get("status") not in {None, "completed"}:
                    raise ValueError("模型返回了未完成的输出，没有执行工具。")
                if item.get("type") == "function_call":
                    name = item.get("name")
                    args = object_json(item.get("arguments", ""))
                    call_id = item.get("call_id")
                    if name not in schemas or not matches(args, schemas[name]) or not isinstance(call_id, str) or not 1 <= len(call_id) <= 200 or call_id in call_ids:
                        raise ValueError("模型工具参数无效，没有执行。")
                    call_ids.add(call_id)
                    pending.append({**item, "parsed": args})
            save_history(normalized(input) + output if not isinstance(input, str) else history + output)
            return response

        async def stream_response(self, *args, **kwargs):
            raise ValueError("秘书当前 SDK 模型适配使用完整回合，尚未开放原始流执行。")
            yield  # An async iterator is required by the SDK Model interface.

    def make_tool(name, schema):
        async def invoke(_context, raw):
            journal.check(identity)
            arguments = object_json(raw)
            if not matches(arguments, schema):
                raise ValueError("工具参数与接口不一致。")
            call = next((c for c in pending if c["name"] == name and c["parsed"] == arguments), None)
            if call is None:
                raise ValueError("工具调用没有完整模型响应依据。")
            pending.remove(call)
            action = {"action": name, **arguments}
            if name == "read_run_source":
                journal.event(identity, "tool-start", tool=name)
                source_id = arguments["sourceId"]
                if not source_id.startswith(identity + ":source:"):
                    raise ValueError("只能读取本次运行的工具资料。")
                source = journal.store.get("runtime", source_id)
                if not source or source.get("kind") != "SECRETARY_SOURCE":
                    raise ValueError("找不到原始工具资料。")
                text = json.dumps(source["result"], ensure_ascii=False)
                offset = arguments.get("offset", 0)
                result = {"text": text[offset:offset + 8000], "truncated": len(text) > offset + 8000}
                journal.count(identity, "toolCalls", 12)
                journal.event(identity, "tool-end", tool=name, status="completed")
            else:
                result = journal.invoke(identity, action, execute, call_id=call["call_id"])
            text = json.dumps(result, ensure_ascii=False, allow_nan=False)
            source_id = identity + ":source:" + call["call_id"]
            journal.store.put("runtime", source_id, {"kind": "SECRETARY_SOURCE", "runId": identity, "result": result}, 0)
            history = journal.get(identity).get("history", [])
            save_history(history + [{"type": "function_call_output", "call_id": call["call_id"], "output": text}])
            if after_tool is not None:
                after_tool(action, result)
            return text
        return FunctionTool(name=name, description="Use this declared secretary tool within the current user's request and existing authority.",
                            params_json_schema=schema, on_invoke_tool=invoke, strict_json_schema=False)

    row = journal.get(identity)
    history = paired_history(row.get("history", []))
    history.append({"role": "user", "content": json.dumps(context, ensure_ascii=False)})
    if row.get("notes"):
        history.append({"role": "user", "content": "Current user updates, in order:\n" + "\n".join(row["notes"])})
    preferences = {"parallel_tool_calls": False, "store": False}
    if config.get("effort") and config["effort"] != "none":
        preferences["reasoning"] = {"effort": config["effort"]}
    agent = Agent(name="Personal Secretary", instructions=instructions, model=GuardedModel(), tools=[make_tool(n, s) for n, s in schemas.items()],
        model_settings=ModelSettings(**preferences))
    try:
        result = await Runner.run(agent, project(history, identity), run_config=RunConfig(tracing_disabled=True), max_turns=12)
        journal.check(identity)
        if not isinstance(result.final_output, str) or not result.final_output.strip() or len(result.final_output.encode()) > MAX_OUTPUT:
            raise ValueError("模型未返回有效的有限完成结果。")
        return result.final_output
    finally:
        outcomes = {}
        for receipt in journal.records("SECRETARY_TOOL", identity):
            if receipt.get("callId"):
                outcomes[receipt["callId"]] = receipt.get("result") if receipt.get("state") == "SUCCEEDED" else {
                    "status": "UNKNOWN" if receipt.get("state") in {"UNKNOWN", "STARTED"} else "WAITING_USER" if receipt.get("state") == "WAITING" else "NOT_EXECUTED",
                    "note": "This is the durable host receipt; do not replay an unknown effect."}
        save_history(paired_history(journal.get(identity).get("history", []), outcomes))
        if client is not None:
            await client.close()
