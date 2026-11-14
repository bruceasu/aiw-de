"""Secretary-owned model entry; API protocols share the SDK path."""


def complete(settings, instruction, legacy, *, cancelled=None, on_evidence=None):
    value = dict(settings.value)
    value["_connections"] = settings.connections
    if value.get("provider") in {"codex-app-server", "openai-agents"}:
        try:
            from assistant_model_runtime import complete_text
            return complete_text(value, instruction, cancelled, on_evidence)
        except Exception as error:
            code = str(error)
            if error.__class__.__name__ == "ModelAuthRequired":
                raise ValueError(code) from None
            if code == "PROCESS_TREE_UNCERTAIN":
                if cancelled is not None:
                    cancelled.set()
                settings.pause(True)
                raise ValueError("\u65e0\u6cd5\u786e\u8ba4 Codex \u8fdb\u7a0b\u6811\u5df2\u505c\u6b62\uff0c\u79d8\u4e66\u5df2\u6682\u505c\u3002\u8bf7\u6838\u5bf9\u8fdb\u7a0b\u540e\u91cd\u542f\u4e2a\u4eba\u670d\u52a1\u3002") from None
            if code.startswith("CODEX_EXECUTABLE_UNAVAILABLE"):
                message = "\u627e\u4e0d\u5230\u8bbe\u7f6e\u4e2d\u7684 Codex \u7a0b\u5e8f\u3002\u8bf7\u68c0\u67e5\u7a0b\u5e8f\u8def\u5f84\uff0c\u6216\u586b\u5199 codex.cmd\u3001codex.ps1 \u6216 codex.exe\u3002"
            elif code.startswith("CODEX_NATIVE_EXE_REQUIRED"):
                message = "Codex \u542f\u52a8\u5165\u53e3\u672a\u89e3\u6790\u5230\u539f\u751f codex.exe\u3002\u8bf7\u68c0\u67e5 Windows \u5b89\u88c5\uff0c\u6216\u76f4\u63a5\u586b\u5199 codex.exe \u8def\u5f84\u3002"
            elif code == "CODEX_ACCOUNT_UNAVAILABLE":
                message = "\u5f53\u524d\u8fd0\u884c\u73af\u5883\u7684 Codex \u5c1a\u672a\u767b\u5f55\u3002\u8bf7\u5728\u540c\u4e00\u7528\u6237\u73af\u5883\u767b\u5f55\u540e\u91cd\u542f\u79d8\u4e66\uff1b\u79d8\u4e66\u6cbf\u7528\u672c\u673a Codex \u767b\u5f55\u3002"
            elif code == "OPENAI_AGENTS_SDK_MISSING":
                message = "\u7f3a\u5c11 OpenAI Agents SDK\u3002\u8bf7\u5728\u8fd0\u884c\u79d8\u4e66\u7684 Python \u73af\u5883\u5b89\u88c5\u672c\u76ee\u5f55 requirements.txt\u3002"
            elif code == "OPENAI_API_KEY_REQUIRED":
                message = "\u8bf7\u5728\u8bbe\u7f6e\u4e2d\u586b\u5199 Agents SDK \u4f7f\u7528\u7684 API \u5bc6\u94a5\uff0c\u6216\u914d\u7f6e OPENAI_API_KEY\u3002"
            elif code == "ATTEMPT_CANCELLED":
                message = "\u6a21\u578b\u8bf7\u6c42\u5df2\u505c\u6b62\u6216\u8d85\u8fc7 120 \u79d2\uff0c\u8bf7\u68c0\u67e5\u8fde\u63a5\u540e\u518d\u8bd5\u3002"
            elif code.startswith("WINDOWS_JOB_"):
                message = "Windows \u65e0\u6cd5\u5efa\u7acb Codex \u8fdb\u7a0b\u6811\u7ba1\u7406\uff0c\u79d8\u4e66\u672a\u7ee7\u7eed\u6267\u884c\u3002\u8bf7\u68c0\u67e5\u5f53\u524d\u8fd0\u884c\u73af\u5883\u3002"
            elif code.startswith("CODEX_"):
                message = f"Codex \u6a21\u578b\u8bf7\u6c42\u5931\u8d25\uff08{code}\uff09\u3002\u8bf7\u68c0\u67e5 Codex \u8fde\u63a5\u4e0e\u6a21\u578b\u8bbe\u7f6e\u3002"
            elif code in {"MODEL_INPUT_BUDGET_EXCEEDED", "AGENT_OUTPUT_INVALID"}:
                message = "\u6a21\u578b\u8bf7\u6c42\u5185\u5bb9\u8fc7\u957f\u6216\u672a\u8fd4\u56de\u6709\u6548\u6587\u672c\uff0c\u8bf7\u7f29\u77ed\u5185\u5bb9\u540e\u91cd\u8bd5\u3002"
            else:
                message = "\u79d8\u4e66\u6a21\u578b\u8bf7\u6c42\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u6240\u9009\u6a21\u578b\u3001\u670d\u52a1\u5730\u5740\u3001\u5bc6\u94a5\u53ca SDK \u914d\u7f6e\u3002"
            raise ValueError(message) from None
    if value.get("provider") == "command" or not value.get("endpoint") and settings.legacy_command:
        return legacy(instruction, value.get("command") or settings.legacy_command)
    raise ValueError("请在设置中选择模型连接并保存。")
