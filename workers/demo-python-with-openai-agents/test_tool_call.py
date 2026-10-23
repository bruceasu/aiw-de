#!/usr/bin/env python3
"""Acceptance check for Responses API function calling through Agents SDK."""
from __future__ import annotations

import asyncio
import os
import sys

from agents import Agent, OpenAIProvider, Runner, function_tool, set_tracing_disabled
from openai import AsyncOpenAI


async def main() -> int:
    api_key = os.getenv("OPENAI_API_KEY", "").strip()
    if not api_key:
        print("OPENAI_API_KEY is required", file=sys.stderr)
        return 2

    model_name = os.getenv("AIW_OPENAI_MODEL", "gpt-6-luna").strip()
    client = AsyncOpenAI(
        api_key=api_key,
        base_url=os.getenv("OPENAI_BASE_URL") or None,
        max_retries=0,
    )
    calls: list[tuple[int, int]] = []

    @function_tool
    def add_numbers(a: int, b: int) -> int:
        """Add two integers and return their sum."""
        calls.append((a, b))
        return a + b

    try:
        set_tracing_disabled(True)
        provider = OpenAIProvider(openai_client=client, use_responses=True)
        agent = Agent(
            name="tool-call-compatibility-check",
            instructions=(
                "You must call add_numbers exactly once with a=19 and b=23. "
                "After receiving its result, reply exactly: TOOL_CALL_OK:42"
            ),
            model=provider.get_model(model_name),
            tools=[add_numbers],
        )
        result = await Runner.run(
            agent,
            "Perform the required calculation using the provided tool.",
            max_turns=3,
        )
        output = str(result.final_output).strip()
        passed = calls == [(19, 23)] and output == "TOOL_CALL_OK:42"
        print(f"model={model_name}")
        print(f"tool_calls={len(calls)}")
        print(f"output={output}")
        print(f"status={'ok' if passed else 'failed'}")
        return 0 if passed else 1
    except Exception as error:
        # SDK exceptions can contain request data; report only the exception type.
        print(f"status=error error_type={type(error).__name__}", file=sys.stderr)
        return 1
    finally:
        await client.close()


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
