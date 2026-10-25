"""One-time daily-assistant settings. Model credentials never enter record exports."""

from pathlib import Path
import urllib.parse

from private_config import read_private, write_private


class AssistantSettings:
    def __init__(self, directory, legacy_command=None):
        self.directory = Path(directory)
        self.path = self.directory / "assistant-settings.json"
        self.value = read_private(self.path, {})
        self.legacy_command = legacy_command
        self.value.setdefault("provider", "command" if legacy_command else "codex-app-server")

    def public(self):
        return {"configured": self.configured(), "paused": self.value.get("paused", False),
                "provider": self.value.get("provider", "codex-app-server"), "endpoint": self.value.get("endpoint", ""),
                "model": self.value.get("model", ""), "hasKey": bool(self.value.get("key")),
                "executable": self.value.get("executable", ""), "effort": self.value.get("effort", ""),
                "roots": self.value.get("roots", []), "scope": "daily-assistant-v1"}

    def configured(self):
        if self.value.get("provider") == "codex-app-server":
            return True  # Installation and login are checked only on an explicit model call.
        if self.value.get("provider") == "openai-agents":
            return bool(self.value.get("model"))
        return bool(self.legacy_command or self.value.get("command") or
                    self.value.get("endpoint") and self.value.get("model"))

    def save(self, data):
        provider = data.get("provider", "codex-app-server")
        value = {"provider": provider, "paused": self.value.get("paused", False)}
        if provider in {"chat", "openai-agents"}:
            endpoint = str(data.get("endpoint", "")).strip().rstrip("/")
            if provider == "openai-agents" and not endpoint:
                endpoint = "https://api.openai.com/v1"
            parsed = urllib.parse.urlsplit(endpoint)
            if parsed.username or parsed.password or parsed.query or parsed.fragment or not parsed.hostname or (
                    parsed.scheme != "https" and not (parsed.scheme == "http" and parsed.hostname in {"localhost", "127.0.0.1", "::1"})):
                raise ValueError("模型服务地址须使用 HTTPS；本机模型服务可使用 HTTP。不要把密钥放进地址。")
            model = str(data.get("model", "")).strip()
            if not model or len(model) > 200:
                raise ValueError("请填写模型服务提供的模型名称。")
            key = data.get("key") or (self.value.get("key", "") if endpoint == self.value.get("endpoint") else "")
            if not isinstance(key, str) or len(key) > 4096 or "\n" in key or "\r" in key:
                raise ValueError("模型密钥格式不正确。")
            value.update(endpoint=endpoint, model=model, key=key)
        elif provider == "codex-app-server":
            executable = data.get("executable", "")
            if not isinstance(executable, str) or len(executable) > 4096 or any(c in executable for c in "\x00\n\r"):
                raise ValueError("Codex 程序路径不正确，请选择已安装的 codex.exe 或留空使用系统 Codex。")
            model = str(data.get("model", "")).strip()
            if len(model) > 200:
                raise ValueError("模型名称过长。")
            value.update(executable=executable.strip(), model=model)
        elif provider == "command":
            command = data.get("command") or self.value.get("command") or self.legacy_command
            if not isinstance(command, list) or not command or len(command) > 32 or any(not isinstance(item, str) or not item or len(item) > 4096 for item in command):
                raise ValueError("自定义模型程序需要有效的程序路径和参数列表。")
            value["command"] = command
        else:
            raise ValueError("请选择可用的模型连接方式。")
        effort = data.get("effort", "")
        if effort not in {"", "none", "minimal", "low", "medium", "high", "xhigh"}:
            raise ValueError("请选择可用的推理强度。")
        value["effort"] = effort
        roots = data.get("roots", [])
        if not isinstance(roots, list) or len(roots) > 16:
            raise ValueError("最多选择 16 个资料目录。")
        value["roots"] = []
        for root in roots:
            path = Path(root).expanduser()
            if not path.is_absolute() or path.resolve() == Path(path.anchor):
                raise ValueError("资料目录需要是具体的绝对路径，不能授权整个磁盘。")
            resolved = path.resolve()
            if resolved == Path.home().resolve() or resolved == self.directory.resolve() or self.directory.resolve().is_relative_to(resolved):
                raise ValueError("请选择具体资料目录，不要授权整个用户目录或秘书私库。")
            value["roots"].append(str(resolved))
        write_private(self.path, value)
        self.value = value
        return self.public()

    def pause(self, paused):
        if type(paused) is not bool:
            raise ValueError("暂停状态不正确。")
        value = {**self.value, "paused": paused}
        write_private(self.path, value)
        self.value = value
        return self.public()
