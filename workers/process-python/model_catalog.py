"""Bounded Codex model/list probe. No thread, model turn, tools, Worker registration or task."""
from __future__ import annotations

import argparse
import json
import os
import sys
import tempfile
import threading
import time
from pathlib import Path

from app_server_runtime import StdioSession, preflight
from worker_runtime import Attempt


class Metadata:
    def record(self, *_args, **_values):
        pass  # Never persist RPC bodies, credentials or account details.


class CatalogCredential:
    def __init__(self, value):
        self.value = value

    def codex_refresh(self, _message):
        raise RuntimeError("MODEL_DIRECTORY_LOGIN_REQUIRED")

    def login(self, session):
        result = session.rpc("account/login/start", {"type": "chatgptAuthTokens", "accessToken": self.value["accessToken"],
            "chatgptAccountId": self.value["accountId"], "chatgptPlanType": self.value.get("planType")})
        if result.get("type") != "chatgptAuthTokens":
            raise RuntimeError("MODEL_DIRECTORY_LOGIN_REQUIRED")


def read_catalog(session):
    models, cursors = [], set()
    cursor = None
    for _page in range(10):
        params = {"limit": 100, "includeHidden": False}
        if cursor is not None:
            params["cursor"] = cursor
        result = session.rpc("model/list", params)
        if not isinstance(result.get("data"), list):
            raise RuntimeError("MODEL_DIRECTORY_FORMAT_UNSUPPORTED")
        for row in result["data"]:
            if not isinstance(row, dict) or row.get("hidden") is True:
                continue
            models.append({key: row[key] for key in ("model", "displayName", "supportedReasoningEfforts", "defaultReasoningEffort") if key in row})
            if len(models) >= 500:
                return {"data": models}
        cursor = result.get("nextCursor")
        if cursor is None:
            return {"data": models}
        if not isinstance(cursor, str) or cursor in cursors:
            raise RuntimeError("MODEL_DIRECTORY_CURSOR_INVALID")
        cursors.add(cursor)
    raise RuntimeError("MODEL_DIRECTORY_LIMIT")


def probe(executable, guard_home, credential):
    attempt = Attempt(None, {}, threading.Event(), evidence=Metadata(), timeout_ms=20000)
    environment = dict(os.environ)
    if guard_home:
        environment["CODEX_HOME"] = guard_home
    source = CatalogCredential(credential) if credential else None
    session = None
    # Never use the repository or a Plan workspace as the probe's current directory.
    directory = tempfile.mkdtemp(prefix="aiw-model-directory-")
    try:
        try:
            command = preflight(executable)
        except ValueError:
            raise RuntimeError("MODEL_DIRECTORY_CODEX_UNAVAILABLE") from None
        session = StdioSession(attempt, directory, argv=[command, "app-server", "--listen", "stdio://"], environment=environment,
                               **({"credential_provider": source} if source else {}))
        initialized = session.rpc("initialize", {"clientInfo": {"name": "aiw_model_directory", "version": "1"}, "capabilities": {"experimentalApi": True}})
        if not isinstance(initialized.get("userAgent"), str):
            raise RuntimeError("MODEL_DIRECTORY_FORMAT_UNSUPPORTED")
        session.send({"method": "initialized", "params": {}}, time.monotonic() + min(10, attempt.remaining()))
        if source:
            source.login(session)
        account = session.rpc("account/read", {"refreshToken": False}).get("account")
        if not isinstance(account, dict) or account.get("type") not in {"chatgpt", "apiKey"}:
            raise RuntimeError("MODEL_DIRECTORY_LOGIN_REQUIRED")
        return read_catalog(session)
    finally:
        try:
            if session:
                session.close()
        finally:
            attempt.close()
            try:
                Path(directory).rmdir()  # Retain unexpected contents; never recursively delete a probe directory.
            except OSError:
                pass


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--codex", required=True)
    parser.add_argument("--guard-home", default="")
    args = parser.parse_args()
    try:
        raw = sys.stdin.buffer.read(65537)
        if len(raw) > 65536:
            raise RuntimeError("MODEL_DIRECTORY_LIMIT")
        credential = json.loads(raw)
        if not isinstance(credential, dict):
            raise RuntimeError("MODEL_DIRECTORY_FORMAT_UNSUPPORTED")
        print(json.dumps(probe(args.codex, args.guard_home, credential), ensure_ascii=False))
        return 0
    except Exception as error:
        allowed = {"MODEL_DIRECTORY_LOGIN_REQUIRED", "MODEL_DIRECTORY_CODEX_UNAVAILABLE", "MODEL_DIRECTORY_TIMEOUT"}
        code = str(error) if str(error) in allowed else "MODEL_DIRECTORY_PROBE_FAILED"
        print(json.dumps({"code": code}))
        return 1


if __name__ == "__main__":
    sys.exit(main())
