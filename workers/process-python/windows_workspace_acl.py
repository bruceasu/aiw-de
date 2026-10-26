"""Give native Windows sandbox readers no more read scope than the source checkout.

Only DACL APIs are used: no audit privilege or ownership change. A coding
worktree may retain the source's existing Authenticated Users modify scope.
Private evidence and guarded execution do not use this compatibility helper.
"""
from pathlib import Path
import ctypes
from ctypes import wintypes as w

READ = 0x120089
READ_EXECUTE = READ | 0x20
TRAVERSE = 0x20
MODIFY = 0x1301BF


class _Trustee(ctypes.Structure):
    _fields_ = [("multiple", ctypes.c_void_p), ("operation", ctypes.c_int),
                ("form", ctypes.c_int), ("kind", ctypes.c_int), ("name", ctypes.c_void_p)]


class _Entry(ctypes.Structure):
    _fields_ = [("permissions", w.DWORD), ("mode", ctypes.c_int),
                ("inheritance", w.DWORD), ("trustee", _Trustee)]


class _Acl:
    def __init__(self, sid="S-1-5-32-545"):
        self.api = ctypes.WinDLL("advapi32", use_last_error=True)
        self.kernel = ctypes.WinDLL("kernel32", use_last_error=True)
        pointer = ctypes.c_void_p
        self.api.GetNamedSecurityInfoW.argtypes = [w.LPWSTR, ctypes.c_int, w.DWORD, pointer, pointer,
                                                   ctypes.POINTER(pointer), pointer, ctypes.POINTER(pointer)]
        self.api.GetNamedSecurityInfoW.restype = w.DWORD
        self.api.SetNamedSecurityInfoW.argtypes = [w.LPWSTR, ctypes.c_int, w.DWORD, pointer, pointer, pointer, pointer]
        self.api.SetNamedSecurityInfoW.restype = w.DWORD
        self.api.ConvertStringSidToSidW.argtypes = [w.LPCWSTR, ctypes.POINTER(pointer)]
        self.api.ConvertStringSidToSidW.restype = w.BOOL
        self.api.GetEffectiveRightsFromAclW.argtypes = [pointer, ctypes.POINTER(_Trustee), ctypes.POINTER(w.DWORD)]
        self.api.GetEffectiveRightsFromAclW.restype = w.DWORD
        self.api.SetEntriesInAclW.argtypes = [w.ULONG, ctypes.POINTER(_Entry), pointer, ctypes.POINTER(pointer)]
        self.api.SetEntriesInAclW.restype = w.DWORD
        self.kernel.LocalFree.argtypes = [pointer]
        self.kernel.LocalFree.restype = pointer
        self.sid = pointer()
        if not self.api.ConvertStringSidToSidW(sid, ctypes.byref(self.sid)):
            raise ctypes.WinError(ctypes.get_last_error())

    def close(self):
        self.kernel.LocalFree(self.sid)

    def read(self, path):
        descriptor, acl = ctypes.c_void_p(), ctypes.c_void_p()
        status = self.api.GetNamedSecurityInfoW(str(path), 1, 4, None, None,
                                              ctypes.byref(acl), None, ctypes.byref(descriptor))
        if status:
            raise OSError(status, "CODEX_WINDOWS_DACL_READ_FAILED")
        return descriptor, acl

    def release(self, saved):
        self.kernel.LocalFree(saved[0])

    def permits(self, path, rights):
        saved = self.read(path)
        try:
            trustee = _Trustee(None, 0, 0, 5, self.sid)
            effective = w.DWORD()
            status = self.api.GetEffectiveRightsFromAclW(saved[1], ctypes.byref(trustee), ctypes.byref(effective))
            if status:
                raise OSError(status, "CODEX_WINDOWS_SOURCE_ACCESS_UNCONFIRMED")
            return effective.value & rights == rights
        finally:
            self.release(saved)

    def write(self, path, acl):
        status = self.api.SetNamedSecurityInfoW(str(path), 1, 4, None, None, acl, None)
        if status:
            raise OSError(status, "CODEX_WINDOWS_DACL_WRITE_FAILED")

    def grant(self, path, saved, rights, inherit):
        entry = _Entry(rights, 1, 3 if inherit else 0, _Trustee(None, 0, 0, 5, self.sid))
        acl = ctypes.c_void_p()
        status = self.api.SetEntriesInAclW(1, ctypes.byref(entry), saved[1], ctypes.byref(acl))
        if status:
            raise OSError(status, "CODEX_WINDOWS_DACL_MERGE_FAILED")
        try:
            self.write(path, acl)
        finally:
            self.kernel.LocalFree(acl)


def permit_workspace_read(root, workspace, source, *, writable=False):
    root, workspace, source = (Path(path).absolute() for path in (root, workspace, source))
    if workspace == root or not workspace.is_relative_to(root):
        raise RuntimeError("CODEX_WINDOWS_ACL_WORKSPACE_OUTSIDE_ROOT")
    # Refuse links before examining or changing an ACL, including ancestor directories.
    entries = [root, workspace, *workspace.rglob("*")]
    for entry in entries:
        for path in (entry, *entry.parents):
            if path.is_symlink() or path.is_junction():
                raise RuntimeError("CODEX_WINDOWS_ACL_LINK_DENIED")
    acl = _Acl()
    writer = None
    saved = []
    try:
        writer = _Acl("S-1-5-11") if writable else None
        if not acl.permits(source, READ_EXECUTE):
            raise RuntimeError("CODEX_WINDOWS_SOURCE_NOT_USERS_READABLE")
        if writer and not writer.permits(source, MODIFY):
            raise RuntimeError("CODEX_WINDOWS_SOURCE_MODIFY_SCOPE_UNAVAILABLE")
        for entry in entries[1:]:
            original = source if entry == workspace else source / entry.relative_to(workspace)
            if (not original.exists() or original.is_symlink() or original.is_junction()
                    or not acl.permits(original, READ_EXECUTE if original.is_dir() else READ)):
                raise RuntimeError("CODEX_WINDOWS_SOURCE_NOT_USERS_READABLE")
            if writer and not writer.permits(original, MODIFY):
                raise RuntimeError("CODEX_WINDOWS_SOURCE_MODIFY_SCOPE_UNAVAILABLE")
        # Traverse the private worktree container without allowing directory listing.
        # Only the assigned worktree receives an inheritable read/execute ACE.
        for path, rights, inherit in ((root, TRAVERSE, False), (workspace, READ_EXECUTE, True)):
            original = acl.read(path)
            saved.append((path, original))
            acl.grant(path, original, rights, inherit)
        if writer:
            current = writer.read(workspace)
            try:
                writer.grant(workspace, current, MODIFY, True)
            finally:
                writer.release(current)
    except BaseException:
        failures = []
        for path, original in reversed(saved):
            try:
                acl.write(path, original[1])
            except OSError:
                failures.append(str(path))
        if failures:
            raise RuntimeError("CODEX_WINDOWS_DACL_RESTORE_FAILED") from None
        raise
    finally:
        for _, original in saved:
            acl.release(original)
        acl.close()
        if writer:
            writer.close()
