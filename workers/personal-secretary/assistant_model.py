"""Small model boundary for a configured compatible chat service or legacy adapter."""

import json
import urllib.error
import urllib.request


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *_args, **_kwargs):
        return None


def complete(settings, instruction, legacy):
    value = settings.value
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
