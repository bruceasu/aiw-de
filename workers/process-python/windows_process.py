"""Windows Job Object ownership for an app-server and all its descendants.

The bootstrap waits for one byte before launching the target. Assign it to a
kill-on-close job first, so the target can never spawn outside the job during
the assignment race. No shell, taskkill, privilege changes or downloads.
"""
from __future__ import annotations

import ctypes
from ctypes import wintypes
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time


def executable(value):
    resolved = shutil.which(value)
    if not resolved:
        raise RuntimeError("CODEX_EXECUTABLE_UNAVAILABLE")
    path = Path(resolved).resolve()
    if path.suffix.lower() == ".exe":
        return str(path)
    # npm's cmd/ps1 shims are not native executables. Resolve only known
    # installed Codex layouts; never send operator paths through cmd.exe.
    import platform
    architecture = "aarch64" if platform.machine().lower() in {"arm64", "aarch64"} else "x86_64"
    target = architecture + "-pc-windows-msvc"
    package_arch = "arm64" if architecture == "aarch64" else "x64"
    roots = (path.parent / "node_modules" / "@openai", path.parent.parent)
    for root in roots:
        for package in (root / "codex", root / ("codex-win32-" + package_arch),
                        root / "codex" / "node_modules" / "@openai" / ("codex-win32-" + package_arch)):
            # Current npm releases use bin/; older releases use codex/.
            for directory in ("bin", "codex"):
                candidate = package / "vendor" / target / directory / "codex.exe"
                if candidate.is_file():
                    return str(candidate.resolve())
    raise RuntimeError("CODEX_NATIVE_EXE_REQUIRED: select the installed codex.exe")


class _BasicLimit(ctypes.Structure):
    _fields_ = [("PerProcessUserTimeLimit", ctypes.c_int64), ("PerJobUserTimeLimit", ctypes.c_int64),
                ("LimitFlags", wintypes.DWORD), ("MinimumWorkingSetSize", ctypes.c_size_t),
                ("MaximumWorkingSetSize", ctypes.c_size_t), ("ActiveProcessLimit", wintypes.DWORD),
                ("Affinity", ctypes.c_size_t), ("PriorityClass", wintypes.DWORD),
                ("SchedulingClass", wintypes.DWORD)]


class _IOCounters(ctypes.Structure):
    _fields_ = [(name, ctypes.c_uint64) for name in (
        "ReadOperationCount", "WriteOperationCount", "OtherOperationCount",
        "ReadTransferCount", "WriteTransferCount", "OtherTransferCount")]


class _ExtendedLimit(ctypes.Structure):
    _fields_ = [("BasicLimitInformation", _BasicLimit), ("IoInfo", _IOCounters),
                ("ProcessMemoryLimit", ctypes.c_size_t), ("JobMemoryLimit", ctypes.c_size_t),
                ("PeakProcessMemoryUsed", ctypes.c_size_t), ("PeakJobMemoryUsed", ctypes.c_size_t)]


class _Accounting(ctypes.Structure):
    _fields_ = [("TotalUserTime", ctypes.c_int64), ("TotalKernelTime", ctypes.c_int64),
                ("ThisPeriodTotalUserTime", ctypes.c_int64), ("ThisPeriodTotalKernelTime", ctypes.c_int64),
                ("TotalPageFaultCount", wintypes.DWORD), ("TotalProcesses", wintypes.DWORD),
                ("ActiveProcesses", wintypes.DWORD), ("TotalTerminatedProcesses", wintypes.DWORD)]


class WindowsJob:
    def __init__(self):
        self.api = ctypes.WinDLL("kernel32", use_last_error=True)
        signatures = {
            "CreateJobObjectW": ([ctypes.c_void_p, wintypes.LPCWSTR], wintypes.HANDLE),
            "SetInformationJobObject": ([wintypes.HANDLE, ctypes.c_int, ctypes.c_void_p, wintypes.DWORD], wintypes.BOOL),
            "AssignProcessToJobObject": ([wintypes.HANDLE, wintypes.HANDLE], wintypes.BOOL),
            "QueryInformationJobObject": ([wintypes.HANDLE, ctypes.c_int, ctypes.c_void_p, wintypes.DWORD, ctypes.c_void_p], wintypes.BOOL),
            "TerminateJobObject": ([wintypes.HANDLE, wintypes.UINT], wintypes.BOOL),
            "CloseHandle": ([wintypes.HANDLE], wintypes.BOOL),
        }
        for name, (arguments, result) in signatures.items():
            function = getattr(self.api, name)
            function.argtypes, function.restype = arguments, result
        self.handle = self.api.CreateJobObjectW(None, None)
        if not self.handle:
            raise RuntimeError("WINDOWS_JOB_CREATE_FAILED")
        limits = _ExtendedLimit()
        limits.BasicLimitInformation.LimitFlags = 0x2000  # JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE; no breakaway
        if not self.api.SetInformationJobObject(self.handle, 9, ctypes.byref(limits), ctypes.sizeof(limits)):
            self.close()
            raise RuntimeError("WINDOWS_JOB_LIMIT_FAILED")

    def start(self, argv, cwd, environment):
        # os.read avoids buffering any of the JSON-RPC bytes after the gate.
        bootstrap = "import os,sys; gate=os.read(0,1); sys.exit(1) if gate!=b'G' else os.execv(sys.argv[1],sys.argv[1:])"
        child = subprocess.Popen([sys.executable, "-I", "-c", bootstrap, *argv], cwd=cwd,
            env=environment, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL, creationflags=subprocess.CREATE_NO_WINDOW)
        try:
            if not self.api.AssignProcessToJobObject(self.handle, wintypes.HANDLE(int(child._handle))):
                raise RuntimeError("WINDOWS_JOB_ASSIGN_FAILED")
            child.stdin.write(b"G")
            child.stdin.flush()
            return child
        except BaseException:
            child.kill()
            child.wait(timeout=5)
            for stream in (child.stdin, child.stdout):
                stream.close()
            self.close()
            raise

    def terminate(self, deadline):
        if not self.api.TerminateJobObject(self.handle, 1):
            raise RuntimeError("WINDOWS_JOB_TERMINATE_FAILED")
        while True:
            state = _Accounting()
            if not self.api.QueryInformationJobObject(self.handle, 1, ctypes.byref(state), ctypes.sizeof(state), None):
                raise RuntimeError("WINDOWS_JOB_QUERY_FAILED")
            if state.ActiveProcesses == 0:
                return
            if time.monotonic() >= deadline:
                raise RuntimeError("PROCESS_TREE_UNCERTAIN")
            time.sleep(0.02)

    def close(self):
        if self.handle:
            self.api.CloseHandle(self.handle)
            self.handle = None
