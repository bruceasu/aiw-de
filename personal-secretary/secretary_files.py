"""Versioned text edits. Existing originals are never overwritten by this adapter."""
from __future__ import annotations

import codecs
import hashlib
import json
import os
from pathlib import Path
import tempfile
import uuid
import difflib
import itertools


LIMIT = 512_000
TEXT_SUFFIXES = {".txt", ".md", ".markdown", ".rst", ".csv", ".json", ".yaml", ".yml", ".toml", ".log"}


class FileNotExecuted(ValueError):
    """A rejected precondition, before publishing any user-visible bytes."""


def digest(value):
    return hashlib.sha256(value).hexdigest()


def snapshot(path):
    path = Path(path)
    if path.is_symlink() or not path.is_file() or path.stat().st_size > LIMIT:
        raise ValueError("仅支持不超过 512 KB 的普通文本文件；未修改原文件。")
    raw = path.read_bytes()
    if len(raw) > LIMIT:
        raise ValueError("文件在读取时变化或超过上限。")
    encoding = "utf-8-sig" if raw.startswith(codecs.BOM_UTF8) else "utf-8"
    try:
        text = raw.decode(encoding)
    except UnicodeError:
        raise ValueError("此文件不是受支持的 UTF-8 文本，不能用普通文本工具修改。") from None
    if "\x00" in text:
        raise ValueError("文件含二进制内容，未修改。")
    return raw, text, encoding


def operation_diff(store, directory, identity):
    record = store.get("decision", identity)
    if not record or record.get("kind") != "FILE_OPERATION" or record.get("state") != "SUCCEEDED" or not record.get("backup"):
        raise ValueError("这项操作没有已确认的修订差异。")
    backup = Path(record["backup"])
    if backup.is_symlink() or backup.resolve().parent != (Path(directory) / "file-backups").resolve():
        raise ValueError("备份位置无效。")
    before, before_text, _encoding = snapshot(backup)
    after, after_text, _encoding = snapshot(record["path"])
    if digest(before) != record["beforeVersion"] or digest(after) != record["afterVersion"]:
        raise ValueError("原备份或修订副本已经变化，不能展示为原操作差异。")
    lines = list(itertools.islice(difflib.unified_diff(before_text.splitlines(keepends=True), after_text.splitlines(keepends=True),
        fromfile=record["originalPath"], tofile=record["path"]), 2001))
    text = "".join(lines[:2000])
    return {"diff": text[:32000], "truncated": len(lines) > 2000 or len(text) > 32000, "originalUnchanged": True}


class SafeFiles:
    def __init__(self, host):
        self.host = host

    def read(self, action, approved=False):
        path = self.host.path(action, approved)
        raw, text, encoding = snapshot(path)
        offset, limit = action.get("offset", 0), action.get("limit", 30000)
        if type(offset) is not int or type(limit) is not int or offset < 0 or not 1 <= limit <= 30000:
            raise ValueError("读取范围无效。")
        content = text[offset:offset + limit]
        reads = getattr(self.host, "file_reads", {})
        previous = reads.get(str(path), {})
        ranges = previous.get("ranges", []) if previous.get("version") == digest(raw) else []
        ranges = sorted(ranges + [[offset, offset + len(content)]])
        covered = 0
        for start, end in ranges:
            if start > covered:
                break
            covered = max(covered, end)
        reads[str(path)] = {"version": digest(raw), "complete": covered >= len(text), "ranges": ranges}
        self.host.file_reads = reads
        if getattr(self.host, "run_id", None) and self.host.store:
            self.host.persist_file_reads(reads)
        return {"path": str(path), "version": digest(raw), "text": content,
                "offset": offset, "nextOffset": offset + len(content) if offset + len(content) < len(text) else None,
                "complete": offset == 0 and len(content) == len(text),
                "truncated": offset > 0 or len(content) < len(text), "encoding": encoding,
                "newline": "CRLF" if "\r\n" in text else "LF"}

    def perform(self, action, approved=False):
        kind = action["action"]
        if kind == "read_file":
            return self.read(action, approved)
        if kind == "apply_edit":
            # A compare-then-replace is not a filesystem CAS. Keep this explicit
            # until an actual platform adapter proves its exclusion boundary.
            raise FileNotExecuted("此平台尚未启用可靠的原文件写回。修订副本已保留，请使用副本或交给专业文件工具处理。")
        if kind == "restore_file":
            return self.restore(action, approved)
        if kind == "prepare_edit":
            return self.prepare(action, approved)
        path = self.host.path(action, approved)
        text = action.get("text")
        if not isinstance(text, str) or len(text.encode("utf-8")) > LIMIT:
            raise FileNotExecuted("保存内容不是文本或超过 512 KB。")
        if path.suffix.lower() not in TEXT_SUFFIXES:
            raise FileNotExecuted("此文件格式需要专门工具；没有按普通文本写入。")
        if path.exists():
            if kind == "create_file":
                raise FileNotExecuted("同名文件已经存在，create_file 不会覆盖或替代它。")
            raw, _old, encoding = snapshot(path)
            expected = action.get("expectedVersion")
            if expected is None or expected != digest(raw):
                raise FileNotExecuted("覆盖已有文件需要读取时的 expectedVersion；请先读取并准备修订副本。")
            observed = getattr(self.host, "file_reads", {}).get(str(path), {})
            if observed.get("version") != expected or observed.get("complete") is not True:
                raise FileNotExecuted("本次请求没有完整读取原文件，不能生成整篇替换。请使用精确的局部编辑。")
            return self.publish(path, text.encode(encoding), "DRAFT_READY", raw)
        return self.publish(path, text.encode("utf-8"), "CREATED")

    def prepare(self, action, approved):
        path = self.host.path(action, approved)
        if path.suffix.lower() not in TEXT_SUFFIXES:
            raise FileNotExecuted("此格式不能使用普通文本编辑工具。")
        raw, original, encoding = snapshot(path)
        if action.get("expectedVersion") != digest(raw):
            raise FileNotExecuted("文件版本已经变化，未应用修改。请重新读取。")
        if getattr(self.host, "file_reads", {}).get(str(path), {}).get("version") != digest(raw):
            raise FileNotExecuted("请先在本次请求中读取原文件及版本。")
        edits = action.get("edits")
        if not isinstance(edits, list) or not 1 <= len(edits) <= 32:
            raise FileNotExecuted("请提供 1–32 个精确的文本替换。")
        ranges = []
        for edit in edits:
            if not isinstance(edit, dict) or set(edit) != {"old", "new"}:
                raise FileNotExecuted("每项编辑必须包含 old 和 new。")
            old, new = edit["old"], edit["new"]
            if not isinstance(old, str) or not isinstance(new, str) or (not old and original != "") or original.count(old) != 1:
                raise FileNotExecuted("原文必须精确匹配且仅出现一次，未猜测修改位置。")
            start = original.index(old)
            ranges.append((start, start + len(old), new))
        ranges.sort()
        if any(left[1] > right[0] for left, right in zip(ranges, ranges[1:])):
            raise FileNotExecuted("修改位置重叠，未应用。")
        revised = original
        for start, end, new in reversed(ranges):
            revised = revised[:start] + new + revised[end:]
        data = revised.encode(encoding)
        if len(data) > LIMIT:
            raise FileNotExecuted("修订内容超过上限。")
        # Recheck before creating a copy, while making no claim to lock others.
        if digest(path.read_bytes()) != digest(raw):
            raise FileNotExecuted("准备修订时原文件发生变化，未保存。")
        return self.publish(path, data, "DRAFT_READY", raw)

    def publish(self, original, data, status, before=None):
        operation = uuid.uuid4().hex
        target = original if status == "CREATED" else original.with_name(original.stem + ".aiw-" + operation[:8] + original.suffix)
        if target.is_symlink():
            raise ValueError("目标是符号链接，未写入。")
        target.parent.mkdir(parents=True, exist_ok=True)
        backup = None
        if before is not None:
            backup = self.host.settings.directory / "file-backups" / operation
            backup.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
            with open(backup, "xb") as stream:
                os.chmod(backup, 0o600)
                stream.write(before)
                stream.flush()
                os.fsync(stream.fileno())
        record = {"kind": "FILE_OPERATION", "state": "PREPARED", "path": str(target), "originalPath": str(original),
                  "runId": getattr(self.host, "run_id", None),
                  "receiptKey": getattr(self.host, "tool_receipt", None),
                  "afterVersion": digest(data), "beforeVersion": digest(before) if before is not None else None,
                  "backup": str(backup) if backup else None, "resultStatus": status}
        if self.host.store is None:
            raise ValueError("缺少持久操作记录，文件未写入。")
        saved = self.host.store.put("decision", "file-op:" + operation, record, 0)
        fd, temporary = tempfile.mkstemp(prefix=".aiw-draft-", dir=target.parent)
        try:
            with os.fdopen(fd, "wb") as stream:
                stream.write(data)
                stream.flush()
                os.fsync(stream.fileno())
            record["state"] = "COMMITTING"
            saved = self.host.store.put("decision", saved["id"], record, saved["revision"])
            # Hard-link creation is exclusive and exposes only fully written
            # bytes. A filesystem without this capability fails closed.
            os.link(temporary, target)
            if target.is_symlink() or digest(target.read_bytes()) != digest(data):
                raise ValueError("文件结果尚未确认，请核对操作记录。")
            record["state"] = "SUCCEEDED"
            self.host.store.put("decision", saved["id"], record, saved["revision"])
        except FileExistsError:
            record["state"] = "NOT_EXECUTED"
            self.host.store.put("decision", saved["id"], record, saved["revision"])
            raise FileNotExecuted("同名目标已经存在，未覆盖原内容。") from None
        except Exception:
            record["state"] = "UNKNOWN" if record["state"] == "COMMITTING" else "NOT_EXECUTED"
            self.host.store.put("decision", saved["id"], record, saved["revision"])
            raise
        finally:
            Path(temporary).unlink(missing_ok=True)
        self.host.written.append(str(target))
        return {"path": str(target), "originalPath": str(original), "status": status,
                "saved": status == "CREATED", "originalUnchanged": before is not None,
                "beforeVersion": record["beforeVersion"], "afterVersion": record["afterVersion"],
                "operationId": operation, "editId": operation if before is not None else None,
                "note": "修订副本已保存，原文件保留。" if before is not None else "新文件已保存。"}

    def restore(self, action, approved):
        operation = action.get("operationId")
        if not isinstance(operation, str) or not operation.isalnum() or len(operation) != 32:
            raise ValueError("文件操作标识无效。")
        record = self.host.store.get("decision", "file-op:" + operation)
        if not record or record.get("kind") != "FILE_OPERATION" or not record.get("backup"):
            raise ValueError("此操作没有可恢复的原文备份。")
        path = self.host.path({"action": "restore_file", "path": record["originalPath"]}, approved)
        raw, _text, _encoding = snapshot(path)
        if action.get("expectedCurrentVersion") != digest(raw):
            raise ValueError("原文件已变化，未覆盖你的后续编辑。")
        backup = Path(record["backup"])
        data = backup.read_bytes()
        if len(data) > LIMIT or digest(data) != record["beforeVersion"]:
            raise ValueError("备份校验失败。")
        # Recovery also creates a distinct copy. It cannot undo later edits.
        return self.publish(path, data, "DRAFT_READY", raw)
