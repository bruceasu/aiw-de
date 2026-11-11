"""Owner-private, retryable model usage observations; never replay model calls."""
from __future__ import annotations

import base64
import copy
import ctypes
import hashlib
import json
import os
from pathlib import Path
import stat
import tempfile
import threading
import time
import uuid
from urllib.parse import quote

from worker_runtime import _regular_parents


def _windows_protect(value, decrypt=False):
    class Blob(ctypes.Structure):
        _fields_ = [("size", ctypes.c_ulong), ("data", ctypes.POINTER(ctypes.c_ubyte))]
    buffer = ctypes.create_string_buffer(value)
    source = Blob(len(value), ctypes.cast(buffer, ctypes.POINTER(ctypes.c_ubyte)))
    target = Blob()
    fn = ctypes.windll.crypt32.CryptUnprotectData if decrypt else ctypes.windll.crypt32.CryptProtectData
    if not fn(ctypes.byref(source), None, None, None, None, 1, ctypes.byref(target)):
        raise OSError("USAGE_OUTBOX_PROTECTION_FAILED")
    try:
        return ctypes.string_at(target.data, target.size)
    finally:
        ctypes.windll.kernel32.LocalFree(target.data)


class UsageOutbox:
    def __init__(self, config, client):
        default = Path(config.fallback_workspace).parent / ".aidev-usage-outbox"
        root = Path(os.environ.get("AIW_WORKER_USAGE_OUTBOX", str(default))).absolute()
        for candidate in (config.fallback_workspace, config.repository_root, config.worktree_root):
            if candidate and root.resolve().is_relative_to(Path(candidate).resolve()):
                raise RuntimeError("USAGE_OUTBOX_MUST_BE_OUTSIDE_WORKSPACE")
        _regular_parents(root)
        if os.name != "nt":
            info = root.stat()
            if info.st_uid != os.getuid() or stat.S_IMODE(info.st_mode) & 0o077:
                raise RuntimeError("USAGE_OUTBOX_PERMISSIONS_INVALID")
        self.root = root
        self.worker = config.worker_id
        self.plane = client.base
        self.client = copy.copy(client)
        self.client.timeout = min(client.timeout, 2)
        self.stopped = threading.Event()
        self.cursor = ""
        self.thread = threading.Thread(target=self._watch, name="model-usage-outbox", daemon=True)

    def start(self):
        self.thread.start()

    def close(self):
        self.stopped.set()
        self.thread.join(timeout=3)

    def begin(self, task, provider, model, protocol, purpose, connection, tier, price_scope):
        request_id = uuid.uuid4().hex
        lease = {"workerId": self.worker, "attemptId": task["attemptId"], "leaseToken": task["leaseToken"]}
        start = {"lease": lease, "requestId": request_id, "provider": provider, "model": model,
                 "protocol": protocol, "purpose": purpose, "connectionKind": connection, "priceScope": price_scope, "serviceTier": tier,
                 "startedMs": time.time_ns() // 1_000_000}
        item = {"version": 1, "plane": self.plane, "worker": self.worker,
                "task": task["taskId"], "start": start, "terminal": None}
        path = self.root / (uuid.uuid4().hex + ".usage")
        self._write(path, item)
        return path, item

    def finish(self, ticket, state, usage, effective_tier=None):
        if ticket is None:
            return
        path, item = ticket
        start = item["start"]
        terminal = {"lease": start["lease"], "requestId": start["requestId"], "state": state,
                    "finishedMs": max(start["startedMs"], time.time_ns() // 1_000_000),
                    "inputTokens": usage.get("inputTokens"), "cachedTokens": usage.get("cachedTokens"),
                    "outputTokens": usage.get("outputTokens"), "cacheWriteTokens": usage.get("cacheWriteTokens"),
                    "effectiveServiceTier": effective_tier}
        self._write(path, {**item, "terminal": terminal})

    def _write(self, target, item):
        raw = json.dumps(item, ensure_ascii=False, separators=(",", ":"), allow_nan=False).encode("utf-8")
        if os.name == "nt":
            raw = json.dumps({"format": "windows-dpapi-v1", "value": base64.b64encode(_windows_protect(raw)).decode("ascii")}).encode("utf-8")
        descriptor, name = tempfile.mkstemp(prefix=".usage-", dir=self.root)
        try:
            with os.fdopen(descriptor, "wb") as stream:
                os.chmod(name, 0o600)
                stream.write(raw); stream.flush(); os.fsync(stream.fileno())
            os.replace(name, target)
        finally:
            Path(name).unlink(missing_ok=True)

    def _read(self, path):
        if path.is_symlink() or not path.is_file() or path.stat().st_size > 65536:
            raise ValueError("USAGE_OUTBOX_ENTRY_INVALID")
        if os.name != "nt":
            info = path.stat()
            if info.st_uid != os.getuid() or stat.S_IMODE(info.st_mode) & 0o077:
                raise ValueError("USAGE_OUTBOX_ENTRY_PERMISSIONS_INVALID")
        raw = path.read_bytes()
        if os.name == "nt":
            envelope = json.loads(raw)
            if envelope.get("format") != "windows-dpapi-v1":
                raise ValueError("USAGE_OUTBOX_FORMAT_INVALID")
            raw = _windows_protect(base64.b64decode(envelope["value"], validate=True), decrypt=True)
        item = json.loads(raw)
        if item.get("version") != 1 or item.get("plane") != self.plane or item.get("worker") != self.worker:
            raise ValueError("USAGE_OUTBOX_IDENTITY_MISMATCH")
        return item

    def _watch(self):
        while not self.stopped.is_set():
            try:
                self.flush_once()
            except OSError:
                pass
            self.stopped.wait(15)

    def flush_once(self):
        entries = sorted(self.root.glob("*.usage"))
        entries = [p for p in entries if p.name > self.cursor] + [p for p in entries if p.name <= self.cursor]
        for path in entries[:32]:
            if self.stopped.is_set():
                return
            self.cursor = path.name
            try:
                item = self._read(path)
                base = "/tasks/" + quote(item["task"], safe="") + "/model-usage/"
                self.client._post(base + "start", item["start"])
                if item["terminal"] is not None:
                    self.client._post(base + "finish", item["terminal"])
                    path.unlink(missing_ok=True)
            except (OSError, ValueError, KeyError, TypeError):
                continue
            except Exception:
                # Plane unavailable or outcome unknown: keep the exact entry for retry.
                break
