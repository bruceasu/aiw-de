"""Display translations for the Qt shell; private content is never translated."""
import locale

MESSAGES = {
    "确认操作": ("Confirm action", "操作の確認"),
    "取消": ("Cancel", "キャンセル"),
    "确认": ("Confirm", "確認"),
    "退出秘书并停止本地服务": ("Quit secretary and stop local service", "秘書を終了してローカルサービスを停止"),
    "最小化": ("Minimize", "最小化"),
    "最大化或还原": ("Maximize or restore", "最大化・元に戻す"),
    "隐藏窗口": ("Hide window", "ウィンドウを非表示"),
    "我的秘书": ("My secretary", "マイ秘書"),
    "秘书伙伴": ("Secretary companion", "秘書パートナー"),
    "正在连接": ("Connecting", "接続中"),
    "正在连接个人服务": ("Connecting to personal service", "個人サービスに接続中"),
    "等待连接个人服务": ("Waiting for personal service", "個人サービスを待機中"),
    "等待连接": ("Waiting for connection", "接続待ち"),
    "请先连接模型": ("Connect a model first", "先にモデルを接続"),
    "秘书可用": ("Secretary ready", "秘書は利用可能"),
    "秘书已暂停": ("Secretary paused", "秘書は一時停止中"),
    "与秘书对话": ("Chat with secretary", "秘書と会話"),
    "工作与安排": ("Work and schedules", "作業とスケジュール"),
    "待你确认": ("Needs your confirmation", "確認待ち"),
    "模型与设置": ("Model and settings", "モデルと設定"),
    "HUD · 专注": ("HUD · Focus", "HUD · 集中"),
    "RPG · 伙伴": ("RPG · Companion", "RPG · パートナー"),
    "按时间自动切换": ("Switch automatically by time", "時刻で自動切替"),
    "显示秘书伙伴": ("Show companion", "パートナーを表示"),
    "隐藏秘书伙伴": ("Hide companion", "パートナーを非表示"),
    "退出桌面界面（服务继续运行）": ("Quit desktop (service keeps running)", "デスクトップを終了（サービスは継続）"),
    "连接个人服务": ("Connect personal service", "個人サービスに接続"),
    "个人服务尚未连接，请从托盘打开工作台查看并重试。": ("Service is not connected. Open the workbench from the tray to check and retry.", "未接続です。トレイから作業画面を開いて確認し、再試行してください。"),
    "个人服务尚未就绪，请启动服务后刷新页面。": ("Service is not ready. Start it and refresh the page.", "サービスが未起動です。起動後にページを更新してください。"),
    "需要桌面依赖。请在秘书目录使用当前 Python 安装 requirements-desktop.txt；或使用浏览器访问个人服务。": ("Desktop dependencies are missing. Install requirements-desktop.txt with the current Python, or use the browser.", "デスクトップ用依存がありません。現在の Python で requirements-desktop.txt をインストールするか、ブラウザーを使用してください。"),
    "进行中": ("Running", "処理中"),
    "已完成": ("Completed", "完了"),
    "启动本地服务": ("Start local service", "ローカルサービスを起動"),
    "停止本地服务": ("Stop local service", "ローカルサービスを停止"),
    "本地服务已停止": ("Local service stopped", "ローカルサービスは停止しました"),
    "正在停止本地服务": ("Stopping local service", "ローカルサービスを停止中"),
    "停止本地服务会中断正在处理的请求。私人记录和设置保留，桌面仍继续运行。是否停止？": ("Stopping the local service interrupts active requests. Private records and settings are kept; the desktop stays open. Stop the service?", "サービスを停止すると処理中の要求は中断されます。個人記録と設定は保持し、デスクトップは継続します。停止しますか？"),
    "停止本地服务未确认，请核对服务状态。": ("Service shutdown is unconfirmed. Check its status.", "サービスの停止は未確認です。状態を確認してください。"),
    "请先启动或连接本地服务，再停止服务。": ("Start or connect to the local service before stopping it.", "先にローカルサービスを起動または接続してください。"),
    "运行中的旧服务不支持正常退出。请手动关闭并更新服务后再使用此入口。": ("This older service does not support graceful shutdown. Close it manually and update it before using this action.", "実行中の旧サービスは正常終了に未対応です。手動で閉じて更新してから、この操作を使用してください。"),
    "服务没有确认停止请求，请核对状态。": ("The service did not confirm shutdown. Check its status.", "サービスが停止要求を確認しませんでした。状態を確認してください。"),
    "停止请求已接受，但无法确认本地服务是否关闭。": ("Shutdown accepted, but service closure could not be confirmed.", "停止要求は受け付けましたが、サービスの終了は確認できません。"),
    "停止请求已接受，服务仍在清理。请稍后查看状态，不要强制结束正在保存的操作。": ("Shutdown accepted; cleanup is still running. Check later and do not force-stop active saves.", "停止要求を受け付け、後処理中です。後で状態を確認し、保存中の操作を強制終了しないでください。"),
    "桌面正在退出，已取消启动本地服务。": ("Desktop is exiting; local service startup cancelled.", "デスクトップを終了中のため、ローカルサービスの起動を取り消しました。"),
}

MESSAGES.update({
    "月夜 · Lady": ("Lady · Moonlit", "Lady · 月夜"),
})


class DesktopTranslations:
    def __init__(self):
        self.language = (locale.getlocale()[0] or "en").split("_")[0].lower()
        if self.language not in {"en", "zh", "ja"}:
            self.language = "en"

    def t(self, source):
        if self.language == "zh":
            return source
        return MESSAGES.get(source, (source, source))[1 if self.language == "ja" else 0]
