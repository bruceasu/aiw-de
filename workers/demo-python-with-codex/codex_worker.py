#!/usr/bin/env python3
"""R5 external Codex worker with per-attempt Git worktree isolation."""
from __future__ import annotations

import json
import os
import signal
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, replace
from pathlib import Path
from typing import Any

from openai_codex import ApprovalMode, Codex, Sandbox


class ProtocolError(RuntimeError):
    def __init__(self, status: int, message: str):
        super().__init__(f"worker protocol HTTP {status}: {message}")
        self.status = status


@dataclass(frozen=True)
class WorkerConfig:
    control_plane_url: str
    worker_id: str
    worker_token: str
    capabilities: tuple[str, ...]
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
            raise ValueError("AIW_CODEX_NETWORK_ACCESS is forbidden for Workers")
        repository_ref = os.getenv("AIW_REPOSITORY_REF", "").strip()
        repository_root = os.getenv("AIW_REPOSITORY_ROOT", "").strip()
        if bool(repository_ref) != bool(repository_root):
            raise ValueError("AIW_REPOSITORY_REF and AIW_REPOSITORY_ROOT must be configured together")
        capabilities = {part.strip() for part in os.getenv("AIW_WORKER_CAPABILITIES", "coding").split(",") if part.strip()}
        if repository_ref:
            capabilities.add(f"repo:{repository_ref}")
        max_concurrency = int(os.getenv("AIW_WORKER_MAX_CONCURRENCY", "1"))
        if max_concurrency < 1:
            raise ValueError("AIW_WORKER_MAX_CONCURRENCY must be >= 1")
        fallback = str(Path(os.getenv("AIW_WORKSPACE", repository_root or ".")).resolve())
        default_worktrees = str((Path(repository_root or fallback).resolve().parent / ".aidev-worktrees").resolve())
        return WorkerConfig(
            control_plane_url=os.getenv("AIW_CONTROL_PLANE_URL", "http://127.0.0.1:8080").rstrip("/"),
            worker_id=os.getenv("AIW_WORKER_ID", "codex-python-1"),
            worker_token=os.getenv("AIW_WORKER_TOKEN", ""),
            capabilities=tuple(sorted(capabilities)),
            max_concurrency=max_concurrency,
            fallback_workspace=fallback,
            repository_ref=repository_ref,
            repository_root=str(Path(repository_root).resolve()) if repository_root else "",
            worktree_root=str(Path(os.getenv("AIW_WORKTREE_ROOT", default_worktrees)).resolve()),
            model=os.getenv("AIW_CODEX_MODEL") or None,
            reasoning_effort=os.getenv("AIW_CODEX_REASONING_EFFORT", "high"),
            sandbox=os.getenv("AIW_CODEX_SANDBOX", "workspace-write"),
            approval_mode=os.getenv("AIW_CODEX_APPROVAL_MODE", "auto-review"),
            network_access=False,
            poll_interval=float(os.getenv("AIW_WORKER_POLL_SECONDS", "1")),
            heartbeat_interval=float(os.getenv("AIW_WORKER_HEARTBEAT_SECONDS", "20")),
            http_timeout=float(os.getenv("AIW_WORKER_HTTP_TIMEOUT_SECONDS", "15")),
        )


class ControlPlaneClient:
    def __init__(self, config: WorkerConfig):
        self.base = config.control_plane_url + "/api/worker-protocol/v1"
        self.token = config.worker_token
        self.timeout = config.http_timeout

    def register(self, config: WorkerConfig) -> dict[str, Any]:
        return self._post("/workers/register", {
            "workerId": config.worker_id,
            "runtimeType": "codex-python-sdk",
            "capabilities": list(config.capabilities),
            "maxConcurrency": config.max_concurrency,
        }) or {}

    def worker_heartbeat(self, worker_id: str) -> None: self._post(f"/workers/{_quote(worker_id)}/heartbeat", None)
    def drain(self, worker_id: str) -> None: self._post(f"/workers/{_quote(worker_id)}/drain", None)
    def offline(self, worker_id: str) -> None: self._post(f"/workers/{_quote(worker_id)}/offline", None)
    def claim(self, worker_id: str) -> dict[str, Any] | None: return self._post("/tasks/claim", {"workerId": worker_id}, allow_no_content=True)
    def start(self, task: dict[str, Any], worker_id: str) -> None: self._task_action(task, worker_id, "start", None)
    def task_heartbeat(self, task: dict[str, Any], worker_id: str, session_id: str | None) -> dict[str, Any]: return self._task_action(task, worker_id, "heartbeat", session_id) or {}
    def acknowledge_stop(self, task: dict[str, Any], worker_id: str) -> None: self._task_action(task, worker_id, "stop-acknowledgement", None)
    def fail(self, task: dict[str, Any], worker_id: str, session_id: str | None, reason: str | None = None) -> None:
        self._post(f"/tasks/{_quote(task['taskId'])}/fail", {
            "workerId": worker_id, "leaseToken": task["leaseToken"],
            "externalSessionId": session_id, "failureReason": reason,
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
            with urllib.request.urlopen(req, timeout=self.timeout) as response:
                if response.status == 204: return None if allow_no_content else {}
                body = response.read()
                return json.loads(body.decode("utf-8")) if body else {}
        except urllib.error.HTTPError as error:
            body = error.read().decode("utf-8", errors="replace")
            raise ProtocolError(error.code, body) from error
        except urllib.error.URLError as error:
            raise ProtocolError(0, str(error.reason)) from error


class GitWorkspace:
    def __init__(self, config: WorkerConfig, cwd: str, assignment: dict[str, Any] | None):
        self.config = config
        self.cwd = cwd
        self.assignment = assignment

    @staticmethod
    def materialize(config: WorkerConfig, task: dict[str, Any]) -> "GitWorkspace":
        assignment = task.get("workspace")
        if not assignment:
            return GitWorkspace(config, config.fallback_workspace, None)
        repository_root = assignment.get("repositoryPath") or config.repository_root
        if not repository_root:
            raise RuntimeError("repository task claimed without AIW_REPOSITORY_REF/AIW_REPOSITORY_ROOT")
        if not assignment.get("repositoryPath") and assignment["repositoryRef"] != config.repository_ref:
            raise RuntimeError(f"repository mismatch: task={assignment['repositoryRef']} worker={config.repository_ref}")
        if not assignment.get("baseRevision"):
            return GitWorkspace(config, str(Path(repository_root).resolve(strict=True)), assignment)
        config = replace(config, repository_root=repository_root)
        _fetch_inputs(config, task)
        resolved = _git(config.repository_root, "rev-parse", "--verify", assignment["baseRevision"] + "^{commit}").strip().lower()
        if resolved != assignment["baseRevision"].lower():
            raise RuntimeError(f"base revision mismatch: expected {assignment['baseRevision']} resolved {resolved}")
        Path(config.worktree_root).mkdir(parents=True, exist_ok=True)
        cwd = str((Path(config.worktree_root) / assignment["workspaceKey"]).resolve())
        _git(config.repository_root, "worktree", "add", "-b", assignment["branchName"], cwd, assignment["baseRevision"])
        return GitWorkspace(config, cwd, assignment)

    def finalize(self, task: dict[str, Any]) -> dict[str, Any] | None:
        if self.assignment is None or not self.assignment.get("baseRevision"):
            return None
        _git(self.cwd, "add", "-A")
        staged = subprocess.check_output(["git", "-C", self.cwd, "diff", "--cached", "--name-only", "-z"])
        if staged:
            subprocess.run([
                "git", "-C", self.cwd,
                "-c", "user.name=AI Dev Worker", "-c", "user.email=aidev-worker@localhost",
                "commit", "--no-gpg-sign", "-m", f"aidev: {task['taskId']} attempt {task['attemptNo']}",
            ], check=True)
        result_revision = _git(self.cwd, "rev-parse", "HEAD").strip().lower()
        raw = subprocess.check_output(["git", "-C", self.cwd, "diff", "--name-only", "-z", self.assignment["baseRevision"], result_revision])
        changed = sorted(p for p in raw.decode("utf-8").split("\0") if p)
        return {"resultRevision": result_revision, "changedFiles": changed, "publicationRef": None}

    def cleanup(self) -> None:
        if self.assignment is None or not self.assignment.get("baseRevision"):
            return
        try:
            _git(self.config.repository_root, "worktree", "remove", "--force", self.cwd)
        except Exception as error:
            print(f"worktree cleanup failed {self.cwd}: {error}", file=sys.stderr, flush=True)


def _fetch_inputs(config: WorkerConfig, task: dict[str, Any]) -> None:
    if not config.repository_root:
        return
    for item in task.get("inputs") or []:
        ref = (item.get("publicationRef") or "").strip()
        if not ref.startswith("git:"):
            continue
        parts = ref.split(":", 2)
        if len(parts) != 3:
            continue
        _, remote, branch = parts
        _git(config.repository_root, "fetch", remote, branch)


def _git(cwd: str, *args: str) -> str:
    if "push" in args:
        raise RuntimeError("GIT_PUSH_FORBIDDEN")
    return subprocess.check_output(["git", "-C", cwd, *args], text=True, stderr=subprocess.STDOUT)


def _completion_report(task: dict[str, Any], final_response: str) -> dict[str, str] | None:
    task_type = task.get("taskType")
    if task_type in {"REQUIREMENT_ANALYSIS", "SOLUTION_ARCHITECTURE", "IMPLEMENTATION_PLANNING"}:
        summary = final_response.strip()
        if not summary:
            raise RuntimeError(f"{task_type} must return a non-empty final response")
        return {"verdict": "PASS", "summary": summary}
    if task_type not in {"CODE_REVIEW", "VERIFICATION", "INTEGRATION_VERIFICATION"}:
        return None
    verdict = None
    for line in reversed(final_response.splitlines()):
        line = line.strip()
        if line.startswith("AIW_VERDICT:"):
            verdict = line.split(":", 1)[1].strip()
            break
    if verdict not in {"PASS", "REPAIR_REQUIRED"}:
        raise RuntimeError("review/verification final response must end with AIW_VERDICT: PASS or AIW_VERDICT: REPAIR_REQUIRED")
    summary = final_response or f"AIW_VERDICT: {verdict}"
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
    def __init__(self, client: ControlPlaneClient, task: dict[str, Any], worker_id: str, interval: float):
        self.client = client; self.task = task; self.worker_id = worker_id; self.interval = max(0.5, interval)
        self.stop_event = threading.Event(); self.ownership_lost = threading.Event(); self.stop_requested = threading.Event(); self.session_id: str | None = None
        self.active_turn = ActiveTurn(); self.thread = threading.Thread(target=self._run, name=f"lease-heartbeat-{task['taskId']}", daemon=True)
    def start(self) -> None: self.thread.start()
    def set_session(self, session_id: str, handle=None) -> None:
        self.session_id = session_id
        if handle is not None: self.active_turn.set(handle)
        try:
            if self.client.task_heartbeat(self.task, self.worker_id, self.session_id).get("stopRequested"):
                self.stop_requested.set(); self.active_turn.interrupt()
        except ProtocolError: self.ownership_lost.set(); self.active_turn.interrupt()
    def close(self) -> None: self.stop_event.set(); self.thread.join(timeout=self.interval + 1)
    def _run(self) -> None:
        while not self.stop_event.wait(self.interval):
            try:
                if self.client.task_heartbeat(self.task, self.worker_id, self.session_id).get("stopRequested"):
                    self.stop_requested.set(); self.active_turn.interrupt(); return
            except ProtocolError as error:
                print(f"lease heartbeat lost for {self.task['taskId']}: {error}", file=sys.stderr, flush=True)
                self.ownership_lost.set(); self.active_turn.interrupt(); return


class CodexSlot:
    def __init__(self, slot_no: int, config: WorkerConfig, client: ControlPlaneClient, stop_event: threading.Event):
        self.slot_no = slot_no; self.config = config; self.client = client; self.stop_event = stop_event
        self.thread = threading.Thread(target=self._run, name=f"codex-slot-{slot_no}", daemon=False)
    def start(self) -> None: self.thread.start()
    def join(self) -> None: self.thread.join()
    def _run(self) -> None:
        try:
            with Codex() as codex:
                while not self.stop_event.is_set():
                    try:
                        task = self.client.claim(self.config.worker_id)
                        if task is None: self.stop_event.wait(self.config.poll_interval); continue
                        self._execute_task(codex, task)
                    except ProtocolError as error:
                        if self.stop_event.is_set(): return
                        print(f"slot {self.slot_no} protocol error: {error}", file=sys.stderr, flush=True)
                        self.stop_event.wait(self.config.poll_interval)
        except Exception as error:
            print(f"slot {self.slot_no} Codex SDK initialization failed: {error}", file=sys.stderr, flush=True); self.stop_event.set()

    def _execute_task(self, codex: Codex, task: dict[str, Any]) -> None:
        heartbeat: LeaseHeartbeat | None = None; workspace: GitWorkspace | None = None; session_id: str | None = None
        stop_before_start = False
        publication_started = False
        try:
            try: self.client.start(task, self.config.worker_id)
            except ProtocolError:
                stop_before_start = bool(self.client.task_heartbeat(task, self.config.worker_id, None).get("stopRequested"))
                if stop_before_start: return
                raise
            heartbeat = LeaseHeartbeat(self.client, task, self.config.worker_id, self.config.heartbeat_interval); heartbeat.start()
            workspace = GitWorkspace.materialize(self.config, task)
            thread = codex.thread_start(
                approval_mode=_approval_mode(self.config.approval_mode), cwd=workspace.cwd, model=self.config.model,
                sandbox=_sandbox(self.config.sandbox),
                config={"model_reasoning_effort": self.config.reasoning_effort,
                        "sandbox_workspace_write": {"network_access": self.config.network_access}},
            )
            session_id = thread.id; turn = thread.turn(_prompt(task)); heartbeat.set_session(session_id, turn)
            if heartbeat.ownership_lost.is_set() or heartbeat.stop_requested.is_set(): return
            result = turn.run()
            if heartbeat.ownership_lost.is_set() or heartbeat.stop_requested.is_set(): return
            publication_started = True
            workspace_result = workspace.finalize(task)
            final_response = (result.final_response or "").strip()
            report = _completion_report(task, final_response)
            self.client.complete(task, self.config.worker_id, session_id, workspace_result, report)
            final = final_response.replace("\n", " ")
            rev = workspace_result["resultRevision"] if workspace_result else "-"
            print(f"completed {task['taskId']} attempt={task['attemptNo']} thread={session_id} revision={rev}: {final[:240]}", flush=True)
        except ProtocolError as error:
            if heartbeat is not None and heartbeat.ownership_lost.is_set(): return
            if heartbeat is not None and heartbeat.stop_requested.is_set(): return
            print(f"task {task.get('taskId')} protocol failure: {error}", file=sys.stderr, flush=True)
        except Exception as error:
            if heartbeat is not None and heartbeat.ownership_lost.is_set(): return
            if heartbeat is not None and heartbeat.stop_requested.is_set(): return
            print(f"task {task.get('taskId')} execution failed: {error}", file=sys.stderr, flush=True)
            try: self.client.fail(task, self.config.worker_id, session_id, str(error))
            except ProtocolError as report_error: print(f"cannot report task failure: {report_error}", file=sys.stderr, flush=True)
        finally:
            if heartbeat is not None: heartbeat.close()
            cleanup_ok = True
            try:
                if workspace is not None: workspace.cleanup()
            except Exception:
                cleanup_ok = False
                raise
            finally:
                if cleanup_ok and not publication_started and (stop_before_start or (heartbeat is not None and heartbeat.stop_requested.is_set())):
                    try: self.client.acknowledge_stop(task, self.config.worker_id)
                    except ProtocolError as error: print(f"cannot acknowledge stop for {task.get('taskId')}: {error}", file=sys.stderr, flush=True)


def _prompt(task: dict[str, Any]) -> str:
    instruction = (task.get("instruction") or "").strip() or f"Execute AI Dev Manager task {task['taskId']} for {task.get('subjectType')} {task.get('subjectId')}."
    if task.get("inputs"):
        instruction += "\n\nUpstream task inputs (authoritative):\n" + json.dumps(task["inputs"], indent=2)
    skill_ref = (task.get("skillRef") or "").strip(); skill_line = f"\nSkill reference: {skill_ref}" if skill_ref else ""
    return (f"Task ID: {task['taskId']}\nTask type: {task.get('taskType')}\nAttempt: {task.get('attemptNo')}{skill_line}\n\n"
            f"Instruction:\n{instruction}\n\nWork only inside the assigned workspace. Complete the requested engineering work and leave it in a verifiable state.")


def _sandbox(value: str) -> Sandbox:
    mapping = {"read-only": Sandbox.read_only, "workspace-write": Sandbox.workspace_write, "full-access": Sandbox.full_access}
    key = value.strip().lower().replace("_", "-")
    if key not in mapping: raise ValueError(f"unsupported AIW_CODEX_SANDBOX: {value}")
    return mapping[key]

def _approval_mode(value: str) -> ApprovalMode:
    mapping = {"auto-review": ApprovalMode.auto_review, "deny-all": ApprovalMode.deny_all}
    key = value.strip().lower().replace("_", "-")
    if key not in mapping: raise ValueError(f"unsupported AIW_CODEX_APPROVAL_MODE: {value}")
    return mapping[key]

def _quote(value: str) -> str:
    from urllib.parse import quote
    return quote(value, safe="")

def _bool_env(name: str, default: bool) -> bool:
    raw = os.getenv(name); return default if raw is None else raw.strip().lower() in {"1", "true", "yes", "on"}


def main() -> int:
    config = WorkerConfig.from_env(); client = ControlPlaneClient(config); stop_event = threading.Event()
    def stop(_signum=None, _frame=None): stop_event.set()
    signal.signal(signal.SIGINT, stop); signal.signal(signal.SIGTERM, stop)
    client.register(config)
    print(f"registered worker={config.worker_id} capabilities={','.join(config.capabilities)} concurrency={config.max_concurrency}", flush=True)
    slots = [CodexSlot(i + 1, config, client, stop_event) for i in range(config.max_concurrency)]
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
