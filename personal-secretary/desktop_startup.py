"""Desktop selection and one companion per service origin; no service ownership."""

import atexit
import errno
import hashlib
import importlib.util
import os
from pathlib import Path
import subprocess
import sys


class DesktopLease:
    def __init__(self, origin):
        self.key = hashlib.sha256(origin.encode()).hexdigest()
        self.handle = self.api = self.stream = None

    def acquire(self):
        if sys.platform == "win32":
            import ctypes
            from ctypes import wintypes
            api = ctypes.WinDLL("kernel32", use_last_error=True)
            api.CreateMutexW.argtypes = [ctypes.c_void_p, wintypes.BOOL, wintypes.LPCWSTR]
            api.CreateMutexW.restype = wintypes.HANDLE
            api.CloseHandle.argtypes = [wintypes.HANDLE]
            api.CloseHandle.restype = wintypes.BOOL
            ctypes.set_last_error(0)
            handle = api.CreateMutexW(None, False, "Local\\AIWSecretaryDesktop-" + self.key)
            error = ctypes.get_last_error()
            if not handle:
                raise ctypes.WinError(error)
            if error == 183:  # ERROR_ALREADY_EXISTS; the existing GUI keeps its handle.
                api.CloseHandle(handle)
                return False
            self.api, self.handle = api, handle
        else:
            import fcntl
            directory = Path.home() / ".aiw-personal" / "desktop-locks"
            directory.mkdir(mode=0o700, parents=True, exist_ok=True)
            descriptor = os.open(directory / (self.key + ".lock"), os.O_CREAT | os.O_RDWR | getattr(os, "O_NOFOLLOW", 0), 0o600)
            stream = os.fdopen(descriptor, "a+b")
            try:
                os.fchmod(stream.fileno(), 0o600)
                try:
                    fcntl.flock(stream.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
                except OSError as error:
                    if error.errno in {errno.EACCES, errno.EAGAIN}:
                        stream.close()
                        return False
                    raise
            except Exception:
                stream.close()
                raise
            self.stream = stream
        atexit.register(self.release)
        return True

    def release(self):
        if self.handle is not None:
            self.api.CloseHandle(self.handle)
            self.handle = None
        if self.stream is not None:
            self.stream.close()  # Closing also releases flock; no stale marker deletion.
            self.stream = None


def interactive_desktop():
    if any(os.environ.get(key) for key in ("SSH_CONNECTION", "SSH_CLIENT", "SSH_TTY")):
        return False
    if sys.platform == "win32":
        import ctypes
        from ctypes import wintypes

        class UserObjectFlags(ctypes.Structure):
            _fields_ = [("inherit", wintypes.BOOL), ("reserved", wintypes.BOOL), ("flags", wintypes.DWORD)]

        api = ctypes.WinDLL("user32", use_last_error=True)
        api.GetProcessWindowStation.restype = wintypes.HANDLE
        api.GetUserObjectInformationW.argtypes = [wintypes.HANDLE, ctypes.c_int, ctypes.c_void_p, wintypes.DWORD, ctypes.POINTER(wintypes.DWORD)]
        api.GetUserObjectInformationW.restype = wintypes.BOOL
        station, flags, needed = api.GetProcessWindowStation(), UserObjectFlags(), wintypes.DWORD()
        return bool(station and api.GetUserObjectInformationW(station, 1, ctypes.byref(flags), ctypes.sizeof(flags), ctypes.byref(needed)) and flags.flags & 1)
    return sys.platform.startswith("linux") and bool(os.environ.get("DISPLAY") or os.environ.get("WAYLAND_DISPLAY"))


def launch_desktop_pet(origin):
    if not interactive_desktop():
        return False
    directory = Path(__file__).parent
    try:
        qt = importlib.util.find_spec("PySide6") is not None
    except (ImportError, ValueError):
        qt = False
    script = directory / "desktop_webview.py" if qt else directory / "desktop.py"
    if not script.is_file() or not qt and sys.platform != "win32":
        return False
    executable = Path(sys.executable)
    if sys.platform == "win32" and executable.name.lower() == "python.exe":
        windowed = executable.with_name("pythonw.exe")
        if windowed.is_file():
            executable = windowed
    subprocess.Popen([str(executable), str(script), "--service", origin], stdin=subprocess.DEVNULL,
                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                     creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
                     start_new_session=sys.platform != "win32")
    return True  # Launch requested; this does not assert that a tray was created.
