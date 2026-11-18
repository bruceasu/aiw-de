"""Display translations for the Qt shell; private content is never translated."""
import locale

MESSAGES = {
    "确认操作": "Confirm action",
    "取消": "Cancel",
    "确认": "Confirm",
    "退出秘书并停止本地服务": "Quit secretary and stop local service",
    "最小化": "Minimize",
    "最大化或还原": "Maximize or restore",
    "隐藏窗口": "Hide window",
    "我的秘书": "My secretary",
    "秘书伙伴": "Secretary companion",
    "正在连接": "Connecting",
    "正在连接个人服务": "Connecting to personal service",
    "等待连接个人服务": "Waiting for personal service",
    "等待连接": "Waiting for connection",
    "请先连接模型": "Connect a model first",
    "秘书可用": "Secretary ready",
    "秘书已暂停": "Secretary paused",
    "与秘书对话": "Chat with secretary",
    "工作与安排": "Work and schedules",
    "待你确认": "Needs your confirmation",
    "模型与设置": "Model and settings",
    "HUD · 专注": "HUD · Focus",
    "RPG · 伙伴": "RPG · Companion",
    "按时间自动切换": "Switch automatically by time",
    "人物大小": "Companion size",
    "显示秘书伙伴": "Show companion",
    "隐藏秘书伙伴": "Hide companion",
    "退出桌面界面（服务继续运行）": "Quit desktop (service keeps running)",
    "连接个人服务": "Connect personal service",
    "个人服务尚未连接，请从托盘打开工作台查看并重试。": "Service is not connected. Open the workbench from the tray to check and retry.",
    "个人服务尚未就绪，请启动服务后刷新页面。": "Service is not ready. Start it and refresh the page.",
    "需要桌面依赖。请在秘书目录使用当前 Python 安装 requirements-desktop.txt；或使用浏览器访问个人服务。": "Desktop dependencies are missing. Install requirements-desktop.txt with the current Python, or use the browser.",
    "进行中": "Running",
    "已完成": "Completed",
    "启动本地服务": "Start local service",
    "停止本地服务": "Stop local service",
    "本地服务已停止": "Local service stopped",
    "正在停止本地服务": "Stopping local service",
    "停止本地服务会中断正在处理的请求。私人记录和设置保留，桌面仍继续运行。是否停止？": "Stopping the local service interrupts active requests. Private records and settings are kept; the desktop stays open. Stop the service?",
    "停止本地服务未确认，请核对服务状态。": "Service shutdown is unconfirmed. Check its status.",
    "请先启动或连接本地服务，再停止服务。": "Start or connect to the local service before stopping it.",
    "运行中的旧服务不支持正常退出。请手动关闭并更新服务后再使用此入口。": "This older service does not support graceful shutdown. Close it manually and update it before using this action.",
    "服务没有确认停止请求，请核对状态。": "The service did not confirm shutdown. Check its status.",
    "停止请求已接受，但无法确认本地服务是否关闭。": "Shutdown accepted, but service closure could not be confirmed.",
    "停止请求已接受，服务仍在清理。请稍后查看状态，不要强制结束正在保存的操作。": "Shutdown accepted; cleanup is still running. Check later and do not force-stop active saves.",
    "桌面正在退出，已取消启动本地服务。": "Desktop is exiting; local service startup cancelled.",
}

MESSAGES.update({
    "月夜 · Lady": "Lady · Moonlit",
})


class DesktopTranslations:
    def __init__(self):
        self.language = (locale.getlocale()[0] or "en").split("_")[0].lower()
        if self.language not in {"en", "zh"}:
            self.language = "en"

    def t(self, source):
        if self.language == "zh":
            return source
        return MESSAGES.get(source, source)
