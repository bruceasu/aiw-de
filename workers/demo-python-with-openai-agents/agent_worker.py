#!/usr/bin/env python3
"""Minimal OpenAI Agents SDK worker for checking an OpenAI-compatible endpoint."""
from __future__ import annotations

import asyncio
import os
import sys


async def main() -> int:
    api_key = os.getenv("OPENAI_API_KEY", "").strip()
    if not api_key:
        print("OPENAI_API_KEY is required", file=sys.stderr)
        return 2

    from agents import Agent, OpenAIProvider, Runner, set_tracing_disabled
    from openai import AsyncOpenAI

    set_tracing_disabled(True)
    model_name = os.getenv("AIW_OPENAI_MODEL", "gpt-6-luna").strip()
    base_url = os.getenv("OPENAI_BASE_URL") or None
    client = AsyncOpenAI(api_key=api_key, base_url=base_url, max_retries=0)
    try:
        provider = OpenAIProvider(openai_client=client, use_responses=True)
        agent = Agent(
            name="compatibility-check",
            instructions="Reply with exactly: AGENTS_SDK_OK",
            model=provider.get_model(model_name),
        )
        result = await Runner.run(agent, "Run the compatibility check.", max_turns=1)
        output = str(result.final_output).strip()
        print(f"model={model_name}")
        print(f"output={output}")
        if output != "AGENTS_SDK_OK":
            print("The request completed, but the model did not return the expected marker.", file=sys.stderr)
            return 1
        print("status=ok")
        return 0
    except Exception as error:
        # Do not print exception details: HTTP client exceptions can contain request data.
        print(f"status=error error_type={type(error).__name__}", file=sys.stderr)
        return 1
    finally:
        await client.close()


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
