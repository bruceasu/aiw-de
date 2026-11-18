"""Task-authorized host commands with bounded output and owned processes."""
import base64
import os
from pathlib import Path
import signal
import subprocess
import sys
import threading
import time


def shell_executable():
    if sys.platform == "win32":
        shell = Path(os.environ.get("SystemRoot", r"C:\Windows")) / "System32/WindowsPowerShell/v1.0/powershell.exe"
    elif sys.platform == "linux":
        shell = Path("/bin/sh")
    else:
        return None
    return str(shell) if shell.is_file() else None


def run_shell(workspace, command, attempt):
    shell = shell_executable()
    if shell is None:
        raise RuntimeError("TASK_SHELL_UNAVAILABLE")
    attempt.check()
    windows = sys.platform == "win32"
    env = {key: os.environ[key] for key in ("PATH", "PATHEXT", "SystemRoot", "WINDIR", "ComSpec",
                                           "JAVA_HOME", "LANG", "LC_ALL") if key in os.environ}
    env["NO_COLOR"] = "1"
    if windows:
        from windows_process import WindowsJob
        # Encode the script as data so PowerShell receives the exact command text.
        script = "[Console]::OutputEncoding = [Text.UTF8Encoding]::new();\n" + command
        argv = [shell, "-NoProfile", "-NonInteractive", "-EncodedCommand",
                base64.b64encode(script.encode("utf-16-le")).decode("ascii")]
        job = WindowsJob()
        try:
            process = job.start(argv, workspace, env, merge_stderr=True)
        except BaseException:
            job.close()
            raise
        process.stdin.close()
    else:
        job = None
        process = subprocess.Popen([shell, "-c", command], cwd=workspace, env=env,
                                   stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT, start_new_session=True)

    output = bytearray()
    truncated = False

    def drain():
        nonlocal truncated
        with process.stdout as stream:
            while chunk := stream.read1(8192):
                available = 16384 - len(output)
                output.extend(chunk[:available])
                truncated |= len(chunk) > available

    reader = threading.Thread(target=drain, name="task-shell-output", daemon=True)
    reader.start()
    status = "completed"
    try:
        deadline = time.monotonic() + 300
        while process.poll() is None:
            if attempt.cancelled.is_set() or attempt.force_event.is_set() or time.monotonic() >= attempt.deadline:
                status = "cancelled"
                break
            if time.monotonic() >= deadline:
                status = "timedOut"
                break
            time.sleep(0.05)
    finally:
        try:
            cleanup_deadline = time.monotonic() + 5
            if job is not None:
                job.terminate(cleanup_deadline)
            else:
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
            process.wait(timeout=max(0.01, cleanup_deadline - time.monotonic()))
            if job is None:
                while True:
                    try:
                        os.killpg(process.pid, 0)
                    except ProcessLookupError:
                        break
                    if time.monotonic() >= cleanup_deadline:
                        raise RuntimeError("PROCESS_TREE_UNCERTAIN")
                    time.sleep(0.05)
            reader.join(timeout=max(0.01, cleanup_deadline - time.monotonic()))
            if reader.is_alive():
                raise RuntimeError("PROCESS_TREE_UNCERTAIN")
        except Exception:
            attempt.resource_uncertain = True
            raise
        finally:
            if job is not None:
                job.close()
    return {"status": status, "exitCode": process.returncode if status == "completed" else None, "output": output.decode("utf-8", errors="replace"),
            "outputTruncated": truncated}
