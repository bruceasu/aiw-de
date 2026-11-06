"""Owner-bound native turns and location-backed questions. No credential import or model calls."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import socket
import sys
from types import SimpleNamespace

from formal_questions import YieldRequested
from run_contract import RuntimeCapabilities
from run_channel import RunChannel, encoded

CAPABILITY = "worker-loop-turn-v1"
QUESTION_CAPABILITY = "questions-v2"
PROTOCOL = "aiw.questions.v2"


def storage_capability():
    namespace = os.environ.get("AIW_HANDOFF_STORE_ID", socket.gethostname().lower()).strip()
    if not namespace or len(namespace) > 512:
        raise RuntimeError("HANDOFF_STORE_ID_INVALID")
    return "handoff-store:" + hashlib.sha256(namespace.encode("utf-8")).hexdigest()


def capabilities(config):
    root = Path(__file__).resolve().parent
    modules = ("native_run.py", "run_channel.py", "run_contract.py", "process_worker.py", "worker_runtime.py",
               "app_server_runtime.py", "plan_workspace.py", "agent_runtime.py", "process_profiles.py", "skill_files.py")
    value = {"runtime": config.runtime, "role": config.process_role, "modelPolicy": config.model,
             "effort": config.reasoning_effort, "sandbox": config.sandbox, "approval": config.approval_mode,
             "network": config.network_access, "platform": sys.platform, "python": list(sys.version_info[:2]),
             "codexPath": os.environ.get("AIW_CODEX_PATH", "codex"),
             "codexHome": str(Path(os.environ.get("CODEX_HOME", "~/.codex")).expanduser().absolute()),
             "code": {name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in modules}}
    return {CAPABILITY, QUESTION_CAPABILITY, storage_capability(), "native-config:" + hashlib.sha256(encoded(value)).hexdigest()}


def _json_report(text):
    cleaned = text.strip()
    lines = cleaned.splitlines()
    if lines and re.fullmatch(r"AIW_VERDICT:\s*(PASS|REPAIR_REQUIRED)", lines[-1]):
        cleaned = "\n".join(lines[:-1]).strip()
    fenced = re.fullmatch(r"```(?:json)?[ \t]*\r?\n(.*?)\r?\n?```", cleaned, re.DOTALL | re.IGNORECASE)
    if fenced:
        cleaned = fenced.group(1)
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise RuntimeError("NATIVE_REPORT_DUPLICATE_FIELD")
            result[key] = value
        return result
    try:
        value = json.loads(cleaned, object_pairs_hook=pairs)
    except json.JSONDecodeError:
        return None
    return value if isinstance(value, dict) else None


def input_question(text):
    if not text.strip():
        return None
    value = _json_report(text)
    statuses = {"BLOCKED_INPUT", "BLOCKED_BEFORE_IMPLEMENTATION", "WAITING_INPUT", "NEEDS_CLARIFICATION"}
    plain = re.fullmatch(r"(?:\*\*)?(?:result|outcome|status|结果|状态)(?:\*\*)?\s*[:：]\s*(?:\*\*)?`?(BLOCKED_INPUT|BLOCKED_BEFORE_IMPLEMENTATION|WAITING_INPUT|NEEDS_CLARIFICATION)`?(?:\*\*)?", text.strip().splitlines()[0], re.IGNORECASE)
    if not (isinstance(value, dict) and (value.get("requiresInput") is True or str(value.get("outcome", "")).upper() in statuses) or plain):
        return None
    raw = value.get("question") if value else None
    question = raw if isinstance(raw, dict) else {"text": raw} if isinstance(raw, str) else {}
    title = question.get("text")
    if not isinstance(title, str) or not title.strip() or len(title) > 8000:
        title = "Please provide the missing decision or information described in the retained report before this task continues."
    options = question.get("options", [])
    if not isinstance(options, list) or len(options) > 12 or any(not isinstance(item, str) or not item.strip() or len(item) > 1000 for item in options):
        options = []
    context = question.get("context", "")
    requirements = question.get("answerRequirements", "Provide the missing input. This answer does not grant new tool, file, command or billing permissions.")
    return {"question": title, "context": context if isinstance(context, str) and len(context) <= 6000 else "",
            "options": options, "answerRequirements": requirements if isinstance(requirements, str) and len(requirements) <= 2000 else "Provide the missing input within the original task scope."}


class NativeRun:
    def __init__(self, config, client, task, attempt, availability):
        self.config, self.client, self.task, self.attempt = config, client, task, attempt
        self.base = "/tasks/" + task["taskId"]
        self.lease = {"workerId": config.worker_id, "attemptId": task["attemptId"], "leaseToken": task["leaseToken"]}
        self.availability = availability
        self.configuration = next((item for item in config.capabilities if item.startswith("native-config:")), None)
        if self.configuration is None:
            raise RuntimeError("NATIVE_CONFIGURATION_CAPABILITY_MISSING")
        broker = SimpleNamespace(client=client, task=task, attempt=attempt, base=self.base, lease=self.lease)
        caps = RuntimeCapabilities("BACKEND", "BACKEND", "WORKER_PROJECTION", "TURN", "NONE", "NONE", "TURN", "BEST_EFFORT", "UNKNOWN", "BACKEND_TURN")
        self.channel = RunChannel(broker, caps, self.configuration)
        attempt.run_channel = self.channel
        self.active = False
        self.pending = None
        self.question_key = None

    def begin(self):
        self.channel.enter()
        self.active = True
        self.attempt.evidence.record("native-turn-started", status="running")
        try:
            self._load_checkpoint()
        except Exception:
            # An unverified continuation must not become an ordinary repair/retry.
            self.attempt.resource_uncertain = True
            raise

    def _load_checkpoint(self):
        records = self.client._post(self.base + "/questions/checkpoints", self.lease)
        if not isinstance(records, list) or len(records) > 1:
            raise RuntimeError("NATIVE_CHECKPOINT_SET_INVALID")
        if QUESTION_CAPABILITY in self.task.get("requiredCapabilities", []) and not records:
            raise RuntimeError("NATIVE_CHECKPOINT_REQUIRED")
        for item in records:
            if (not isinstance(item, dict) or item.get("storageCapability") not in self.config.capabilities
                    or not isinstance(item.get("taskId"), str) or not item["taskId"]
                    or not isinstance(item.get("attemptId"), str) or not item["attemptId"]
                    or type(item.get("answerRevision")) is not int or item["answerRevision"] < 1
                    or not re.fullmatch(r"[0-9a-f]{64}", str(item.get("answerDigest", "")))):
                raise RuntimeError("NATIVE_CHECKPOINT_IDENTITY_INVALID")
            ref = item.get("handoff", {})
            expected = ".ai/handoffs/" + hashlib.sha256(item["taskId"].encode()).hexdigest() + "/" + hashlib.sha256(item["attemptId"].encode()).hexdigest() + "/manifest.json"
            if not isinstance(ref, dict) or ref.get("manifest") != expected or not re.fullmatch(r"[0-9a-f]{64}", str(ref.get("sha256", ""))):
                raise RuntimeError("NATIVE_CHECKPOINT_SOURCE_CHANGED")
        self.task["checkpointInputs"] = records

    def end(self):
        if self.active:
            self.channel.leave()
            self.active = False
            if not self.attempt.resource_uncertain:
                self.attempt.evidence.record("execution-quiescent", count=0, complete=True)
        if not self.attempt.resource_uncertain:
            self.channel.flush()

    def prompt(self):
        return ("\nThis Plan has a fixed run Owner and location-backed questions. Keep the original goal and tool authority. "
                "If required input or a decision is missing, do not invent an answer or report PASS. Return a JSON report with "
                '"outcome":"BLOCKED_INPUT", "completed":false, "verdict":"REPAIR_REQUIRED", and '
                '"question":{"text":"the missing decision", "context":"necessary facts only", "options":[], "answerRequirements":"what is needed"}. '
                "If the task requires a final AIW_VERDICT line, keep that line after the JSON report and use REPAIR_REQUIRED. "
                "The host will retain a local handoff and request a confirmed answer. Do not execute more actions after this blocked report.")

    def validate_report(self, report, text):
        if input_question(text) is None:
            return report
        detail = _json_report(text)
        if (detail is not None and detail.get("verdict") == "PASS") or re.fullmatch(r"AIW_VERDICT:\s*PASS", text.strip().splitlines()[-1]):
            raise RuntimeError("COMPLETION_REPORT_CONFLICT")
        return {**report, "verdict": "REPAIR_REQUIRED"}

    def maybe_question(self, workspace, report, text):
        question = input_question(text)
        if question is None:
            return
        if report["verdict"] != "REPAIR_REQUIRED":
            raise RuntimeError("NATIVE_INPUT_BLOCKER_VERDICT_CONFLICT")
        from worker_runtime import snapshot
        try:
            tree = snapshot(workspace.cwd, self.attempt)
        except Exception:
            self.attempt.resource_uncertain = True
            raise
        ref = workspace.handoff(self.task, report, None, checkpoint_tree_digest=tree)
        self.task["_handoff"] = ref
        question["context"] += ("\nRetained report and working tree: " + ref["manifest"] + "; manifest SHA-256 " + ref["sha256"] + ". Raw files remain at the Worker.")
        self.end()
        self.attempt.check()
        source = self.client._post(self.base + "/questions/source", self.lease)
        self.question_key = hashlib.sha256(encoded({"task": self.task["taskId"], "instructionRevision": self.task["instructionRevision"], "configuration": self.configuration, "gap": "INPUT"})).hexdigest()
        checkpoint = {"protocol": PROTOCOL, "location": self.task["location"], "handoff": ref, "changedFiles": [],
                      "configurationDigest": self.channel.configuration_digest, "storageCapability": storage_capability()}
        payload = {"protocol": PROTOCOL, "sourceQuestionKey": self.question_key, "source": source, "lease": self.lease,
                   "checkpoint": checkpoint, **question}
        self.attempt.evidence.record("question-intent", questionKey=self.question_key, status="pending")
        try:
            result = self.client._post(self.base + "/questions/open", payload)
            actual = result.get("question", {})
            ack = result.get("ack", {})
            if (actual.get("protocol") != PROTOCOL or actual.get("sourceAttemptId") != self.task["attemptId"]
                    or actual.get("source") != source or actual.get("checkpoint") != checkpoint
                    or actual.get("sourceQuestionKey") != self.question_key or set(ack) != {"questionId", "checkpointDigest"}
                    or ack["questionId"] != actual.get("questionId") or not re.fullmatch(r"[0-9a-f]{64}", str(ack.get("checkpointDigest", "")))):
                raise RuntimeError("NATIVE_QUESTION_RECEIPT_CHANGED")
            self.pending = result
        except Exception:
            self.attempt.resource_uncertain = True
            raise
        raise YieldRequested("NATIVE_QUESTION_WAITING")

    def acknowledge(self):
        if self.pending is None or self.active or self.attempt.resource_uncertain:
            raise RuntimeError("NATIVE_QUESTION_QUIESCENCE_UNCONFIRMED")
        value = {"protocol": PROTOCOL, "lease": self.lease, "allProcessesQuiescent": True, **self.pending["ack"]}
        from worker_runtime import attempt_scope
        # This releases resources only; no model or tool may run after the original deadline.
        with attempt_scope(None):
            self.channel.client._post(self.base + "/questions/ack?version=v2", value)
        self.attempt.evidence.record("question-yield", questionId=value["questionId"], status="acknowledged")


def open_run(config, client, task, attempt):
    if config.runtime != "codex-app-server" or "execution-guard-v1" in task.get("requiredCapabilities", []):
        return None
    lease = {"workerId": config.worker_id, "attemptId": task["attemptId"], "leaseToken": task["leaseToken"]}
    availability = client._post("/tasks/" + task["taskId"] + "/execution-run/availability", lease)
    if availability.get("supported") is not True:
        if CAPABILITY in task.get("requiredCapabilities", []):
            raise RuntimeError("NATIVE_PLAN_OWNER_BINDING_REQUIRED")
        return None
    expected = next(item for item in capabilities(config) if item.startswith("native-config:"))
    if expected not in config.capabilities:
        raise RuntimeError("NATIVE_ADAPTER_CHANGED_DRAIN_REQUIRED")
    try:
        return NativeRun(config, client, task, attempt, availability)
    except Exception:
        attempt.resource_uncertain = True
        raise
