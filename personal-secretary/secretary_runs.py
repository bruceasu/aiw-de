"""Durable private run controls and tool receipts. No platform authority."""
from __future__ import annotations

import hashlib
import json
import threading
import time
import uuid
import os


TERMINAL = {"SUCCEEDED", "FAILED", "CANCELLED"}


class RunYield(Exception):
    def __init__(self, reason):
        super().__init__(reason)
        self.reason = reason


def body(row):
    return {key: item for key, item in row.items() if key not in {"id", "revision"}}


class RunJournal:
    def __init__(self, runtime):
        self.runtime, self.store = runtime, runtime.store
        self.lock = threading.RLock()
        self.active = {}
        # Interrupted effects stay unknown; a restart never replays a run.
        for row in self.store.list("runtime"):
            if row.get("kind") == "SECRETARY_RUN" and row.get("state") in {"RUNNING", "PAUSE_REQUESTED"}:
                self.save(row, state="RECONCILING", note="进程中断；请核对在途操作，再明确继续。")
            elif row.get("kind") == "SECRETARY_TOOL" and row.get("state") == "STARTED":
                self.save(row, state="UNKNOWN")
                self.save(self.get(row["runId"]), hasUnknownEffect=True)
            elif row.get("kind") == "SECRETARY_CONTROL" and row.get("state") == "STARTING":
                self.save(row, state="UNKNOWN", note="进程中断，后续请求是否已开始尚未确认；未自动重发。")

    def save(self, row, **changes):
        with self.lock:
            kind = "runtime" if row.get("kind", "").startswith("SECRETARY_") else "decision"
            current = self.store.get(kind, row["id"])
            return self.store.put(kind, row["id"], {**body(current), **changes}, current["revision"])

    def get(self, identity):
        row = self.store.get("runtime", identity)
        if not row or row.get("kind") != "SECRETARY_RUN":
            raise ValueError("找不到这次运行。")
        return row

    def view(self, identity):
        rows = self.records("SECRETARY_RUN", identity, view=True)
        if not rows:
            raise ValueError("找不到这次运行。")
        return rows[0]

    def start(self, job, settings):
        identity = "secretary-run:" + job["id"]
        with self.lock:
            row = self.store.get("runtime", identity)
            binding = self.binding(settings)
            if row:
                if row.get("format") != "secretary-run-v1":
                    raise ValueError("运行记录版本不受支持，原记录已保留，未自动执行。")
                if row.get("binding") != binding:
                    raise ValueError("本次运行的模型连接已经变化，请停止原运行并新建请求。")
                if row["state"] in TERMINAL:
                    raise ValueError("这次运行已结束，不能自动重放。")
                if row["state"] == "RECONCILING":
                    raise ValueError("原运行有待核对的操作，不能自动恢复。请先查看运行和文件记录。")
                row = self.save(row, state="RUNNING", hasWaitingTool=False if job.get("approvedAction") or job.get("resumePlatformAction") else row.get("hasWaitingTool", False))
            else:
                row = self.store.put("runtime", identity, {"kind": "SECRETARY_RUN", "format": "secretary-run-v1", "workId": job["workId"],
                    "jobId": job["id"], "messageId": job.get("messageId"), "state": "RUNNING", "sequence": 0,
                    "generation": 0, "createdAt": int(time.time() * 1000), "binding": binding,
                    "modelCalls": 0, "toolCalls": 0, "history": [], "knowledgeCounts": {}, "notes": []}, 0)
            self.active[job["workId"]] = identity
            self.event(identity, "run-start", state="RUNNING")
            return self.get(identity)

    def event(self, identity, event, **metadata):
        # Event payloads are a closed metadata projection, never prompts/tokens.
        allowed = {"state", "tool", "status", "inputTokens", "outputTokens", "cachedTokens", "usageKnown", "control", "sourceId"}
        with self.lock:
            row = self.get(identity)
            sequence = row.get("sequence", 0) + 1
            # A crash can leave an event durable before its sequence checkpoint.
            # Retain that event and reserve the next number instead of overwriting.
            while self.store.get("runtime", identity + ":event:" + str(sequence).zfill(8)) is not None:
                sequence += 1
            self.store.put("runtime", identity + ":event:" + str(sequence).zfill(8),
                {"kind": "SECRETARY_EVENT", "runId": identity, "sequence": sequence, "type": event,
                 "createdAt": int(time.time() * 1000), **{k: v for k, v in metadata.items() if k in allowed}}, 0)
            self.save(row, sequence=sequence)

    def control(self, data):
        kind, request_id = data.get("control"), data.get("requestId")
        if kind not in {"pause", "resume", "cancel", "steer", "follow-up"}:
            raise ValueError("请选择暂停、继续、取消、调整当前工作或稍后处理。")
        if not isinstance(request_id, str) or not 1 <= len(request_id) <= 100:
            raise ValueError("缺少控制请求标识。")
        text = data.get("text", "")
        if not isinstance(text, str) or len(text) > 4000 or kind in {"steer", "follow-up"} and not text.strip():
            raise ValueError("补充内容为空或超过上限。")
        with self.lock:
            row = self.get(data.get("runId"))
            payload = {"control": kind, "text": text, "runId": row["id"]}
            key = "secretary-control:" + hashlib.sha256(request_id.encode()).hexdigest()
            prior = self.store.get("runtime", key)
            if prior:
                if prior.get("payload") != payload:
                    raise ValueError("同一请求标识不能用于不同控制内容。")
                return {"state": prior["state"], "note": "此控制请求已记录，未重复提交。"}
            if row["state"] in TERMINAL or type(data.get("revision")) is not int or data["revision"] != row["revision"]:
                raise ValueError("运行已结束或发生变化，请刷新后再操作。")
            if kind == "resume" and row["state"] != "PAUSED":
                raise ValueError("只能明确继续已暂停的运行；未知结果需要先核对。")
            if kind == "pause" and row["state"] != "RUNNING":
                raise ValueError("只能暂停正在处理的运行。")
            if kind == "steer" and row["state"] not in {"RUNNING", "PAUSED", "PAUSE_REQUESTED"}:
                raise ValueError("请先处理原确认，或取消原运行后再提出新要求；补充文字不会改写待批准的动作。")
            command = self.store.put("runtime", key, {"kind": "SECRETARY_CONTROL", "runId": row["id"],
                "payload": payload, "binding": self.binding(self.runtime.settings.value), "state": "QUEUED", "createdAt": int(time.time() * 1000)}, 0)
            row = self.save(row, generation=row["generation"] + 1,
                state="PAUSE_REQUESTED" if kind == "pause" else row["state"])
            self.event(row["id"], "control-queued", control=kind)
            if kind == "cancel" and row["state"] in {"PAUSED", "WAITING_USER", "WAITING_AUTH", "RECONCILING"}:
                self.save(command, state="APPLIED")
                self.finish(row["id"], "CANCELLED", "已停止后续协调；在途专业任务或远端保存没有被自动撤销。")
                job = self.store.get("decision", row["jobId"])
                self.runtime.daily._decision(job, state="CANCELLED")
                for pending in self.store.list("decision"):
                    if pending.get("kind") == "DAILY_CONFIRMATION" and pending.get("jobId") == row["jobId"] and pending.get("state") == "WAITING":
                        self.save(pending, state="CANCELLED")
                return {"state": "CANCELLED", "note": "已取消后续操作；没有撤销已完成或已受理的操作。"}
        if kind == "resume":
            try:
                self.runtime.daily.resume_run(row["id"])
            except Exception:
                self.save(command, state="NOT_APPLIED")
                raise
        return {"state": "QUEUED", "note": "控制已记录，将在安全边界处理；在途操作尚未宣称停止。"}

    def check(self, identity):
        with self.lock:
            row = self.view(identity)
            if row.get("state") == "CANCELLED":
                raise RunYield("cancel")
            if row.get("state") == "PAUSED":
                raise RunYield("pause")
            if row.get("hasWaitingTool"):
                raise RunYield("waiting")
            if row.get("steerPending"):
                raise RunYield("steer")
            if row.get("binding") != self.binding(self.runtime.settings.value):
                raise ValueError("运行中的模型配置已经变化，未切换账户或执行方式。请停止原运行后重新安排。")
            if row.get("hasUnknownEffect"):
                raise ValueError("本次运行有未知副作用，必须先对账，不能继续调用模型或工具。")
            work = self.store.get("work", row["workId"])
            if self.runtime.cancelled.is_set() or not work or work.get("state") in {"CANCELLED", "TRASHED"}:
                raise RunYield("cancel")
            if self.runtime.settings.value.get("paused", False):
                raise RunYield("pause")
            queued = [r for r in self.records("SECRETARY_CONTROL", identity) if r.get("kind") == "SECRETARY_CONTROL"
                      and r.get("runId") == identity and r.get("state") == "QUEUED"]
            queued.sort(key=lambda r: (r["createdAt"], r["id"]))
            for command in queued:
                kind = command["payload"]["control"]
                if kind == "follow-up":
                    continue
                self.save(command, state="APPLIED")
                self.event(identity, "control-applied", control=kind)
                if kind in {"pause", "cancel"}:
                    if kind == "cancel":
                        self.finish(identity, "CANCELLED", "已取消后续操作，在途副作用没有被撤销。")
                    else:
                        self.save(self.get(identity), state="PAUSED")
                    raise RunYield(kind)
                if kind == "steer":
                    row = self.get(identity)
                    self.save(row, notes=row.get("notes", []) + [command["payload"]["text"]], steerPending=True)
                    raise RunYield("steer")
            if time.time() * 1000 - row["createdAt"] > 10 * 60 * 1000:
                raise ValueError("本次运行达到十分钟期限，请核对结果后另行安排。")

    @staticmethod
    def binding(settings):
        result = {key: settings.get(key, "") for key in ("provider", "authMode", "connectionId", "connectionVersion", "endpoint", "model", "effort",
            "knowledgeEnabled", "knowledgeServiceId", "knowledgeCredentialRef", "knowledgeBaseUrl", "knowledgePublicBaseUrl")}
        key = settings.get("key") or (os.environ.get("OPENAI_API_KEY", "").strip() if settings.get("credentialSource") == "environment" else None)
        if key:
            result["legacyKeyDigest"] = hashlib.sha256(key.encode()).hexdigest()
        return result

    def count(self, identity, field, maximum):
        with self.lock:
            row = self.get(identity)
            count = row.get(field, 0)
            if count >= maximum:
                raise ValueError("本次运行已达到有限的模型或工具次数上限。")
            self.save(row, **{field: count + 1})

    def invoke(self, identity, action, execute, call_id=None):
        self.check(identity)
        encoded = json.dumps(action, sort_keys=True, ensure_ascii=False, separators=(",", ":"), allow_nan=False)
        effect = action.get("action") in {"write_file", "create_file", "prepare_edit", "apply_edit", "restore_file", "delete_file",
            "knowledge_save_note", "platform_action", "platform_answer", "delegate_task", "create_work"}
        key = self.call_key(identity, action) if effect else identity + ":tool:" + uuid.uuid4().hex
        with self.lock:
            prior = self.store.get("runtime", key)
            if prior:
                if prior["state"] == "SUCCEEDED":
                    return prior["result"]
                if prior["state"] != "NOT_EXECUTED":
                    raise ValueError("这项操作有待确认或未知结果，未重复执行。请核对原操作记录。")
            self.count(identity, "toolCalls", 12)
            receipt = self.save(prior, state="STARTED", callId=call_id, result=None) if prior else self.store.put("runtime", key,
                {"kind": "SECRETARY_TOOL", "runId": identity, "action": action, "callId": call_id, "state": "STARTED",
                 "ordinal": self.get(identity)["toolCalls"]}, 0)
            self.event(identity, "tool-start", tool=action["action"])
        try:
            result = execute(action)
        except Exception as error:
            waiting = error.__class__.__name__ in {"ConfirmationRequired", "AuthorizationRequired"} or isinstance(error, RunYield) and error.reason in {"confirmation", "authorization", "waiting"}
            rejected = error.__class__.__name__ in {"FileNotExecuted", "KnowledgeNotExecuted"} or isinstance(error, RunYield) and not waiting
            self.save(receipt, state="WAITING" if waiting else "NOT_EXECUTED" if rejected else "UNKNOWN" if effect else "FAILED")
            if effect and not waiting and not rejected:
                self.save(self.get(identity), hasUnknownEffect=True)
            if waiting:
                self.save(self.get(identity), hasWaitingTool=True)
            self.event(identity, "tool-end", tool=action["action"], status="waiting" if waiting else "unknown" if effect else "failed")
            raise
        self.save(receipt, state="SUCCEEDED", result=result)
        self.event(identity, "tool-end", tool=action["action"], status="completed")
        return result

    @staticmethod
    def call_key(identity, action):
        encoded = json.dumps(action, sort_keys=True, ensure_ascii=False, separators=(",", ":"), allow_nan=False)
        return identity + ":tool:" + hashlib.sha256(encoded.encode()).hexdigest()

    def waiting_receipt(self, identity, action):
        candidates = [row for row in self.records("SECRETARY_TOOL", identity) if row.get("state") in {"STARTED", "WAITING"}
                      and row.get("action", {}).get("action") == action.get("action")]
        if not candidates:
            raise ValueError("找不到原工具的待确认记录，未创建替代授权。")
        return max(candidates, key=lambda row: row.get("ordinal", 0))["id"]

    def admitted(self, key, execute):
        receipt = self.begin_admitted(key)
        try:
            result = execute()
        except Exception as error:
            if receipt is not None:
                waiting = error.__class__.__name__ in {"ConfirmationRequired", "AuthorizationRequired"}
                rejected = error.__class__.__name__ in {"FileNotExecuted", "KnowledgeNotExecuted"}
                self.save(receipt, state="WAITING" if waiting else "NOT_EXECUTED" if rejected else "UNKNOWN")
                self.save(self.get(receipt["runId"]), hasWaitingTool=waiting, hasUnknownEffect=not waiting and not rejected)
            raise
        self.approved(key, result)
        return result

    def approved(self, key, result):
        if not key:
            return
        with self.lock:
            row = self.store.get("runtime", key)
            if row is None:
                return
            if row.get("kind") != "SECRETARY_TOOL" or row.get("state") != "STARTED":
                raise ValueError("原工具确认记录已变化，不能替换结果。")
            self.save(row, state="SUCCEEDED", result=result)
            self.save(self.get(row["runId"]), hasWaitingTool=False)

    def begin_admitted(self, key):
        if not key:
            return
        with self.lock:
            row = self.store.get("runtime", key)
            # Historical admissions predate run receipts and are still checked
            # by their original domain handler; never invent a receipt for them.
            if row is None:
                return
            if row.get("kind") != "SECRETARY_TOOL" or row.get("state") != "WAITING":
                raise ValueError("原授权操作不再等待，未重复执行。")
            return self.save(row, state="STARTED")

    def finish(self, identity, state, note=""):
        with self.lock:
            row = self.get(identity)
            # Formal asynchronous receipts can arrive before the submitting
            # tool unwinds, or after cancellation. Never revive a terminal run.
            if row["state"] in TERMINAL:
                return row
            if row["state"] == state and row.get("note", "") == note[:1000]:
                return row
            if state == "CANCELLED":
                # WAITING is an unadmitted request, not an in-flight effect.
                # Cancellation closes it without granting or executing it.
                for receipt in self.records("SECRETARY_TOOL", identity):
                    if receipt.get("state") == "WAITING":
                        self.save(receipt, state="NOT_EXECUTED")
                row = self.save(row, hasWaitingTool=False,
                    hasUnknownEffect=bool(row.get("hasUnknownEffect") or self.unresolved_effects(identity)))
            self.save(row, state=state, note=note[:1000])
            self.event(identity, "terminal" if state in TERMINAL else "waiting", state=state)
            if self.active.get(row["workId"]) == identity:
                self.active.pop(row["workId"], None)
            if state in {"FAILED", "CANCELLED"}:
                for command in self.records("SECRETARY_CONTROL", identity):
                    if command.get("state") == "QUEUED" and command.get("payload", {}).get("control") == "follow-up":
                        self.save(command, state="NOT_APPLIED", note="原请求失败或已取消，此后续请求未开始。请核对结果后明确重新提交。")
            return self.get(identity)

    def unresolved_effects(self, identity):
        unresolved = []
        for receipt in self.records("SECRETARY_TOOL", identity):
            state = receipt.get("state")
            if state == "NOT_EXECUTED":
                continue
            if state in {"UNKNOWN", "STARTED"}:
                unresolved.append(receipt)
                continue
            action, result = receipt.get("action"), receipt.get("result")
            if (isinstance(action, dict) and action.get("action") == "knowledge_save_note"
                    and isinstance(result, dict) and result.get("state") in {"pending", "unknown"}):
                unresolved.append(receipt)
        return unresolved

    def sync_job(self, job):
        """Apply only persisted business terminal facts, including async replies."""
        state = {"DONE": "SUCCEEDED", "COMPLETED": "SUCCEEDED", "FAILED": "FAILED",
                 "CANCELLED": "CANCELLED", "REJECTED": "CANCELLED",
                 "BLOCKED": "FAILED", "NEEDS_HUMAN": "FAILED"}.get(job.get("state"))
        if state is None:
            return
        identity = "secretary-run:" + job["id"]
        with self.lock:
            if not self.store.get("runtime", identity):
                return
            if state != "CANCELLED" and any(r.get("state") in {"UNKNOWN", "STARTED"}
                                            for r in self.records("SECRETARY_TOOL", identity)):
                state = "RECONCILING"
            self.finish(identity, state, job.get("result") or job.get("message") or "")

    def public(self):
        rows = self.records("SECRETARY_RUN", view=True)
        rows.sort(key=lambda r: r.get("createdAt", 0), reverse=True)
        controls = self.records("SECRETARY_CONTROL")
        usage = {}
        for run in rows[:30]:
            observed = [event for event in self.records("SECRETARY_EVENT", run["id"]) if event.get("type") == "model-end"]
            known = len(observed) == run.get("modelCalls", 0) and bool(observed) and all(event.get("usageKnown") is True for event in observed)
            usage[run["id"]] = {"usageKnown": known,
                **({"inputTokens": sum(event["inputTokens"] for event in observed), "outputTokens": sum(event["outputTokens"] for event in observed)} if known else {})}
        return [{**{key: r.get(key) for key in ("id", "revision", "workId", "state", "sequence", "modelCalls", "toolCalls", "note")},
                 **usage[r["id"]], "hasUnknownEffect": bool(r.get("hasUnknownEffect") or self.unresolved_effects(r["id"])),
                 "queuedControls": [{"control": c["payload"]["control"], "text": c["payload"]["text"], "state": c["state"], "note": c.get("note", "")}
                                    for c in controls if c.get("runId") == r["id"] and c.get("state") in {"QUEUED", "STARTING", "UNKNOWN", "NOT_APPLIED"}]}
                for r in rows[:30]]

    def reconcile(self, data):
        from pathlib import Path
        from assistant_tools import DailyTools
        with self.lock:
            row = self.get(data.get("runId"))
            cancelled = row["state"] == "CANCELLED"
            if row["state"] not in {"RECONCILING", "CANCELLED"} or data.get("revision") != row["revision"]:
                raise ValueError("运行已变化，或没有待核对的中断。请刷新。")
            if row.get("binding") != self.binding(self.runtime.settings.value):
                raise ValueError("原模型或知识库连接已变化，未核对或重发任何操作。请恢复原设置。")
            job = self.store.get("decision", row["jobId"])
            receipts = self.records("SECRETARY_TOOL", row["id"])
            unknown = self.unresolved_effects(row["id"])
            written = list(job.get("written", []))
            unresolved, known_failed = [], False
            for receipt in unknown:
                action, result = receipt["action"], None
                if action.get("action") in {"create_file", "write_file", "prepare_edit", "restore_file"}:
                    operations = [r for r in self.store.list("decision") if r.get("kind") == "FILE_OPERATION" and r.get("receiptKey") == receipt["id"]]
                    if len(operations) == 1:
                        operation = operations[0]
                        path = Path(operation["path"])
                        try:
                            if not path.is_symlink() and path.is_file() and path.stat().st_size <= 512000 and hashlib.sha256(path.read_bytes()).hexdigest() == operation["afterVersion"]:
                                result = {"path": str(path), "originalPath": operation["originalPath"], "status": operation["resultStatus"],
                                    "saved": operation["resultStatus"] == "CREATED", "afterVersion": operation["afterVersion"], "reconciled": True}
                                if str(path) not in written:
                                    written.append(str(path))
                                self.save(operation, state="SUCCEEDED")
                        except OSError:
                            pass
                elif action.get("action") == "knowledge_save_note":
                    host = DailyTools(self.runtime.settings, job["userText"], store=self.store)
                    try:
                        result = host.perform({"action": "knowledge_note_status", "requestId": job["id"] + ":knowledge-note", "queryOnly": cancelled})
                        if result.get("state") == "failed":
                            known_failed = True
                        elif result.get("state") != "succeeded":
                            result = None
                    except ValueError:
                        pass
                if result is None:
                    unresolved.append({"tool": action.get("action"), "note": "结果仍未知；请核对原文件、知识库或平台正式回执，未重放。"})
                else:
                    self.save(receipt, state="SUCCEEDED", result=result)
                    job = self.runtime.daily._decision(job, steps=job["steps"] + [{"action": action, "result": result}], written=written)
            if unresolved:
                self.save(self.get(row["id"]), hasUnknownEffect=True)
                return {"state": "CANCELLED" if cancelled else "RECONCILING", "unresolved": unresolved, "note": "部分操作仍待核对，不能自动恢复或删除回执。"}
            if cancelled:
                self.save(self.get(row["id"]), hasUnknownEffect=False, note="已核对原副作用，运行仍已取消；未恢复、重放或撤销其它操作。")
                self.event(row["id"], "checkpoint", state="CANCELLED")
                return {"state": "CANCELLED", "note": "已核对可验证的原副作用，运行仍已取消，没有恢复执行。"}
            if known_failed:
                self.runtime.daily._decision(job, state="FAILED")
                self.finish(row["id"], "FAILED", "知识库确认创建失败；未自动换键重试。")
                return {"state": "FAILED", "note": "知识库确认笔记创建失败，原操作已核对；如需重试请明确提出新的保存请求。"}
            waiting = any(r.get("state") == "WAITING" for r in receipts)
            state = "WAITING_USER" if waiting else "PAUSED"
            self.save(self.get(row["id"]), state=state, hasUnknownEffect=False, hasWaitingTool=waiting, note="已核对已知副作用；请处理原确认或明确继续。")
            self.runtime.daily._decision(job, state=state)
            self.event(row["id"], "checkpoint", state=state)
            return {"state": state, "note": "已完成可验证的对账，尚未自动继续。"}

    def events(self, identity):
        self.get(identity)
        return self.records("SECRETARY_EVENT", identity)[-100:]

    def records(self, kind, identity=None, view=False):
        if hasattr(self.store, "runtime_records"):
            return self.store.runtime_records(kind, identity, view)
        return [row for row in self.store.list("runtime") if row.get("kind") == kind and
                (identity is None or (row["id"] == identity if kind == "SECRETARY_RUN" else row.get("runId") == identity))]
