#!/usr/bin/env python3
"""R10 role-specialized pull worker using the frozen R2 software-process Skills."""
from __future__ import annotations

import json
import os
import signal
import subprocess
import tempfile
import sys
import threading
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, replace
from pathlib import Path
from typing import Any

from process_profiles import ProcessProfile, get_profile
from worker_runtime import Attempt, attempt_scope, current_attempt, git as runtime_git, snapshot, skill_package, workspace_lock, setting, safe_directories
from ai_preferences import step_selection
from agent_runtime import AgentRunRequest, AgentRuntime
from app_server_runtime import CodexAppServerRuntime, preflight as app_server_preflight
from openai_api_runtime import OpenAIApiRuntime
from formal_questions import YieldRequested
import hashlib
from contextlib import nullcontext


class ProtocolError(RuntimeError):
    def __init__(self, status: int, message: str):
        super().__init__(f"worker protocol HTTP {status}: {message}")
        self.status = status


def choose_ai_profile(guidance: dict[str, Any] | None, runtime: str = "codex-app-server") -> tuple[dict[str, Any] | None, str | None]:
    if guidance is None:
        return None, None
    allowed = [item for item in guidance["allowedProfiles"]
               if item["profile"]["provider"] == "openai" and item["profile"]["runtime"] == runtime
               and not item["profile"].get("fast", False)
               and item["profile"].get("effort") in ({"none", "minimal", "low", "medium", "high", "xhigh"}
                     if runtime == "codex-app-server" else {"none", "minimal", "low", "medium", "high"})]
    if not allowed:
        raise ValueError(f"no approved compatible profile for runtime {runtime}")
    previous = guidance.get("previous")
    index = next((i for i, item in enumerate(allowed) if item["id"] == (previous or {}).get("profileId")), -1)
    if index < 0:
        return allowed[0], "initial approved profile"
    if previous.get("failureCategory") in ("MODEL_ERROR", "OUTPUT_INVALID"):
        return allowed[min(index + 1, len(allowed) - 1)], "model failure; select next approved profile"
    return allowed[index], "retain profile after non-model failure"


@dataclass(frozen=True)
class WorkerConfig:
    runtime: str
    control_plane_url: str
    worker_id: str
    worker_token: str
    capabilities: tuple[str, ...]
    process_role: str
    skills_root: str
    max_concurrency: int
    fallback_workspace: str
    repository_ref: str
    repository_root: str
    worktree_root: str
    model: str | None
    reasoning_effort: str
    sandbox: str
    approval_mode: str
    network_access: bool
    poll_interval: float
    heartbeat_interval: float
    http_timeout: float

    @staticmethod
    def from_env() -> "WorkerConfig":
        if os.getenv("AIW_GIT_PUBLISH_REMOTE", "").strip():
            raise ValueError("AIW_GIT_PUBLISH_REMOTE is forbidden: Workers cannot git push")
        if _bool_env("AIW_CODEX_NETWORK_ACCESS", False):
            raise ValueError("AIW_CODEX_NETWORK_ACCESS is forbidden for process Workers")
        repository_ref = os.getenv("AIW_REPOSITORY_REF", "").strip()
        repository_root = os.getenv("AIW_REPOSITORY_ROOT", "").strip()
        if bool(repository_ref) != bool(repository_root):
            raise ValueError("AIW_REPOSITORY_REF and AIW_REPOSITORY_ROOT must be configured together")
        process_role = os.getenv("AIW_PROCESS_ROLE", "requirements").strip().lower()
        runtime = os.getenv("AIW_AGENT_RUNTIME", "codex-app-server").strip().lower()
        if runtime not in {"codex-app-server", "openai-agents"}:
            raise ValueError("AIW_AGENT_RUNTIME must be codex-app-server or openai-agents; codex-sdk was removed")
        if runtime == "codex-app-server":
            app_server_preflight()
            if os.getenv("AIW_CODEX_APPROVAL_MODE", "deny-all") not in {"deny-all", "never"}:
                raise ValueError("Python App Server requires AIW_CODEX_APPROVAL_MODE=deny-all (or never); migrate SDK auto-review explicitly")
            if os.getenv("AIW_CODEX_SANDBOX", "workspace-write") not in {"read-only", "workspace-write"}:
                raise ValueError("Python App Server supports read-only or workspace-write sandbox only")
        if runtime == "openai-agents" and not os.getenv("OPENAI_API_KEY", "").strip():
            raise ValueError("OPENAI_API_KEY is required for openai-agents runtime")
        if runtime == "openai-agents" and _bool_env("AIW_AGENT_ALLOW_SHELL", False):
            raise ValueError("AIW_AGENT_ALLOW_SHELL is not supported by the Python API runtime")
        if process_role in {"release-readiness", "orchestrator"}:
            raise ValueError("ROLE_NONEXECUTABLE")
        profile = get_profile(process_role)
        capabilities = {part.strip() for part in os.getenv("AIW_WORKER_CAPABILITIES", "").split(",") if part.strip()}
        role_capabilities = {"goal-discussion", "requirement-analysis", "solution-architecture", "implementation-planning", "coding", "integration", "repair", "code-review", "verification", "integration-verification", "workflow-review", "release-readiness", "development-orchestration"}
        if any(part == "*" or part in {"flexible-work-v1", "execution-config-v1"} or part.startswith("repo:") or part in role_capabilities for part in capabilities):
            raise ValueError("ROLE_CAPABILITY_DENIED")
        capabilities.update(profile.capabilities)
        if _bool_env("AIW_EXECUTION_GUARD", False):
            from guarded_preflight import capabilities as guarded_capabilities
            capabilities.update(guarded_capabilities(runtime, profile, str(Path(os.getenv("AIW_PROCESS_SKILLS_ROOT", Path(__file__).with_name("skills"))).resolve())))
            from formal_questions import storage_capability
            if not repository_root: raise ValueError("QUESTION_PERSISTENT_REPOSITORY_REQUIRED")
            capabilities.update({"questions-v1", storage_capability(repository_root)})
        elif "execution-guard-v1" in capabilities or "guarded-command-linux-v1" in capabilities or "questions-v1" in capabilities or any(c.startswith("checkpoint-store:") for c in capabilities):
            raise ValueError("GUARDED_CAPABILITIES_REQUIRE_VERIFIED_ADAPTER")
        if _bool_env("AIW_DYNAMIC_CONFIG", False):
            if not _bool_env("AIW_EXECUTION_GUARD", False): raise ValueError("DYNAMIC_CONFIGURATION_REQUIRES_GUARDED_ADAPTER")
            capabilities.add("execution-config-v1")
        if repository_ref:
            capabilities.add(f"repo:{repository_ref}")
        max_concurrency = int(os.getenv("AIW_WORKER_MAX_CONCURRENCY", "1"))
        if max_concurrency < 1:
            raise ValueError("AIW_WORKER_MAX_CONCURRENCY must be >= 1")
        fallback = str(Path(os.getenv("AIW_WORKSPACE", repository_root or ".")).resolve())
        default_worktrees = str((Path(repository_root or fallback).resolve().parent / ".aidev-worktrees").resolve())
        return WorkerConfig(
            runtime=runtime,
            control_plane_url=os.getenv("AIW_CONTROL_PLANE_URL", "http://127.0.0.1:8080").rstrip("/"),
            worker_id=os.getenv("AIW_WORKER_ID", "process-api-" + process_role + "-1" if runtime == "openai-agents" else "codex-python-1"),
            worker_token=os.getenv("AIW_WORKER_TOKEN", ""),
            capabilities=tuple(sorted(capabilities)),
            process_role=process_role,
            skills_root=str(Path(os.getenv("AIW_PROCESS_SKILLS_ROOT", Path(__file__).with_name("skills"))).resolve()),
            max_concurrency=max_concurrency,
            fallback_workspace=fallback,
            repository_ref=repository_ref,
            repository_root=str(Path(repository_root).resolve()) if repository_root else "",
            worktree_root=str(Path(os.getenv("AIW_WORKTREE_ROOT", default_worktrees)).resolve()),
            model=(os.getenv("AIW_OPENAI_MODEL") if runtime == "openai-agents" else os.getenv("AIW_CODEX_MODEL")) or None,
            reasoning_effort=os.getenv("AIW_OPENAI_REASONING_EFFORT", "") if runtime == "openai-agents" else os.getenv("AIW_CODEX_REASONING_EFFORT", "high"),
            sandbox=os.getenv("AIW_CODEX_SANDBOX", "workspace-write"),
            approval_mode=os.getenv("AIW_CODEX_APPROVAL_MODE", "deny-all"),
            network_access=False,
            poll_interval=float(os.getenv("AIW_WORKER_POLL_SECONDS", "1")),
            heartbeat_interval=float(os.getenv("AIW_WORKER_HEARTBEAT_SECONDS", "20")),
            http_timeout=float(os.getenv("AIW_WORKER_HTTP_TIMEOUT_SECONDS", "15")),
        )


class ControlPlaneClient:
    def __init__(self, config: WorkerConfig):
        self.base = config.control_plane_url + "/api/r1/worker-protocol/v1"
        self.token = config.worker_token
        self.timeout = config.http_timeout

    def register(self, config: WorkerConfig) -> dict[str, Any]:
        result = self._post("/workers/register", {
            "workerId": config.worker_id,
            "runtimeType": f"{config.runtime}:{config.process_role}",
            "capabilities": list(config.capabilities),
            "maxConcurrency": config.max_concurrency,
        }) or {}
        if "execution-config-v1" in config.capabilities:
            from execution_configuration import metadata
            self._post(f"/workers/{_quote(config.worker_id)}/configuration", {"workerId": config.worker_id, "capabilities": metadata(config)})
        return result

    def worker_heartbeat(self, worker_id: str) -> None: self._post(f"/workers/{_quote(worker_id)}/heartbeat", None)
    def drain(self, worker_id: str) -> None: self._post(f"/workers/{_quote(worker_id)}/drain", None)
    def offline(self, worker_id: str) -> None: self._post(f"/workers/{_quote(worker_id)}/offline", None)
    def claim(self, worker_id: str) -> dict[str, Any] | None: return self._post("/tasks/claim", {"workerId": worker_id}, allow_no_content=True)
    def start(self, task: dict[str, Any], worker_id: str,
              selection: dict[str, Any] | None, reason: str | None) -> None:
        payload = {"workerId": worker_id, "leaseToken": task["leaseToken"]}
        if selection is not None:
            payload.update(profileId=selection["id"], profileVersion=task["aiGuidance"]["version"], selectionReason=reason)
        self._post(f"/tasks/{_quote(task['taskId'])}/start", payload)
    def task_heartbeat(self, task: dict[str, Any], worker_id: str, session_id: str | None): return self._task_action(task, worker_id, "heartbeat", session_id)
    def fail(self, task: dict[str, Any], worker_id: str, session_id: str | None,
             reason: str | None = None, category: str = "UNKNOWN") -> None:
        self._post(f"/tasks/{_quote(task['taskId'])}/fail", {
            "workerId": worker_id, "leaseToken": task["leaseToken"],
            "externalSessionId": session_id, "failureReason": reason, "failureCategory": category,
        })

    def complete(self, task: dict[str, Any], worker_id: str, session_id: str | None,
                 workspace_result: dict[str, Any] | None, report: dict[str, str] | None) -> dict[str, Any]:
        return self._post(f"/tasks/{_quote(task['taskId'])}/complete", {
            "workerId": worker_id,
            "leaseToken": task["leaseToken"],
            "externalSessionId": session_id,
            "workspaceResult": workspace_result,
            "report": report,
        }) or {}

    def _task_action(self, task: dict[str, Any], worker_id: str, action: str, session_id: str | None):
        return self._post(f"/tasks/{_quote(task['taskId'])}/{action}", {
            "workerId": worker_id, "leaseToken": task["leaseToken"], "externalSessionId": session_id,
        })

    def _post(self, path: str, payload: dict[str, Any] | None, allow_no_content: bool = False):
        data = None if payload is None else json.dumps(payload).encode("utf-8")
        headers = {"Accept": "application/json"}
        if data is not None: headers["Content-Type"] = "application/json"
        if self.token: headers["Authorization"] = "Bearer " + self.token
        req = urllib.request.Request(self.base + path, data=data or b"", headers=headers, method="POST")
        try:
            active = current_attempt()
            timeout = min(self.timeout, active.remaining()) if active else self.timeout
            with urllib.request.urlopen(req, timeout=timeout) as response:
                if response.status == 204: return None if allow_no_content else {}
                body = response.read()
                return json.loads(body.decode("utf-8")) if body else {}
        except urllib.error.HTTPError as error:
            raise ProtocolError(error.code, "REQUEST_REJECTED") from error
        except urllib.error.URLError as error:
            raise ProtocolError(0, "RESPONSE_UNKNOWN") from error


class GitWorkspace:
    def __init__(self, config: WorkerConfig, cwd: str, assignment: dict[str, Any] | None,
                 repository_root: str | None = None):
        self.config = config
        self.cwd = cwd
        self.assignment = assignment
        self.repository_root = repository_root

    @staticmethod
    def materialize(config: WorkerConfig, task: dict[str, Any]) -> "GitWorkspace":
        assignment = task.get("workspace")
        if config.process_role == "goal-discussion" or task.get("subjectType") == "goal-discussion":
            if assignment:
                raise RuntimeError("GOAL_WORKSPACE_ASSIGNMENT_DENIED")
            return GitWorkspace(config, str(Path(tempfile.mkdtemp(prefix="aiw-goal-")).resolve()), None)
        if not assignment:
            return GitWorkspace(config, config.fallback_workspace, None)
        repository_root = assignment.get("repositoryPath") or config.repository_root
        if not repository_root:
            raise RuntimeError("repository task claimed without AIW_REPOSITORY_REF/AIW_REPOSITORY_ROOT")
        if not assignment.get("repositoryPath") and assignment["repositoryRef"] != config.repository_ref:
            raise RuntimeError(f"repository mismatch: task={assignment['repositoryRef']} worker={config.repository_ref}")
        repository_root = str(Path(repository_root).resolve(strict=True))
        if not Path(repository_root).is_dir():
            raise RuntimeError("WORKSPACE_DIRECTORY_INVALID")
        if assignment.get("baseRevision") is None:
            return GitWorkspace(config, repository_root, assignment, repository_root)
        _fetch_inputs(repository_root, task)
        resolved = _git(repository_root, "rev-parse", "--verify", assignment["baseRevision"] + "^{commit}").strip().lower()
        if resolved != assignment["baseRevision"].lower():
            raise RuntimeError(f"base revision mismatch: expected {assignment['baseRevision']} resolved {resolved}")
        safe_directories(config.worktree_root)
        root = Path(config.worktree_root).absolute()
        candidate = (root / assignment["workspaceKey"]).absolute()
        if not candidate.is_relative_to(root) or candidate == root or ".." in Path(assignment["workspaceKey"]).parts:
            raise RuntimeError("WORKSPACE_KEY_INVALID")
        safe_directories(candidate.parent)
        cwd = str(candidate)
        _git(repository_root, "worktree", "add", "-b", assignment["branchName"], cwd, assignment["baseRevision"])
        return GitWorkspace(config, cwd, assignment, repository_root)

    def finalize(self, task: dict[str, Any], read_only=False) -> dict[str, Any] | None:
        if self.assignment is None or self.assignment.get("baseRevision") is None:
            return None
        if not read_only:
            _git(self.cwd, "add", "-A")
            staged = _git(self.cwd, "diff", "--cached", "--name-only", "-z")
            if staged:
                _git(self.cwd, "-c", "user.name=AI Dev Worker", "-c", "user.email=aidev-worker@localhost",
                     "commit", "--no-gpg-sign", "-m", f"aidev: {task['taskId']} attempt {task['attemptNo']}")
        revision = _git(self.cwd, "rev-parse", "HEAD").strip().lower()
        if read_only and revision != self.assignment["baseRevision"].lower():
            raise RuntimeError("READ_ONLY_REVISION_CHANGED")
        changed = sorted(filter(None, _git(self.cwd, "diff", "--name-only", "-z", self.assignment["baseRevision"], revision).split("\0")))
        attempt = current_attempt()
        if attempt:
            attempt.evidence.record("workspace-result", resultRevision=revision, count=len(changed))
        return {"resultRevision": revision, "changedFiles": changed}

    def cleanup(self) -> None:
        if self.assignment is None or self.assignment.get("baseRevision") is None:
            return
        status = "completed"
        try:
            runtime_git(self.repository_root, "worktree", "remove", "--force", self.cwd, cleanup=True)
        except Exception:
            status = "preserved"
        attempt = current_attempt()
        if attempt:
            attempt.evidence.record("workspace-cleanup", status=status)

def _fetch_inputs(repository_root: str, task: dict[str, Any]) -> None:
    if not repository_root:
        return
    for item in task.get("inputs") or []:
        ref = (item.get("publicationRef") or "").strip()
        if not ref.startswith("git:"):
            continue
        parts = ref.split(":", 2)
        if len(parts) != 3:
            continue
        _, remote, branch = parts
        _git(repository_root, "fetch", remote, branch)


def _git(cwd: str, *args: str) -> str:
    return runtime_git(cwd, *args)


def _completion_report(result_kind: str, final_response: str) -> dict[str, str]:
    summary = final_response.strip()
    if not summary:
        raise RuntimeError("COMPLETION_REPORT_EMPTY")
    if result_kind in {"REPORT", "WORKSPACE"}:
        return {"verdict": "PASS", "summary": summary}
    if result_kind != "VERDICT":
        raise RuntimeError("RESULT_KIND_INVALID")
    marker = summary.splitlines()[-1].strip()
    verdict = marker.split(":", 1)[1].strip() if marker.startswith("AIW_VERDICT:") else None
    if verdict not in {"PASS", "REPAIR_REQUIRED"}:
        raise RuntimeError("COMPLETION_VERDICT_INVALID")
    return {"verdict": verdict, "summary": summary}


class ActiveTurn:
    def __init__(self) -> None:
        self._lock = threading.Lock(); self._handle = None
    def set(self, handle) -> None:
        with self._lock: self._handle = handle
    def interrupt(self) -> None:
        with self._lock: handle = self._handle
        if handle is not None:
            try: handle.interrupt()
            except Exception: pass


class LeaseHeartbeat:
    def __init__(self, client: ControlPlaneClient, task: dict[str, Any], worker_id: str, interval: float, attempt=None):
        self.attempt = attempt
        self.client = client; self.task = task; self.worker_id = worker_id; self.interval = max(0.5, interval)
        self.stop_event = threading.Event(); self.ownership_lost = threading.Event(); self.session_id: str | None = None
        self.active_turn = ActiveTurn(); self.thread = threading.Thread(target=self._run, name=f"lease-heartbeat-{task['taskId']}", daemon=True)
    def start(self) -> None: self.thread.start()
    def set_session(self, session_id: str, handle=None) -> None:
        self.session_id = session_id
        if handle is not None: self.active_turn.set(handle)
        try: self._heartbeat()
        except ProtocolError:
            self.ownership_lost.set(); self.active_turn.interrupt()
            if self.attempt: self.attempt.cancel("lease-lost")
    def close(self) -> None:
        self.stop_event.set(); self.thread.join(timeout=self.client.timeout + 1)
        if self.thread.is_alive(): raise RuntimeError("LEASE_HEARTBEAT_UNSETTLED")
    def _heartbeat(self):
        response = self.client.task_heartbeat(self.task, self.worker_id, self.session_id)
        if response and response.get("stopRequested") is True:
            self.active_turn.interrupt()
            if self.attempt: self.attempt.cancel("stop-requested")
    def _run(self) -> None:
        while not self.stop_event.wait(self.interval):
            try: self._heartbeat()
            except ProtocolError as error:
                print(f"lease heartbeat lost for {self.task['taskId']}: {error}", file=sys.stderr, flush=True)
                self.ownership_lost.set(); self.active_turn.interrupt()
                if self.attempt: self.attempt.cancel("lease-lost")
                return


class ProcessSlot:
    def __init__(self, slot_no: int, config: WorkerConfig, client: ControlPlaneClient, stop_event: threading.Event, force_event=None):
        self.force_event = force_event or threading.Event()
        self.runtime: AgentRuntime = CodexAppServerRuntime() if config.runtime == "codex-app-server" else OpenAIApiRuntime.from_env()
        self.slot_no = slot_no; self.config = config; self.client = client; self.stop_event = stop_event
        self.thread = threading.Thread(target=self._run, name=f"process-slot-{slot_no}", daemon=False)
    def start(self) -> None: self.thread.start()
    def join(self) -> None: self.thread.join()
    def _run(self) -> None:
        try:
            runtime_context = nullcontext(None)
            with runtime_context as runtime_client:
                while not self.stop_event.is_set():
                    try:
                        task = self.client.claim(self.config.worker_id)
                        if task is None: self.stop_event.wait(self.config.poll_interval); continue
                        self._execute_task(runtime_client, task)
                    except ProtocolError as error:
                        if self.stop_event.is_set(): return
                        print(f"slot {self.slot_no}: CONTROL_PLANE_POLL_FAILED", file=sys.stderr, flush=True)
                        self.stop_event.wait(self.config.poll_interval)
        except Exception as error:
            print(f"slot {self.slot_no}: AGENT_RUNTIME_INITIALIZATION_FAILED", file=sys.stderr, flush=True); self.stop_event.set()

    def _execute_task(self, runtime_client: Any, task: dict[str, Any]) -> None:
        heartbeat = workspace = attempt = gate = None
        broker = None
        session_id = None
        started = reporting = finished = cleaned = False
        phase = "TOOL_ERROR"
        try:
            attempt = Attempt(self.config, task, self.force_event)
            with attempt_scope(attempt):
                from execution_configuration import load as load_configuration
                prepared = load_configuration(self.config, task)
                profile = prepared.profile if prepared else _profile(self.config, task.get("taskType"))
                selection, reason = choose_ai_profile(task.get("aiGuidance"), self.config.runtime)
                fallback_selection = selection
                default_model = self.config.model or (self.runtime.config.model if self.config.runtime == "openai-agents" else None)
                effective_selection = selection if prepared else step_selection(task, self.config.runtime, selection, default_model, self.config.reasoning_effort)
                if task.get("aiGuidance") and effective_selection is not selection:
                    selection, reason = effective_selection, "Step preference matches an approved profile"
                self.client.start(task, self.config.worker_id, selection, reason)
                started = True
                attempt.evidence.record("attempt-running", status="running")
                heartbeat = LeaseHeartbeat(self.client, task, self.config.worker_id, self.config.heartbeat_interval, attempt)
                heartbeat.start()
                if "execution-guard-v1" in task.get("requiredCapabilities", []):
                    from execution_guard import GuardBroker
                    broker = GuardBroker(self.client, task, self.config.worker_id, attempt)
                    broker.writable = profile.writable
                    if prepared:
                        prepared.verify_context(broker.context)
                        broker.allowed_tools = set(prepared.reference["toolRequirements"])
                    attempt.guard_broker = broker
                if task.get("taskType") not in profile.task_types:
                    raise RuntimeError("ROLE_TASK_TYPE_DENIED")
                if task.get("taskType") != "EXTERNAL_WORK" or task.get("skillRef") != profile.primary_skill:
                    raise RuntimeError("ROLE_SKILL_DENIED")
                assignment = task.get("workspace") or {}
                revision = assignment.get("baseRevision") or ""
                if profile.result_kind == "WORKSPACE" and (len(revision) not in {40, 64} or any(c not in "0123456789abcdefABCDEF" for c in revision)):
                    raise RuntimeError("WRITABLE_WORKSPACE_REQUIRED")
                if profile.result_kind == "VERDICT" and any(item.get("resultRevision") and item["resultRevision"] != revision for item in task.get("inputs") or []):
                    raise RuntimeError("UPSTREAM_REVISION_MISMATCH")
                # Read and freeze trusted skills before workspace side effects.
                instruction = prepared.prompt(task) if prepared else _prompt(task, self.config)
                if task.get("workspace"):
                    repository_gate = workspace_lock(task["workspace"].get("repositoryPath") or self.config.repository_root, attempt)
                    try:
                        if broker:
                            from guarded_workspace import GuardedWorkspace
                            workspace = GuardedWorkspace.materialize(self.config, task, broker)
                        else: workspace = GitWorkspace.materialize(self.config, task)
                    finally: repository_gate.release()
                else:
                    workspace = GitWorkspace.materialize(self.config, task)
                gate = workspace_lock(workspace.cwd, attempt)
                baseline = snapshot(workspace.cwd, attempt) if not profile.writable and not broker else None
                selected = effective_selection["profile"] if effective_selection else None
                for name in ("AGENTS.md", "CODEX.md"):
                    if broker: continue  # Project instructions can be read only by an authorized broker tool.
                    file = Path(workspace.cwd) / name
                    if not file.exists(): continue
                    if file.is_symlink() or not file.is_file() or file.stat().st_size > setting("AIW_WORKER_MAX_INPUT_BYTES", 1024 * 1024):
                        raise RuntimeError("REPOSITORY_INSTRUCTION_UNSAFE")
                    raw = file.read_bytes()
                    attempt.evidence.record("context-source", path=str(file), digest=hashlib.sha256(raw).hexdigest(), bytes=len(raw))
                    instruction += f"\n--- {name} ---\n{raw.decode('utf-8')}"
                if len(instruction.encode("utf-8")) > setting("AIW_WORKER_MAX_INPUT_BYTES", 1024 * 1024):
                    raise RuntimeError("CONTEXT_BUDGET_EXCEEDED")
                attempt.evidence.record("context-built", digest=hashlib.sha256(instruction.encode("utf-8")).hexdigest(), bytes=len(instruction.encode("utf-8")), usageKnown=False, unknown="SDK-added-context-and-model-window")
                attempt.check()
                phase = "MODEL_ERROR"
                request = AgentRunRequest(
                    attempt=attempt, workspace=workspace.cwd, profile=profile, instruction=instruction,
                    model=selected["model"] if selected else self.config.model,
                    reasoning_effort=selected["effort"] if selected else self.config.reasoning_effort,
                    network_access=self.config.network_access,
                    service_tier="default" if selected and task.get("aiGuidance") and self.config.runtime == "codex-app-server" else None,
                    sandbox=self.config.sandbox, approval_mode=self.config.approval_mode, broker=broker,
                )
                def set_session(runtime_session_id, handle=None):
                    nonlocal session_id
                    session_id = runtime_session_id
                    heartbeat.set_session(runtime_session_id, handle)
                preferences = task.get("aiPreferences") or {}
                settings_reason = "Step preference" if preferences.get("model") or preferences.get("effort") else "Worker choice"
                if ((preferences.get("model") and preferences["model"] != request.model)
                        or (preferences.get("effort") and preferences["effort"] != request.reasoning_effort)):
                    settings_reason = "Invalid or unavailable Step preference; use Worker choice for unavailable fields"
                attempt.evidence.record("ai-settings", model=request.model, effort=request.reasoning_effort, reason=settings_reason)
                try:
                    try:
                        final_response = self.runtime.run(runtime_client, request, set_session)
                    except RuntimeError as error:
                        fallback = fallback_selection["profile"] if fallback_selection else {}
                        if (broker or str(error) != "AI_PREFERENCES_REJECTED" or not task.get("aiPreferences")
                                or (request.model == fallback.get("model", default_model)
                                    and request.reasoning_effort == fallback.get("effort", self.config.reasoning_effort))):
                            raise
                        attempt.check()
                        request = replace(request, model=fallback.get("model", default_model),
                                          reasoning_effort=fallback.get("effort", self.config.reasoning_effort),
                                          service_tier="default" if fallback_selection and self.config.runtime == "codex-app-server" else None)
                        attempt.evidence.record("ai-settings-fallback", model=request.model, effort=request.reasoning_effort,
                                                reason="Step model or effort rejected before tool execution; use Worker choice")
                        final_response = self.runtime.run(runtime_client, request, set_session)
                except RuntimeError as error:
                    if broker and broker.pending_question:
                        from formal_questions import YieldRequested
                        raise YieldRequested("QUESTION_WAITING") from error
                    if str(error) == "UNRESOLVED_TOOL_FAILURE": phase = "OUTPUT_INVALID"
                    raise
                if broker and broker.pending_question:
                    from formal_questions import YieldRequested
                    raise YieldRequested("QUESTION_WAITING")
                if heartbeat.ownership_lost.is_set(): raise RuntimeError("LEASE_LOST")
                phase = "OUTPUT_INVALID"
                report = _completion_report(profile.result_kind, final_response)
                if not profile.writable and not broker and snapshot(workspace.cwd, attempt) != baseline:
                    raise RuntimeError("READ_ONLY_WORKSPACE_CHANGED")
                attempt.evidence.record("report-validated", status="valid", digest=hashlib.sha256(final_response.encode("utf-8")).hexdigest())
                phase = "TOOL_ERROR"
                workspace_result = workspace.finalize(task, read_only=not profile.writable)
                if broker: broker.record_delivery(workspace_result, report)
                if profile.result_kind == "WORKSPACE" and (not workspace_result or not workspace_result.get("resultRevision") or not isinstance(workspace_result.get("changedFiles"), list)):
                    raise RuntimeError("WORKSPACE_RESULT_INVALID")
                heartbeat.close()
                attempt.check()
                if heartbeat.ownership_lost.is_set(): raise RuntimeError("LEASE_LOST")
                attempt.evidence.record("report-posting", reportStatus="pending")
                workspace.cleanup(); cleaned = True
                attempt.evidence.finish("ready-to-report"); finished = True
                attempt.check()
                reporting = True
                self.client.complete(task, self.config.worker_id, session_id, workspace_result, report)
                attempt.evidence.record("report-posted", reportStatus="accepted")
                print(f"completed {task['taskId']} attempt={task['attemptNo']}", flush=True)
        except YieldRequested:
            try:
                with attempt_scope(attempt):
                    from formal_questions import acknowledge
                    reporting = True
                    heartbeat.close()
                    acknowledge(broker)
                    workspace.cleanup(); cleaned = True
                    attempt.evidence.finish("waiting-input"); finished = True
                print(f"yielded {task['taskId']} question={broker.pending_question['question']['questionId']}", flush=True)
            except Exception:
                if attempt: attempt.resource_uncertain = True
                print("QUESTION_YIELD_OUTCOME_UNCONFIRMED", file=sys.stderr, flush=True)
        except Exception:
            lost = heartbeat is not None and heartbeat.ownership_lost.is_set()
            status = "lease-lost" if lost else "report-unknown" if reporting else "failed"
            if attempt:
                attempt.cancel("lease-lost" if lost else attempt.reason or "execution-failed")
                try: attempt.evidence.record("attempt-failed", status=status, phase=phase, code="OPERATION_FAILED")
                except Exception: pass
                if attempt.reason in {"deadline", "lease-lost", "force-shutdown"}:
                    try: attempt.evidence.record("attempt-cancelled", reason=attempt.reason)
                    except Exception: pass
            if started and not lost and not reporting:
                try:
                    if broker and attempt.reason == "stop-requested" and not attempt.resource_uncertain and not broker._active:
                        self.client._task_action(task, self.config.worker_id, "stop-acknowledgement", session_id)
                    else: self.client.fail(task, self.config.worker_id, session_id, "OPERATION_FAILED", phase)
                except ProtocolError: pass
            print(f"attempt {task.get('attemptId')}: {status}", file=sys.stderr, flush=True)
        finally:
            if heartbeat:
                try: heartbeat.close()
                except Exception:
                    if attempt: attempt.resource_uncertain = True
            if attempt:
                with attempt_scope(attempt):
                    try:
                        if workspace and not cleaned and not attempt.resource_uncertain: workspace.cleanup()
                        if attempt.resource_uncertain: attempt.evidence.record("resource-terminal", status="unknown", code="WORKSPACE_PRESERVED")
                        elif not finished: attempt.evidence.finish("failed")
                    except Exception: print("CRITICAL_EVIDENCE_OR_CLEANUP_FAILED", file=sys.stderr, flush=True)
                attempt.close()
            if gate and not (attempt and attempt.resource_uncertain): gate.release()
            if attempt and attempt.resource_uncertain: self.stop_event.set()

def _profile(config: WorkerConfig, task_type: str | None = None) -> ProcessProfile:
    return get_profile(config.process_role, task_type)


def _read_skill_package(skills_root: str, skill_name: str) -> str:
    content = skill_package(skills_root, skill_name)
    active = current_attempt()
    broker = getattr(active, "guard_broker", None) if active else None
    if broker:
        expected = [ref for ref in broker.context["snapshot"]["references"] if ref["kind"] == "skill" and ref["id"] == skill_name]
        if len(expected) != 1 or expected[0]["sha256"] != hashlib.sha256(content.encode()).hexdigest():
            raise RuntimeError("FIXED_SKILL_REFERENCE_MISSING_OR_CHANGED")
    return content


def _skill_context(config: WorkerConfig, task_type=None) -> str:
    profile = _profile(config, task_type)
    parts = [
        "R2 PROCESS AUTHORITY BOUNDARY:\n"
        "- The Control Plane owns lifecycle state, scheduling, durable IDs, approval and authoritative commits.\n"
        "- This worker owns bounded semantic execution only.\n"
        "- Never create a second workflow engine or claim lifecycle authority.\n"
        "- Treat upstream inputs supplied by the Control Plane as authoritative.\n"
        "- Never run git push or publish a Git branch. Return the local result revision only.\n"
    ]
    for skill in profile.protocol_skills:
        parts.append(_read_skill_package(config.skills_root, skill))
    if profile.primary_skill != "engineering-artifact-protocol":
        parts.append(_read_skill_package(config.skills_root, profile.primary_skill))
    return "\n\n".join(parts)


def _prompt(task: dict[str, Any], config: WorkerConfig) -> str:
    task_type = task.get("taskType")
    profile = _profile(config, task_type)
    if profile.task_types and task_type not in profile.task_types:
        raise RuntimeError(f"profile {profile.name} cannot execute task type {task_type}; allowed={profile.task_types}")
    if not profile.task_types:
        raise RuntimeError(f"profile {profile.name} is preserved from R2 but R9 has no matching executable TaskType")
    instruction = (task.get("instruction") or "").strip() or f"Execute AI Dev Manager task {task['taskId']} for {task.get('subjectType')} {task.get('subjectId')}."
    if task.get("inputs"):
        instruction += "\n\nUpstream task inputs (authoritative):\n" + json.dumps(task["inputs"], indent=2)
    if task_type != "EXTERNAL_WORK" or task.get("skillRef") != profile.primary_skill:
        raise RuntimeError("ROLE_SKILL_DENIED")
    skill_ref = (task.get("skillRef") or "").strip()
    skill_line = f"\nControl Plane skill reference: {skill_ref}" if skill_ref else ""
    permission = "WRITABLE repository task" if profile.writable else "READ-ONLY semantic/review task; do not modify repository files"
    return (
        f"AI Dev Manager R10 Process Worker\n"
        f"Process role: {profile.name}\n"
        f"Primary R2 Skill: {profile.primary_skill}\n"
        f"Profile instruction: {profile.instruction}\n"
        f"Permission boundary: {permission}\n"
        f"Task ID: {task['taskId']}\nTask type: {task_type}\nAttempt: {task.get('attemptNo')}{skill_line}\n\n"
        f"=== Frozen R2 process context ===\n{_skill_context(config, task_type)}\n\n"
        f"=== Current authoritative work instruction ===\n{instruction}\n\n"
        "Execute only this bounded task. Preserve the Control Plane authority boundary. "
        "For read-only roles, do not change repository files. For writable roles, work only inside the assigned workspace."
    )


def _quote(value: str) -> str:
    from urllib.parse import quote
    return quote(value, safe="")

def _bool_env(name: str, default: bool) -> bool:
    raw = os.getenv(name); return default if raw is None else raw.strip().lower() in {"1", "true", "yes", "on"}


def main() -> int:
    config = WorkerConfig.from_env(); client = ControlPlaneClient(config); stop_event = threading.Event()
    force_event = threading.Event()
    def stop(_signum=None, _frame=None):
        if stop_event.is_set(): force_event.set()
        stop_event.set()
    signal.signal(signal.SIGINT, stop); signal.signal(signal.SIGTERM, stop)
    profile = _profile(config)
    slots = [ProcessSlot(i + 1, config, client, stop_event, force_event) for i in range(config.max_concurrency)]
    client.register(config)
    print(f"registered worker={config.worker_id} role={profile.name} primarySkill={profile.primary_skill} capabilities={','.join(config.capabilities)} concurrency={config.max_concurrency}", flush=True)
    for slot in slots: slot.start()
    try:
        while not stop_event.wait(config.heartbeat_interval): client.worker_heartbeat(config.worker_id)
    except (KeyboardInterrupt, ProtocolError): stop_event.set()
    finally:
        try: client.drain(config.worker_id)
        except ProtocolError: pass
        for slot in slots: slot.join()
        try: client.offline(config.worker_id)
        except ProtocolError: pass
    return 0


if __name__ == "__main__": raise SystemExit(main())
