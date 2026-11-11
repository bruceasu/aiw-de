"""Materialize lease-protected public inputs and collect one official URL for review."""
from __future__ import annotations

import base64
import hashlib
import os
from pathlib import Path
import re
import stat
import tempfile

_FILES = {"collector.py": 1_000_000, "last-success.page": 2_000_000, "changed.page": 2_000_000}
_PAGE_URL_FILE = "latest-page.url"
_SHA = re.compile(r"[a-f0-9]{64}\Z")


def materialize(payload, parent=None):
    if not isinstance(payload, dict) or payload.get("protocol") != "aiw.price-repair-material.v1":
        raise ValueError("PRICE_REPAIR_MATERIAL_INVALID")
    if not isinstance(payload.get("provider"), str) or payload["provider"] not in {"openai", "deepseek"}:
        raise ValueError("PRICE_REPAIR_MATERIAL_INVALID")
    if not isinstance(payload.get("repairId"), str) or not re.fullmatch(r"price-repair-[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}", payload["repairId"]):
        raise ValueError("PRICE_REPAIR_MATERIAL_INVALID")
    if not isinstance(payload.get("failureFingerprint"), str) or not _SHA.fullmatch(payload["failureFingerprint"]):
        raise ValueError("PRICE_REPAIR_MATERIAL_INVALID")
    files = payload.get("files")
    if not isinstance(files, list) or len(files) != len(_FILES):
        raise ValueError("PRICE_REPAIR_MATERIAL_INVALID")
    decoded = {}
    for item in files:
        if not isinstance(item, dict) or set(item) != {"name", "sha256", "contentBase64"}:
            raise ValueError("PRICE_REPAIR_MATERIAL_INVALID")
        name, digest, encoded = item["name"], item["sha256"], item["contentBase64"]
        if not isinstance(name, str) or name not in _FILES or name in decoded or not isinstance(digest, str) or not _SHA.fullmatch(digest):
            raise ValueError("PRICE_REPAIR_MATERIAL_INVALID")
        if not isinstance(encoded, str) or len(encoded) > (_FILES[name] * 4 // 3) + 8:
            raise ValueError("PRICE_REPAIR_MATERIAL_INVALID")
        try:
            raw = base64.b64decode(encoded, validate=True)
        except (ValueError, TypeError) as error:
            raise ValueError("PRICE_REPAIR_MATERIAL_INVALID") from error
        if not raw or len(raw) > _FILES[name] or hashlib.sha256(raw).hexdigest() != digest:
            raise ValueError("PRICE_REPAIR_MATERIAL_INVALID")
        decoded[name] = raw
    root = Path(tempfile.mkdtemp(prefix="aiw-price-repair-", dir=parent)).resolve(strict=True)
    for name, content in decoded.items():
        with (root / name).open("xb") as stream:
            stream.write(content)
    return root


def submission(root, task, worker_id, repair_id, summary):
    """Build a proposal tied to the official URL used for the repair."""
    root = Path(root)
    if root.is_symlink() or getattr(root, "is_junction", lambda: False)() or not root.is_dir():
        raise ValueError("PRICE_REPAIR_RESULT_INVALID")
    if (not isinstance(repair_id, str) or not re.fullmatch(r"price-repair-[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}", repair_id)
            or not isinstance(summary, str) or not summary.strip() or len(summary) > 8192 or "\0" in summary
            or not isinstance(worker_id, str) or not worker_id or not isinstance(task, dict)
            or not isinstance(task.get("attemptId"), str) or not isinstance(task.get("leaseToken"), str)):
        raise ValueError("PRICE_REPAIR_RESULT_INVALID")
    root = root.resolve(strict=True)
    source_path = root / _PAGE_URL_FILE
    if source_path.is_symlink() or getattr(source_path, "is_junction", lambda: False)() or not source_path.is_file():
        raise ValueError("PRICE_REPAIR_RESULT_INVALID")
    try:
        descriptor = os.open(source_path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
        with os.fdopen(descriptor, "rb") as stream:
            if not stat.S_ISREG(os.fstat(stream.fileno()).st_mode):
                raise ValueError("PRICE_REPAIR_RESULT_INVALID")
            raw_url = stream.read(2049)
        source_url = raw_url.decode("utf-8").strip()
        from urllib.parse import urlsplit
        parsed = urlsplit(source_url)
        if (not source_url or len(source_url) > 2048 or "\0" in source_url or parsed.scheme != "https"
                or parsed.username or parsed.password or parsed.fragment or parsed.port not in (None, 443)):
            raise ValueError("PRICE_REPAIR_RESULT_INVALID")
    except (OSError, UnicodeError, ValueError) as error:
        raise ValueError("PRICE_REPAIR_RESULT_INVALID") from error
    result = {"lease": {"workerId": worker_id, "attemptId": task["attemptId"], "leaseToken": task["leaseToken"]},
              "repairId": repair_id, "sourceUrl": source_url, "summary": summary}
    for name, prefix in (("collector.py", "module"), ("test_collector.py", "checks")):
        path = root / name
        if path.is_symlink() or getattr(path, "is_junction", lambda: False)() or not path.is_file() or path.resolve(strict=True).parent != root:
            raise ValueError("PRICE_REPAIR_RESULT_INVALID")
        try:
            descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
            with os.fdopen(descriptor, "rb") as stream:
                if not stat.S_ISREG(os.fstat(stream.fileno()).st_mode):
                    raise ValueError("PRICE_REPAIR_RESULT_INVALID")
                raw = stream.read(1_000_001)
            if not raw or len(raw) > 1_000_000 or b"\0" in raw:
                raise ValueError("PRICE_REPAIR_RESULT_INVALID")
            raw.decode("utf-8")
        except (OSError, UnicodeError) as error:
            raise ValueError("PRICE_REPAIR_RESULT_INVALID") from error
        result[prefix + "Sha256"] = hashlib.sha256(raw).hexdigest()
        result[prefix + "Base64"] = base64.b64encode(raw).decode("ascii")
    return result
