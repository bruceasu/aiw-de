"""Expose workspace broker operations as SDK function tools."""
import json
import asyncio
from agents import FunctionTool
from guarded_app_server import definitions
from execution_guard import WORKSPACE_TOOLS


def rule_paths(policy, kind, task):
    return {rule["pathPrefix"] or "." for rule in policy.get("rules", [])
            if rule["tool"] == kind and rule["repositoryRef"] == task.get("repositoryRef")
            and rule.get("baseRevision") in (None, task.get("baseRevision"))}


def covers(prefix, resource):
    return prefix == "." or resource == prefix or resource.startswith(prefix + "/")


def tools(broker):
    result = []
    unavailable = set()
    if not broker.writable:
        unavailable.update({"write","edit","edit_many","mkdir","delete","restore","git_commit","git_integrate","run_command"})
    original = broker.context.get("originalPolicy", {})
    current = broker.context.get("currentPolicy", {})
    task = broker.context["snapshot"]["task"]
    allowed = {kind for _, kind in WORKSPACE_TOOLS.values()}
    if broker.allowed_tools is not None:
        allowed &= broker.allowed_tools
    commands = [command["id"] for command in original.get("commands", [])
                if command in current.get("commands", [])]
    scopes = {kind: (rule_paths(original, kind, task), rule_paths(current, kind, task)) for kind in allowed}
    for definition in definitions(broker):
        name = definition["name"]
        if name in unavailable:
            continue
        if name in {"read_skill_file", "read_skill_text"}:
            if getattr(broker.attempt, "skill_files", None) is None:
                continue
        else:
            if name not in WORKSPACE_TOOLS or WORKSPACE_TOOLS[name][1] not in scopes:
                continue
            arguments, kind = WORKSPACE_TOOLS[name]
            if kind == "GIT" and (broker.root is None or not (broker.root / ".git").exists()):
                continue
            frozen, live = scopes[kind]
            resources = (frozen | live) if "path" in arguments else commands if kind == "COMMAND" else (".",)
            if not any(any(covers(path, resource) for path in frozen)
                       and any(covers(path, resource) for path in live) for resource in resources):
                continue
        async def invoke(context, arguments, name=name):
            if not isinstance(arguments, str): raise RuntimeError("SDK_TOOL_ARGUMENTS_UNSUPPORTED")
            call_id = getattr(context, "tool_call_id", None)
            value = await asyncio.to_thread(broker.invoke, name, json.loads(arguments), call_id)
            sources = getattr(broker, "context_sources", None)
            output = sources.model_result(value) if sources else json.dumps(value, ensure_ascii=False)
            if len(output.encode()) > 131072: raise RuntimeError("SDK_TOOL_OUTPUT_LIMIT")
            return output
        result.append(FunctionTool(name=definition["name"], description=definition["description"],
                                   params_json_schema=definition["inputSchema"], on_invoke_tool=invoke,
                                   strict_json_schema=True))
    return result
