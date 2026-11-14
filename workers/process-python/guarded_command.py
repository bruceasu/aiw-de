"""Fixed command adapter: delegated cgroups on Linux and approved App Server processes on Windows."""
from __future__ import annotations

import base64
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
    if sys.platform == "win32":
        shell = Path(os.environ.get("SystemRoot", r"C:\Windows")) / "System32" / "WindowsPowerShell" / "v1.0" / "powershell.exe"
        if not shell.is_file(): raise RuntimeError("POWERSHELL_NOT_AVAILABLE")
        return None, []
    if sys.platform != "linux": raise RuntimeError("GUARDED_COMMAND_PLATFORM_UNSUPPORTED")
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
    group = root / ("aiw-" + uuid.uuid4().hex) if root else None
    if group: group.mkdir(mode=0o700)
    session = None
    empty = Path(tempfile.mkdtemp(prefix="aiw-command-"))
    profile = command["permissionProfile"]
    if group:
        # Linux command/exec uses its configured sandbox and the delegated cgroup.
        config = [f"default_permissions = {json.dumps(profile)}", 'approval_policy = "never"', 'web_search = "disabled"',
                  f"[permissions.{json.dumps(profile)}.filesystem]", '":root" = "deny"', '":minimal" = "read"',
                  f"{json.dumps(str(workspace))} = \"write\""]
        config += [f"{json.dumps(str(tool))} = \"read\"" for tool in tools]
        config += [f"{json.dumps(str(workspace / p))} = \"deny\"" for p in (".git", ".ai", ".env", ".codex", ".venv")]
        config += [f"{json.dumps(str(workspace / '**' / '.env'))} = \"deny\"", f"{json.dumps(str(empty))} = \"deny\""]
        config += ['"/sys/fs/cgroup" = "deny"', '"/run" = "deny"', '"/var/run" = "deny"']
        config += [f"[permissions.{json.dumps(profile)}.network]", "enabled = false", "dangerously_allow_all_unix_sockets = false"]
        (empty / "config.toml").write_text("\n".join(config) + "\n", encoding="utf-8")
    environment = {k: os.environ[k] for k in ("PATH", "PATHEXT", "SystemRoot", "WINDIR", "ComSpec") if k in os.environ}
    environment.update({"CODEX_HOME": str(empty), "HOME": str(empty), "TMPDIR": str(workspace), "NO_COLOR": "1"})
    cancel_callback = None
    uncertain = False
    try:
        if group:
            (group / "memory.max").write_text(str(command["memoryBytes"]), encoding="ascii")
            (group / "memory.swap.max").write_text("0", encoding="ascii")
            (group / "pids.max").write_text(str(command["processLimit"]), encoding="ascii")
            (group / "cpu.max").write_text("100000 100000", encoding="ascii")
            launcher = str(Path(__file__).with_name("cgroup_launcher.py"))
            session = StdioSession(attempt, str(empty), argv=[sys.executable, launcher, str(group), preflight(), "app-server", "--listen", "stdio://"], environment=environment)
            cancel_callback = lambda: (group / "cgroup.kill").write_text("1", encoding="ascii")
        else:
            argv = list(command["argv"])
            executable = Path(argv[0]).name.casefold() if argv else ""
            if executable in {"python", "python.exe"}:
                argv[0] = sys.executable
            elif executable in {"powershell", "powershell.exe"}:
                shell = (Path(os.environ.get("SystemRoot", r"C:\Windows")) / "System32" / "WindowsPowerShell" / "v1.0" / "powershell.exe").resolve(strict=True)
                argv[0] = str(shell)
                if "-noprofile" not in [value.casefold() for value in argv[1:]]:
                    argv.insert(1, "-NoProfile")
                if "-noninteractive" not in [value.casefold() for value in argv[1:]]:
                    argv.insert(2, "-NonInteractive")
            session = StdioSession(attempt, str(empty), environment=environment,
                                   windows_job_memory_bytes=command["memoryBytes"],
                                   windows_job_process_limit=command["processLimit"])
            cancel_callback = session.interrupt
        if cancel_callback is not None: attempt.add_cancel_callback(cancel_callback)
        session.rpc("initialize", {"clientInfo": {"name": "aiw_command_broker", "version": "1"}, "capabilities": {"experimentalApi": True}})
        session.send({"method": "initialized", "params": {}}, time.monotonic() + 10)
        command_environment = {"HOME": str(empty), "TMPDIR": str(workspace)}
        if group is None:
            command_environment.update({"USERPROFILE": str(empty), "TEMP": str(workspace), "TMP": str(workspace)})
        if group:
            from guarded_app_server import verify_config
            verify_config(session, empty, profile, command=True)
            request = {
                "command": command["argv"], "cwd": str(cwd), "env": command_environment,
                "permissionProfile": profile, "processId": "command-" + uuid.uuid4().hex,
                "timeoutMs": command["timeoutMs"], "disableTimeout": False, "disableOutputCap": False,
                "streamStdin": False, "streamStdoutStderr": False, "tty": False,
                "outputBytesCap": command["outputBytes"],
            }
            result = session.rpc("command/exec", request, timeout=command["timeoutMs"] / 1000 + 10)
            if type(result.get("exitCode")) is not int or not isinstance(result.get("stdout"), str) or not isinstance(result.get("stderr"), str): raise RuntimeError("COMMAND_RECEIPT_UNSUPPORTED")
            if len(result["stdout"].encode()) + len(result["stderr"].encode()) > command["outputBytes"]:
                raise RuntimeError("COMMAND_OUTPUT_LIMIT")
            return result

        # Windows process/spawn is unsandboxed; AIW requires approval for every command.
        process_handle = "command-" + uuid.uuid4().hex
        session.rpc("process/spawn", {
            "command": argv, "cwd": str(cwd), "processHandle": process_handle,
            "streamStdoutStderr": True, "outputBytesCap": command["outputBytes"],
            "timeoutMs": command["timeoutMs"], "env": command_environment,
        }, timeout=10)
        output = {"stdout": bytearray(), "stderr": bytearray()}
        deadline = time.monotonic() + command["timeoutMs"] / 1000
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise RuntimeError("COMMAND_TIMEOUT")
            try:
                event = session.next_event(timeout=remaining)
            except RuntimeError as error:
                if str(error) == "CODEX_RPC_TIMEOUT":
                    raise RuntimeError("COMMAND_TIMEOUT") from None
                raise
            method, params = event.get("method"), event.get("params", {})
            if not isinstance(params, dict) or params.get("processHandle") != process_handle:
                continue
            if method == "process/outputDelta":
                stream = params.get("stream")
                if stream not in output or not isinstance(params.get("deltaBase64"), str):
                    raise RuntimeError("COMMAND_OUTPUT_UNSUPPORTED")
                try:
                    output[stream].extend(base64.b64decode(params["deltaBase64"], validate=True))
                except ValueError:
                    raise RuntimeError("COMMAND_OUTPUT_UNSUPPORTED") from None
                if params.get("capReached") or len(output["stdout"]) + len(output["stderr"]) > command["outputBytes"]:
                    raise RuntimeError("COMMAND_OUTPUT_LIMIT")
            elif method == "process/exited":
                exit_code = params.get("exitCode")
                if time.monotonic() >= deadline:
                    raise RuntimeError("COMMAND_TIMEOUT")
                if type(exit_code) is not int or params.get("outputDrainTimedOut") is True:
                    raise RuntimeError("COMMAND_RECEIPT_UNSUPPORTED")
                return {"exitCode": exit_code,
                        "stdout": output["stdout"].decode("utf-8", errors="replace"),
                        "stderr": output["stderr"].decode("utf-8", errors="replace")}
    finally:
        try:
            if group:
                (group / "cgroup.kill").write_text("1", encoding="ascii")
                deadline = time.monotonic() + 10
                while "populated 0" not in (group / "cgroup.events").read_text():
                    if time.monotonic() >= deadline: raise RuntimeError("COMMAND_CGROUP_NOT_QUIESCENT")
                    time.sleep(0.02)
            if session is not None: session.close()
            if group: group.rmdir()
        except Exception:
            uncertain = True
            attempt.resource_uncertain = True
            raise RuntimeError("COMMAND_RESOURCE_OUTCOME_UNKNOWN") from None
        finally:
            if cancel_callback is not None:
                attempt.remove_cancel_callback(cancel_callback)
            if not uncertain: shutil.rmtree(empty)
