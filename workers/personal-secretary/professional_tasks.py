"""Durable professional delegation; an unconfirmed submission is only queried."""

import time
import uuid

from platform_client import PlatformError
from store import encode


GAPS = {
    "NO_COMPATIBLE_WORKER": "没有在线且兼容此仓库和模型运行时的专业 Worker。请启动对应 Worker 后重新安排。",
    "AUTHORIZATION_REQUIRED": "缺少这项任务所需的工具或仓库授权，或执行范围已暂停、过期。请确认具体范围后重新安排。",
    "BUDGET_CONFIGURATION_REQUIRED": "平台要求的执行预算或模型定价尚未满足，请核对平台返回的缺项。",
    "UNSUPPORTED_RUNTIME": "没有兼容的已批准模型或运行时，请在平台配置可用模型。",
    "SOURCE_CHANGED": "任务来源、仓库基线或执行范围已变化，请核对后重新安排。",
    "INVALID_CONFIGURATION": "工作方法与当前任务不兼容，请选择平台列出的工作方法后重新安排。",
}


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
            identity = platform.request("/sessions", {"workerBoot": platform.boot})
            if identity.get("ownerId") != self.store.owner or identity.get("workerId") != self.store.worker:
                raise ValueError("专业平台身份与当前秘书私库不一致，请核对原来的启动身份。")
            platform.session_started = True

    def options(self):
        try:
            self._ready()
            result = self.runtime.platform.request("/professional-tasks/options")
        except PlatformError as error:
            raise ValueError(self._error(error)) from None
        self._identity(result)
        # The model needs choices, not the platform's full internal method prompts.
        result["methods"] = [{"role": item["role"], "category": item.get("category"),
                              "name": item.get("method", {}).get("name"), "readOnly": item.get("method", {}).get("readOnly")}
                             for item in result.get("methods", [])]
        result["models"] = [{"id": item["id"], "runtime": item.get("profile", {}).get("runtime")}
                            for item in result.get("models", [])]
        return result

    def _identity(self, result):
        if (not isinstance(result, dict) or result.get("ownerId") != self.store.owner
                or result.get("workerId") != self.store.worker or not result.get("platformInstanceId")):
            raise ValueError("专业平台返回的身份无法核对，本次不会提交任务。")
        self.store.bind_platform(result["platformInstanceId"])

    @staticmethod
    def _error(error):
        return {
            "PLATFORM_NOT_CONNECTED": "尚未连接专业平台。请先配置一次秘书的平台连接；本机聊天和日常工具仍可使用。",
            401: "专业平台未接受秘书身份或凭证。请核对已登记的秘书和保存的凭证。",
            403: "当前秘书进程或目标计划尚未获平台授权。请确认秘书平台授权、该计划托管及读取范围。",
            404: "平台尚未提供专业任务接口，请更新并重启平台服务。",
            409: "平台来源或托管范围发生变化。请核对原请求记录，不要直接重复提交。",
            422: "专业任务的计划、仓库基线、方法或模型不符合平台要求，请核对秘书报告。",
        }.get(error.status, "专业平台连接未获确认；秘书将查询原请求，不会自动重复提交。")

    def submit(self, work, job, action):
        if self.pending(work["id"]):
            raise ValueError("这项工作已有专业任务正在执行或核对，秘书会继续跟踪原任务，不会重复创建。")
        options = self.options()
        plans = options.get("plans", [])
        plan_id = action.get("planId") or work.get("planId") or work.get("professionalDraftPlanId") or work.get("professionalPlanId")
        choices = [plan for plan in plans if not plan_id or plan["id"] == plan_id]
        if not plans:
            selected = action.get("instruction")
            selected = selected if isinstance(selected, str) and 0 < len(selected) <= 16000 else job["userText"]
            draft_work = {**work, "body": selected,
                          "title": job["userText"].strip().splitlines()[0][:200] if work["title"] == "日常对话" else work["title"]}
            draft = self.runtime.personal_plans.ensure(draft_work)
            if draft.get("state") == "CREATED":
                plan_id = draft["receipt"]["planId"]
                self.runtime.daily._report(work["id"], "NEEDS_HUMAN", "PROFESSIONAL_SCOPE_REQUIRED",
                                           "专业计划草稿已创建，需要确认执行范围。", professionalDraftPlanId=plan_id)
                raise ValueError("已自动创建计划草稿：" + plan_id + "。尚未执行：该计划还没有获准管理的仓库和工具范围。请确认这个计划的执行范围，之后秘书会自行选择方法和模型，无需填写任务配置表。")
            raise ValueError("自动创建的计划草稿结果待核对，秘书不会重复创建。请查看原计划请求记录。")
        if len(choices) != 1:
            raise ValueError("没有唯一可用的已授权计划。请说明使用哪个计划；没有计划时先让秘书创建计划草稿，并确认其专业执行范围。")
        plan = choices[0]
        repositories = [item for item in plan.get("repositories", []) if not action.get("repositoryRef") or item["ref"] == action["repositoryRef"]]
        if len(repositories) != 1:
            raise ValueError("请明确这项工作使用哪个已授权仓库，秘书不会猜测目标仓库。")
        repository = repositories[0]
        baselines = repository.get("baselines", [])
        baseline = action.get("baseRevision") or (baselines[0] if len(baselines) == 1 else None)
        if baseline not in baselines:
            raise ValueError("需要明确已登记的仓库提交基线；没有唯一基线时，秘书不会自行选择或使用浮动 HEAD。")
        models = [item for item in options.get("models", []) if not action.get("aiProfileId") or item["id"] == action["aiProfileId"]]
        if not models:
            raise ValueError("专业平台没有可用的已批准模型，请先配置模型。")
        role = action.get("role")
        if role not in {item.get("role") for item in options.get("methods", [])}:
            raise ValueError("秘书选择的工作方法未在平台登记，请使用 professional_options 返回的方法。")
        instruction = action.get("instruction")
        if not isinstance(instruction, str) or not instruction.strip() or len(instruction) > 16000:
            raise ValueError("需要具体且不超过 16000 字的专业任务说明。")
        request_id = str(uuid.uuid4())
        body = {"requestId": request_id, "planId": plan["id"], "repositoryRef": repository["ref"],
                "baseRevision": baseline, "role": role, "aiProfileId": models[0]["id"], "instruction": instruction}
        if len(encode(body).encode()) > 32768:
            raise ValueError("专业任务说明过长，请缩短要交给 Worker 的任务内容。")
        row = self.store.put("decision", "professional:" + request_id, {"kind": "PROFESSIONAL_TASK", "state": "SENDING",
            "requestId": request_id, "workId": work["id"], "jobId": job["id"], "messageId": job.get("messageId"),
            "body": body, "platformInstanceId": options["platformInstanceId"]}, 0)
        self.runtime.daily._decision(job, state="WAITING_PLATFORM", professionalRequestId=request_id)
        try:
            result = self.runtime.platform.request("/professional-tasks", body)
        except PlatformError as error:
            if error.status in {400, 401, 403, 404, 409, 413, 422}:
                self._save(row, state="REJECTED")
                text = self._error(error)
                self._publish(row, "NEEDS_HUMAN", "PROFESSIONAL_REJECTED", text)
                self.runtime.daily._decision(job, state="BLOCKED", result=text)
                return
            # Even a server error may occur after the transaction committed. Read the original request only.
            row = self._save(row, state="UNKNOWN")
            self._publish(row, "NEEDS_HUMAN", "SUBMISSION_UNCONFIRMED", self._error(error))
            return
        self._receive(row, result)

    def _publish(self, row, state, code, text, **extra):
        work = self.store.get("work", row["workId"])
        if not work or work.get("state") in {"TRASHED", "CANCELLED"}:
            return
        self.runtime.daily._report(work["id"], state, code, text, assistantMode="professional", **extra)
        if row.get("messageId"):
            self.runtime.daily._message(row["messageId"], text, "DONE" if state == "DONE" else "FAILED" if state == "FAILED" else "WAITING_PLATFORM")

    def _receive(self, row, result):
        self._identity(result)
        if result["platformInstanceId"] != row["platformInstanceId"] or result.get("requestId") != row["requestId"]:
            raise ValueError("专业任务回执与原请求或平台实例不一致，执行结果需要核对。")
        preparation = result.get("preparation", {})
        task_id = preparation.get("taskId")
        if preparation.get("state") != "READY":
            text = GAPS.get(preparation.get("errorCode"), "专业任务准备受阻，请核对工作方法和执行条件。")
            self._save(row, state="BLOCKED", receipt=result)
            self._publish(row, "NEEDS_HUMAN", preparation.get("errorCode") or "PREPARATION_BLOCKED", text)
            job = self.store.get("decision", row["jobId"])
            self.runtime.daily._decision(job, state="BLOCKED", result=text)
            return
        task_state = result.get("taskState")
        report = result.get("report") or {}
        if task_state == "COMPLETED" and report.get("summary"):
            passed = report.get("verdict") == "PASS"
            text = ("专业 Worker 已完成工作：\n" if passed else "专业 Worker 已返回结果，需要处理其发现的问题：\n") + report["summary"][:12000]
            state, code, tracking = ("DONE", "COMPLETED", "DONE") if passed else ("NEEDS_HUMAN", "REPAIR_REQUIRED", "BLOCKED")
        elif task_state in {"FAILED", "CANCELLED"}:
            state, code, tracking, text = "FAILED", "WORKER_" + task_state, "FAILED", "专业任务失败或已取消。请查看平台任务报告；秘书不会自动重试原任务。"
        elif task_state == "COMPLETED":
            state, code, tracking, text = "NEEDS_HUMAN", "COMPLETION_REPORT_MISSING", "ACTIVE", "任务显示已结束，但尚未取得 Worker 的结果报告，秘书会继续核对，暂不标记完成。"
        elif task_state == "WAITING_INPUT":
            state, code, tracking, text = "WAITING_CONFIRMATION", "WORKER_WAITING_INPUT", "ACTIVE", "专业 Worker 需要补充信息或确认，请在平台“工作问题与人工控制”查看这项任务的问题。"
        else:
            state, code, tracking = "ACTIVE", "WORKER_" + str(task_state), "ACTIVE"
            text = "专业任务已创建，" + ("Worker 正在处理。" if task_state in {"RUNNING", "CLAIMED", "CONTINUED"} else "正在等待 Worker 处理；若长期无进展，请检查 Worker 在线状态。")
        text += "\n计划：" + result["planId"] + "\n任务：" + str(task_id)
        if row.get("receipt") != result or row.get("state") != tracking:
            self._save(row, state=tracking, receipt=result)
            self._publish(row, state, code, text, professionalTaskId=task_id, professionalPlanId=result["planId"])
        job = self.store.get("decision", row["jobId"])
        wanted = "WAITING_PLATFORM" if tracking == "ACTIVE" else tracking
        if job.get("state") != wanted:
            self.runtime.daily._decision(job, state=wanted, result=text)

    def tick(self):
        if time.monotonic() - self.last_poll < 15 or self.runtime.settings.value.get("paused", False):
            return
        self.last_poll = time.monotonic()
        for row in self.store.list("decision"):
            if row.get("kind") != "PROFESSIONAL_TASK" or row.get("state") not in {"SENDING", "UNKNOWN", "ACTIVE"}:
                continue
            work = self.store.get("work", row["workId"])
            if not work or work.get("state") in {"TRASHED", "CANCELLED"}:
                continue
            try:
                self._ready()
                self._receive(row, self.runtime.platform.request("/professional-tasks/" + row["requestId"]))
            except (PlatformError, ValueError) as error:
                self._publish(row, "NEEDS_HUMAN", "PROFESSIONAL_STATUS_UNCONFIRMED",
                              self._error(error) if isinstance(error, PlatformError) else str(error))
