"""Small owner-local configuration, excluded from the portable private backup."""

import base64
import ctypes
import json
import os
from pathlib import Path
import tempfile


class PrivateProcessLock:
    def __init__(self, path):
        self.path, self.stream = Path(path), None

    def acquire(self):
        if self.path.is_symlink():
            raise OSError("Invalid private lock")
        stream = open(self.path, "a+b")
        try:
            os.chmod(self.path, 0o600)
            if self.path.stat().st_size == 0:
                stream.write(b"0"); stream.flush()
            stream.seek(0)
            if os.name == "nt":
                import msvcrt
                msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(stream.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
            self.stream = stream
        except Exception:
            stream.close()
            raise

    def release(self):
        if self.stream is None: return
        try:
            self.stream.seek(0)
            if os.name == "nt":
                import msvcrt
                msvcrt.locking(self.stream.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                import fcntl
                fcntl.flock(self.stream.fileno(), fcntl.LOCK_UN)
        finally:
            self.stream.close()
            self.stream = None


def _protect(value, decrypt=False):
    class Blob(ctypes.Structure):
        _fields_ = [("size", ctypes.c_ulong), ("data", ctypes.POINTER(ctypes.c_ubyte))]
    buffer = ctypes.create_string_buffer(value)
    source = Blob(len(value), ctypes.cast(buffer, ctypes.POINTER(ctypes.c_ubyte)))
    target = Blob()
    function = ctypes.windll.crypt32.CryptUnprotectData if decrypt else ctypes.windll.crypt32.CryptProtectData
    if not function(ctypes.byref(source), None, None, None, None, 1, ctypes.byref(target)):
        raise OSError("Private configuration encryption failed")
    try:
        return ctypes.string_at(target.data, target.size)
    finally:
        ctypes.windll.kernel32.LocalFree(target.data)


def read_private(path, default=None):
    path = Path(path)
    if not path.exists():
        return default
    if path.is_symlink() or path.stat().st_size > 1_000_000:
        raise ValueError("Invalid private configuration file")
    envelope = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(envelope, dict):
        raise ValueError("Invalid private configuration format")
    if isinstance(envelope, dict) and envelope.get("format") == "windows-dpapi-v1":
        if os.name != "nt":
            raise ValueError("This private configuration belongs to a Windows account")
        return json.loads(_protect(base64.b64decode(envelope["value"]), decrypt=True))
    return envelope


def write_private(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    if path.is_symlink():
        raise ValueError("Private configuration must not be a symlink")
    payload = json.dumps(value, ensure_ascii=False, allow_nan=False).encode("utf-8")
    if os.name == "nt":
        payload = json.dumps({"format": "windows-dpapi-v1", "value": base64.b64encode(_protect(payload)).decode("ascii")}).encode("utf-8")
    descriptor, temporary = tempfile.mkstemp(prefix=".settings-", dir=path.parent)
    try:
        with os.fdopen(descriptor, "wb") as output:
            os.chmod(temporary, 0o600)
            output.write(payload)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
    finally:
        Path(temporary).unlink(missing_ok=True)
