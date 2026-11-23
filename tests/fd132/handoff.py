import asyncio
import json
from pathlib import Path
import re
import sys
from tempfile import TemporaryDirectory
from types import SimpleNamespace
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "workers" / "process-python"))

from agents import Runner
from agents.tool_context import ToolContext as SdkToolContext
from agent_runtime import AgentRunRequest
from openai_api_runtime import ApiRuntimeConfig, OpenAIApiRuntime, ToolContext
from plan_workspace import PlanWorkspace, input_manifests
from process_profiles import get_profile


async def check():
    tail = "交接末尾的核对值"
    prefix = "result line\n" * 700
    full = prefix + tail
    parent = {"planId": "fixture-plan", "taskId": "step-one", "attemptId": "attempt-one"}
    with TemporaryDirectory() as location:
        attempt = SimpleNamespace(check=Mock(), evidence=Mock(identity={"attemptId": "attempt-two"}),
                                  cancelled=Mock(is_set=Mock(return_value=False)),
                                  add_cancel_callback=Mock(), remove_cancel_callback=Mock())
        workspace = PlanWorkspace(Path(location), location, attempt, None, None)
        summary = workspace.report_summary(parent, full)
        assert tail not in summary
        ref = workspace.handoff(parent, {"summary": summary, "verdict": "COMPLETED"}, None, full_report=full)
        successor = {"inputs": [{**parent, "handoff": {**ref, "location": location}}]}
        attempt.handoff_reports = []
        input_manifests(successor, attempt.handoff_reports)
        request = AgentRunRequest(attempt=attempt, workspace=location, profile=get_profile("reviewer"),
                                  instruction="Check the recorded result.\n" + summary, model=None,
                                  reasoning_effort="", network_access=False, service_tier=None,
                                  sandbox="read-only", approval_mode="never")
        context = ToolContext(request, 24000, 1000)
        runtime = OpenAIApiRuntime(ApiRuntimeConfig("fixture", "http://127.0.0.1:1/v1", "fixture", "", 1000,
                                                   4, 1024 * 1024, 24000, 1000))

        def simulate_model(agent, _input, **_kwargs):
            assert tail not in agent.instructions
            source = re.search(r'"sourceId": "(handoff-[0-9a-f]{64})"', agent.instructions).group(1)
            tool = next(tool for tool in agent.tools if tool.name == "read_context_source")

            async def stream():
                arguments = json.dumps({
                    "source_id": source, "offset": len(prefix.encode("utf-8")), "limit": 1024,
                })
                wrapped = SdkToolContext(context=context, tool_name=tool.name,
                                         tool_call_id="read-handoff", tool_arguments=arguments)
                page = json.loads(await tool.on_invoke_tool(wrapped, arguments))
                assert page["text"] == tail and page["complete"]
                yield SimpleNamespace(type="fixture")

            return SimpleNamespace(stream_events=stream, final_output="verified downstream")

        with patch.object(Runner, "run_streamed", side_effect=simulate_model), patch.dict("os.environ", {
            "AIW_WORKER_LOOP_CONTROL": "false", "AIW_WORKER_MODEL_MAX_INPUT_TOKENS": "200000",
        }):
            assert await runtime._run(request, context, Mock()) == "verified downstream"

        (Path(location) / ref["manifest"]).with_name("report.md").write_text("tampered", encoding="utf-8")
        try:
            input_manifests(successor, [])
        except RuntimeError as error:
            assert str(error) == "HANDOFF_REPORT_HASH_MISMATCH"
        else:
            raise AssertionError("tampered report was accepted")
    print("PASS upstream handoff, summary boundary, downstream context tool, tamper rejection")


asyncio.run(check())
