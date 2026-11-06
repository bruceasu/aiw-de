"""Pure host-action protocol and finite correction policy. No task or tool authority."""
from __future__ import annotations

from dataclasses import dataclass
import json
import math
import re


class ActionProtocolError(ValueError):
    def __init__(self, code):
        super().__init__(code)
        self.code = code


@dataclass(frozen=True)
class ToolCall:
    alias: str
    name: str
    arguments: dict


@dataclass(frozen=True)
class ActionBatch:
    calls: tuple[ToolCall, ...] = ()
    result: dict | None = None


def _object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ActionProtocolError("HOST_ACTION_DUPLICATE_FIELD")
        result[key] = value
    return result


def _constant(_value):
    raise ActionProtocolError("HOST_ACTION_NONFINITE_VALUE")


def _float(value):
    parsed = float(value)
    if not math.isfinite(parsed):
        raise ActionProtocolError("HOST_ACTION_NONFINITE_VALUE")
    return parsed


def _matches(value, schema):
    # Only the small vocabulary emitted by guarded_app_server.definitions is supported.
    if set(schema) - {"type", "properties", "required", "additionalProperties", "items", "minimum", "maximum",
                      "minLength", "maxLength", "minItems", "maxItems", "enum", "description", "title"}:
        raise RuntimeError("HOST_TOOL_SCHEMA_UNSUPPORTED")
    kind = schema.get("type")
    if "enum" in schema and value not in schema["enum"]:
        return False
    if kind == "object":
        if not isinstance(value, dict): return False
        properties = schema.get("properties", {})
        if set(schema.get("required", [])) - set(value): return False
        if schema.get("additionalProperties") is False and set(value) - set(properties): return False
        return all(key not in properties or _matches(item, properties[key]) for key, item in value.items())
    if kind == "array":
        return (isinstance(value, list) and schema.get("minItems", 0) <= len(value) <= schema.get("maxItems", math.inf)
                and all(_matches(item, schema["items"]) for item in value))
    if kind == "string":
        return isinstance(value, str) and schema.get("minLength", 0) <= len(value) <= schema.get("maxLength", math.inf)
    if kind == "integer":
        return type(value) is int and schema.get("minimum", -math.inf) <= value <= schema.get("maximum", math.inf)
    if kind == "number":
        return (type(value) in {int, float} and (type(value) is int or math.isfinite(value))
                and schema.get("minimum", -math.inf) <= value <= schema.get("maximum", math.inf))
    if kind == "boolean": return type(value) is bool
    if kind == "null": return value is None
    raise RuntimeError("HOST_TOOL_SCHEMA_UNSUPPORTED")


def parse_action(output, definitions):
    text = output.strip()
    fenced = re.fullmatch(r"```(?:json)?[ \t]*\r?\n(.*?)\r?\n?```", text, re.DOTALL | re.IGNORECASE)
    if fenced:
        text = fenced.group(1)
    try:
        action = json.loads(text, object_pairs_hook=_object, parse_constant=_constant, parse_float=_float)
    except ActionProtocolError:
        raise
    except (ValueError, RecursionError) as error:
        raise ActionProtocolError("HOST_ACTION_JSON_INVALID") from error
    if not isinstance(action, dict):
        raise ActionProtocolError("HOST_ACTION_INVALID")
    if action.get("action") == "finish":
        if set(action) != {"action", "result"} or not isinstance(action["result"], dict):
            raise ActionProtocolError("HOST_ACTION_RESULT_INVALID")
        return ActionBatch(result=action["result"])
    if action.get("action") == "tool":
        if set(action) != {"action", "name", "arguments"}:
            raise ActionProtocolError("HOST_ACTION_INVALID")
        calls = [{"id": "call-1", "name": action["name"], "arguments": action["arguments"]}]
    elif action.get("action") == "tools":
        if set(action) != {"action", "calls"}:
            raise ActionProtocolError("HOST_ACTION_INVALID")
        calls = action["calls"]
    else:
        raise ActionProtocolError("HOST_ACTION_INVALID")
    if not isinstance(calls, list) or not 1 <= len(calls) <= 16:
        raise ActionProtocolError("HOST_ACTION_BATCH_LIMIT")
    schemas = {tool["name"]: tool["inputSchema"] for tool in definitions}
    parsed, aliases = [], set()
    for call in calls:
        if not isinstance(call, dict) or set(call) != {"id", "name", "arguments"}:
            raise ActionProtocolError("HOST_ACTION_INVALID")
        alias, name = call["id"], call["name"]
        if not isinstance(alias, str) or not re.fullmatch(r"[A-Za-z0-9._-]{1,64}", alias) or alias in aliases:
            raise ActionProtocolError("HOST_ACTION_CALL_ID_INVALID")
        if not isinstance(name, str) or name not in schemas:
            raise ActionProtocolError("HOST_ACTION_TOOL_INVALID")
        if not _matches(call["arguments"], schemas[name]):
            raise ActionProtocolError("HOST_ACTION_ARGUMENTS_INVALID")
        aliases.add(alias)
        parsed.append(ToolCall(alias, name, call["arguments"]))
    # No operation is dispatched until every call has passed the structural checks.
    return ActionBatch(calls=tuple(parsed))


class CorrectionBudget:
    def __init__(self, maximum=2):
        self.maximum = maximum
        self.used = 0
        self.seen = set()

    def feedback(self, error, progress_digest):
        key = (error.code, progress_digest)
        if self.used >= self.maximum or key in self.seen:
            raise RuntimeError("HOST_ACTION_CORRECTION_LIMIT") from error
        self.used += 1
        self.seen.add(key)
        return {"error": error.code, "retryable": True,
                "hint": "Use exactly one declared JSON action. Use only the supplied tool names and schemas. "
                        "A tools action has 1-16 calls with unique ids. This feedback grants no permissions."}
