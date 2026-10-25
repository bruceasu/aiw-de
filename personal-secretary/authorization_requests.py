"""Permission gaps pause the original job; uncertain submissions are never replayed."""
import time
import threading
from platform_client import PlatformError


class AuthorizationRequired(ValueError):
    def __init__(self, message, reason="SCOPE_REQUIRED", plan_id=None):
        super().__init__(message)
        self.reason, self.plan_id = reason, plan_id


class AuthorizationRequests:
    def __init__(self, runtime):
        self.runtime, self.store = runtime, runtime.store
        self.last_poll = 0
        self.checking = threading.Lock()
        for row in self.store.list("decision"):
            if row.get("kind") == "PLATFORM_AUTHORIZATION" and row.get("state") == "RESUMING":
                self._save(row, state="INTERRUPTED", message="上次继续任务时服务中断，执行结果需要核对；秘书不会自动重放原操作。")

    def wait(self, work, job, action, error):
        if not work or work.get("state") in {"CANCELLED", "TRASHED"}:
            self.runtime.daily._decision(job, state="CANCELLED")
            return
        identity = "authorization:" + job["id"]
        previous = self.store.get("decision", identity)
        value = {"kind": "PLATFORM_AUTHORIZATION", "state": "WAITING", "workId": work["id"], "jobId": job["id"],
                 "messageId": job.get("messageId"), "action": getattr(error, "resume_action", action), "reason": error.reason,
                 "planId": error.plan_id or work.get("professionalDraftPlanId") or work.get("planId"),
                 "message": str(error)[:1000], "createdAt": previous.get("createdAt") if previous else int(time.time() * 1000)}
        self.store.put("decision", identity, value, previous["revision"] if previous else 0)
        self.runtime.daily._decision(job, state="WAITING_AUTHORIZATION")
        self.runtime.daily._report(work["id"], "NEEDS_CONFIRMATION", "PLATFORM_AUTHORIZATION_REQUIRED",
                                   "需要你确认平台连接或代理范围，请到“待你确认”查看申请；原任务会保留。")
        if job.get("messageId"):
            self.runtime.daily._message(job["messageId"], "需要你确认：" + str(error)[:800] + "\n请查看“待你确认”；完成授权后会继续原任务，无需重新描述。", "WAITING_CONFIRMATION")

    def decide(self, identity, revision, accepted):
        if type(accepted) is not bool or type(revision) is not int:
            raise ValueError("请明确确认或拒绝这项申请。")
        with self.runtime.daily.lock:
            row = self.store.get("decision", identity)
            if not row or row.get("kind") != "PLATFORM_AUTHORIZATION" or row.get("state") not in {"WAITING", "INTERRUPTED"} or row["revision"] != revision:
                raise ValueError("这项申请已变化，请刷新后核对。")
            if row.get("state") == "INTERRUPTED":
                if accepted: raise ValueError("请先查看原工作与平台回执，不能直接重放中断的操作。")
                self._save(row, state="RESOLVED")
                return {"state": "RESOLVED"}
            if not accepted:
                self._save(row, state="REJECTED")
                job = self.store.get("decision", row["jobId"])
                if job: self.runtime.daily._decision(job, state="CANCELLED")
                if row.get("messageId"): self.runtime.daily._message(row["messageId"], "已拒绝这项申请，原任务不会继续执行。", "CANCELLED")
                self.runtime.daily._report(row["workId"], "NEEDS_HUMAN", "AUTHORIZATION_DECLINED", "你已拒绝授权，原任务已停止等待。")
                return {"state": "REJECTED"}
        self.resume(row, strict=True)
        return {"state": "RESUMING"}

    def _save(self, row, **changes):
        current = self.store.get("decision", row["id"])
        return self.store.put("decision", row["id"], {**{key: item for key, item in current.items() if key not in {"id", "revision"}}, **changes}, current["revision"])

    def resume(self, row, strict=False):
        runtime = self.runtime
        def blocked(reason, message):
            current = self.store.get("decision", row["id"])
            if current and current.get("state") == "WAITING" and (current.get("reason") != reason or current.get("message") != message):
                self._save(current, reason=reason, message=message)
            if strict: raise ValueError(message)
            return False
        if runtime.cancelled.is_set() or runtime.settings.value.get("paused", False):
            return blocked("SECRETARY_PAUSED", "秘书已暂停，请先恢复秘书；原任务仍在等待。")
        work = self.store.get("work", row["workId"])
        if not work or work.get("state") in {"CANCELLED", "TRASHED"}:
            return blocked("WORK_STOPPED", "这项工作已取消或移除，不能继续执行。")
        if runtime.professional.pending(work["id"]):
            return blocked("ORIGINAL_REQUEST_PENDING", "这项工作已有专业任务正在执行或核对，请查看原回执，不要重新提交。")
        try:
            if not runtime.platform.session_started:
                session = runtime.platform.request("/sessions", {"workerBoot": runtime.platform.boot})
                if session.get("ownerId") != self.store.owner or session.get("workerId") != self.store.worker:
                    raise AuthorizationRequired("平台身份与原秘书不一致，请重新连接原秘书。", "PLATFORM_IDENTITY_MISMATCH")
                runtime.platform.session_started = True
            identity = runtime.platform.request("/connection-state")
            runtime.platform_observed = {"enabled": identity.get("enabled") is True, "workerBoot": runtime.platform.boot, "generation": identity.get("generation")}
            if row.get("planId") and row["action"].get("action") in {"professional_options", "delegate_task"}:
                details = runtime.platform_setup.diagnostics(row["planId"])
                if details.get("missing"):
                    missing = details["missing"]
                    raise AuthorizationRequired("\n".join(item["message"] for item in missing), missing[0]["code"])
            if identity.get("enabled") is not True:
                runtime.enabled = False
                raise AuthorizationRequired("平台已连接，但当前秘书进程尚未启用。请在平台确认当前秘书进程，保留原授权。", "PROCESS_ENABLE_REQUIRED")
            if row["action"].get("action") in {"professional_options", "delegate_task"}:
                options = runtime.professional.options()
                if row.get("planId"):
                    plan = next((plan for plan in options.get("plans", []) if plan["id"] == row["planId"]), None)
                    if not plan:
                        raise AuthorizationRequired("平台连接已确认，但此计划尚无可用的托管和文件执行范围。请打开“配置计划与文件范围”，核对仓库、文件和 Git 基线后保存。", "PLAN_SCOPE_REQUIRED")
                    if plan.get("paused") or plan.get("revoked") or plan.get("expiresAt", 0) <= int(time.time() * 1000):
                        raise AuthorizationRequired("此计划的文件执行范围已暂停、撤销或到期，请在平台执行控制中核对。", "EXECUTION_SCOPE_INACTIVE")
                    if not any(repository.get("baselines") for repository in plan.get("repositories", [])):
                        raise AuthorizationRequired("此计划缺少固定 Git 基线。请在文件执行范围中选择仓库，准备基线并确认保存。", "BASELINE_REQUIRED")
                if not options.get("models"):
                    raise AuthorizationRequired("Agent 工厂尚未选择默认模型。请打开“Agent 工厂设置”保存一次模型与自动 Worker 设置，之后由工厂自动准备。", "PROFESSIONAL_MODEL_REQUIRED")
            else:
                permissions = runtime.platform.request("/action-permissions")
                if not any(item.get("effectiveState") == "ENABLED" and item.get("remaining", 0) > 0
                    and (not row.get("planId") or item.get("scopeKind") == "ALL" or item.get("scopeId") == row["planId"]) for item in permissions):
                    raise AuthorizationRequired("此计划没有有效且有剩余次数的代理动作权限，请核对动作、计划范围和有效期。", "ACTION_PERMISSION_REQUIRED")
        except AuthorizationRequired as error:
            return blocked(error.reason, str(error))
        except PlatformError as error:
            message = "平台后端尚不支持此版本的计划状态检查，请更新并重启平台服务；刷新页面不会更新后端。" if error.status == 404 else "平台拒绝了当前进程的连接检查，请重新连接原秘书并核对当前进程。" if error.status in {401, 403} else "暂时无法读取平台授权状态，请检查平台服务；原任务未重新提交。"
            return blocked("PLATFORM_CHECK_FAILED", message)
        except Exception:
            return blocked("SCOPE_CHECK_FAILED", "平台执行范围暂时无法核对，请检查平台是否已更新；原任务未重新提交。")
        with runtime.daily.lock:
            current = self.store.get("decision", row["id"])
            work = self.store.get("work", row["workId"])
            job = self.store.get("decision", row["jobId"])
            if (not current or current.get("state") != "WAITING" or not work or work.get("state") in {"CANCELLED", "TRASHED"}
                    or not job or job.get("state") != "WAITING_AUTHORIZATION"
                    or work["id"] in runtime.daily.busy or len(runtime.daily.busy) >= 2):
                return blocked("WORK_BUSY_OR_CHANGED", "这项申请已变化，或秘书正在处理工作。请刷新申请并稍后继续；无需重新建立工作。")
            runtime.daily.busy.add(work["id"])
            try:
                self._save(current, state="RESUMING")
                # No professional submission was accepted at this permission gap.
                # Re-read current choices before asking the model to choose a method/model/baseline.
                action = {"action": "professional_options"} if row["action"].get("action") == "delegate_task" else row["action"]
                job = runtime.daily._decision(job, state="RUNNING", resumePlatformAction=action)
            except Exception:
                runtime.daily.busy.discard(work["id"])
                raise
        runtime.enabled = True
        def run():
            try:
                runtime.daily.run(work, job["userText"], job.get("messageId"), job)
                current = self.store.get("decision", row["id"])
                if current and current.get("state") == "RESUMING": self._save(current, state="RESUMED")
            finally:
                with runtime.daily.lock: runtime.daily.busy.discard(work["id"])
        threading.Thread(target=run, name="secretary-authorized-resume", daemon=True).start()
        return True

    def tick(self):
        if self.runtime.cancelled.is_set() or self.runtime.settings.value.get("paused", False): return
        decisions = self.store.list("decision")
        waiting = [row for row in decisions if row.get("kind") == "PLATFORM_AUTHORIZATION" and row.get("state") == "WAITING"]
        followup = any(
            row.get("kind") == "DAILY_PLAN" and row.get("state") == "WAITING_PLATFORM" and row.get("businessRequestId")
            or row.get("kind") == "PROFESSIONAL_TASK" and row.get("state") == "ACTIVE" and row.get("receipt", {}).get("taskState") == "WAITING_INPUT"
            or row.get("kind") == "PLATFORM_QUESTION" and row.get("state") != "RESOLVED"
            for row in decisions)
        if not waiting and not followup:
            self.last_poll = 0
            return
        if time.monotonic() - self.last_poll < 5: return
        self.last_poll = time.monotonic()
        if not self.runtime.platform_setup.public()["connected"] or not self.checking.acquire(blocking=False): return
        def check():
            try:
                # Only pending authorization requests fetch connection/approval state.
                if waiting:
                    try:
                        self.runtime.platform_setup.check()
                    except (PlatformError, ValueError):
                        return
                    for row in waiting: self.resume(row)
                if not followup or not self.runtime.enabled: return
                self.runtime.platform_actions.poll()
                self.runtime.platform_actions.follow_questions()
            finally:
                self.checking.release()
        threading.Thread(target=check, name="secretary-authorization-check", daemon=True).start()

    def pending(self, work_id):
        return any(row.get("workId") == work_id and (row.get("kind") == "PLATFORM_AUTHORIZATION" and row.get("state") in {"WAITING", "RESUMING"}
            or row.get("kind") == "DAILY_PLAN" and row.get("state") == "WAITING_PLATFORM" and row.get("businessRequestId")) for row in self.store.list("decision"))
