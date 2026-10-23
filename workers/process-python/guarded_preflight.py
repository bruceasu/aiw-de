"""Startup capability discovery. No Task/Attempt, model call, dependency installation or login."""
import os
import hashlib
import json
import subprocess
from dataclasses import asdict
from pathlib import Path
import tempfile
import time


class StartupScope:
    def __init__(self):
        self.deadline = time.monotonic() + 60
        self.resource_uncertain = False
        self.evidence = self
    def check(self):
        if self.remaining() <= 0: raise RuntimeError("GUARDED_STARTUP_TIMEOUT")
    def remaining(self): return max(0, self.deadline - time.monotonic())
    def record(self, _event, **_values): pass


def _protocol_properties(schema, name):
    """Resolve one unambiguous local public/v2 type, including local aliases/allOf."""
    definitions = schema.get("definitions", schema.get("$defs", {}))
    if not isinstance(definitions, dict): raise RuntimeError("CODEX_PROTOCOL_DEFINITIONS_INVALID")
    namespaces = [definitions]
    if isinstance(definitions.get("v2"), dict): namespaces.append(definitions["v2"])
    candidates = [namespace[name] for namespace in namespaces if name in namespace]
    if len(candidates) != 1: raise RuntimeError("CODEX_PROTOCOL_TYPE_MISSING_OR_AMBIGUOUS:" + name)

    def properties(node, seen=frozenset(), depth=0):
        if not isinstance(node, dict) or depth > 32: raise RuntimeError("CODEX_PROTOCOL_TYPE_INVALID")
        result = node.get("properties", {})
        if not isinstance(result, dict): raise RuntimeError("CODEX_PROTOCOL_PROPERTIES_INVALID")
        result = dict(result)
        reference = node.get("$ref")
        if reference is not None:
            if not isinstance(reference, str) or not reference.startswith("#/") or reference in seen:
                raise RuntimeError("CODEX_PROTOCOL_REFERENCE_UNSUPPORTED")
            target = schema
            for part in reference[2:].split("/"):
                key = part.replace("~1", "/").replace("~0", "~")
                if not isinstance(target, dict) or key not in target: raise RuntimeError("CODEX_PROTOCOL_REFERENCE_UNRESOLVED")
                target = target[key]
            result.update(properties(target, seen | {reference}, depth + 1))
        parents = node.get("allOf", [])
        if not isinstance(parents, list): raise RuntimeError("CODEX_PROTOCOL_COMPOSITION_UNSUPPORTED")
        for parent in parents: result.update(properties(parent, seen, depth + 1))
        return result

    return properties(candidates[0])


def protocol_capability(model=False):
    from app_server_runtime import preflight
    with tempfile.TemporaryDirectory(prefix="aiw-guard-schema-") as directory:
        environment = {"PATH": os.environ.get("PATH", ""), "HOME": directory, "CODEX_HOME": directory}
        result = subprocess.run([preflight(), "app-server", "generate-json-schema", "--experimental", "--out", directory],
                                env=environment, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30)
        file = Path(directory) / "codex_app_server_protocol.schemas.json"
        if result.returncode or not file.is_file() or file.stat().st_size > 32 * 1024 * 1024: raise RuntimeError("CODEX_PROTOCOL_METADATA_UNAVAILABLE")
        raw = file.read_bytes();schema = json.loads(raw)
        required = {"CommandExecParams": {"command", "permissionProfile", "timeoutMs", "outputBytesCap", "processId"}}
        if model:
            required.update({"ThreadStartParams": {"dynamicTools", "permissions", "runtimeWorkspaceRoots", "ephemeral"},
                             "TurnInterruptParams": {"threadId", "turnId"},
                             "ExperimentalFeatureListParams": {"threadId", "limit", "cursor"},
                             "ExperimentalFeature": {"name", "enabled"},
                             "DynamicToolCallParams": {"threadId", "turnId", "tool", "arguments", "callId"},
                             "ThreadTokenUsageUpdatedNotification": {"threadId", "turnId", "tokenUsage"},
                             "ThreadStartResponse": {"activePermissionProfile", "instructionSources"}})
        if any(not fields.issubset(_protocol_properties(schema, name)) for name, fields in required.items()):
            raise RuntimeError("CODEX_REQUIRED_PROTOCOL_CAPABILITY_UNSUPPORTED")
        return "guard-protocol:" + hashlib.sha256(raw).hexdigest()


def capabilities(runtime, profile, skills_root):
    if not os.environ.get("AIW_WORKER_TOKEN", "").strip(): raise RuntimeError("GUARDED_MACHINE_TOKEN_REQUIRED")
    result = {"execution-guard-v1"}
    from execution_guard import digest
    from worker_runtime import skill_package
    result.add("fixed-profile:" + digest(asdict(profile)))
    if os.environ.get("AIW_DYNAMIC_CONFIG", "").lower() != "true":
        for name in set((*profile.protocol_skills, profile.primary_skill)):
            result.add("fixed-skill:" + name + ":" + hashlib.sha256(skill_package(skills_root, name).encode()).hexdigest())
    if runtime == "openai-agents":
        from agents import FunctionTool, Runner
        if not callable(getattr(Runner, "run_streamed", None)) or not callable(FunctionTool): raise RuntimeError("OPENAI_AGENTS_REQUIRED_APIS_UNAVAILABLE")
    else:
        from app_server_runtime import StdioSession, preflight
        from guarded_app_server import verify_config
        result.add(protocol_capability(model=True))
        value = os.environ.get("AIW_CODEX_GUARD_HOME")
        if not value: raise RuntimeError("DEDICATED_GUARDED_CODEX_HOME_REQUIRED")
        home = Path(value).resolve(strict=True)
        if home == Path(os.environ.get("CODEX_HOME", str(Path.home() / ".codex"))).resolve(): raise RuntimeError("GUARDED_HOME_MUST_BE_DEDICATED")
        environment = {"PATH": os.environ.get("PATH", ""), "CODEX_HOME": str(home), "HOME": str(home), "NO_COLOR": "1"}
        with tempfile.TemporaryDirectory(prefix="aiw-guard-preflight-") as directory:
            scope = StartupScope()
            session = StdioSession(scope, directory, argv=[preflight(), "app-server", "--listen", "stdio://"], environment=environment)
            try:
                session.rpc("initialize", {"clientInfo": {"name": "aiw_capability_check", "version": "1"}, "capabilities": {"experimentalApi": True}})
                session.send({"method": "initialized", "params": {}}, time.monotonic() + 10)
                verify_config(session, home, os.environ.get("AIW_CODEX_GUARD_PROFILE", "aiw-model"))
            finally: session.close()
    if os.environ.get("AIW_GUARD_COMMAND_ENABLED", "").lower() == "true":
        from guarded_command import configuration
        from app_server_runtime import preflight
        configuration();preflight()
        result.add(protocol_capability())
        result.add("guarded-command-linux-v1")
    return result
