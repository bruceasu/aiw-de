"""Secretary access to shared AgentRuntime backends and existing model adapters."""

import json
import re
import urllib.error
import urllib.request
from pathlib import Path
import sys


_worker_runtime = str(Path(__file__).resolve().parent.parent / "process-python")
if _worker_runtime not in sys.path:
    sys.path.append(_worker_runtime)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *_args, **_kwargs):
        return None


def complete(settings, instruction, legacy, *, cancelled=None, on_evidence=None):
    value = dict(settings.value)
    if value.get("provider") in {"codex-app-server", "openai-agents"}:
        try:
            from model_runtime import complete_text
            return complete_text(value, instruction, cancelled, on_evidence)
        except ImportError:
            raise ValueError("共享 AgentRuntime 或 SDK 未安装。请保留相邻的 process-python 目录；Agents SDK 依赖见 requirements.txt。") from None
        except Exception as error:
            code = str(error)
            if code == "PROCESS_TREE_UNCERTAIN":
                if cancelled is not None:
                    cancelled.set()
                settings.pause(True)
                raise ValueError("无法确认模型进程树已停止，秘书已暂停。请核对进程后重启个人服务。") from None
            if code.startswith("CODEX_EXECUTABLE_UNAVAILABLE"):
                message = "找不到设置中的 Codex 程序入口。请检查程序路径是否存在；可填写 codex.cmd、codex.ps1 或 codex.exe。"
            elif code.startswith("CODEX_NATIVE_EXE_REQUIRED"):
                message = "已找到 Codex 程序入口，但未找到其安装目录内的原生 codex.exe。请检查该安装的 Windows 平台包，或直接填写已安装的 codex.exe 路径。"
            elif code == "CODEX_ACCOUNT_UNAVAILABLE":
                message = "当前运行环境的 Codex 尚未登录。请在同一用户环境运行 codex login，再检查连接；秘书沿用本机 Codex 登录。"
            elif code == "OPENAI_API_KEY_REQUIRED":
                message = "请在设置中填写 Agents SDK 使用的 API 密钥，或配置 OPENAI_API_KEY。"
            elif code == "ATTEMPT_CANCELLED":
                message = "模型请求已停止或超过 120 秒，请检查连接后再试。"
            elif code == "CODEX_OUTPUT_EMPTY":
                message = "Codex 本轮已结束，但没有收到可用的回复或宿主工具动作（CODEX_OUTPUT_EMPTY）。这不代表尚未登录，也不能据此确认已搜索或保存文件。"
            elif code == "CODEX_HOST_ACTION_AMBIGUOUS":
                message = "Codex 本轮返回了多个不同的工具动作，秘书未执行，避免误操作（CODEX_HOST_ACTION_AMBIGUOUS）。"
            elif code.startswith("WINDOWS_JOB_"):
                message = "Windows 无法建立模型进程的 Job Object 管理，未继续执行。请检查当前运行环境。"
            elif code.startswith("CODEX_"):
                stages = {"initialize": "初始化连接", "account/read": "读取本机登录",
                    "thread/start": "创建会话", "thread/read": "读取会话", "turn/start": "开始模型请求"}
                method = getattr(error, "method", "")
                stage = stages.get(method, "处理会话")
                if code == "CODEX_THREAD_POLICY_UNCONFIRMED": stage = "检查创建会话的回执"
                if code == "CODEX_EPHEMERAL_THREAD_UNCONFIRMED": stage = "检查临时会话的回执"
                safe_code = code if re.fullmatch(r"CODEX_[A-Z0-9_]{1,80}", code) else "CODEX_RUNTIME_FAILED"
                rpc_code = getattr(error, "rpc_code", None)
                rpc_note = f"，RPC {rpc_code}" if type(rpc_code) is int else ""
                reason = {-32601: "当前 app-server 不支持该接口。", -32602: "app-server 拒绝了请求参数。"}.get(rpc_code, "")
                message = f"Codex 在{stage}时失败（{safe_code}{rpc_note}）。{reason}这是运行时接入错误，不能仅据此判断尚未登录。"
            else:
                message = "AgentRuntime 请求失败，请检查所选模型、服务地址、密钥、额度和 SDK 配置。"
            raise ValueError(message) from None
    if value.get("provider") == "command" or not value.get("endpoint") and settings.legacy_command:
        return legacy(instruction, value.get("command") or settings.legacy_command)
    if not settings.configured():
        raise ValueError("还没有连接模型。请打开“设置”，填写模型服务地址、模型名称和密钥，保存一次后即可使用。")
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if value.get("key"):
        headers["Authorization"] = "Bearer " + value["key"]
    payload = json.dumps({"model": value["model"], "messages": [{"role": "user", "content": instruction}]}, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(value["endpoint"] + "/chat/completions", payload, headers, method="POST")
    try:
        with urllib.request.build_opener(NoRedirect()).open(request, timeout=120) as response:
            raw = response.read(128_001)
        if len(raw) > 128_000:
            raise ValueError("模型回复过长，请拆分工作后重试。")
        result = json.loads(raw)
        text = result["choices"][0]["message"]["content"]
        if not isinstance(text, str) or not text.strip() or len(text) > 32000:
            raise ValueError("模型没有返回可用的文字回复，请检查所选模型是否支持聊天。")
        return text.strip()
    except urllib.error.HTTPError as error:
        status = error.code
        error.close()
        if status in {401, 403}:
            raise ValueError("模型服务没有接受当前密钥或模型权限，请在“设置”更新模型连接。") from None
        if status == 429:
            raise ValueError("模型服务额度不足或请求过于频繁。请检查额度后再继续，本次不会自动重试。") from None
        raise ValueError(f"模型服务返回 {status}，请检查“设置”中的服务地址和模型名称。") from None
    except (urllib.error.URLError, TimeoutError, OSError):
        raise ValueError("暂时无法连接模型服务，请检查网络和“设置”中的模型地址。") from None
    except (KeyError, IndexError, TypeError, UnicodeError, json.JSONDecodeError):
        raise ValueError("模型服务返回格式不兼容，请选择支持 Chat Completions 的服务及模型。") from None
