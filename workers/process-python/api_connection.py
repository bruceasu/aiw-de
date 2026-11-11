"""Explicit API identity and protocol. Never infer a provider from a model name."""
import os
from urllib.parse import urlsplit


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


def pricing_scope(provider_name, base_url, has_images):
    """Only standard official text endpoints can use the standard price table."""
    if has_images:
        return "UNSUPPORTED"
    if provider_name == "openai" and not base_url:
        return "TEXT_ONLY"
    hosts = {"openai": "api.openai.com", "deepseek": "api.deepseek.com"}
    if provider_name not in hosts or not base_url:
        return "UNSUPPORTED"
    try:
        url = urlsplit(base_url)
        allowed_path = {"/v1", "/v1/"} if provider_name == "openai" else {"", "/", "/v1", "/v1/"}
        if (url.scheme == "https" and url.hostname == hosts[provider_name] and url.port in (None, 443)
                and not url.username and not url.password and not url.query and not url.fragment
                and url.path in allowed_path):
            return "TEXT_ONLY"
    except (TypeError, ValueError):
        pass
    return "UNSUPPORTED"


def effective_service_tier(value):
    """Only a concrete provider-reported tier is eligible for cost matching."""
    return value if isinstance(value, str) and value in {"default", "flex", "priority", "fast", "ultrafast"} else None


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
        return {"default", "none", "low", "high", "max"}
    return {"default", "none", "minimal", "low", "medium", "high", "xhigh", "max"}


def compatible(profile, runtime):
    return (profile["provider"] == provider(runtime) and profile["runtime"] == runtime
            and not profile.get("fast", False) and profile.get("effort") in efforts(runtime))


def runtime_type(config):
    identity = provider(config.runtime) + ":" + protocol() + ":" if config.runtime == "openai-agents" and (
        provider(config.runtime) == "deepseek" or protocol() == "chat-completions") else ""
    return f"{config.runtime}:{identity}{config.process_role}"
