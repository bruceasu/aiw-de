"""Qt desktop companion and shared Web UI for Windows/Linux. No platform bridge."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import sys
import threading

from desktop_client import PersonalClient, PersonalError, personal_origin
from desktop_startup import DesktopLease


def main():
    parser = argparse.ArgumentParser(description="Desktop secretary with HUD/RPG themes")
    parser.add_argument("--service", default="http://127.0.0.1:8766")
    parser.add_argument("--opaque", action="store_true", help="Use a normal background for the companion")
    parser.add_argument("--show-pet", action="store_true", help="Show the companion instead of starting in the tray")
    args = parser.parse_args()
    try:
        origin = personal_origin(args.service)
    except PersonalError as error:
        raise SystemExit(str(error)) from None
    desktop_lease = DesktopLease(origin)
    if not desktop_lease.acquire():
        return 0
    try:
        from PySide6.QtCore import QObject, QRectF, Qt, QTimer, QUrl, Signal
        from PySide6.QtGui import QColor, QDesktopServices, QFont, QIcon, QPainter, QPen, QPixmap
        from PySide6.QtWidgets import QApplication, QHBoxLayout, QLabel, QMainWindow, QMenu, QMessageBox, QPushButton, QSystemTrayIcon, QVBoxLayout, QWidget
        from PySide6.QtWebEngineCore import QWebEnginePage, QWebEngineProfile, QWebEngineSettings, QWebEngineUrlRequestInterceptor
        from PySide6.QtWebEngineWidgets import QWebEngineView
    except ImportError:
        message = "需要桌面依赖。请在秘书目录使用当前 Python 安装 requirements-desktop.txt；或使用浏览器访问个人服务。"
        if sys.platform == "win32":
            import ctypes
            ctypes.windll.user32.MessageBoxW(None, message, "桌面秘书", 0x10)
        raise SystemExit(message) from None

    app = QApplication(sys.argv[:1])
    app.setApplicationName("AIW Secretary")
    app.setOrganizationName("AIW")
    app.setQuitOnLastWindowClosed(False)
    image_path = Path(__file__).parent / "desktop-assets/secretary.png"
    icon = QIcon(str(image_path))
    app.setWindowIcon(icon)
    trusted = QUrl(origin)

    def same_origin(url):
        return (url.scheme() == trusted.scheme() and url.host() == trusted.host()
                and url.port() == trusted.port() and not url.userInfo())

    class LocalRequests(QWebEngineUrlRequestInterceptor):
        def interceptRequest(self, request):
            url = request.requestUrl()
            # Public pages are fetched by authorized secretary tools, never by
            # a native WebView with access to personal session cookies.
            if url.scheme() not in {"data", "blob", "about"} and not same_origin(url):
                request.block(True)

    class LocalPage(QWebEnginePage):
        def acceptNavigationRequest(self, url, kind, main_frame):
            if same_origin(url):
                return True
            if kind == QWebEnginePage.NavigationType.NavigationTypeLinkClicked and url.scheme() in {"http", "https"} and not url.userInfo():
                QDesktopServices.openUrl(url)
            return False

        def createWindow(self, _kind):
            # target=_blank links leave the private WebView via the system
            # browser. The temporary page has no Python or platform bridge.
            page = ExternalPage(self.profile(), self)
            self.external_page = page
            return page

        def javaScriptConsoleMessage(self, *_args):
            pass  # Never forward private chat or JavaScript bodies into logs.

    class ExternalPage(QWebEnginePage):
        def acceptNavigationRequest(self, url, _kind, _main_frame):
            if url.scheme() in {"http", "https"} and not url.userInfo():
                QDesktopServices.openUrl(url)
            QTimer.singleShot(0, self.deleteLater)
            return False

    class Startup(QObject):
        ready = Signal(str)

    class DragBar(QWidget):
        def mousePressEvent(self, event):
            if event.button() == Qt.MouseButton.LeftButton and self.window().windowHandle():
                self.window().windowHandle().startSystemMove()

        def mouseDoubleClickEvent(self, event):
            if event.button() == Qt.MouseButton.LeftButton:
                window = self.window()
                window.showNormal() if window.isMaximized() else window.showMaximized()

    class Workbench(QMainWindow):
        def __init__(self):
            super().__init__()
            self.setWindowTitle("我的秘书")
            self.setWindowFlag(Qt.WindowType.FramelessWindowHint)
            screen = app.primaryScreen().availableGeometry()
            self.setMinimumSize(min(760, screen.width() - 32), min(560, screen.height() - 48))
            self.resize(min(1220, screen.width() - 32), min(880, screen.height() - 48))
            content = QWidget(); layout = QVBoxLayout(content); layout.setContentsMargins(1, 1, 1, 1); layout.setSpacing(0)
            bar = DragBar(); bar.setObjectName("windowBar"); row = QHBoxLayout(bar); row.setContentsMargins(16, 6, 8, 6)
            self.caption = QLabel("◇ 我的秘书 · 正在连接"); row.addWidget(self.caption); row.addStretch()
            for label, callback in (("—", self.showMinimized), ("□", lambda: self.showNormal() if self.isMaximized() else self.showMaximized()), ("×", self.hide)):
                button = QPushButton(label); button.setFixedSize(34, 28); button.clicked.connect(callback); row.addWidget(button)
            layout.addWidget(bar)
            self.view = QWebEngineView()
            # Off-the-record profile keeps session cookies in memory. Theme
            # preference is saved separately in an owner-local Qt setting.
            self.profile = QWebEngineProfile(self)
            self.requests = LocalRequests(self.profile); self.profile.setUrlRequestInterceptor(self.requests)
            self.page = LocalPage(self.profile, self.view); self.page.setBackgroundColor(QColor("#080e17"))
            self.page.settings().setAttribute(QWebEngineSettings.WebAttribute.LocalContentCanAccessFileUrls, False)
            self.page.settings().setAttribute(QWebEngineSettings.WebAttribute.FullScreenSupportEnabled, False)
            self.view.setPage(self.page); layout.addWidget(self.view, 1); self.setCentralWidget(content)
            self.statusBar().setSizeGripEnabled(True)
            self.statusBar().setStyleSheet("background:#111c2b;color:#9aadbf;")
            self.apply_theme("hud")

        def apply_theme(self, theme):
            if getattr(self, "theme", None) == theme:
                return
            self.theme = theme
            accent, panel = ("#e8c88f", "#191f2e") if theme == "rpg" else ("#64e4dc", "#101e2c")
            self.setStyleSheet(f"QMainWindow {{background:{accent};}} QWidget#windowBar {{background:{panel};}} QLabel {{color:{accent};font:12px 'Microsoft YaHei UI';}} QPushButton {{background:transparent;color:{accent};border:1px solid {accent};border-radius:3px;}} QPushButton:hover {{background:#2b4055;}}")
            self.caption.setText("◇ 我的秘书 · " + theme.upper())

        def open_view(self, name="overview"):
            url = QUrl(origin + "/"); url.setFragment(name)
            self.view.setUrl(url)
            if self.isMinimized(): self.showNormal()
            else: self.show()
            self.raise_(); self.activateWindow()

        def closeEvent(self, event):
            event.ignore(); self.hide()
            if tray is None: companion.show()

    class Companion(QWidget):
        def __init__(self):
            super().__init__(None, Qt.WindowType.FramelessWindowHint | Qt.WindowType.Tool | Qt.WindowType.WindowStaysOnTopHint)
            self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground, not args.opaque)
            self.setFixedSize(310, 454)
            self.setWindowTitle("秘书伙伴")
            self.theme = "hud"; self.summary = "正在连接个人服务"; self.counts = (0, 0, 0)
            self.image = QPixmap(str(image_path)).scaled(258, 386, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation)
            self.press = None; self.dragged = False
            self.buttons = []
            screen = app.primaryScreen().availableGeometry()
            self.move(screen.right() - self.width() - 20, max(screen.top(), screen.bottom() - self.height() - 12))
            self.update_theme()

        def update_theme(self):
            accent = "#e8c88f" if self.theme == "rpg" else "#64e4dc"
            radius = "9px" if self.theme == "rpg" else "2px"
            for button in self.buttons:
                button.setStyleSheet(f"QPushButton {{background:#111c2b;color:{accent};border:1px solid {accent};border-radius:{radius};font:11px 'Microsoft YaHei UI';}} QPushButton:hover {{background:#2b4055;}}")
            self.update()

        def paintEvent(self, _event):
            painter = QPainter(self); painter.setRenderHint(QPainter.RenderHint.Antialiasing)
            if args.opaque: painter.fillRect(self.rect(), QColor("#080e17"))
            accent = QColor("#e8c88f" if self.theme == "rpg" else "#64e4dc")
            panel = QColor("#191f2e" if self.theme == "rpg" else "#101e2c")
            painter.setPen(QPen(accent, 1)); painter.setBrush(panel)
            radius = 16 if self.theme == "rpg" else 3
            painter.drawRoundedRect(QRectF(8, 10, 294, 42), radius, radius)
            painter.setFont(QFont("Microsoft YaHei UI", 11, QFont.Weight.Bold)); painter.drawText(QRectF(20, 10, 270, 42), Qt.AlignmentFlag.AlignCenter, "◇ 秘书伙伴" if self.theme == "rpg" else "SECRETARY / HUD")
            painter.setPen(QPen(QColor("#66573d" if self.theme == "rpg" else "#295361"), 1))
            painter.setBrush(QColor("#0b1420")); painter.drawRoundedRect(QRectF(28, 72, 254, 338), radius, radius)
            if self.theme == "hud":
                for y in range(90, 410, 24): painter.drawLine(30, y, 280, y)
            else:
                painter.setPen(QPen(accent, 1)); painter.drawEllipse(QRectF(60, 102, 190, 244))
            painter.drawPixmap((self.width() - self.image.width()) // 2, 54, self.image)

        def mousePressEvent(self, event):
            if event.button() == Qt.MouseButton.LeftButton:
                self.press = event.globalPosition(); self.dragged = False

        def mouseMoveEvent(self, event):
            if self.press is not None and (event.globalPosition() - self.press).manhattanLength() > 6:
                self.dragged = True
                if self.windowHandle(): self.windowHandle().startSystemMove()

        def mouseReleaseEvent(self, event):
            if event.button() == Qt.MouseButton.LeftButton and self.press is not None:
                if not self.dragged: workbench.open_view("chat")
                self.press = None

        def contextMenuEvent(self, event):
            menu.popup(event.globalPos())

        def closeEvent(self, event):
            event.ignore()
            if tray is not None: self.hide()
            else: workbench.show()

    workbench = Workbench()
    companion = Companion()
    menu = QMenu()
    for label, name in (("与秘书对话", "chat"), ("工作与安排", "work"), ("待你确认", "confirmations"), ("模型与设置", "setup")):
        menu.addAction(label, lambda _checked=False, value=name: workbench.open_view(value))
    menu.addSeparator()

    def choose_theme(mode):
        workbench.page.runJavaScript("window.AiwAppearance && window.AiwAppearance.setPreference(" + json.dumps({"mode": mode}) + ")")

    for label, mode in (("HUD · 专注", "hud"), ("RPG · 伙伴", "rpg"), ("按时间自动切换", "auto")):
        menu.addAction(label, lambda _checked=False, value=mode: choose_theme(value))
    menu.addSeparator()
    menu.addAction("显示秘书伙伴", companion.show)
    tray = None
    if QSystemTrayIcon.isSystemTrayAvailable():
        tray = QSystemTrayIcon(icon, app); tray.setToolTip("我的秘书"); tray.setContextMenu(menu)
        tray.activated.connect(lambda reason: workbench.open_view("chat") if reason == QSystemTrayIcon.ActivationReason.Trigger else None)
        tray.show(); menu.addAction("隐藏秘书伙伴", companion.hide)
    menu.addAction("退出桌面界面（服务继续运行）", app.quit)

    from PySide6.QtCore import QSettings
    preferences = QSettings()
    settings_key = "appearance/" + hashlib.sha256(origin.encode()).hexdigest()[:16]
    restored = False
    last_saved = None
    def synchronize(value):
        nonlocal restored, last_saved
        if not isinstance(value, dict) or not value.get("appearance"):
            return
        if not restored:
            restored = True
            saved = preferences.value(settings_key, "")
            try: saved = json.loads(saved)
            except (ValueError, TypeError): saved = None
            if isinstance(saved, dict) and saved.get("mode") in {"auto", "hud", "rpg"}:
                workbench.page.runJavaScript("try { window.AiwAppearance.setPreference(" + json.dumps(saved) + "); } catch (_) {}")
                return
        appearance = value["appearance"]
        theme = appearance.get("effective", "hud")
        if theme in {"hud", "rpg"} and companion.theme != theme:
            companion.theme = theme; companion.update_theme()
        workbench.apply_theme(companion.theme)
        encoded = json.dumps(appearance.get("preference", {}), sort_keys=True)
        if encoded != last_saved:
            preferences.setValue(settings_key, encoded); last_saved = encoded
        companion.summary = value.get("status", "等待连接")
        companion.counts = tuple(value.get("counts", [0, 0, 0])); companion.update()

    script = """(() => {
      if (!window.AiwAppearance) return null;
      const s = typeof snapshot === 'object' && snapshot ? snapshot : null;
      if (document.hidden && typeof refresh === 'function' && typeof csrf === 'string' && csrf
          && typeof pollingState === 'boolean' && !pollingState) {
        pollingState = true;
        refresh().catch(() => {}).finally(() => { pollingState = false; });
      }
      const work = s && Array.isArray(s.work) ? s.work : [];
      const decisions = s && Array.isArray(s.decisions) ? s.decisions : [];
      return { appearance: window.AiwAppearance.getState(),
        status: !s ? '等待连接个人服务' : s.modelConfigured === false ? '请先连接模型' : s.enabled ? '秘书可用' : '秘书已暂停',
        counts: [work.filter(w => ['DAILY_RUNNING','ACTIVE','START_PENDING'].includes(w.state)).length,
          decisions.filter(d => d.kind === 'DAILY_CONFIRMATION' && d.state === 'WAITING').length,
          work.filter(w => w.state === 'DONE').length] };
    })()"""
    timer = QTimer(app); timer.setInterval(3000)
    def poll():
        if tray is not None and not QSystemTrayIcon.isSystemTrayAvailable() and not companion.isVisible():
            companion.show()
        workbench.page.runJavaScript(script, synchronize)
    timer.timeout.connect(poll); timer.start()
    startup = Startup()
    def ready(error):
        if error:
            if tray is not None:
                tray.showMessage("连接个人服务", "个人服务尚未连接，请从托盘打开工作台查看并重试。")
            else:
                QMessageBox.warning(workbench, "连接个人服务", error)
        workbench.view.setUrl(QUrl(origin + "/"))
        if tray is None: workbench.show()
    startup.ready.connect(ready)
    def connect():
        try:
            PersonalClient(origin).auto_connect()
            startup.ready.emit("")
        except PersonalError as error:
            startup.ready.emit(str(error))
        except Exception:
            startup.ready.emit("个人服务尚未就绪，请启动服务后刷新页面。")
    if args.show_pet or tray is None: companion.show()
    if tray is None: workbench.show()
    threading.Thread(target=connect, daemon=True).start()
    app.aboutToQuit.connect(timer.stop)
    app.aboutToQuit.connect(desktop_lease.release)
    return app.exec()


if __name__ == "__main__":
    raise SystemExit(main())
