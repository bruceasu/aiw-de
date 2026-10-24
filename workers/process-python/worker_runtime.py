"""Common attempt behavior. Only metadata is persisted; SDK internals remain opaque."""
from __future__ import annotations
import hashlib
import json
import os
import signal
import subprocess
import threading
import time
import uuid
from contextlib import contextmanager
from pathlib import Path
from datetime import datetime, timezone

OWNER = "aiw.worker.attempt.v1"
_local = threading.local()
_locks_guard = threading.Lock()
_locks = {}

def setting(name, default):
    value = int(os.getenv(name, str(default)))
    if value < 1:
        raise ValueError(f"INVALID_{name}")
    return value

def _regular_parents(target):
    for entry in [*reversed(target.parents), target]:
        if entry.exists():
            if entry.is_symlink() or getattr(entry, "is_junction", lambda: False)() or not entry.is_dir():
                raise RuntimeError("DIRECTORY_UNSAFE")
        else:
            entry.mkdir(mode=0o700)

class Evidence:
    fields = set("status runtime unknown usageKnown path digest bytes count inputBytes outputBytes durationMs complete truncated operation callId parentCallId tool inputTokens outputTokens cachedTokens publicationRef resultRevision sideEffect verdict code reportStatus reason model effort".split())
    def __init__(self, config, task):
        root = Path(os.getenv("AIW_WORKER_EVIDENCE_ROOT", str(Path(config.fallback_workspace).parent / ".aidev-attempt-evidence"))).absolute()
        for candidate in (config.fallback_workspace, config.repository_root, config.worktree_root):
            if candidate and root.resolve().is_relative_to(Path(candidate).resolve()):
                raise RuntimeError("EVIDENCE_MUST_BE_OUTSIDE_WORKSPACE")
        _regular_parents(root)
        self.prune(root)
        self.directory = root / uuid.uuid4().hex
        self.directory.mkdir(mode=0o700)
        self.lock = threading.Lock()
        self.failed = False
        self.limit = setting("AIW_WORKER_EVIDENCE_MAX_BYTES", 4 * 1024 * 1024)
        self.identity = {"taskId": task["taskId"], "attemptId": task["attemptId"], "role": config.process_role}
        header = json.dumps({"owner": OWNER, **self.identity}, ensure_ascii=False).encode("utf-8")
        self._write("owner.json", header)
        self.bytes = len(header)
        api_runtime = getattr(config, "runtime", "codex-app-server") == "openai-agents"
        self.record("attempt-started", status="running", runtime="openai-agents-python" if api_runtime else "codex-app-server", usageKnown=False,
                    unknown="model-window-and-token-count" if api_runtime else "inner-tool-policies-and-model-window")

    def _write(self, name, data):
        descriptor = os.open(self.directory / name, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(data)

    def record(self, event, **values):
        with self.lock:
            if self.failed:
                raise RuntimeError("CRITICAL_EVIDENCE_FAILED")
            try:
                if set(values) - self.fields:
                    raise RuntimeError("EVIDENCE_FIELD_FORBIDDEN")
                raw = (json.dumps({**self.identity, "event": event, "time": datetime.now(timezone.utc).isoformat(), **values}, ensure_ascii=False) + "\n").encode("utf-8")
                if self.bytes + len(raw) > self.limit:
                    raise RuntimeError("EVIDENCE_CAPACITY_EXCEEDED")
                descriptor = os.open(self.directory / "events.jsonl", os.O_CREAT | os.O_APPEND | os.O_WRONLY, 0o600)
                with os.fdopen(descriptor, "ab") as stream:
                    stream.write(raw)
                self.bytes += len(raw)
            except Exception:
                self.failed = True
                raise

    def finish(self, status):
        self.record("attempt-terminal", status=status)
        with self.lock:
            marker = json.dumps({"owner": OWNER, "status": status, "time": datetime.now(timezone.utc).isoformat()}).encode("utf-8")
            try:
                if self.bytes + len(marker) > self.limit:
                    raise RuntimeError("EVIDENCE_CAPACITY_EXCEEDED")
                self._write("terminal.json", marker)
                self.bytes += len(marker)
            except Exception:
                self.failed = True
                raise

    @staticmethod
    def prune(root):
        import shutil
        for directory in root.iterdir():
            if directory.is_symlink() or getattr(directory, "is_junction", lambda: False)() or not directory.is_dir():
                continue
            if not directory.resolve().is_relative_to(root.resolve()):
                raise RuntimeError("RETENTION_PATH_INVALID")
            try:
                files = [directory / "owner.json", directory / "terminal.json"]
                if any(not file.is_file() or file.is_symlink() for file in files):
                    continue
                owner, terminal = (json.loads(file.read_text(encoding="utf-8")) for file in files)
                terminal_time = datetime.fromisoformat(terminal["time"].replace("Z", "+00:00")).timestamp()
                if owner.get("owner") == OWNER and terminal.get("owner") == OWNER and time.time() - terminal_time > 30 * 86400:
                    shutil.rmtree(directory)
            except (FileNotFoundError, ValueError, KeyError, TypeError):
                continue

class Attempt:
    def __init__(self, config, task, force_event, *, evidence=None, timeout_ms=None):
        duration = timeout_ms if timeout_ms is not None else setting("AIW_WORKER_ATTEMPT_TIMEOUT_MS", setting("AIW_AGENT_TASK_TIMEOUT_MS", 900000))
        self.deadline = time.monotonic() + duration / 1000
        self.evidence = evidence if evidence is not None else Evidence(config, task)
        self.cancelled = threading.Event()
        self.closed = threading.Event()
        self.force_event = force_event
        self.turn = None
        self.reason = None
        self.interrupt_started = False
        self.interrupt_lock = threading.Lock()
        self.cancel_callbacks = []
        self.resource_uncertain = False
        self.watcher = threading.Thread(target=self._watch, daemon=True)
        self.watcher.start()

    def _watch(self):
        while not self.closed.wait(0.05):
            if time.monotonic() >= self.deadline or self.force_event.is_set():
                self.cancel("deadline" if time.monotonic() >= self.deadline else "force-shutdown")
                return

    def cancel(self, reason="cancelled"):
        self.reason = self.reason or reason
        already_cancelled = self.cancelled.is_set()
        self.cancelled.set()
        with self.interrupt_lock:
            turn = self.turn if not self.interrupt_started else None
            if turn is not None: self.interrupt_started = True
            callbacks = () if already_cancelled else tuple(self.cancel_callbacks)
        if turn is not None:
            def interrupt():
                try: turn.interrupt()
                except Exception: pass
            # The public SDK interruption request may itself block; never block cancellation.
            threading.Thread(target=interrupt, daemon=True).start()
        for callback in callbacks:
            try: callback()
            except Exception: pass

    def add_cancel_callback(self, callback):
        with self.interrupt_lock:
            self.cancel_callbacks.append(callback)
            cancelled = self.cancelled.is_set()
        if cancelled:
            try: callback()
            except Exception: pass

    def remove_cancel_callback(self, callback):
        with self.interrupt_lock:
            self.cancel_callbacks = [item for item in self.cancel_callbacks if item is not callback]

    def check(self):
        if self.cancelled.is_set() or self.force_event.is_set() or time.monotonic() >= self.deadline:
            self.cancel("deadline" if time.monotonic() >= self.deadline else "force-shutdown")
            raise RuntimeError("ATTEMPT_CANCELLED")

    def remaining(self):
        self.check()
        return max(0.001, self.deadline - time.monotonic())

    def run_turn(self, turn):
        self.turn = turn
        return self.run_operation(turn.run)

    def run_operation(self, operation):
        self.check()
        done = threading.Event()
        result = []
        def execute():
            try:
                self.check()
                result.append((True, operation()))
            except BaseException as error:
                result.append((False, error))
            finally:
                done.set()
        threading.Thread(target=execute, daemon=True).start()
        while not done.wait(0.05):
            try:
                self.check()
            except RuntimeError:
                self.cancel()
                # Python SDK offers interruption, but no proof of descendant termination.
                self.resource_uncertain = True
                done.wait(setting("AIW_WORKER_CLEANUP_TIMEOUT_MS", 10000) / 1000)
                raise
        self.check()
        if not result[0][0]:
            raise RuntimeError("CODEX_TURN_FAILED") from result[0][1]
        return result[0][1]

    def close(self):
        self.closed.set()
        self.watcher.join(timeout=1)

def current_attempt():
    return getattr(_local, "attempt", None)

@contextmanager
def attempt_scope(attempt):
    previous = current_attempt()
    _local.attempt = attempt
    try:
        yield
    finally:
        _local.attempt = previous

def workspace_lock(cwd, attempt):
    key = os.path.normcase(str(Path(cwd).resolve()))
    with _locks_guard:
        lock = _locks.setdefault(key, threading.Lock())
    while not lock.acquire(timeout=0.05):
        if attempt:
            attempt.check()
    return lock

def safe_directories(path):
    _regular_parents(Path(path).absolute())

def git(cwd, *args, cleanup=False):
    if "push" in args:
        raise RuntimeError("GIT_PUSH_FORBIDDEN")
    attempt = None if cleanup else current_attempt()
    if attempt:
        attempt.check()
    timeout = setting("AIW_WORKER_CLEANUP_TIMEOUT_MS" if cleanup else "AIW_WORKER_GIT_TIMEOUT_MS", 10000 if cleanup else 120000) / 1000
    deadline = time.monotonic() + min(timeout, attempt.remaining() if attempt else timeout)
    allowed = {"PATH", "PATHEXT", "SYSTEMROOT", "WINDIR", "COMSPEC", "TEMP", "TMP", "HOME", "USERPROFILE", "LANG", "SSH_AUTH_SOCK", "GIT_SSH", "GIT_SSH_COMMAND"}
    env = {key: value for key, value in os.environ.items() if key.upper() in allowed}
    env["GIT_TERMINAL_PROMPT"] = "0"
    windows = os.name == "nt"
    child = subprocess.Popen(["git", "-C", cwd, *args], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, stdin=subprocess.DEVNULL,
        env=env, start_new_session=not windows, creationflags=(subprocess.CREATE_NO_WINDOW | subprocess.CREATE_NEW_PROCESS_GROUP) if windows else 0)
    output = bytearray()
    exceeded = threading.Event()
    def collect():
        while chunk := child.stdout.read(65536):
            remaining = 1024 * 1024 - len(output)
            output.extend(chunk[:remaining])
            if len(chunk) > remaining:
                exceeded.set()
    reader = threading.Thread(target=collect, daemon=True)
    reader.start()
    try:
        while child.poll() is None:
            if attempt:
                attempt.check()
            if time.monotonic() >= deadline:
                raise RuntimeError("GIT_TIMEOUT")
            time.sleep(0.02)
        reader.join(timeout=1)
        if reader.is_alive():
            raise RuntimeError("PROCESS_TREE_UNCERTAIN")
        if child.returncode != 0:
            raise RuntimeError("GIT_COMMAND_FAILED")
        if exceeded.is_set():
            raise RuntimeError("GIT_OUTPUT_TOO_LARGE")
        return output.decode("utf-8")
    except Exception:
        if windows:
            try:
                subprocess.run([str(Path(os.environ.get("SystemRoot", "C:/Windows")) / "System32/taskkill.exe"), "/PID", str(child.pid), "/T", "/F"],
                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=5, creationflags=subprocess.CREATE_NO_WINDOW)
            except Exception:
                if attempt:
                    attempt.resource_uncertain = True
                child.kill()
        else:
            try:
                os.killpg(child.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
        try:
            child.wait(timeout=5)
            reader.join(timeout=1)
            if reader.is_alive() and attempt:
                attempt.resource_uncertain = True
        except subprocess.TimeoutExpired:
            if attempt:
                attempt.resource_uncertain = True
        raise
    finally:
        if not reader.is_alive():
            child.stdout.close()

def snapshot(cwd, attempt):
    digest = hashlib.sha256()
    total = 0
    count = 0
    for directory, dirs, names in os.walk(cwd, followlinks=False):
        dirs[:] = sorted(name for name in dirs if name.lower() != ".git")
        for name in sorted([*dirs, *names]):
            if name.lower() == ".git":
                continue
            attempt.check()
            path = Path(directory) / name
            relative = path.relative_to(cwd).as_posix()
            info = path.lstat()
            count += 1
            digest.update(f"{relative}:{info.st_mode}\n".encode("utf-8"))
            if count > 50000:
                raise RuntimeError("SNAPSHOT_CAPACITY_EXCEEDED")
            if path.is_symlink():
                digest.update(os.readlink(path).encode("utf-8"))
            elif path.is_file():
                total += info.st_size
                if total > 128 * 1024 * 1024:
                    raise RuntimeError("SNAPSHOT_CAPACITY_EXCEEDED")
                with path.open("rb") as stream:
                    while chunk := stream.read(65536):
                        attempt.check()
                        digest.update(chunk)
    return digest.hexdigest()

def skill_package(root, skill):
    if not skill or Path(skill).name != skill or skill in {".", ".."}:
        raise RuntimeError("SKILL_NAME_INVALID")
    directory = Path(root) / skill
    if directory.is_symlink() or getattr(directory, "is_junction", lambda: False)() or not directory.is_dir():
        raise RuntimeError("SKILL_DIRECTORY_UNSAFE")
    files = [directory / "SKILL.md"]
    for subdir in ("references", "agents"):
        start = directory / subdir
        if not start.exists():
            continue
        for parent, dirs, names in os.walk(start, followlinks=False):
            for name in [*dirs, *names]:
                entry = Path(parent) / name
                if entry.is_symlink() or getattr(entry, "is_junction", lambda: False)():
                    raise RuntimeError("SKILL_REFERENCE_UNSAFE")
                if entry.is_file() and entry.suffix.lower() in {".md", ".yaml", ".yml", ".json"}:
                    files.append(entry)
    chunks = []
    total = 0
    attempt = current_attempt()
    for file in [files[0], *sorted(files[1:])]:
        if file.is_symlink() or not file.is_file():
            raise RuntimeError("SKILL_FILE_UNSAFE")
        total += file.stat().st_size
        if total > setting("AIW_WORKER_MAX_INPUT_BYTES", 1024 * 1024):
            raise RuntimeError("CONTEXT_BUDGET_EXCEEDED")
        raw = file.read_bytes()
        if attempt:
            attempt.check()
            attempt.evidence.record("context-source", path=str(file), digest=hashlib.sha256(raw).hexdigest(), bytes=len(raw))
        chunks.append(f"\n--- {skill}/{file.relative_to(directory).as_posix()} ---\n{raw.decode('utf-8')}\n")
    return "".join(chunks).strip()
