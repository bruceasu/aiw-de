#!/usr/bin/env python3
"""Responses API sequential function-call check with sanitized HTTP traces."""
from __future__ import annotations

import asyncio
import json
import os
import sys

import httpx
from agents import Agent, ModelSettings, OpenAIProvider, Runner, function_tool, set_tracing_disabled
from openai import AsyncOpenAI


def _input_trace(item: object) -> dict[str, object]:
    if not isinstance(item, dict):
        return {"type": type(item).__name__}
    result: dict[str, object] = {"type": item.get("type", "message")}
    for key in ("role", "call_id", "name"):
        if isinstance(item.get(key), str):
            result[key] = item[key]
    if item.get("type") == "function_call" and isinstance(item.get("arguments"), str):
        result["arguments"] = item["arguments"]
    if item.get("type") == "function_call_output" and isinstance(item.get("output"), str):
        result["output_length"] = len(item["output"])
    return result


def _response_trace(item: object) -> dict[str, object]:
    if not isinstance(item, dict):
        return {"type": type(item).__name__}
    result: dict[str, object] = {"type": item.get("type", "unknown")}
    for key in ("call_id", "name"):
        if isinstance(item.get(key), str):
            result[key] = item[key]
    if item.get("type") == "function_call" and isinstance(item.get("arguments"), str):
        result["arguments"] = item["arguments"]
    if item.get("type") == "message":
        content = item.get("content", [])
        text = "".join(part.get("text", "") for part in content
                       if isinstance(part, dict) and isinstance(part.get("text"), str))
        result["text_preview"] = text[:120]
    return result


async def main() -> int:
    api_key = os.getenv("OPENAI_API_KEY", "").strip()
    if not api_key:
        print("OPENAI_API_KEY is required", file=sys.stderr)
        return 2

    deepseek_model = os.getenv("DEEPSEEK_MODEL", "").strip()
    model_name = deepseek_model or os.getenv("AIW_OPENAI_MODEL", "gpt-6-luna").strip()
    model_source = "DEEPSEEK_MODEL" if deepseek_model else "AIW_OPENAI_MODEL/default"
    calls: list[tuple[int, int]] = []
    trace_number = 0

    async def trace_request(request: httpx.Request) -> None:
        nonlocal trace_number
        if not request.url.path.endswith("/responses"):
            return
        try:
            await request.aread()
            body = json.loads(request.content)
            inputs = body.get("input", [])
            if not isinstance(inputs, list):
                inputs = [{"type": "text"}]
            trace_number += 1
            summary = {
                "event": "request",
                "turn": trace_number,
                "path": request.url.path,
                "parallel_tool_calls": body.get("parallel_tool_calls"),
                "tool_choice": body.get("tool_choice"),
                "tools": [tool.get("name") for tool in body.get("tools", [])
                          if isinstance(tool, dict)],
                "input": [_input_trace(item) for item in inputs],
            }
            print("trace=" + json.dumps(summary, ensure_ascii=False), flush=True)
        except Exception as error:
            print(f"trace=request-summary-error type={type(error).__name__}", flush=True)

    async def trace_response(response: httpx.Response) -> None:
        if not response.request.url.path.endswith("/responses"):
            return
        try:
            await response.aread()
            body = json.loads(response.content)
            output = body.get("output", [])
            if not isinstance(output, list):
                output = []
            error = body.get("error")
            summary = {
                "event": "response",
                "turn": trace_number,
                "http_status": response.status_code,
                "response_status": body.get("status"),
                "output": [_response_trace(item) for item in output],
                "error_code": error.get("code") if isinstance(error, dict) else None,
            }
            print("trace=" + json.dumps(summary, ensure_ascii=False), flush=True)
        except Exception as error:
            print(f"trace=response-summary-error type={type(error).__name__}", flush=True)

    http_client = httpx.AsyncClient(
        timeout=httpx.Timeout(120.0, connect=5.0),
        follow_redirects=False,
        event_hooks={"request": [trace_request], "response": [trace_response]},
    )
    client = AsyncOpenAI(
        api_key=api_key,
        base_url=os.getenv("OPENAI_BASE_URL") or None,
        max_retries=0,
        http_client=http_client,
    )

    @function_tool
    def add_numbers(a: int, b: int) -> int:
        """Add two integers and return their sum."""
        calls.append((a, b))
        return a + b

    try:
        set_tracing_disabled(True)
        schema = add_numbers.params_json_schema
        properties = schema.get("properties", {})
        has_title_annotations = isinstance(schema.get("title"), str) and set(properties) == {"a", "b"} and all(
            isinstance(parameter.get("title"), str) for parameter in properties.values()
        )
        provider = OpenAIProvider(openai_client=client, use_responses=True)
        agent = Agent(
            name="tool-call-compatibility-check",
            instructions=(
                "You must call add_numbers exactly once with a=19 and b=23. "
                "After receiving its result, reply exactly: TOOL_CALL_OK:42"
            ),
            model=provider.get_model(model_name),
            tools=[add_numbers],
            model_settings=ModelSettings(parallel_tool_calls=False),
        )
        result = await Runner.run(agent, "Perform the required calculation using the provided tool.", max_turns=3)
        output = str(result.final_output).strip()
        passed = has_title_annotations and calls == [(19, 23)] and output == "TOOL_CALL_OK:42"
        print(f"model_source={model_source}")
        print(f"title_annotations={str(has_title_annotations).lower()}")
        print(f"tool_calls={json.dumps(calls)}")
        print(f"output={output}")
        print(f"status={'ok' if passed else 'failed'}")
        return 0 if passed else 1
    except Exception as error:
        print(f"tool_calls={json.dumps(calls)}", file=sys.stderr)
        print(f"status=error error_type={type(error).__name__}", file=sys.stderr)
        return 1
    finally:
        await client.close()


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
