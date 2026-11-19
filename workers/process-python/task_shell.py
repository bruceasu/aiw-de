"""Task-authorized host commands with bounded output and owned processes."""
import base64
import os
from pathlib import Path
import sys
import threading
import time


def shell_executable():
    if sys.platform != "win32":
        return None
    shell = Path(os.environ.get("SystemRoot", r"C:\Windows")) / "System32/WindowsPowerShell/v1.0/powershell.exe"
    return str(shell) if shell.is_file() else None


def run_shell(workspace, command, attempt):
    shell = shell_executable()
    if shell is None:
        raise RuntimeError("TASK_SHELL_UNAVAILABLE")
    attempt.check()
    env = {key: os.environ[key] for key in ("PATH", "PATHEXT", "SystemRoot", "WINDIR", "ComSpec",
                                           "JAVA_HOME", "LANG", "LC_ALL") if key in os.environ}
    env["NO_COLOR"] = "1"
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

    output = bytearray()
    truncated = False

    def drain():
        nonlocal truncated
        with process.stdout as stream:
            while chunk := stream.read1(8192):
                output.extend(chunk)
                if len(output) > 16384:
                    del output[:-16384]
                    truncated = True

    reader = threading.Thread(target=drain, name="task-shell-output", daemon=True)
    status = "completed"
    try:
        process.stdin.close()
        reader.start()
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
            job.terminate(cleanup_deadline)
            process.wait(timeout=max(0.01, cleanup_deadline - time.monotonic()))
            if reader.ident is not None:
                reader.join(timeout=max(0.01, cleanup_deadline - time.monotonic()))
            else:
                process.stdout.close()
            if reader.is_alive():
                raise RuntimeError("PROCESS_TREE_UNCERTAIN")
        except Exception:
            attempt.resource_uncertain = True
            raise
        finally:
            job.close()
    return {"status": status, "exitCode": process.returncode if status == "completed" else None, "output": output.decode("utf-8", errors="replace"),
            "outputTruncated": truncated}
