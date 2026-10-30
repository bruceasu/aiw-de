"""Qt desktop companion and shared Web UI for Windows/Linux. No platform bridge."""
from __future__ import annotations

from env_config import load_program_env

load_program_env(__file__)

import argparse
import datetime
import hashlib
import json
import math
from pathlib import Path
import sys
import threading

from desktop_client import PersonalClient, PersonalError, personal_origin
from desktop_startup import DesktopLease
from desktop_i18n import DesktopTranslations


THEME_COLORS = {
    "hud": {"accent": "#64e4dc", "panel": "#101e2c", "background": "#080e17", "hover": "#2b4055", "radius": "2px"},
    "rpg": {"accent": "#e8c88f", "panel": "#191f2e", "background": "#10121c", "hover": "#413725", "radius": "9px"},
    "lady": {"accent": "#c4b6ff", "panel": "#222943", "background": "#171b35", "hover": "#34314f", "radius": "12px"},
}


def scheduled_theme(now, day_start, night_start):
    current = now.strftime("%H:%M")
    if day_start < night_start:
        if current < day_start:
            return "lady"
        return "hud" if current < night_start else "rpg"
    if current < night_start:
        return "lady"
    return "rpg" if current < day_start else "hud"


def main():
    labels = DesktopTranslations()
    t = labels.t
    parser = argparse.ArgumentParser(description="Desktop secretary with HUD/RPG/Lady themes")
    parser.add_argument("--service", default="http://127.0.0.1:8766")
    parser.add_argument("--opaque", action="store_true", help="Use opaque backgrounds for the workbench and companion")
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
        from PySide6.QtCore import QObject, QRectF, Qt, QTimer, QUrl, Signal, Slot, QElapsedTimer, QSettings
        from PySide6.QtGui import QColor, QDesktopServices, QFont, QIcon, QPainter, QPainterPath, QPen, QPixmap
        from PySide6.QtWidgets import QApplication, QDialog, QHBoxLayout, QLabel, QMainWindow, QMenu, QMessageBox, QPushButton, QSlider, QSystemTrayIcon, QVBoxLayout, QWidget, QWidgetAction
        from PySide6.QtWebEngineCore import QWebEnginePage, QWebEngineProfile, QWebEngineSettings, QWebEngineUrlRequestInterceptor
        from PySide6.QtWebEngineWidgets import QWebEngineView
    except ImportError:
        message = t("需要桌面依赖。请在秘书目录使用当前 Python 安装 requirements-desktop.txt；或使用浏览器访问个人服务。")
        if sys.platform == "win32":
            import ctypes
            ctypes.windll.user32.MessageBoxW(None, message, t("我的秘书"), 0x10)
        raise SystemExit(message) from None

    app = QApplication(sys.argv[:1])
    app.setApplicationName("AIW Secretary")
    app.setOrganizationName("AIW")
    app.setQuitOnLastWindowClosed(False)
    preferences = QSettings()
    settings_key = "appearance/" + hashlib.sha256(origin.encode()).hexdigest()[:16]
    companion_size_key = "companion/sizePercent"
    try:
        companion_size_percent = int(preferences.value(companion_size_key, 100))
    except (TypeError, ValueError):
        companion_size_percent = 100
    if not 25 <= companion_size_percent <= 125:
        companion_size_percent = 100
    saved_language = preferences.value(settings_key + "/language", "")
    if saved_language in {"en", "zh", "ja"}: labels.language = saved_language
    image_path = Path(__file__).parent / "desktop-assets/secretary.png"
    portrait_root = Path(__file__).parent / "ui" / "assets"
    def line_icon(name="hud", color="#64e4dc", size=64):
        bitmap = QPixmap(size, size); bitmap.fill(Qt.GlobalColor.transparent)
        painter = QPainter(bitmap); painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        painter.scale(size / 24, size / 24)
        painter.setPen(QPen(QColor(color), 1.6, Qt.PenStyle.SolidLine, Qt.PenCapStyle.RoundCap, Qt.PenJoinStyle.RoundJoin))
        shapes = {
            "hud": [[(3, 8), (3, 16)], [(8, 4), (8, 20)], [(13, 9), (13, 15)], [(18, 6), (18, 18)], [(22, 11), (22, 13)]],
            "rpg": [[(12, 2), (22, 12), (12, 22), (2, 12), (12, 2)], [(12, 6), (16, 12), (12, 18), (8, 12), (12, 6)]],
            "lady": [[(16, 3), (13, 3), (10, 5), (8, 8), (8, 12), (10, 16), (14, 18), (18, 17), (21, 14)], [(16, 3), (14, 6), (14, 10), (16, 13), (20, 14)]],
            "minimize": [[(5, 16), (19, 16)]],
            "maximize": [[(5, 5), (19, 5), (19, 19), (5, 19), (5, 5)]],
            "close": [[(6, 6), (18, 18)], [(18, 6), (6, 18)]],
            "running": [[(8, 4), (20, 12), (8, 20), (8, 4)]],
            "waiting": [[(12, 3), (22, 21), (2, 21), (12, 3)], [(12, 9), (12, 14)], [(12, 17), (12, 17.2)]],
            "done": [[(4, 12), (10, 18), (20, 5)]],
        }
        for points in shapes.get(name, shapes["hud"]):
            path = QPainterPath(); path.moveTo(*points[0])
            for point in points[1:]: path.lineTo(*point)
            painter.drawPath(path)
        painter.end()
        return QIcon(bitmap)

    def colors_for(theme):
        return THEME_COLORS.get(theme, THEME_COLORS["hud"])

    icon = line_icon()
    app.setWindowIcon(icon)
    tray = None
    trusted = QUrl(origin)
    quitting = False
    service_busy = True
    exit_after_stop = False
    service_cancelled = threading.Event()
    service_client = PersonalClient(origin)

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
        stopped = Signal(str)

        def __init__(self):
            super().__init__()
            self.ready_callback = None
            self.stopped_callback = None

        @Slot(str)
        def deliver_ready(self, error):
            if self.ready_callback is not None:
                self.ready_callback(error)

        @Slot(str)
        def deliver_stopped(self, error):
            if self.stopped_callback is not None:
                self.stopped_callback(error)

    class WindowSurface(QWidget):
        def paintEvent(self, event):
            super().paintEvent(event)
            theme = getattr(self, "theme", "hud")
            painter = QPainter(self); painter.setRenderHint(QPainter.RenderHint.Antialiasing)
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Source)
            painter.fillRect(self.rect(), QColor(0, 0, 0, 0))
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceOver)
            colors = colors_for(theme)
            accent = QColor(colors["accent"])
            rect = QRectF(self.rect()).adjusted(1, 1, -1, -1)
            painter.setPen(QPen(accent, 2))
            painter.setBrush(QColor(colors["background"]) if args.opaque else Qt.BrushStyle.NoBrush)
            if theme == "rpg":
                painter.drawRoundedRect(rect, 12, 12)
                painter.setPen(QPen(QColor("#66573d"), 1)); painter.drawRoundedRect(rect.adjusted(3, 3, -3, -3), 9, 9)
            elif theme == "lady":
                painter.drawRoundedRect(rect, 12, 12)
                painter.setPen(QPen(QColor("#91d7dc"), 1)); painter.drawRoundedRect(rect.adjusted(4, 4, -4, -4), 9, 9)
            else:
                path = QPainterPath(); path.moveTo(1, 18); path.lineTo(18, 1)
                path.lineTo(self.width() - 18, 1); path.lineTo(self.width() - 1, 18)
                path.lineTo(self.width() - 1, self.height() - 18); path.lineTo(self.width() - 18, self.height() - 1)
                path.lineTo(18, self.height() - 1); path.lineTo(1, self.height() - 18); path.closeSubpath()
                painter.drawPath(path)
            painter.setPen(QPen(accent, 2))
            for x, y, dx, dy in ((7, 7, 1, 1), (self.width()-7, 7, -1, 1), (7, self.height()-7, 1, -1), (self.width()-7, self.height()-7, -1, -1)):
                painter.drawLine(x, y+dy*17, x, y); painter.drawLine(x, y, x+dx*32, y)

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
            self.setWindowTitle(t("我的秘书"))
            self.setWindowFlag(Qt.WindowType.FramelessWindowHint)
            self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground, not args.opaque)
            self.setAutoFillBackground(False)
            screen = app.primaryScreen().availableGeometry()
            self.setMinimumSize(min(760, screen.width() - 32), min(560, screen.height() - 48))
            self.resize(min(1220, screen.width() - 32), min(880, screen.height() - 48))
            content = WindowSurface(); self.surface = content
            layout = QVBoxLayout(content); layout.setContentsMargins(6, 6, 6, 6); layout.setSpacing(0)
            bar = DragBar(); bar.setObjectName("windowBar"); row = QHBoxLayout(bar); row.setContentsMargins(18, 8, 10, 8)
            self.emblem = QLabel(); self.emblem.setFixedSize(30, 30); row.addWidget(self.emblem)
            self.caption = QLabel(t("我的秘书")); row.addWidget(self.caption); row.addStretch()
            self.window_buttons = []
            for name, source, callback in (("minimize", "最小化", self.showMinimized), ("maximize", "最大化或还原", lambda: self.showNormal() if self.isMaximized() else self.showMaximized()), ("close", "隐藏窗口", self.hide)):
                button = QPushButton(); button.setObjectName("windowClose" if name == "close" else "windowControl")
                button.setFixedSize(34, 30); button.clicked.connect(callback); row.addWidget(button)
                self.window_buttons.append((button, name, source))
            layout.addWidget(bar)
            self.view = QWebEngineView()
            self.view.setAutoFillBackground(False)
            self.view.setAttribute(Qt.WidgetAttribute.WA_OpaquePaintEvent, args.opaque)
            self.view.setStyleSheet("background:transparent;")
            # Off-the-record profile keeps session cookies in memory. Theme
            # preference is saved separately in an owner-local Qt setting.
            self.profile = QWebEngineProfile(self)
            self.requests = LocalRequests(self.profile); self.profile.setUrlRequestInterceptor(self.requests)
            self.page = LocalPage(self.profile, self.view); self.page.setBackgroundColor(QColor("#080e17"))
            self.page.settings().setAttribute(QWebEngineSettings.WebAttribute.LocalContentCanAccessFileUrls, False)
            self.page.settings().setAttribute(QWebEngineSettings.WebAttribute.FullScreenSupportEnabled, False)
            self.view.setPage(self.page); layout.addWidget(self.view, 1); self.setCentralWidget(content)
            self.statusBar().setSizeGripEnabled(True)
            self.apply_theme("hud")

        def apply_theme(self, theme, force=False):
            if getattr(self, "theme", None) == theme and not force:
                return
            self.theme = theme
            colors = colors_for(theme)
            accent, panel = colors["accent"], colors["panel"]
            background, hover = colors["background"], colors["hover"]
            self.surface.theme = theme
            def surface_color(value):
                color = QColor(value)
                return value if args.opaque else f"rgba({color.red()},{color.green()},{color.blue()},240)"
            panel_fill, background_fill = surface_color(panel), surface_color(background)
            self.page.setBackgroundColor(QColor(background) if args.opaque else QColor(0, 0, 0, 0))
            window_fill = background if args.opaque else "transparent"
            self.setStyleSheet(f"QMainWindow {{background:{window_fill};}} QWidget#windowBar {{background:qlineargradient(x1:0,y1:0,x2:1,y2:0,stop:0 {panel_fill},stop:1 {background_fill});border-bottom:1px solid {accent};}} QLabel {{color:{accent};font:600 14px 'Microsoft YaHei UI';background:transparent;}} QPushButton {{background:transparent;color:{accent};border:1px solid transparent;border-radius:4px;}} QPushButton:hover {{background:{hover};border-color:{accent};}} QPushButton#windowClose:hover {{background:#702e3b;border-color:#ffa1a4;}}")
            self.statusBar().setStyleSheet(f"background:{panel_fill};color:{accent};border-top:1px solid {accent};max-height:14px;")
            app.setStyleSheet(f"QMenu {{background:{panel};color:#e2edf8;border:1px solid {accent};padding:6px;}} QMenu::item {{padding:8px 24px 8px 12px;}} QMenu::item:selected {{background:{hover};color:{accent};}} QMenu::item:disabled {{color:#738091;}} QMenu::separator {{height:1px;background:{accent};margin:5px 8px;}}")
            theme_icon = line_icon(theme, accent)
            self.setWindowIcon(theme_icon); app.setWindowIcon(theme_icon)
            if tray is not None: tray.setIcon(theme_icon)
            self.emblem.setPixmap(theme_icon.pixmap(28, 28))
            for button, name, source in self.window_buttons:
                button.setIcon(line_icon(name, accent, 32)); button.setToolTip(t(source)); button.setAccessibleName(t(source))
            self.surface.update()
            self.setWindowTitle(t("我的秘书"))
            self.caption.setText(t("我的秘书") + "  /  " + theme.upper())

        def open_view(self, name="overview"):
            url = QUrl(origin + "/"); url.setFragment(name)
            self.view.setUrl(url)
            if self.isMinimized(): self.showNormal()
            else: self.show()
            self.raise_(); self.activateWindow()

        def closeEvent(self, event):
            if quitting:
                event.accept(); return
            event.ignore(); self.hide()
            if tray is None: companion.show()

    class Companion(QWidget):
        def __init__(self):
            super().__init__(None, Qt.WindowType.FramelessWindowHint | Qt.WindowType.Tool | Qt.WindowType.WindowStaysOnTopHint)
            self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground, not args.opaque)
            self.setAutoFillBackground(False)
            self.scale = companion_size_percent / 100
            self.setFixedSize(round(310 * self.scale), round(454 * self.scale))
            self.setWindowTitle(t("秘书伙伴"))
            self.theme = "hud"; self.summary = "正在连接个人服务"; self.counts = (0, 0, 0)
            self.motion = False; self.has_snapshot = False; self.state = "unknown"
            self.clock = QElapsedTimer(); self.clock.start()
            self.greet_until = 0
            self.portraits = {}
            self.expressions = {}
            portrait_files = {"hud": "appearance-hud.png", "rpg": "appearance-rpg.png", "lady": "appearance-secretary.png"}
            for theme, filename in portrait_files.items():
                portrait = QPixmap(str(portrait_root / filename))
                if portrait.isNull(): portrait = QPixmap(str(image_path))
                self.portraits[theme] = portrait.scaled(282, 352, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation)
                wink = QPixmap() if theme == "lady" else QPixmap(str(portrait_root / f"appearance-{theme}-wink.png"))
                self.expressions[theme] = (wink if not wink.isNull() else portrait).scaled(282, 352, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation)
            self.image = self.portraits[self.theme]
            self.press = None; self.dragged = False
            self.buttons = []
            screen = app.primaryScreen().availableGeometry()
            self.move(screen.right() - self.width() - 20, max(screen.top(), screen.bottom() - self.height() - 12))
            self.update_theme()
            self.animation = QTimer(self); self.animation.setInterval(100)
            self.animation.timeout.connect(lambda: self.update() if self.isVisible() and self.motion else None)
            self.animation.start()

        def set_scale(self, percent):
            self.scale = percent / 100
            self.setFixedSize(round(310 * self.scale), round(454 * self.scale))
            screen = app.primaryScreen().availableGeometry()
            self.move(max(screen.left(), screen.right() - self.width() - 20),
                      max(screen.top(), screen.bottom() - self.height() - 12))
            self.update()

        def update_theme(self):
            self.image = self.portraits[self.theme]
            colors = colors_for(self.theme)
            accent, radius = colors["accent"], colors["radius"]
            self.count_icons = [line_icon(name, color, 18).pixmap(18, 18) for name, color in
                                (("running", accent), ("waiting", "#ffd086"), ("done", "#85e5ba"))]
            for button in self.buttons:
                button.setStyleSheet(f"QPushButton {{background:{colors['panel']};color:{accent};border:1px solid {accent};border-radius:{radius};font:11px 'Microsoft YaHei UI';}} QPushButton:hover {{background:{colors['hover']};}}")
            self.update()

        def paintEvent(self, _event):
            painter = QPainter(self); painter.setRenderHint(QPainter.RenderHint.Antialiasing)
            # Clear the alpha buffer on every repaint. No panel behind the PET:
            # the PNG alpha and the desktop remain visible around the character.
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Source)
            painter.fillRect(self.rect(), QColor(0, 0, 0, 0))
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceOver)
            colors = colors_for(self.theme)
            if args.opaque: painter.fillRect(self.rect(), QColor(colors["background"]))
            painter.scale(self.scale, self.scale)
            accent = QColor(colors["accent"])
            panel = QColor(colors["panel"])
            elapsed = self.clock.elapsed()
            wink = self.theme != "lady" and self.motion and (elapsed < self.greet_until or elapsed % 8500 < 230)
            image = self.expressions[self.theme] if wink else self.image
            bob = round(math.sin(elapsed / 750) * 3) if self.motion else 0
            painter.drawPixmap((310 - image.width()) // 2, 28 + bob, image)
            # Only useful status is visible. Descriptions live in the tooltip.
            indicator = {"ready": "#85e5ba", "paused": "#ffd086"}.get(self.state, "#9aadbf")
            painter.setPen(Qt.PenStyle.NoPen); painter.setBrush(QColor(indicator))
            painter.drawEllipse(QRectF(278, 18, 10, 10))
            if self.has_snapshot:
                for index, (count, color) in enumerate(zip(self.counts, (accent, QColor("#ffd086"), QColor("#85e5ba")))):
                    rect = QRectF(250, 185 + index * 43, 54, 34)
                    painter.setPen(QPen(color, 1)); painter.setBrush(panel); painter.drawRoundedRect(rect, 16, 16)
                    painter.drawPixmap(int(rect.left()) + 6, int(rect.top()) + 8, self.count_icons[index])
                    painter.setFont(QFont("Segoe UI", 10, QFont.Weight.Bold))
                    painter.drawText(rect.adjusted(24, 0, -3, 0), Qt.AlignmentFlag.AlignCenter, str(count))

        def enterEvent(self, event):
            if self.motion: self.greet_until = self.clock.elapsed() + 1000; self.update()
            super().enterEvent(event)

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
            if quitting:
                event.accept(); return
            event.ignore()
            if tray is not None: self.hide()
            else: workbench.show()

    workbench = Workbench()
    companion = Companion()
    menu = QMenu()
    pending_appearance = None
    pending_companion_size = None
    companion_size_seeded = False
    menu_labels = []
    def menu_action(source, callback):
        action = menu.addAction(t(source), callback); menu_labels.append((action, source)); return action
    for label, name in (("与秘书对话", "chat"), ("工作与安排", "work"), ("待你确认", "confirmations"), ("模型与设置", "setup")):
        menu_action(label, lambda _checked=False, value=name: workbench.open_view(value))
    menu.addSeparator()

    def choose_theme(mode):
        nonlocal pending_appearance
        try: current = json.loads(preferences.value(settings_key, "{}"))
        except (TypeError, ValueError): current = {}
        if not isinstance(current, dict): current = {}
        pending_appearance = {"mode": mode, "dayStart": current.get("dayStart", "08:00"), "nightStart": current.get("nightStart", "19:00")}
        # Apply PET selection locally even when an old service cannot serve the
        # Web theme/language scripts. The page receives the same preference.
        theme = mode
        if mode == "auto":
            day, night = pending_appearance["dayStart"], pending_appearance["nightStart"]
            theme = scheduled_theme(datetime.datetime.now(), day, night)
        companion.theme = theme; companion.update_theme(); workbench.apply_theme(theme)
        preferences.setValue(settings_key, json.dumps(pending_appearance, sort_keys=True))
        send_pending_appearance()

    def send_pending_appearance():
        if quitting: return
        expected = pending_appearance
        if expected is None: return
        def applied(ok):
            nonlocal pending_appearance
            if ok is True and pending_appearance == expected: pending_appearance = None
        workbench.page.runJavaScript("(() => { try { if (!window.AiwAppearance) return false; window.AiwAppearance.setPreference(" + json.dumps(expected) + "); return true; } catch (_) { return false; } })()", applied)

    for label, mode in (("HUD · 专注", "hud"), ("RPG · 伙伴", "rpg"), ("月夜 · Lady", "lady"), ("按时间自动切换", "auto")):
        menu_action(label, lambda _checked=False, value=mode: choose_theme(value))
    menu.addSeparator()
    size_menu = QMenu(t("人物大小"), menu)
    menu.addMenu(size_menu)
    menu_labels.append((size_menu.menuAction(), "人物大小"))
    size_controls = QWidget(size_menu)
    size_layout = QHBoxLayout(size_controls)
    size_layout.setContentsMargins(12, 8, 12, 8)
    size_layout.setSpacing(10)
    size_layout.addWidget(QLabel("25%"))
    size_slider = QSlider(Qt.Orientation.Horizontal, size_controls)
    size_slider.setRange(25, 125); size_slider.setSingleStep(1); size_slider.setPageStep(5)
    size_slider.setFixedWidth(170); size_slider.setValue(companion_size_percent)
    size_layout.addWidget(size_slider)
    size_value = QLabel(f"{companion_size_percent}%")
    size_value.setMinimumWidth(42); size_layout.addWidget(size_value)
    size_layout.addWidget(QLabel("125%"))
    size_slider_action = QWidgetAction(size_menu)
    size_slider_action.setDefaultWidget(size_controls)
    size_menu.addAction(size_slider_action)
    def set_companion_size(percent):
        nonlocal companion_size_percent, pending_companion_size, companion_size_seeded
        companion_size_percent = percent
        size_value.setText(f"{percent}%")
        companion.set_scale(percent)
        preferences.setValue(companion_size_key, percent)
        pending_companion_size = percent
        companion_size_seeded = True
        size_sync_timer.start(100)

    def send_pending_companion_size():
        if quitting or pending_companion_size is None: return
        expected = pending_companion_size
        script = "(() => { try { if (!window.AiwAppearance) return false; window.AiwAppearance.setCompanionSize(" + str(expected) + "); return true; } catch (_) { return false; } })()"
        def applied(ok):
            nonlocal pending_companion_size
            if ok is True and pending_companion_size == expected: pending_companion_size = None
        workbench.page.runJavaScript(script, applied)

    size_sync_timer = QTimer(app); size_sync_timer.setSingleShot(True)
    size_sync_timer.timeout.connect(send_pending_companion_size)
    size_slider.valueChanged.connect(set_companion_size)
    menu_action("显示秘书伙伴", companion.show)
    tray = None
    if QSystemTrayIcon.isSystemTrayAvailable():
        tray = QSystemTrayIcon(icon, app); tray.setToolTip(t("我的秘书")); tray.setContextMenu(menu)
        tray.activated.connect(lambda reason: workbench.open_view("chat") if reason == QSystemTrayIcon.ActivationReason.Trigger else None)
        tray.show(); menu_action("隐藏秘书伙伴", companion.hide)
    menu.addSeparator()
    def start_service():
        nonlocal service_busy
        if quitting or service_busy: return
        service_busy = True
        start_service_action.setEnabled(False); stop_service_action.setEnabled(False)
        timer.stop()
        companion.summary = "正在连接个人服务"; companion.has_snapshot = False; companion.state = "unknown"; companion.update()
        threading.Thread(target=connect, daemon=True).start()

    def confirm_action(title, message):
        # Match the shared workspace-confirm layout, without a native title bar.
        colors = colors_for(companion.theme)
        panel, nav, accent = colors["panel"], colors["background"], colors["accent"]
        parent = workbench if workbench.isVisible() else companion
        dialog = QDialog(parent)
        dialog.setWindowFlags(dialog.windowFlags() | Qt.WindowType.FramelessWindowHint)
        dialog.setWindowTitle(t(title)); dialog.setAccessibleName(t(title))
        dialog.setMinimumWidth(420); dialog.setMaximumWidth(620)
        dialog.setStyleSheet(f"QDialog {{background:{panel};border:1px solid {accent};border-top:2px solid #ffa1a4;color:#e2edf8;}} QLabel {{color:#e2edf8;background:transparent;border:0;}} QLabel#confirmHeading {{background:{nav};color:{accent};padding:16px 24px;}} QLabel#confirmTitle {{font-size:20px;font-weight:600;}} QPushButton {{padding:10px 24px;border:1px solid {accent};border-radius:4px;background:{nav};color:{accent};}} QPushButton:focus {{border:2px solid {accent};}} QPushButton#confirmAccept {{color:#ffa1a4;border-color:#ffa1a4;}}")
        layout = QVBoxLayout(dialog); layout.setContentsMargins(0, 0, 0, 0); layout.setSpacing(0)
        heading = QLabel(t("确认操作")); heading.setObjectName("confirmHeading"); layout.addWidget(heading)
        body = QWidget(); body_layout = QVBoxLayout(body); body_layout.setContentsMargins(24, 24, 24, 24); body_layout.setSpacing(16)
        label = QLabel(t(title)); label.setObjectName("confirmTitle"); label.setWordWrap(True); body_layout.addWidget(label)
        description = QLabel(t(message)); description.setWordWrap(True); body_layout.addWidget(description); layout.addWidget(body)
        footer = QWidget(); buttons = QHBoxLayout(footer); buttons.setContentsMargins(24, 16, 24, 20); buttons.addStretch()
        cancel = QPushButton(t("取消")); cancel.clicked.connect(dialog.reject); cancel.setDefault(True); buttons.addWidget(cancel)
        accept = QPushButton(t("确认")); accept.setObjectName("confirmAccept"); accept.setAutoDefault(False); accept.clicked.connect(dialog.accept); buttons.addWidget(accept)
        layout.addWidget(footer); cancel.setFocus()
        return dialog.exec() == QDialog.DialogCode.Accepted

    def stop_service():
        nonlocal service_busy
        if quitting or service_busy: return
        if not confirm_action("停止本地服务", "停止本地服务会中断正在处理的请求。私人记录和设置保留，桌面仍继续运行。是否停止？"):
            return
        if quitting or service_busy: return
        service_busy = True
        start_service_action.setEnabled(False); stop_service_action.setEnabled(False)
        timer.stop()
        companion.summary = "正在停止本地服务"; companion.has_snapshot = False; companion.state = "paused"; companion.update()
        def shutdown():
            try:
                service_client.shutdown_service()
                startup.stopped.emit("")
            except PersonalError as error:
                startup.stopped.emit(str(error))
            except Exception:
                startup.stopped.emit("停止本地服务未确认，请核对服务状态。")
        threading.Thread(target=shutdown, daemon=True).start()

    def stop_and_quit():
        nonlocal exit_after_stop
        if quitting or service_busy: return
        exit_after_stop = True
        stop_service()
        if not service_busy: exit_after_stop = False  # Confirmation cancelled.

    start_service_action = menu_action("启动本地服务", start_service)
    stop_service_action = menu_action("停止本地服务", stop_service)
    start_service_action.setEnabled(False); stop_service_action.setEnabled(False)
    menu_action("退出秘书并停止本地服务", stop_and_quit)

    def prepare_exit():
        nonlocal quitting
        if quitting: return
        quitting = True
        service_cancelled.set()
        timer.stop(); companion.animation.stop(); workbench.view.stop()
        menu.close()
        if tray is not None: tray.hide()
        workbench.close(); companion.close()

    def quit_desktop():
        if quitting: return
        prepare_exit()
        QTimer.singleShot(0, lambda: app.exit(0))

    menu_action("退出桌面界面（服务继续运行）", quit_desktop)

    restored = False
    last_saved = None
    def synchronize(value):
        nonlocal restored, last_saved, companion_size_percent, companion_size_seeded
        if quitting or service_busy: return
        # Qt/PySide versions do not consistently convert a JavaScript object
        # returned by runJavaScript into a Python dict. Send JSON explicitly;
        # otherwise this callback silently ignores every snapshot, leaving the
        # native title, frame, locale and PET at their initial HUD defaults.
        if isinstance(value, str):
            if len(value) > 64000: return
            try: value = json.loads(value)
            except (ValueError, TypeError): return
        if not isinstance(value, dict) or not value.get("appearance"):
            return
        if not restored:
            restored = True
            saved_locale = preferences.value(settings_key + "/language", "")
            if saved_locale in {"en", "zh", "ja"}:
                workbench.page.runJavaScript("window.aiwI18n && window.aiwI18n.setLocale(" + json.dumps(saved_locale) + ")")
            saved = preferences.value(settings_key, "")
            try: saved = json.loads(saved)
            except (ValueError, TypeError): saved = None
            if pending_appearance is None and isinstance(saved, dict) and saved.get("mode") in {"auto", "hud", "rpg", "lady"}:
                workbench.page.runJavaScript("try { window.AiwAppearance.setPreference(" + json.dumps(saved) + "); } catch (_) {}")
                return
            if saved_locale in {"en", "zh", "ja"}:
                return
        appearance = value["appearance"]
        if pending_companion_size is not None:
            send_pending_companion_size()
        else:
            web_size = appearance.get("companionSize")
            if isinstance(web_size, int) and 25 <= web_size <= 125:
                if web_size != companion_size_percent:
                    companion_size_percent = web_size
                    companion.set_scale(web_size)
                    preferences.setValue(companion_size_key, web_size)
                if size_slider.value() != web_size:
                    size_slider.blockSignals(True); size_slider.setValue(web_size); size_slider.blockSignals(False)
                size_value.setText(f"{web_size}%")
                companion_size_seeded = True
            elif not companion_size_seeded:
                companion_size_seeded = True
                workbench.page.runJavaScript("window.AiwAppearance.setCompanionSize(" + str(companion_size_percent) + ")")
        language = value.get("locale", "en")
        if language in {"en", "zh", "ja"} and labels.language != language:
            labels.language = language
            preferences.setValue(settings_key + "/language", language)
            for action, source in menu_labels: action.setText(t(source))
            workbench.apply_theme(companion.theme, force=True)
            companion.setWindowTitle(t("秘书伙伴"))
            if tray is not None: tray.setToolTip(t("我的秘书"))
        theme = appearance.get("effective", "hud")
        if pending_appearance is None and theme in {"hud", "rpg", "lady"} and companion.theme != theme:
            companion.theme = theme; companion.update_theme()
        workbench.apply_theme(companion.theme)
        encoded = json.dumps(appearance.get("preference", {}), sort_keys=True)
        if pending_appearance is None and encoded != last_saved:
            preferences.setValue(settings_key, encoded); last_saved = encoded
        companion.summary = value.get("status", "等待连接")
        companion.counts = tuple(value.get("counts", [0, 0, 0]))
        companion.motion = value.get("motion") is True
        companion.has_snapshot = value.get("hasSnapshot") is True
        companion.state = value.get("state", "unknown")
        companion.setToolTip(t(companion.summary) + "\n" + " · ".join(f"{t(name)} {count}" for name, count in zip(("进行中", "待你确认", "已完成"), companion.counts)))
        companion.setAccessibleName(t("秘书伙伴")); companion.setAccessibleDescription(companion.toolTip())
        companion.update()

    script = """(() => {
      if (!window.AiwAppearance) return null;
      const s = typeof snapshot === 'object' && snapshot ? snapshot : null;
      if (document.hidden && typeof refresh === 'function' && typeof csrf === 'string' && csrf
          && typeof pollingState === 'boolean' && !pollingState) {
        pollingState = true;
        refresh().catch(() => {}).finally(() => { pollingState = false; });
      }
      const work = s && Array.isArray(s.work) ? s.work.filter(w => w.state !== 'TRASHED') : [];
      const decisions = s && Array.isArray(s.decisions) ? s.decisions : [];
      return JSON.stringify({ appearance: window.AiwAppearance.getState(),
        locale: window.aiwI18n ? window.aiwI18n.locale : 'en',
        motion: !matchMedia('(prefers-reduced-motion: reduce)').matches && document.documentElement.dataset.motion !== 'off',
        hasSnapshot: !!s,
        state: !s || s.modelConfigured === false ? 'unknown' : s.enabled ? 'ready' : 'paused',
        status: !s ? '等待连接个人服务' : s.modelConfigured === false ? '请先连接模型' : s.enabled ? '秘书可用' : '秘书已暂停',
        counts: [work.filter(w => ['DAILY_RUNNING','ACTIVE','START_PENDING'].includes(w.state)).length,
          decisions.filter(d => d.kind === 'DAILY_CONFIRMATION' && d.state === 'WAITING').length
            + work.filter(w => ['NEEDS_CONFIRMATION','NEEDS_HUMAN'].includes(w.state)
              && ['RECOVERY_CONFIRMATION','LEGACY_SCHEDULE_CONFIRMATION','MISSED_TIME','INTERRUPTED'].includes(w.report && w.report.code)).length,
          work.filter(w => w.state === 'DONE').length] });
    })()"""
    timer = QTimer(app); timer.setInterval(3000)
    def poll():
        if quitting or service_busy: return
        if tray is not None and not QSystemTrayIcon.isSystemTrayAvailable() and not companion.isVisible():
            companion.show()
        workbench.page.runJavaScript(script, synchronize)
    timer.timeout.connect(poll)
    menu.aboutToShow.connect(poll)
    def page_loaded(ok):
        if not ok or quitting: return
        mode = "off" if args.opaque else "on"
        workbench.page.runJavaScript("document.documentElement.dataset.desktopTransparency = " + json.dumps(mode))
        send_pending_appearance()
        send_pending_companion_size()
        poll()
    workbench.view.loadFinished.connect(page_loaded)
    startup = Startup()
    def ready(error):
        nonlocal service_busy, restored
        if quitting: return
        service_busy = False
        start_service_action.setEnabled(True); stop_service_action.setEnabled(not bool(error))
        if error:
            companion.summary = "个人服务尚未就绪，请启动服务后刷新页面。"; companion.state = "unknown"; companion.has_snapshot = False; companion.update()
            if tray is not None:
                tray.showMessage(t("连接个人服务"), t("个人服务尚未连接，请从托盘打开工作台查看并重试。"))
            else:
                QMessageBox.warning(workbench, t("连接个人服务"), t(error))
        else:
            restored = False
            workbench.view.setUrl(QUrl(origin + "/"))
            timer.start()
        if tray is None: workbench.show()
    startup.ready_callback = ready
    startup.ready.connect(startup.deliver_ready, Qt.ConnectionType.QueuedConnection)
    def stopped(error):
        nonlocal service_busy, exit_after_stop
        if quitting: return
        should_exit = exit_after_stop and not error
        exit_after_stop = False
        service_busy = False
        start_service_action.setEnabled(True); stop_service_action.setEnabled(bool(error))
        # The desktop companion remains usable while the local service is
        # stopped. Keep its animation and status polling alive in that state.
        companion.animation.start()
        timer.start()
        companion.summary = "停止本地服务未确认，请核对服务状态。" if error else "本地服务已停止"
        companion.state = "unknown"; companion.has_snapshot = False; companion.counts = (0, 0, 0)
        companion.setToolTip(t(companion.summary)); companion.update()
        if error:
            warning = QMessageBox(QMessageBox.Icon.Warning, t("停止本地服务"), t(error),
                                  QMessageBox.StandardButton.Ok, companion)
            warning.setWindowModality(Qt.WindowModality.NonModal)
            warning.setAttribute(Qt.WidgetAttribute.WA_DeleteOnClose)
            companion.stop_warning = warning
            warning.show()
            if tray is not None:
                tray.showMessage(t("停止本地服务"), t(error))
        elif tray is not None:
            tray.showMessage(t("我的秘书"), t("本地服务已停止"))
        if should_exit: quit_desktop()
    startup.stopped_callback = stopped
    startup.stopped.connect(startup.deliver_stopped, Qt.ConnectionType.QueuedConnection)
    def connect():
        try:
            service_client.auto_connect(cancelled=service_cancelled)
            startup.ready.emit("")
        except PersonalError as error:
            startup.ready.emit(str(error))
        except Exception:
            startup.ready.emit("个人服务尚未就绪，请启动服务后刷新页面。")
    if args.show_pet or tray is None: companion.show()
    if tray is None: workbench.show()
    threading.Thread(target=connect, daemon=True).start()
    app.aboutToQuit.connect(prepare_exit)
    try:
        return app.exec()
    finally:
        desktop_lease.release()


if __name__ == "__main__":
    raise SystemExit(main())
