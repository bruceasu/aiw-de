"""Owner-bound private storage. The platform never imports this module."""

from __future__ import annotations

import hashlib
import json
import os
import sqlite3
import threading
import time
import tempfile
import uuid
from contextlib import contextmanager
from pathlib import Path
from typing import Protocol
from private_config import PrivateProcessLock


def encode(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False)


class Store(Protocol):
    owner: str
    worker: str
    def get(self, kind: str, identity: str): ...
    def list(self, kind: str) -> list: ...
    def put(self, kind: str, identity: str, value: dict, revision: int | None = None): ...
    def delete_draft(self, identity: str, revision: int): ...
    def deleted_drafts(self) -> list: ...
    def change_deleted_draft(self, identity, revision, action, confirmed=False): ...
    def queue(self, request: str, path: str, body: dict, decision: dict | None = None): ...
    def bind_platform(self, instance: str): ...
    def pending(self) -> list: ...
    def received(self, request: str, receipt: dict): ...
    def finish_reminder(self, work: dict, state: str, now: int): ...
    def cancel_record(self, kind: str, identity: str, revision: int): ...
    def sending(self, request: str): ...
    def pause_request(self, request: str): ...
    def backup(self) -> dict: ...
    def restore_backup(self, package: dict) -> dict: ...
    def close(self): ...
    def history(self, kind: str, identity: str) -> list: ...
    def chat_messages(self, work_id=None, limit=20, before=None) -> list: ...
    def chat_counts(self) -> dict: ...
    def assert_runtime_deletable(self, work_id): ...
    def delete_conversation(self, work_id, revision, delete_memories, delete_work=False) -> dict: ...
    def delete_memory(self, identity, revision) -> dict: ...


class SQLiteStore:
    """One owner/Worker per directory. Records cannot activate imported history."""
    KINDS = {"work", "message", "memory", "policy", "template", "draft", "todo", "decision", "runtime"}

    def __init__(self, root: Path, owner: str, worker: str):
        self.owner, self.worker = owner, worker
        leaf = hashlib.sha256((owner + "\n" + worker).encode()).hexdigest()
        directory = root.expanduser().resolve() / leaf
        directory.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.path = directory / "personal.sqlite"
        self.lock = threading.RLock()
        initialization = PrivateProcessLock(directory / "service.lock")
        try:
            initialization.acquire()
        except OSError:
            raise ValueError("这份私库已有服务在运行，或目录不能锁定；未打开或升级数据库。") from None
        try:
            self._open(directory, owner, worker)
        except Exception:
            if hasattr(self, "db"):
                self.db.close()
            raise
        finally:
            initialization.release()

    def _open(self, directory, owner, worker):
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
        # Old executables accept only versions 0/1. Fence them out before they
        # can ignore SDK checkpoints or replay an interrupted private action.
        if self.db.execute("PRAGMA user_version").fetchone()[0] == 1:
            descriptor, temporary = tempfile.mkstemp(prefix=".before-fd084-", dir=directory)
            os.close(descriptor)
            try:
                os.chmod(temporary, 0o600)
                destination = sqlite3.connect(temporary)
                try:
                    deadline = time.monotonic() + 10
                    def bounded_backup(_status, _remaining, _total):
                        if time.monotonic() >= deadline:
                            raise ValueError("Private backup deadline exceeded")
                    self.db.backup(destination, pages=128, progress=bounded_backup, sleep=0.1)
                finally:
                    destination.close()
                os.replace(temporary, directory / ("personal-v1-before-fd084-" + uuid.uuid4().hex + ".sqlite"))
            except Exception:
                self.db.close()
                raise ValueError("无法保存旧版私库的升级前备份，未升级数据库。") from None
            finally:
                Path(temporary).unlink(missing_ok=True)
        with self.transaction() as db:
            version = db.execute("PRAGMA user_version").fetchone()[0]
            if version not in (0, 1, 2):
                raise ValueError("Unsupported private database version")
            db.execute("CREATE TABLE IF NOT EXISTS personal_identity(id INTEGER PRIMARY KEY CHECK(id=1), owner_id TEXT NOT NULL, worker_id TEXT NOT NULL, platform_instance TEXT)")
            row = db.execute("SELECT owner_id,worker_id FROM personal_identity WHERE id=1").fetchone()
            if row and tuple(row) != (owner, worker):
                raise ValueError("Private store belongs to a different owner/Worker")
            db.execute("INSERT OR IGNORE INTO personal_identity(id,owner_id,worker_id) VALUES(1,?,?)", (owner, worker))
            db.execute("CREATE TABLE IF NOT EXISTS personal_record(kind TEXT NOT NULL,id TEXT NOT NULL,revision INTEGER NOT NULL,data TEXT NOT NULL,PRIMARY KEY(kind,id))")
            db.execute("CREATE TABLE IF NOT EXISTS personal_history(kind TEXT NOT NULL,id TEXT NOT NULL,revision INTEGER NOT NULL,data TEXT NOT NULL,created_ms INTEGER NOT NULL,PRIMARY KEY(kind,id,revision))")
            db.execute("CREATE TABLE IF NOT EXISTS personal_outbox(request_id TEXT PRIMARY KEY,path TEXT NOT NULL,body TEXT NOT NULL,digest TEXT NOT NULL,state TEXT NOT NULL,receipt TEXT)")
            db.execute("CREATE TABLE IF NOT EXISTS agent_inbox(id TEXT PRIMARY KEY,event_type TEXT NOT NULL,resource_id TEXT NOT NULL,resource_version INTEGER NOT NULL,received_ms INTEGER NOT NULL)")
            db.execute("CREATE TABLE IF NOT EXISTS personal_import_batch(batch_id TEXT PRIMARY KEY,package_digest TEXT NOT NULL,owner_id TEXT NOT NULL,worker_id TEXT NOT NULL)")
            db.execute("CREATE TABLE IF NOT EXISTS personal_receipt_history(request_id TEXT NOT NULL,sequence INTEGER NOT NULL,receipt TEXT NOT NULL,PRIMARY KEY(request_id,sequence))")
            db.execute("CREATE TABLE IF NOT EXISTS personal_legacy_record(batch_id TEXT NOT NULL,source_key TEXT NOT NULL,data TEXT NOT NULL,activation TEXT NOT NULL CHECK(activation='ARCHIVE_ONLY'),PRIMARY KEY(batch_id,source_key))")
            db.execute("PRAGMA user_version=2")
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

    def accept_agent_message(self, message):
        allowed = {"GRANT_CHANGED", "PERMISSION_CHANGED"}
        if (not isinstance(message, dict) or message.get("eventType") not in allowed
                or not isinstance(message.get("id"), str) or len(message["id"]) > 64
                or not isinstance(message.get("resourceId"), str) or not message["resourceId"] or len(message["resourceId"]) > 256
                or type(message.get("resourceVersion")) is not int or message["resourceVersion"] < 1
                or not isinstance(message.get("deliveryToken"), str) or len(message["deliveryToken"]) < 32):
            raise ValueError("Invalid Agent state-change envelope")
        with self.transaction() as db:
            existing = db.execute("SELECT event_type,resource_id,resource_version FROM agent_inbox WHERE id=?", (message["id"],)).fetchone()
            expected = (message["eventType"], message["resourceId"], message["resourceVersion"])
            if existing is not None and tuple(existing) != expected:
                raise ValueError("Agent message identity was reused with different content")
            db.execute("INSERT OR IGNORE INTO agent_inbox VALUES(?,?,?,?,?)",
                       (message["id"], *expected, int(time.time() * 1000)))

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
            # Reusing an erased ID must not let a stale window edit the new record.
            next_revision = current + 1
            if old is None and kind in {"draft", "memory"}:
                next_revision = db.execute("SELECT COALESCE(MAX(revision),0)+1 FROM personal_history WHERE kind=? AND id=?", (kind, identity)).fetchone()[0]
            db.execute("INSERT INTO personal_record VALUES(?,?,?,?) ON CONFLICT(kind,id) DO UPDATE SET revision=excluded.revision,data=excluded.data", (kind, identity, next_revision, encode(value)))
            # Run checkpoints have their own immutable events, sources and tool
            # receipts. Copying a whole transcript on every event would grow
            # quadratically. Existing personal records retain full revisions.
            if kind != "runtime":
                db.execute("INSERT INTO personal_history VALUES(?,?,?,?,?)", (kind, identity, next_revision, encode(value), int(time.time() * 1000)))
        return self.get(kind, identity)

    def runtime_records(self, record_kind, run_id=None, view=False):
        fields = ("kind", "state", "workId", "jobId", "messageId", "sequence", "generation", "createdAt",
                  "modelCalls", "toolCalls", "note", "binding", "hasUnknownEffect", "hasWaitingTool", "steerPending", "format")
        projection = "json_object(" + ",".join("'" + key + "',json_extract(data,'$." + key + "')" for key in fields) + ")" if view else "data"
        clauses, arguments = ["kind='runtime'", "json_extract(data,'$.kind')=?"], [record_kind]
        if run_id is not None:
            clauses.append("id=?" if record_kind == "SECRETARY_RUN" else "json_extract(data,'$.runId')=?")
            arguments.append(run_id)
        query = "SELECT id,revision," + projection + " FROM personal_record WHERE " + " AND ".join(clauses)
        query += " ORDER BY json_extract(data,'$.createdAt') DESC LIMIT 30" if view else " ORDER BY id"
        with self.lock:
            return [{"id": row[0], "revision": row[1], **json.loads(row[2])} for row in self.db.execute(query, arguments)]

    def chat_messages(self, work_id=None, limit=20, before=None):
        """Newest bounded page, returned in chronological order with a stable cursor."""
        if type(limit) is not int or not 1 <= limit <= 100:
            raise ValueError("Invalid message page size")
        clauses, args = ["kind='message'"], []
        if work_id is not None:
            clauses.append("json_extract(data,'$.workId')=?")
            args.append(work_id)
        if before is not None:
            if not isinstance(before, list) or len(before) != 2 or type(before[0]) is not int or not isinstance(before[1], str) or len(before[1]) > 100:
                raise ValueError("Invalid message cursor")
            clauses.append("(COALESCE(json_extract(data,'$.createdAt'),0),id)<(?,?)")
            args.extend(before)
        with self.lock:
            rows = self.db.execute("SELECT id,revision,data FROM personal_record WHERE " + " AND ".join(clauses)
                + " ORDER BY COALESCE(json_extract(data,'$.createdAt'),0) DESC,id DESC LIMIT ?", (*args, limit)).fetchall()
            return [{"id": row[0], "revision": row[1], **json.loads(row[2])} for row in reversed(rows)]

    def chat_counts(self):
        with self.lock:
            return dict(self.db.execute("SELECT json_extract(data,'$.workId'),COUNT(*) FROM personal_record WHERE kind='message' GROUP BY json_extract(data,'$.workId')"))

    def assert_runtime_deletable(self, work_id):
        """Shared guard; called again under the actual deletion transaction lock."""
        with self.lock:
            runs = "SELECT id FROM personal_record WHERE kind='runtime' AND json_extract(data,'$.kind')='SECRETARY_RUN' AND json_extract(data,'$.workId')=?"
            blocked = self.db.execute("""SELECT 1 FROM personal_record WHERE kind='runtime' AND
                json_extract(data,'$.kind')='SECRETARY_RUN' AND json_extract(data,'$.workId')=? AND
                (COALESCE(json_extract(data,'$.state'),'') NOT IN ('SUCCEEDED','FAILED','CANCELLED') OR
                 json_extract(data,'$.hasUnknownEffect')=1 OR json_extract(data,'$.hasWaitingTool')=1) LIMIT 1""", (work_id,)).fetchone()
            effects = self.db.execute("""SELECT 1 FROM personal_record WHERE kind='runtime' AND
                json_extract(data,'$.kind')='SECRETARY_TOOL' AND json_extract(data,'$.runId') IN (""" + runs + """) AND
                (json_extract(data,'$.state') IN ('STARTED','UNKNOWN','WAITING') OR
                 json_extract(data,'$.result.state') IN ('pending','unknown')) LIMIT 1""", (work_id,)).fetchone()
            if blocked or effects:
                raise ValueError("这项工作仍有未结束的运行或待核对副作用，不能删除恢复回执。请先明确结束运行，并核对原文件、笔记或平台操作；取消不会撤销已受理的操作。")

    def delete_conversation(self, work_id, revision, delete_memories, delete_work=False):
        """Purge a chat, or explicitly purge an already trashed local work item."""
        if type(revision) is not int or type(delete_memories) is not bool or type(delete_work) is not bool:
            raise ValueError("请提供当前版本，并明确选择是否保留记忆。")
        with self.transaction() as db:
            self.assert_runtime_deletable(work_id)
            work = self.get("work", work_id)
            if not work or work["revision"] != revision:
                raise ValueError("对话已变化，请刷新后再删除。")
            if delete_work and work.get("state") != "TRASHED":
                raise ValueError("只能永久删除已移除的工作；请刷新后重新选择。")
            targets = [("message", row[0]) for row in db.execute("SELECT id FROM personal_record WHERE kind='message' AND json_extract(data,'$.workId')=?", (work_id,))]
            targets += [("decision", row[0]) for row in db.execute("SELECT id FROM personal_record WHERE kind='decision' AND json_extract(data,'$.workId')=? AND json_extract(data,'$.kind') IN ('DAILY_PLAN','DAILY_CONFIRMATION','PLATFORM_AUTHORIZATION','PLATFORM_QUESTION')", (work_id,))]
            # Include the whole private runtime graph before removing its roots.
            # These records contain model history, sources and control text.
            targets += [("runtime", row[0]) for row in db.execute("""
                SELECT id FROM personal_record WHERE kind='runtime' AND
                (json_extract(data,'$.workId')=? OR json_extract(data,'$.runId') IN
                 (SELECT id FROM personal_record WHERE kind='runtime'
                  AND json_extract(data,'$.kind')='SECRETARY_RUN'
                  AND json_extract(data,'$.workId')=?))""", (work_id, work_id))]
            if delete_memories:
                targets += [("memory", row[0]) for row in db.execute("SELECT id FROM personal_record WHERE kind='memory' AND json_extract(data,'$.sourceWorkId')=?", (work_id,))]
            if delete_work:
                targets += [("todo", row[0]) for row in db.execute("SELECT id FROM personal_record WHERE kind='todo' AND json_extract(data,'$.kind')='REMINDER' AND json_extract(data,'$.workId')=?", (work_id,))]
            if delete_work or work.get("conversationOnly"):
                targets.append(("work", work_id))
            else:
                # Keep task identity, reports and platform references. Strip chat
                # summaries from ALL retained work revisions as well as live data.
                def without_chat(value):
                    return {key: item for key, item in value.items() if not key.startswith("chatSummary")}
                value = without_chat({key: item for key, item in work.items() if key not in {"id", "revision"}})
                value["conversationHidden"] = True
                for row in db.execute("SELECT revision,data FROM personal_history WHERE kind='work' AND id=?", (work_id,)).fetchall():
                    db.execute("UPDATE personal_history SET data=? WHERE kind='work' AND id=? AND revision=?", (encode(without_chat(json.loads(row[1]))), work_id, row[0]))
                db.execute("UPDATE personal_record SET revision=?,data=? WHERE kind='work' AND id=?", (revision + 1, encode(value), work_id))
                db.execute("INSERT INTO personal_history VALUES('work',?,?,?,?)", (work_id, revision + 1, encode(value), int(time.time() * 1000)))
            for kind, identity in targets:
                db.execute("DELETE FROM personal_record WHERE kind=? AND id=?", (kind, identity))
                db.execute("DELETE FROM personal_history WHERE kind=? AND id=?", (kind, identity))
        return {"deleted": True, "workId": work_id, "memoriesDeleted": delete_memories, "workDeleted": delete_work or work.get("conversationOnly", False)}

    def delete_memory(self, identity, revision):
        with self.transaction() as db:
            if db.execute("DELETE FROM personal_record WHERE kind='memory' AND id=? AND revision=?", (identity, revision)).rowcount != 1:
                raise ValueError("记忆已变化，请刷新后再删除。")
            db.execute("DELETE FROM personal_history WHERE kind='memory' AND id=?", (identity,))
            db.execute("INSERT INTO personal_history VALUES('memory',?,?,?,?)", (identity, revision + 1, encode({"purged": True}), int(time.time() * 1000)))
        return {"deleted": True}

    def delete_draft(self, identity, revision):
        if type(revision) is not int or revision < 1:
            raise ValueError("Current draft revision required")
        with self.transaction() as db:
            if db.execute("DELETE FROM personal_record WHERE kind='draft' AND id=? AND revision=?", (identity, revision)).rowcount != 1:
                raise ValueError("Draft changed or was deleted; reload before deleting")
            db.execute("INSERT INTO personal_history VALUES('draft',?,?,?,?)", (identity, revision + 1, encode({"deleted": True}), int(time.time() * 1000)))
        return {"id": identity, "deleted": True}

    def deleted_drafts(self):
        with self.lock:
            return [{"id": row[0], "revision": row[1], "title": row[2] or row[0]} for row in self.db.execute("""
                SELECT h.id,h.revision,(SELECT json_extract(p.data,'$.title') FROM personal_history p
                    WHERE p.kind='draft' AND p.id=h.id AND p.revision<h.revision ORDER BY p.revision DESC LIMIT 1)
                FROM personal_history h WHERE h.kind='draft'
                AND h.data=? AND NOT EXISTS(SELECT 1 FROM personal_record r WHERE r.kind='draft' AND r.id=h.id)
                AND h.revision=(SELECT MAX(x.revision) FROM personal_history x WHERE x.kind='draft' AND x.id=h.id)
                ORDER BY h.id""", (encode({"deleted": True}),))]

    def change_deleted_draft(self, identity, revision, action, confirmed=False):
        with self.transaction() as db:
            if type(revision) is not int or not any(row["id"] == identity and row["revision"] == revision for row in self.deleted_drafts()):
                raise ValueError("草稿已恢复或发生变化，请刷新后再操作。")
            if action == "purge" and confirmed is True:
                db.execute("DELETE FROM personal_history WHERE kind='draft' AND id=? AND revision<>?", (identity, revision))
                db.execute("UPDATE personal_history SET data=? WHERE kind='draft' AND id=? AND revision=?", (encode({"purged": True}), identity, revision))
                return {"deleted": True}
            prior = db.execute("SELECT data FROM personal_history WHERE kind='draft' AND id=? AND revision<? ORDER BY revision DESC LIMIT 1", (identity, revision)).fetchone()
            if action != "restore" or not prior:
                raise ValueError("请选择恢复草稿，或明确确认永久删除其历史。")
            db.execute("INSERT INTO personal_record VALUES('draft',?,?,?)", (identity, revision + 1, prior[0]))
            db.execute("INSERT INTO personal_history VALUES('draft',?,?,?,?)", (identity, revision + 1, prior[0], int(time.time() * 1000)))
        return self.get("draft", identity)

    def queue(self, request, path, body, decision=None):
        if path not in {"/actions", "/coordination", "/question-replies"} or (body.get("envelope", {}) if path == "/question-replies" else body).get("requestId") != request:
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
                    "receiptHistory": [dict(row) for row in self.db.execute("SELECT * FROM personal_receipt_history ORDER BY request_id,sequence")],
                    "archivedBackups": [json.loads(row[0]) for row in self.db.execute("SELECT data FROM personal_legacy_record WHERE source_key='backup' ORDER BY batch_id")]}

    def restore_backup(self, package):
        """Copy private text into new IDs; never activate imported actions or credentials."""
        if not isinstance(package, dict) or package.get("schema") != "aiw.personal.backup.v1":
            raise ValueError("请选择秘书导出的私人备份 JSON 文件。")
        records = package.get("records")
        if not isinstance(records, dict) or any(kind not in self.KINDS or not isinstance(rows, list) for kind, rows in records.items()):
            raise ValueError("备份的私人记录格式无效。")
        indexed = {}
        for kind, rows in records.items():
            indexed[kind] = {}
            for row in rows:
                if (not isinstance(row, dict) or not isinstance(row.get("id"), str) or not row["id"]
                        or len(row["id"]) > 512 or row["id"] in indexed[kind]):
                    raise ValueError("备份包含无效或重复的记录标识。")
                indexed[kind][row["id"]] = row
        # Archived packages stay flat and inert, including old outbox and run receipts.
        archives = package.get("archivedBackups", [])
        if not isinstance(archives, list) or any(not isinstance(item, dict)
                or item.get("schema") != "aiw.personal.backup.v1" or "archivedBackups" in item for item in archives):
            raise ValueError("备份的历史副本格式无效。")
        original = {key: item for key, item in package.items() if key != "archivedBackups"}
        payload = encode(original)
        digest = hashlib.sha256(payload.encode("utf-8")).hexdigest()
        def identity(kind, old):
            return hashlib.sha256((digest + "\n" + kind + "\n" + old).encode("utf-8")).hexdigest()
        restored, now = [], int(time.time() * 1000)
        for old, row in indexed.get("work", {}).items():
            if not isinstance(row.get("title"), str) or not isinstance(row.get("body", ""), str):
                raise ValueError("备份中的工作标题或说明无效。")
            restored.append(("work", identity("work", old), {
                "title": row["title"], "body": row.get("body", ""), "state": "TRASHED",
                "conversationOnly": bool(row.get("conversationOnly")), "backupSource": digest,
                "originalId": old, "createdAt": now}))
        for old, row in indexed.get("message", {}).items():
            if not all(isinstance(row.get(key, ""), str) for key in ("text", "reply")):
                raise ValueError("备份中的对话文本无效。")
            if not isinstance(row.get("workId"), str) or row["workId"] not in indexed.get("work", {}):
                continue  # Orphaned records remain available in the archived original.
            restored.append(("message", identity("message", old), {
                "workId": identity("work", row["workId"]), "text": row.get("text", ""),
                "reply": row.get("reply", ""), "state": "RESTORED",
                "createdAt": row.get("createdAt") if type(row.get("createdAt")) is int else now}))
        for old, row in indexed.get("memory", {}).items():
            if not isinstance(row.get("text"), str) or not 0 < len(row["text"].strip()) <= 2000:
                raise ValueError("备份中的记忆文本无效或超过 2000 字。")
            memory = {"text": row["text"], "trashed": bool(row.get("trashed")), "backupSource": digest, "originalId": old}
            if isinstance(row.get("sourceTitle"), str):
                memory["sourceTitle"] = row["sourceTitle"]
            if isinstance(row.get("sourceWorkId"), str) and row["sourceWorkId"] in indexed.get("work", {}):
                memory["sourceWorkId"] = identity("work", row["sourceWorkId"])
            if isinstance(row.get("sourceMessageIds"), list):
                memory["sourceMessageIds"] = [identity("message", key) for key in row["sourceMessageIds"]
                    if isinstance(key, str) and key in indexed.get("message", {})]
            restored.append(("memory", identity("memory", old), memory))
        # Drafts and templates are passive private documents, not queued actions.
        for kind in ("draft", "template"):
            for old, row in indexed.get(kind, {}).items():
                restored.append((kind, identity(kind, old), {**{key: item for key, item in row.items()
                    if key not in {"id", "revision"}}, "backupSource": digest, "originalId": old}))
        with self.transaction() as db:
            duplicate = db.execute("SELECT 1 FROM personal_import_batch WHERE batch_id=?", (digest,)).fetchone() is not None
            for archived in [original, *archives]:
                raw = encode(archived)
                batch = hashlib.sha256(raw.encode("utf-8")).hexdigest()
                db.execute("INSERT OR IGNORE INTO personal_legacy_record VALUES(?, 'backup', ?, 'ARCHIVE_ONLY')", (batch, raw))
            if not duplicate:
                for kind, key, value in restored:
                    # Separate IDs and one transaction preserve current data, even on failure.
                    raw = encode(value)
                    db.execute("INSERT INTO personal_record VALUES(?,?,1,?)", (kind, key, raw))
                    db.execute("INSERT INTO personal_history VALUES(?,?,1,?,?)", (kind, key, raw, now))
                # Only an actual restore owns a dedup marker. An archive is not a restore.
                db.execute("INSERT INTO personal_import_batch VALUES(?,?,?,?)", (digest, digest, str(original.get("ownerId", "")), str(original.get("workerId", ""))))
        return {"duplicate": duplicate, "restored": 0 if duplicate else len(restored), "backupSource": digest}

    def close(self):
        with self.lock:
            self.db.close()
