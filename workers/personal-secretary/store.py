"""Owner-bound private storage. The platform never imports this module."""

from __future__ import annotations

import hashlib
import json
import os
import sqlite3
import threading
import time
from contextlib import contextmanager
from pathlib import Path
from typing import Protocol


def encode(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False)


class Store(Protocol):
    owner: str
    worker: str
    def get(self, kind: str, identity: str): ...
    def list(self, kind: str) -> list: ...
    def put(self, kind: str, identity: str, value: dict, revision: int | None = None): ...
    def queue(self, request: str, path: str, body: dict, decision: dict | None = None): ...
    def bind_platform(self, instance: str): ...
    def pending(self) -> list: ...
    def received(self, request: str, receipt: dict): ...
    def finish_reminder(self, work: dict, state: str, now: int): ...
    def cancel_record(self, kind: str, identity: str, revision: int): ...
    def sending(self, request: str): ...
    def pause_request(self, request: str): ...
    def backup(self) -> dict: ...
    def close(self): ...
    def history(self, kind: str, identity: str) -> list: ...


class SQLiteStore:
    """One owner/Worker per directory. Records cannot activate imported history."""
    KINDS = {"work", "message", "policy", "template", "draft", "todo", "decision"}

    def __init__(self, root: Path, owner: str, worker: str):
        self.owner, self.worker = owner, worker
        leaf = hashlib.sha256((owner + "\n" + worker).encode()).hexdigest()
        directory = root.expanduser().resolve() / leaf
        directory.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.path = directory / "personal.sqlite"
        self.lock = threading.RLock()
        existed = self.path.exists()
        self.db = sqlite3.connect(self.path, check_same_thread=False)
        self.db.row_factory = sqlite3.Row
        if existed:
            try:
                identity = self.db.execute("SELECT owner_id,worker_id FROM personal_identity WHERE id=1").fetchone()
                if identity is None or tuple(identity) != (owner, worker):
                    raise ValueError("Private store belongs to a different owner/Worker")
            except (sqlite3.Error, ValueError):
                self.db.close()
                raise ValueError("Existing private store identity cannot be verified") from None
        self.db.execute("PRAGMA foreign_keys=ON")
        self.db.execute("PRAGMA journal_mode=WAL")
        with self.transaction() as db:
            version = db.execute("PRAGMA user_version").fetchone()[0]
            if version not in (0, 1):
                raise ValueError("Unsupported private database version")
            db.execute("CREATE TABLE IF NOT EXISTS personal_identity(id INTEGER PRIMARY KEY CHECK(id=1), owner_id TEXT NOT NULL, worker_id TEXT NOT NULL, platform_instance TEXT)")
            row = db.execute("SELECT owner_id,worker_id FROM personal_identity WHERE id=1").fetchone()
            if row and tuple(row) != (owner, worker):
                raise ValueError("Private store belongs to a different owner/Worker")
            db.execute("INSERT OR IGNORE INTO personal_identity(id,owner_id,worker_id) VALUES(1,?,?)", (owner, worker))
            db.execute("CREATE TABLE IF NOT EXISTS personal_record(kind TEXT NOT NULL,id TEXT NOT NULL,revision INTEGER NOT NULL,data TEXT NOT NULL,PRIMARY KEY(kind,id))")
            db.execute("CREATE TABLE IF NOT EXISTS personal_history(kind TEXT NOT NULL,id TEXT NOT NULL,revision INTEGER NOT NULL,data TEXT NOT NULL,created_ms INTEGER NOT NULL,PRIMARY KEY(kind,id,revision))")
            db.execute("CREATE TABLE IF NOT EXISTS personal_outbox(request_id TEXT PRIMARY KEY,path TEXT NOT NULL,body TEXT NOT NULL,digest TEXT NOT NULL,state TEXT NOT NULL,receipt TEXT)")
            db.execute("CREATE TABLE IF NOT EXISTS personal_import_batch(batch_id TEXT PRIMARY KEY,package_digest TEXT NOT NULL,owner_id TEXT NOT NULL,worker_id TEXT NOT NULL)")
            db.execute("CREATE TABLE IF NOT EXISTS personal_receipt_history(request_id TEXT NOT NULL,sequence INTEGER NOT NULL,receipt TEXT NOT NULL,PRIMARY KEY(request_id,sequence))")
            db.execute("CREATE TABLE IF NOT EXISTS personal_legacy_record(batch_id TEXT NOT NULL,source_key TEXT NOT NULL,data TEXT NOT NULL,activation TEXT NOT NULL CHECK(activation='ARCHIVE_ONLY'),PRIMARY KEY(batch_id,source_key))")
            db.execute("PRAGMA user_version=1")
        try:
            os.chmod(self.path, 0o600)
        except NotImplementedError:
            pass

    @contextmanager
    def transaction(self):
        with self.lock, self.db:
            yield self.db

    def bind_platform(self, instance: str):
        with self.transaction() as db:
            old = db.execute("SELECT platform_instance FROM personal_identity WHERE id=1").fetchone()[0]
            if old is not None and old != instance:
                raise ValueError("Platform instance changed; delegation stays paused for human reconciliation")
            db.execute("UPDATE personal_identity SET platform_instance=? WHERE id=1", (instance,))

    def get(self, kind, identity):
        with self.lock:
            row = self.db.execute("SELECT revision,data FROM personal_record WHERE kind=? AND id=?", (kind, identity)).fetchone()
            return None if row is None else {"id": identity, "revision": row[0], **json.loads(row[1])}

    def list(self, kind):
        with self.lock:
            return [{"id": row[0], "revision": row[1], **json.loads(row[2])} for row in self.db.execute("SELECT id,revision,data FROM personal_record WHERE kind=? ORDER BY id", (kind,))]

    def put(self, kind, identity, value, revision=None):
        if kind not in self.KINDS or not isinstance(value, dict) or "id" in value or "revision" in value:
            raise ValueError("Invalid private record")
        with self.transaction() as db:
            old = db.execute("SELECT revision FROM personal_record WHERE kind=? AND id=?", (kind, identity)).fetchone()
            current = 0 if old is None else old[0]
            if revision is not None and revision != current:
                raise ValueError("Private record changed; reload before saving")
            db.execute("INSERT INTO personal_record VALUES(?,?,?,?) ON CONFLICT(kind,id) DO UPDATE SET revision=excluded.revision,data=excluded.data", (kind, identity, current + 1, encode(value)))
            db.execute("INSERT INTO personal_history VALUES(?,?,?,?,?)", (kind, identity, current + 1, encode(value), int(time.time() * 1000)))
        return self.get(kind, identity)

    def queue(self, request, path, body, decision=None):
        if path not in {"/actions", "/coordination"} or body.get("requestId") != request:
            raise ValueError("Closed platform action path and stable request ID required")
        payload = encode(body)
        checksum = hashlib.sha256((path + "\n" + payload).encode()).hexdigest()
        with self.transaction() as db:
            old = db.execute("SELECT digest FROM personal_outbox WHERE request_id=?", (request,)).fetchone()
            if old and old[0] != checksum:
                raise ValueError("Request ID already belongs to another exact envelope")
            db.execute("INSERT OR IGNORE INTO personal_outbox VALUES(?,?,?,?, 'PENDING',NULL)", (request, path, payload, checksum))
            if decision is not None:
                db.execute("INSERT OR IGNORE INTO personal_record VALUES('decision',?,1,?)", (request, encode(decision)))
                db.execute("INSERT OR IGNORE INTO personal_history VALUES('decision',?,1,?,?)", (request, encode(decision), int(time.time() * 1000)))

    def pending(self):
        with self.lock:
            return [dict(row) for row in self.db.execute("SELECT * FROM personal_outbox WHERE state IN ('PENDING','SENDING','RECONCILING','ADMITTED','IN_PROGRESS','UNKNOWN') ORDER BY request_id")]

    def sending(self, request):
        with self.transaction() as db:
            db.execute("UPDATE personal_outbox SET state='SENDING' WHERE request_id=? AND state IN ('PENDING','SENDING')", (request,))

    def pause_request(self, request):
        with self.transaction() as db:
            db.execute("UPDATE personal_outbox SET state='LOCAL_CANCELLED' WHERE request_id=? AND state='PENDING'", (request,))
            db.execute("UPDATE personal_outbox SET state='RECONCILING' WHERE request_id=? AND state='SENDING'", (request,))

    def cancel_record(self, kind, identity, revision):
        if kind not in {"work", "policy"}:
            raise ValueError("Only private work or policy can be cancelled")
        with self.transaction() as db:
            row = db.execute("SELECT revision,data FROM personal_record WHERE kind=? AND id=?", (kind, identity)).fetchone()
            if row is None or row[0] != revision:
                raise ValueError("Private record changed")
            value = json.loads(row[1])
            value["state"] = "CANCELLED" if kind == "work" else "REVOKED"
            db.execute("UPDATE personal_record SET revision=revision+1,data=? WHERE kind=? AND id=?", (encode(value), kind, identity))
            db.execute("INSERT INTO personal_history VALUES(?,?,?,?,?)", (kind, identity, revision + 1, encode(value), int(time.time() * 1000)))
            field = "workId" if kind == "work" else "policyId"
            for decision in db.execute("SELECT id,data FROM personal_record WHERE kind='decision'").fetchall():
                if json.loads(decision[1]).get(field) == identity:
                    db.execute("UPDATE personal_outbox SET state='LOCAL_CANCELLED' WHERE request_id=? AND state='PENDING'", (decision[0],))
                    db.execute("UPDATE personal_outbox SET state='RECONCILING' WHERE request_id=? AND state='SENDING'", (decision[0],))
        return self.get(kind, identity)

    def received(self, request, receipt):
        if receipt.get("requestId") != request or receipt.get("state") not in {"REJECTED", "APPLIED", "ADMITTED", "IN_PROGRESS", "SUCCEEDED", "FAILED", "UNKNOWN"}:
            raise ValueError("Invalid authoritative receipt")
        with self.transaction() as db:
            previous = db.execute("SELECT receipt FROM personal_outbox WHERE request_id=?", (request,)).fetchone()
            if previous is None:
                raise ValueError("Unknown private outbox identity")
            if previous[0] != encode(receipt):
                db.execute("INSERT INTO personal_receipt_history SELECT ?,COALESCE(MAX(sequence),0)+1,? FROM personal_receipt_history WHERE request_id=?", (request, encode(receipt), request))
            db.execute("UPDATE personal_outbox SET state=?,receipt=? WHERE request_id=?", (receipt["state"], encode(receipt), request))

    def finish_reminder(self, work, state, now):
        value = {key: item for key, item in work.items() if key not in {"id", "revision"}}
        value["state"] = state
        with self.transaction() as db:
            if db.execute("UPDATE personal_record SET data=?,revision=revision+1 WHERE kind='work' AND id=? AND revision=?", (encode(value), work["id"], work["revision"])).rowcount != 1:
                raise ValueError("Reminder changed before delivery")
            db.execute("INSERT INTO personal_history VALUES('work',?,?,?,?)", (work["id"], work["revision"] + 1, encode(value), now))
            if state == "DONE":
                identity = "reminder-" + work["id"] + "-" + str(work["revision"])
                db.execute("INSERT OR IGNORE INTO personal_record VALUES('todo',?,1,?)", (identity, encode({"kind": "REMINDER", "title": work["title"], "workId": work["id"], "createdAt": now})))
                db.execute("INSERT OR IGNORE INTO personal_history VALUES('todo',?,1,?,?)", (identity, encode({"kind": "REMINDER", "title": work["title"], "workId": work["id"], "createdAt": now}), now))

    def history(self, kind, identity):
        if kind not in self.KINDS:
            raise ValueError("Unknown private record kind")
        with self.lock:
            return [dict(row) for row in self.db.execute("SELECT revision,data,created_ms FROM personal_history WHERE kind=? AND id=? ORDER BY revision", (kind, identity))]

    def backup(self):
        # An exportable private artifact, excluding platform credentials and local login sessions.
        with self.lock:
            return {"schema": "aiw.personal.backup.v1", "ownerId": self.owner, "workerId": self.worker,
                    "platformInstanceId": self.db.execute("SELECT platform_instance FROM personal_identity WHERE id=1").fetchone()[0],
                    "records": {kind: self.list(kind) for kind in sorted(self.KINDS)},
                    "outbox": [dict(row) for row in self.db.execute("SELECT * FROM personal_outbox ORDER BY request_id")],
                    "history": [dict(row) for row in self.db.execute("SELECT * FROM personal_history ORDER BY kind,id,revision")],
                    "receiptHistory": [dict(row) for row in self.db.execute("SELECT * FROM personal_receipt_history ORDER BY request_id,sequence")]}

    def close(self):
        with self.lock:
            self.db.close()
