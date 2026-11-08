"""Explicit API identity and protocol. Never infer a provider from a model name."""
import os


def provider(runtime="openai-agents"):
    if runtime != "openai-agents":
        return "openai"
    value = os.environ.get("AIW_API_PROVIDER", "openai")
    if value not in {"openai", "deepseek"}:
        raise ValueError("API_PROVIDER_INVALID")
    return value


def protocol():
    value = os.environ.get("AIW_API_PROTOCOL", "responses")
    if value not in {"responses", "chat-completions"}:
        raise ValueError("API_PROTOCOL_INVALID")
    return value


def key_name():
    return "DEEPSEEK_API_KEY" if provider() == "deepseek" else "OPENAI_API_KEY"


def check_auth():
    name = provider()
    wire = protocol()
    if os.environ.get("AIW_MODEL_CREDENTIAL_REF"):
        if name != "openai" or wire != "responses":
            raise ValueError("MODEL_CONNECTION_PROVIDER_MISMATCH")
    elif not os.environ.get(key_name(), "").strip():
        raise ValueError(key_name() + "_REQUIRED")


def efforts(runtime="openai-agents"):
    if runtime == "codex-app-server":
        return {"none", "minimal", "low", "medium", "high", "xhigh"}
    if provider(runtime) == "deepseek":
        return {"none", "low", "high", "max"}
    return {"none", "minimal", "low", "medium", "high"}


def compatible(profile, runtime):
    return (profile["provider"] == provider(runtime) and profile["runtime"] == runtime
            and not profile.get("fast", False) and profile.get("effort") in efforts(runtime))


def runtime_type(config):
    identity = provider(config.runtime) + ":" + protocol() + ":" if config.runtime == "openai-agents" and (
        provider(config.runtime) == "deepseek" or protocol() == "chat-completions") else ""
    return f"{config.runtime}:{identity}{config.process_role}"
