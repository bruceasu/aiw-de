"""Windows window placement and notification icon. No Worker lifecycle ownership."""

import ctypes
from ctypes import wintypes as w

user32 = ctypes.WinDLL("user32", use_last_error=True)
shell32 = ctypes.WinDLL("shell32", use_last_error=True)
LRESULT = ctypes.c_ssize_t
WNDPROC = ctypes.WINFUNCTYPE(LRESULT, w.HWND, w.UINT, w.WPARAM, w.LPARAM)
user32.GetAncestor.argtypes = [w.HWND, w.UINT]
user32.GetAncestor.restype = w.HWND
user32.SetWindowPos.argtypes = [w.HWND, w.HWND, ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_int, w.UINT]
user32.SetWindowPos.restype = w.BOOL
user32.MonitorFromPoint.argtypes = [w.POINT, w.DWORD]
user32.MonitorFromPoint.restype = w.HANDLE


class MonitorInfo(ctypes.Structure):
    _fields_ = [("cbSize", w.DWORD), ("monitor", w.RECT), ("work", w.RECT), ("flags", w.DWORD)]


user32.GetMonitorInfoW.argtypes = [w.HANDLE, ctypes.POINTER(MonitorInfo)]
user32.GetMonitorInfoW.restype = w.BOOL


def window_handle(root):
    return user32.GetAncestor(root.winfo_id(), 2)


def work_area(x, y):
    monitor = user32.MonitorFromPoint(w.POINT(int(x), int(y)), 2)
    info = MonitorInfo(cbSize=ctypes.sizeof(MonitorInfo))
    if not user32.GetMonitorInfoW(monitor, ctypes.byref(info)):
        raise ctypes.WinError(ctypes.get_last_error())
    return info.work.left, info.work.top, info.work.right, info.work.bottom


def move(root, x, y):
    if not user32.SetWindowPos(window_handle(root), None, int(x), int(y), 0, 0, 0x0015):
        raise ctypes.WinError(ctypes.get_last_error())


class Guid(ctypes.Structure):
    _fields_ = [("a", w.DWORD), ("b", w.WORD), ("c", w.WORD), ("d", ctypes.c_ubyte * 8)]


class NotifyData(ctypes.Structure):
    _fields_ = [("cbSize", w.DWORD), ("hWnd", w.HWND), ("uID", w.UINT), ("uFlags", w.UINT),
                ("uCallbackMessage", w.UINT), ("hIcon", w.HICON), ("szTip", w.WCHAR * 128),
                ("dwState", w.DWORD), ("dwStateMask", w.DWORD), ("szInfo", w.WCHAR * 256),
                ("uTimeout", w.UINT), ("szInfoTitle", w.WCHAR * 64), ("dwInfoFlags", w.DWORD),
                ("guidItem", Guid), ("hBalloonIcon", w.HICON)]


class TrayIcon:
    """Chain Tk's window procedure; defer UI work through the app's event queue."""
    def __init__(self, root, enqueue):
        self.hwnd = window_handle(root)
        self.enqueue = enqueue
        self.message = 0x8000 + 71
        self.active = False
        self.callback = WNDPROC(self._procedure)
        self.set_proc = getattr(user32, "SetWindowLongPtrW", user32.SetWindowLongW)
        self.set_proc.argtypes = [w.HWND, ctypes.c_int, ctypes.c_void_p]
        self.set_proc.restype = ctypes.c_void_p
        user32.CallWindowProcW.argtypes = [ctypes.c_void_p, w.HWND, w.UINT, w.WPARAM, w.LPARAM]
        user32.CallWindowProcW.restype = LRESULT
        user32.RegisterWindowMessageW.argtypes = [w.LPCWSTR]
        user32.RegisterWindowMessageW.restype = w.UINT
        user32.LoadIconW.argtypes = [w.HINSTANCE, ctypes.c_void_p]
        user32.LoadIconW.restype = w.HICON
        shell32.Shell_NotifyIconW.argtypes = [w.DWORD, ctypes.POINTER(NotifyData)]
        shell32.Shell_NotifyIconW.restype = w.BOOL
        self.restart = user32.RegisterWindowMessageW("TaskbarCreated")
        self.data = NotifyData(cbSize=ctypes.sizeof(NotifyData), hWnd=self.hwnd, uID=1,
            uFlags=7, uCallbackMessage=self.message, hIcon=user32.LoadIconW(None, ctypes.c_void_p(32512)),
            szTip="我的秘书 · AIW:DE")
        ctypes.set_last_error(0)
        self.original = self.set_proc(self.hwnd, -4, ctypes.cast(self.callback, ctypes.c_void_p))
        if not self.original:
            raise ctypes.WinError(ctypes.get_last_error())
        if not shell32.Shell_NotifyIconW(0, ctypes.byref(self.data)):
            self.set_proc(self.hwnd, -4, self.original)
            raise OSError("Windows notification icon is unavailable")
        self.active = True

    def _procedure(self, hwnd, message, wp, lp):
        if message == self.message:
            event = lp & 0xffff
            if event == 0x0202:
                self.enqueue("chat")
            elif event in {0x0205, 0x007b}:
                self.enqueue("menu")
            return 0
        if self.restart and message == self.restart:
            if not shell32.Shell_NotifyIconW(0, ctypes.byref(self.data)):
                self.enqueue("tray-lost")
            return 0
        return user32.CallWindowProcW(self.original, hwnd, message, wp, lp)

    def close(self):
        if self.active:
            shell32.Shell_NotifyIconW(2, ctypes.byref(self.data))
            self.set_proc(self.hwnd, -4, self.original)
            self.active = False
