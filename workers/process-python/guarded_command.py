"""Explicit Linux/WSL-only command adapter. No model request, account lookup or inherited secrets."""
from __future__ import annotations

import json
import os
from pathlib import Path
import sys
import shutil
import tempfile
import time
import uuid

from app_server_runtime import StdioSession, preflight
from execution_guard import safe_path


def configuration():
    if sys.platform != "linux": raise RuntimeError("GUARDED_COMMAND_REQUIRES_LINUX_OR_WSL")
    root = Path(os.environ.get("AIW_COMMAND_CGROUP_ROOT", "")).absolute()
    mount = Path("/sys/fs/cgroup").resolve(strict=True)
    if not os.environ.get("AIW_COMMAND_CGROUP_ROOT") or root.is_symlink() or root.resolve(strict=True) == mount or not root.resolve().is_relative_to(mount):
        raise RuntimeError("DELEGATED_CGROUP_V2_ROOT_REQUIRED")
    if not {"cpu", "memory", "pids"}.issubset(set((root / "cgroup.controllers").read_text().split())):
        raise RuntimeError("CGROUP_RESOURCE_CONTROLLERS_REQUIRED")
    if not {"cpu", "memory", "pids"}.issubset(set((root / "cgroup.subtree_control").read_text().split())):
        raise RuntimeError("HOST_MUST_ENABLE_DELEGATED_CONTROLLERS")
    tools = json.loads(os.environ.get("AIW_COMMAND_TOOLCHAIN_ROOTS", "[]"))
    if not isinstance(tools, list) or not tools or len(tools) > 16: raise RuntimeError("EXPLICIT_TOOLCHAIN_READ_ROOTS_REQUIRED")
    paths = []
    for value in tools:
        path = Path(value).absolute()
        if not path.is_absolute() or str(path) in {"/", "/home", "/root", "/etc", "/run", "/sys", "/proc", "/var"} or path.is_symlink():
            raise RuntimeError("TOOLCHAIN_READ_ROOT_TOO_BROAD")
        paths.append(path.resolve(strict=True))
    return root.resolve(), paths


def run_command(workspace, command, attempt):
    root, tools = configuration()
    cwd = safe_path(workspace, command["cwd"])
    if not cwd.is_dir(): raise RuntimeError("COMMAND_CWD_INVALID")
    group = root / ("aiw-" + uuid.uuid4().hex)
    group.mkdir(mode=0o700)
    session = None
    empty = Path(tempfile.mkdtemp(prefix="aiw-command-"))
    profile = command["permissionProfile"]
    # This command-only home is generated from the human-approved profile and exact current workspace.
    # It never contains auth.json, platform credentials, private policy or the model's home.
    config = [f"default_permissions = {json.dumps(profile)}", 'approval_policy = "never"', 'web_search = "disabled"',
              f"[permissions.{json.dumps(profile)}.filesystem]", '":root" = "deny"', '":minimal" = "read"',
              f"{json.dumps(str(workspace))} = \"write\""]
    config += [f"{json.dumps(str(tool))} = \"read\"" for tool in tools]
    config += [f"{json.dumps(str(workspace / p))} = \"deny\"" for p in (".git", ".ai", ".env", ".codex", ".venv")]
    config += [f"{json.dumps(str(workspace / '**' / '.env'))} = \"deny\"", f"{json.dumps(str(empty))} = \"deny\"",
               '"/sys/fs/cgroup" = "deny"', '"/run" = "deny"', '"/var/run" = "deny"',
               f"[permissions.{json.dumps(profile)}.network]", "enabled = false", "dangerously_allow_all_unix_sockets = false"]
    (empty / "config.toml").write_text("\n".join(config) + "\n", encoding="utf-8")
    environment = {k: os.environ[k] for k in ("PATH",) if k in os.environ}
    environment.update({"CODEX_HOME": str(empty), "HOME": str(empty), "TMPDIR": str(workspace), "NO_COLOR": "1"})
    callback = lambda: (group / "cgroup.kill").write_text("1", encoding="ascii")
    uncertain = False
    try:
        (group / "memory.max").write_text(str(command["memoryBytes"]), encoding="ascii")
        (group / "memory.swap.max").write_text("0", encoding="ascii")
        (group / "pids.max").write_text(str(command["processLimit"]), encoding="ascii")
        (group / "cpu.max").write_text("100000 100000", encoding="ascii")
        launcher = str(Path(__file__).with_name("cgroup_launcher.py"))
        session = StdioSession(attempt, str(empty), argv=[sys.executable, launcher, str(group), preflight(), "app-server", "--listen", "stdio://"], environment=environment)
        attempt.add_cancel_callback(callback)
        session.rpc("initialize", {"clientInfo": {"name": "aiw_command_broker", "version": "1"}, "capabilities": {"experimentalApi": True}})
        session.send({"method": "initialized", "params": {}}, time.monotonic() + 10)
        from guarded_app_server import verify_config
        verify_config(session, empty, profile, command=True)
        result = session.rpc("command/exec", {
            "command": command["argv"], "cwd": str(cwd), "env": {"HOME": str(empty), "TMPDIR": str(workspace)},
            "permissionProfile": profile, "processId": "command-" + uuid.uuid4().hex,
            "timeoutMs": command["timeoutMs"], "outputBytesCap": command["outputBytes"],
            "disableTimeout": False, "disableOutputCap": False, "streamStdin": False, "streamStdoutStderr": False, "tty": False,
        }, timeout=command["timeoutMs"] / 1000 + 10)
        if type(result.get("exitCode")) is not int or not isinstance(result.get("stdout"), str) or not isinstance(result.get("stderr"), str): raise RuntimeError("COMMAND_RECEIPT_UNSUPPORTED")
        if len(result["stdout"].encode()) + len(result["stderr"].encode()) > command["outputBytes"]: raise RuntimeError("COMMAND_OUTPUT_LIMIT")
        return result
    finally:
        try:
            (group / "cgroup.kill").write_text("1", encoding="ascii")
            deadline = time.monotonic() + 10
            while "populated 0" not in (group / "cgroup.events").read_text():
                if time.monotonic() >= deadline: raise RuntimeError("COMMAND_CGROUP_NOT_QUIESCENT")
                time.sleep(0.02)
            if session is not None: session.close()
            group.rmdir()
        except Exception:
            uncertain = True
            attempt.resource_uncertain = True
            raise RuntimeError("COMMAND_RESOURCE_OUTCOME_UNKNOWN") from None
        finally:
            attempt.remove_cancel_callback(callback)
            if not uncertain: shutil.rmtree(empty)
