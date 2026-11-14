"""Selected secretary work delegates to the shared task executor; uncertain POSTs are only queried."""
import time
import uuid

from platform_client import PlatformError
from store import encode
from authorization_requests import AuthorizationRequired


class ProfessionalTasks:
    def __init__(self, runtime):
        self.runtime, self.store = runtime, runtime.store
        self.last_poll = 0

    def _save(self, row, **changes):
        current = self.store.get("decision", row["id"])
        value = {key: value for key, value in current.items() if key not in {"id", "revision"}}
        return self.store.put("decision", current["id"], {**value, **changes}, current["revision"])

    def pending(self, work_id):
        return any(row.get("kind") == "PROFESSIONAL_TASK" and row.get("workId") == work_id
                   and row.get("state") in {"SENDING", "UNKNOWN", "ACTIVE"} for row in self.store.list("decision"))

    def _ready(self):
        platform = self.runtime.platform
        if not getattr(platform, "session_started", False):
            identity = platform.request("/sessions", {"agentBoot": platform.boot})
            self._identity(identity)
            platform.session_started = True

    def _identity(self, result):
        if (not isinstance(result, dict) or result.get("ownerId") != self.store.owner
                or result.get("agentId") != self.store.worker or not result.get("platformInstanceId")):
            raise ValueError("平台回执与当前秘书身份不一致，原请求需要核对。")
        self.store.bind_platform(result["platformInstanceId"])

    @staticmethod
    def _error(error):
        return {"PLATFORM_NOT_CONNECTED": "尚未连接 Plane。本机对话和已授权的本地工具仍可使用。",
                401: "平台未接受当前秘书连接，请核对连接和进程登记。",
                403: "当前秘书进程或创建任务所需的计划确认权限尚未获准，请检查原授权。",
                404: "未找到统一委派接口或原请求，请确认 Plane 已更新；不会改用旧专业任务接口。",
                409: "原委派请求的内容或状态发生变化，请核对原记录，不要重复提交。",
                422: "委派内容、成员或 Skills 无效，请核对已选内容。"}.get(error.status,
                "平台回执尚未确认，秘书只查询原请求，不会自动重复提交。")

    def options(self):
        try:
            self._ready()
            result = self.runtime.platform.request("/task-delegations/options")
        except PlatformError as error:
            if error.status in {"PLATFORM_NOT_CONNECTED", 401, 403}:
                raise AuthorizationRequired(self._error(error), "PLATFORM_PERMISSION_REQUIRED") from None
            raise ValueError(self._error(error)) from None
        self._identity(result)
        if result.get("available") is not True:
            raise AuthorizationRequired("当前秘书没有可用的全局计划确认权限（A05）。请核对原管理权限；无须配置仓库或 Git 基线。", "ACTION_PERMISSION_REQUIRED")
        return result

    def submit(self, work, job, action):
        if self.pending(work["id"]):
            raise ValueError("这项工作已有委派请求正在执行或核对，不会重复创建。")
        instruction = action.get("instruction")
        if not isinstance(instruction, str) or not instruction.strip() or len(instruction) > 8000:
            raise ValueError("请提供不超过 8000 字的明确任务及完成条件。")
        options = self.options()
        request_id = str(uuid.uuid4())
        body = {"requestId": request_id, "instruction": instruction,
                **{key: action[key] for key in ("memberId", "requiredSkills") if key in action}}
        if len(encode(body).encode("utf-8")) > 32768:
            raise ValueError("交接内容过长，请缩小明确选择的任务内容。")
        row = self.store.put("decision", "professional:" + request_id, {"kind": "PROFESSIONAL_TASK", "state": "SENDING",
            "requestId": request_id, "workId": work["id"], "jobId": job["id"], "messageId": job.get("messageId"),
            "body": body, "platformInstanceId": options["platformInstanceId"]}, 0)
        self.runtime.daily._decision(job, state="WAITING_PLATFORM", professionalRequestId=request_id)
        try:
            result = self.runtime.platform.request("/task-delegations", body)
        except PlatformError as error:
            if error.status in {400, 401, 403, 404, 409, 413, 422}:
                self._save(row, state="REJECTED", rejection={"status": error.status, "code": error.code})
                if error.status in {401, 403}:
                    raise AuthorizationRequired(self._error(error), "PLATFORM_PERMISSION_REQUIRED") from None
                raise ValueError(self._error(error)) from None
            self._save(row, state="UNKNOWN")
            self._publish(row, "NEEDS_HUMAN", "DELEGATION_UNCONFIRMED", "委派回执尚未确认，秘书会查询原请求，不会自动重复提交。")
            return {"status": "UNKNOWN", "requestId": request_id, "note": "查询原委派回执后才能确认是否受理。"}
        self._receive(row, result)
        return {"status": "DELEGATED", "requestId": request_id, "executionId": result["executionId"], "state": result["state"]}

    def _publish(self, row, state, code, text, **extra):
        work = self.store.get("work", row["workId"])
        if not work or work.get("state") in {"TRASHED", "CANCELLED"}:
            return
        self.runtime.daily._report(work["id"], state, code, text, assistantMode="professional", **extra)
        if row.get("messageId"):
            self.runtime.daily._message(row["messageId"], text, "DONE" if state == "DONE" else "FAILED" if state == "FAILED" else "WAITING_PLATFORM")

    def _receive(self, row, result):
        self._identity(result)
        if (result["platformInstanceId"] != row["platformInstanceId"] or result.get("requestId") != row["requestId"]
                or not isinstance(result.get("executionId"), str) or result["executionId"] != result.get("planId")):
            raise ValueError("委派回执与原请求或执行不一致，需要核对，不能标记完成。")
        state = result.get("state")
        report = result.get("report") or ""
        if state not in {"RUNNING", "WAITING_MEMBER", "WAITING_USER", "PAUSED", "FAILED", "STOPPED", "STOPPING", "COMPLETED"}:
            raise ValueError("平台返回了无法识别的执行状态，请核对原请求。")
        if state == "COMPLETED" and (not isinstance(report, str) or not report.strip()):
            raise ValueError("平台标记结束但没有结果报告，尚不能确认完成。")
        if state == "COMPLETED":
            tracking, display, code, text = "COMPLETED", "DONE", "DELEGATED_COMPLETED", report
        elif state == "STOPPING" or state == "FAILED" and result.get("activeLease") is True:
            tracking, display, code = "ACTIVE", "NEEDS_HUMAN", "DELEGATED_WAITING_FOR_STOP"
            text = "原执行尚未确认结束，请在 Plane 核对结果与停止回执；不会创建重复委派。\n" + str(result.get("message") or report)
        elif state in {"FAILED", "STOPPED"}:
            tracking, display, code = "FAILED", "NEEDS_HUMAN", "DELEGATED_" + state
            text = "委派执行需要处理，请查看原执行；是否重做由你决定。\n" + str(result.get("message") or report)
        else:
            tracking, display, code = "ACTIVE", "ACTIVE", "DELEGATED_" + str(state)
            text = "任务已交给 Plane；这表示已受理，不表示已完成。"
            if state == "WAITING_MEMBER":
                text += "正在等待合适成员；请在 Plane 核对成员和 Skills。"
            elif state == "WAITING_USER" or result.get("taskState") == "WAITING_INPUT":
                text += "Worker 正在等待问题的回答，请打开原执行处理。"
        text += "\n执行：" + result["executionId"]
        current = self.store.get("decision", row["id"])
        if current.get("receipt") != result or current.get("state") != tracking:
            self._save(row, state=tracking, receipt=result)
            self._publish(row, display, code, text, professionalTaskId=result.get("taskId"),
                          professionalPlanId=result["planId"], professionalExecutionId=result["executionId"])
        job = self.store.get("decision", row["jobId"])
        wanted = "WAITING_PLATFORM" if tracking == "ACTIVE" else "DONE" if tracking == "COMPLETED" else "BLOCKED"
        if job.get("state") != wanted:
            self.runtime.daily._decision(job, state=wanted, result=text)

    def recheck(self, identity, revision):
        row = self.store.get("decision", identity)
        if not row or row.get("kind") != "PROFESSIONAL_TASK" or row["revision"] != revision:
            raise ValueError("原委派请求已变化，请刷新后核对。")
        work = self.store.get("work", row["workId"])
        if not work or work.get("state") in {"CANCELLED", "TRASHED"}:
            raise ValueError("工作已取消或移除，不会重新提交。")
        self._ready()
        self._receive(row, self.runtime.platform.request("/task-delegations/" + row["requestId"]))
        return {"requestId": row["requestId"], "state": self.store.get("decision", identity)["state"], "note": "已查询原委派，没有重复提交。"}

    def tick(self):
        if time.monotonic() - self.last_poll < 15:
            return
        self.last_poll = time.monotonic()
        for row in self.store.list("decision"):
            if row.get("kind") != "PROFESSIONAL_TASK" or row.get("state") not in {"SENDING", "UNKNOWN", "ACTIVE"}:
                continue
            work = self.store.get("work", row["workId"])
            if not work or work.get("state") in {"CANCELLED", "TRASHED"}:
                continue
            try:
                self.recheck(row["id"], row["revision"])
            except (PlatformError, ValueError) as error:
                text = self._error(error) if isinstance(error, PlatformError) else str(error)
                if row.get("state") != "UNKNOWN" or row.get("statusError") != text:
                    self._save(row, state="UNKNOWN", statusError=text)
                    self._publish(row, "NEEDS_HUMAN", "DELEGATION_STATUS_UNCONFIRMED", text)
