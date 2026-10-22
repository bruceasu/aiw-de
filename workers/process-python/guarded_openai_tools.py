"""Only broker function tools are registered with the OpenAI Agents Runner."""
import json
from agents import FunctionTool
from guarded_app_server import definitions


def tools(broker):
    result = []
    for definition in definitions():
        async def invoke(context, arguments, name=definition["name"]):
            if not isinstance(arguments, str): raise RuntimeError("SDK_TOOL_ARGUMENTS_UNSUPPORTED")
            call_id = getattr(context, "tool_call_id", None)
            value = broker.invoke(name, json.loads(arguments), call_id)
            output = json.dumps(value, ensure_ascii=False)
            if len(output.encode()) > 131072: raise RuntimeError("SDK_TOOL_OUTPUT_LIMIT")
            return output
        result.append(FunctionTool(name=definition["name"], description=definition["description"],
                                   params_json_schema=definition["inputSchema"], on_invoke_tool=invoke,
                                   strict_json_schema=True))
    return result
