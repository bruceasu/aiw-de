"""Completion claims must agree with actual file and knowledge receipts."""
import re

from assistant_tools import explicit_paths


def validate_completion(request, answer, steps):
    note_results = [step.get("result", {}) for step in steps if step.get("action", {}).get("action") in {"knowledge_save_note", "knowledge_note_status"}]
    if note_results and any(result.get("state") in {"pending", "unknown", "failed"} for result in note_results[-1:]):
        raise ValueError("知识库笔记尚未确认保存，不能把整个请求标记完成。")
    if re.search(r"(?:已保存|已存入|已写入).{0,16}(?:知识库|知識庫)|(?:知识库|知識庫).{0,16}(?:已保存|已存入|已写入)", answer):
        if not any(result.get("state") == "succeeded" for result in note_results):
            raise ValueError("没有知识库保存成功的回执，不能声称笔记已保存。")
    if note_results and "已可搜索" in answer and not any(result.get("indexState") == "ready" for result in note_results):
        raise ValueError("知识库尚未确认索引就绪，不能声称已可搜索。")
    if re.search(r"原文件.{0,8}(?:已修改|已更新|已覆盖|已写回)|(?:已修改|已更新|已覆盖|已写回).{0,8}原文件", answer):
        if not any(step.get("result", {}).get("status") == "UPDATED" for step in steps):
            raise ValueError("没有原文件写回成功的回执，不能声称原文件已更新。")
    paths = explicit_paths(request)
    if paths and re.search(r"修改|编辑|修订|\bedit\b|\bmodify\b", request, re.I):
        changes = [step.get("result", {}) for step in steps if step.get("action", {}).get("action") in {"prepare_edit", "write_file", "apply_edit"}]
        if not any(result.get("status") in {"DRAFT_READY", "UPDATED"} for result in changes):
            raise ValueError("没有可验证的文件修订结果，不能声称已修改文件。")
        if re.search(r"写回原文件|直接修改原文件|覆盖原文件|edit (?:the )?original", request, re.I):
            if not any(result.get("status") == "UPDATED" for result in changes):
                raise ValueError("目前只生成了修订副本，原文件没有写回；不能把原件修改标记为完成。")
