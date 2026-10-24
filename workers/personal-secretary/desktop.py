"""Optional Windows desktop UI. Connect to an already running personal Worker."""

from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime
import json
import math
from pathlib import Path
import queue
import sys
import threading
import time
import webbrowser

from desktop_client import PersonalClient, PersonalError, personal_origin

BG, PANEL, TEXT, MUTED, LINE, ACCENT = "#080e17", "#111c2b", "#e2edf8", "#9aadbf", "#2b4055", "#64e4dc"
TRANSPARENT = "#010203"


def main():
    if sys.platform != "win32":
        raise SystemExit("The desktop UI currently supports Windows only. The Web UI remains available.")
    import tkinter as tk
    from tkinter import ttk, messagebox
    from tkinter.scrolledtext import ScrolledText
    from desktop_win32 import TrayIcon, move, work_area

    parser = argparse.ArgumentParser(description="AIW:DE desktop secretary; attach to an existing service")
    parser.add_argument("--service", default="http://127.0.0.1:8766")
    args = parser.parse_args()
    try:
        origin = personal_origin(args.service)
    except PersonalError as error:
        raise SystemExit(str(error)) from None

    class Desktop:
        def __init__(self):
            self.root = tk.Tk()
            self.root.title("我的秘书 · AIW:DE")
            self.root.overrideredirect(True)
            self.root.configure(bg=TRANSPARENT)
            self.root.attributes("-transparentcolor", TRANSPARENT)
            self.root.attributes("-toolwindow", True)
            self.root.attributes("-topmost", True)
            self.topmost = tk.BooleanVar(value=True)
            self.client = None
            self.snapshot = {}
            self.events = queue.Queue()
            self.busy = False
            self.closing = False
            self.controls = []
            self.fields = {}
            self.choices = []
            self.choice_ids = {}
            self.pages = []
            self.goal_preview = None
            self.goal_version = 0
            self.goal_submitted = False
            self.schedule_preview = None
            self.schedule_conflict = False
            self.chat_window = self.workbench = self.connection = None
            self.tray = None
            self.status = tk.StringVar(value="请连接本机秘书服务")
            self.setup_style()
            image = tk.PhotoImage(file=str(Path(__file__).parent / "desktop-assets" / "secretary.png"))
            factor = max(1, math.ceil(max(image.width() / 280, image.height() / 400)))
            self.image = image.subsample(factor, factor)
            self.width, self.height = max(260, self.image.width() + 24), self.image.height() + 70
            self.canvas = tk.Canvas(self.root, width=self.width, height=self.height, bg=TRANSPARENT, highlightthickness=0)
            self.canvas.pack()
            self.canvas.create_image(self.width // 2, 8, image=self.image, anchor="n")
            self.canvas.create_rectangle(8, self.height - 46, self.width - 8, self.height - 8, fill=PANEL, outline=LINE)
            self.pet_label = self.canvas.create_text(self.width // 2, self.height - 27, text="尚未配对 · 点击连接", fill=ACCENT, font=("Microsoft YaHei UI", 9))
            self.canvas.bind("<ButtonPress-1>", self.drag_start)
            self.canvas.bind("<B1-Motion>", self.drag_move)
            self.canvas.bind("<ButtonRelease-1>", self.drag_end)
            self.canvas.bind("<Button-3>", self.context_menu)
            self.root.update_idletasks()
            left, top, right, bottom = work_area(0, 0)
            move(self.root, right - self.width - 24, max(top, bottom - self.height - 16))
            try:
                self.tray = TrayIcon(self.root, lambda command: self.events.put(("tray", command)))
            except OSError:
                self.status.set("托盘不可用，可使用人物右键菜单；已禁用隐藏人物。")
            self.menu = tk.Menu(self.root, tearoff=False, bg=PANEL, fg=TEXT, activebackground=LINE, activeforeground=TEXT)
            self.menu.add_command(label="与秘书对话", command=self.show_chat)
            self.menu.add_command(label="工作与安排", command=lambda: self.show_workbench(0))
            self.menu.add_command(label="代理策略", command=lambda: self.show_workbench(1))
            self.menu.add_command(label="目标提案", command=lambda: self.show_workbench(2))
            self.menu.add_separator()
            self.menu.add_command(label="刷新状态", command=self.refresh)
            self.menu.add_command(label="暂停动作申请", command=lambda: self.operate("enable", {"enabled": False}, "暂停动作申请"))
            self.menu.add_command(label="打开 Web", command=lambda: self.open_web())
            self.menu.add_command(label="连接与设置", command=self.show_connection)
            self.menu.add_checkbutton(label="人物置顶", variable=self.topmost, command=lambda: self.root.attributes("-topmost", self.topmost.get()))
            self.menu.add_command(label="显示人物", command=self.show_pet)
            self.menu.add_command(label="隐藏人物", command=self.hide_pet, state="normal" if self.tray else "disabled")
            self.menu.add_separator()
            self.menu.add_command(label="退出桌面界面（服务继续运行）", command=self.close)
            self.root.bind_all("<MouseWheel>", self.scroll_page, add="+")
            self.root.after(100, self.drain)
            self.root.after(200, self.show_connection)

        def setup_style(self):
            self.root.option_add("*Font", ("Microsoft YaHei UI", 10))
            style = ttk.Style(self.root)
            style.theme_use("clam")
            style.configure(".", background=BG, foreground=TEXT, bordercolor=LINE, darkcolor=LINE, lightcolor=LINE, troughcolor=BG)
            style.configure("TFrame", background=BG)
            style.configure("TLabel", background=BG, foreground=TEXT)
            style.configure("Muted.TLabel", foreground=MUTED)
            style.configure("Title.TLabel", font=("Microsoft YaHei UI", 17, "bold"))
            style.configure("TLabelframe", bordercolor=LINE, padding=16)
            style.configure("TLabelframe.Label", foreground=ACCENT)
            style.configure("TButton", padding=(14, 9), background=PANEL, foreground=TEXT)
            style.map("TButton", background=[("active", LINE)], foreground=[("disabled", MUTED)])
            style.configure("Accent.TButton", background=ACCENT, foreground=BG)
            style.map("Accent.TButton", background=[("active", "#8ceee8")], foreground=[("disabled", "#567b7a")])
            style.configure("TEntry", fieldbackground=PANEL, foreground=TEXT, padding=8, insertcolor=TEXT)
            style.map("TEntry", fieldbackground=[("readonly", BG)], foreground=[("readonly", MUTED)])
            style.configure("TCombobox", fieldbackground=PANEL, foreground=TEXT, padding=8, arrowsize=16)
            style.map("TCombobox", fieldbackground=[("readonly", PANEL)], selectbackground=[("readonly", PANEL)], selectforeground=[("readonly", TEXT)])
            style.configure("TCheckbutton", background=BG, foreground=TEXT)
            style.configure("TNotebook", background=BG, borderwidth=0)
            style.configure("TNotebook.Tab", padding=(18, 12), background=PANEL)
            style.map("TNotebook.Tab", background=[("selected", LINE)], foreground=[("selected", ACCENT)])
            self.root.option_add("*TCombobox*Listbox.background", PANEL)
            self.root.option_add("*TCombobox*Listbox.foreground", TEXT)

        def window(self, title, size):
            window = tk.Toplevel(self.root)
            window.title(title + " · 我的秘书")
            window.configure(bg=BG)
            window.geometry(size)
            window.protocol("WM_DELETE_WINDOW", window.withdraw)
            window.iconphoto(False, self.image)
            return window

        def activate(self, window):
            window.deiconify()
            window.lift()
            window.focus_force()

        def text(self, parent, height=4, readonly=False):
            widget = ScrolledText(parent, height=height, wrap="word", bg=PANEL, fg=TEXT,
                insertbackground=TEXT, relief="flat", padx=12, pady=10, highlightthickness=1, highlightbackground=LINE)
            widget.pack(fill="both", expand=True, pady=(5, 12))
            if readonly:
                widget.configure(state="disabled")
            return widget

        def set_text(self, widget, value, readonly=False):
            widget.configure(state="normal")
            widget.delete("1.0", "end")
            widget.insert("1.0", value)
            if readonly:
                widget.configure(state="disabled")

        def field(self, parent, label, value="", readonly=False, secret=False):
            ttk.Label(parent, text=label, style="Muted.TLabel").pack(anchor="w", pady=(8, 4))
            variable = tk.StringVar(value=value)
            entry = ttk.Entry(parent, textvariable=variable, show="•" if secret else "")
            self.fields[str(variable)] = entry
            entry.pack(fill="x", pady=(0, 6))
            if readonly:
                entry.configure(state="readonly")
            return variable

        def action(self, parent, label, command, primary=False):
            control = ttk.Button(parent, text=label, command=command, style="Accent.TButton" if primary else "TButton")
            control.pack(side="left", padx=(0, 10), pady=10)
            self.controls.append(control)
            return control

        def page(self, title):
            shell = ttk.Frame(self.notebook)
            self.notebook.add(shell, text=title)
            canvas = tk.Canvas(shell, bg=BG, highlightthickness=0)
            scrollbar = ttk.Scrollbar(shell, orient="vertical", command=canvas.yview)
            canvas.configure(yscrollcommand=scrollbar.set)
            scrollbar.pack(side="right", fill="y")
            canvas.pack(side="left", fill="both", expand=True)
            body = ttk.Frame(canvas, padding=24)
            item = canvas.create_window(0, 0, window=body, anchor="nw")
            body.bind("<Configure>", lambda _event: canvas.configure(scrollregion=canvas.bbox("all")))
            canvas.bind("<Configure>", lambda event: canvas.itemconfigure(item, width=event.width))
            self.pages.append((body, canvas))
            return body

        def scroll_page(self, event):
            for body, canvas in self.pages:
                if str(event.widget).startswith(str(body)) or event.widget == canvas:
                    canvas.yview_scroll(-int(event.delta / 120), "units")
                    return "break"

        def work_select(self, parent, label, command=None):
            ttk.Label(parent, text=label, style="Muted.TLabel").pack(anchor="w", pady=(8, 4))
            control = ttk.Combobox(parent, state="readonly")
            control.pack(fill="x", pady=(0, 10))
            self.choices.append(control)
            if command:
                control.bind("<<ComboboxSelected>>", lambda _event: command())
            return control

        def selected_work(self, control):
            ids = self.choice_ids.get(control, [])
            index = control.current()
            return ids[index] if 0 <= index < len(ids) else ""

        def update_choices(self):
            work = sorted(self.snapshot.get("work", []), key=lambda row: row.get("title", ""))
            counts = Counter(row.get("title", "未命名工作") for row in work)
            seen = Counter()
            labels = []
            for row in work:
                title = row.get("title", "未命名工作")
                seen[title] += 1
                labels.append(title if counts[title] == 1 else f"{title}（{seen[title]}）")
            for control in self.choices:
                previous = self.selected_work(control)
                self.choice_ids[control] = [row["id"] for row in work]
                control.configure(values=labels)
                if previous in self.choice_ids[control]:
                    control.current(self.choice_ids[control].index(previous))
                elif len(work) == 1 and not previous:
                    control.current(0)
                else:
                    control.set("")

        def update_state(self, state):
            self.snapshot = state
            self.update_choices()
            runtime_label = "状态待读取" if "enabled" not in state else ("动作申请已启用" if state["enabled"] else "动作申请已暂停")
            self.canvas.itemconfigure(self.pet_label, text=runtime_label + " · 点击对话")
            if self.chat_window:
                self.render_chat()
            if self.workbench:
                self.update_schedule()
                self.update_goal_history()
                policies = self.snapshot.get("policies", [])
                self.policy_choices.configure(values=[row["id"] for row in policies])
                self.set_text(self.state_text, json.dumps(state, ensure_ascii=False, indent=2), True)

        def run(self, label, action, complete):
            if self.busy:
                self.status.set("请等待当前操作返回；窗口仍可移动、隐藏或关闭。")
                return
            self.busy = True
            self.status.set(label + "…")
            self.canvas.itemconfigure(self.pet_label, text=label + "…")
            for control in self.controls:
                control.configure(state="disabled")

            def work():
                try:
                    self.events.put(("result", complete, action(), None))
                except PersonalError as error:
                    self.events.put(("result", complete, None, error))
                except Exception:
                    self.events.put(("result", complete, None, PersonalError("操作结果未确认。请刷新状态并核对原请求回执。")))
            threading.Thread(target=work, daemon=True).start()

        def drain(self):
            if self.closing:
                return
            try:
                while True:
                    item = self.events.get_nowait()
                    if item[0] == "tray":
                        if item[1] == "chat": self.show_chat()
                        elif item[1] == "menu": self.context_menu()
                        else:
                            self.show_pet()
                            self.menu.entryconfigure("隐藏人物", state="disabled")
                            self.status.set("托盘恢复失败，已显示人物并禁用隐藏。")
                        continue
                    _, complete, result, error = item
                    self.busy = False
                    for control in self.controls:
                        control.configure(state="normal")
                    self.sync_goal_button()
                    self.sync_schedule_button()
                    if error:
                        self.status.set(str(error))
                        self.canvas.itemconfigure(self.pet_label, text="操作未确认 · 点击查看")
                        if error.status == 401:
                            self.show_connection()
                    else:
                        try:
                            complete(result)
                        except Exception:
                            self.status.set("结果已返回，但界面更新失败。请刷新状态并核对原回执。")
            except queue.Empty:
                pass
            self.root.after(100, self.drain)

        def require_session(self):
            if not self.client:
                self.show_connection()
                return False
            return True

        def operate(self, route, data, label, callback=None):
            if not self.require_session():
                return
            if not self.client.csrf:
                self.status.set("配对已接受，请先刷新已有会话以读取状态，无需重复配对。")
                self.show_connection()
                return
            def complete(outcome):
                result, state, warning = outcome
                if state is not None:
                    self.update_state(state)
                if callback:
                    callback(result)
                self.status.set(warning or ("个人模型回复已保存。" if route == "chat" else label + "已返回结果，状态已更新。"))
                if warning:
                    self.canvas.itemconfigure(self.pet_label, text="状态读取失败 · 请刷新")
            self.run(label, lambda: self.client.operate(route, data), complete)

        def refresh(self):
            if self.require_session():
                def complete(state):
                    self.update_state(state)
                    self.status.set("已读取最新私人状态。")
                self.run("读取私人状态", self.client.state, complete)

        def show_connection(self):
            if not self.connection:
                self.connection = self.window("连接与设置", "660x650")
                body = ttk.Frame(self.connection, padding=28)
                body.pack(fill="both", expand=True)
                ttk.Label(body, text="连接你的秘书", style="Title.TLabel").pack(anchor="w")
                ttk.Label(body, text="在 Web 的“状态与备份”生成新配对码；尚未使用的启动配对码也可使用。", wraplength=580, style="Muted.TLabel").pack(anchor="w", pady=(8, 12))
                self.service = self.field(body, "本机个人服务地址", origin)
                self.code = self.field(body, "一次性配对码（不填写平台凭证）", secret=True)
                bar = ttk.Frame(body); bar.pack(fill="x")
                self.action(bar, "配对并连接", self.connect, True)
                self.action(bar, "刷新已有会话", self.refresh)
                self.action(bar, "打开 Web", lambda: self.open_web("state", True))
                ttk.Separator(body).pack(fill="x", pady=16)
                ttk.Label(body, text="连接另一种界面", font=("Microsoft YaHei UI", 12, "bold")).pack(anchor="w")
                ttk.Label(body, text="已配对后可生成新码，供另一个 Web 或桌面界面单独登录。新码五分钟有效且只能使用一次。", wraplength=580, style="Muted.TLabel").pack(anchor="w", pady=8)
                self.pair_code = self.field(body, "临时配对码", readonly=True, secret=True)
                self.pair_expiry = 0
                bar = ttk.Frame(body); bar.pack(fill="x")
                self.action(bar, "生成新配对码", self.create_pairing)
                self.action(bar, "复制配对码", self.copy_pairing)
                ttk.Label(body, textvariable=self.status, wraplength=580, style="Muted.TLabel").pack(anchor="w", pady=16)
                self.connection.protocol("WM_DELETE_WINDOW", lambda: (self.code.set(""), self.connection.withdraw()))
            self.activate(self.connection)

        def connect(self):
            try:
                candidate = PersonalClient(self.service.get())
                code = self.code.get()
                if not code.strip():
                    raise PersonalError("请填写个人服务的一次性配对码。")
            except PersonalError as error:
                self.status.set(str(error)); return
            def complete(outcome):
                _result, state, warning = outcome
                self.client = candidate
                self.code.set("")
                self.goal_preview = None
                if self.workbench:
                    self.goal_request.set(""); self.goal_digest.set("")
                    self.goal_query_id.set("")
                    self.goal_history.set("")
                    self.schedule_preview = None
                    self.new_policy()
                    self.sync_goal_button()
                self.update_state(state if state is not None else {})
                self.status.set(warning or "个人服务已连接。")
                if state is not None:
                    self.connection.withdraw()
                    self.show_chat()
            self.run("连接个人服务", lambda: candidate.login(code), complete)

        def create_pairing(self):
            def complete(result):
                self.pair_code.set(result["code"])
                self.pair_expiry = time.monotonic() + result["expiresIn"]
                def expire():
                    if time.monotonic() >= self.pair_expiry:
                        self.pair_code.set("")
                self.root.after(result["expiresIn"] * 1000, expire)
            self.operate("pairing", {}, "生成临时配对码", complete)

        def copy_pairing(self):
            if self.pair_code.get() and time.monotonic() < self.pair_expiry:
                self.root.clipboard_clear(); self.root.clipboard_append(self.pair_code.get())
                self.status.set("临时配对码已复制，请在另一个界面使用。")
            else:
                self.pair_code.set(""); self.status.set("请先生成新的临时配对码。")

        def show_chat(self):
            if not self.require_session():
                return
            if not self.chat_window:
                self.chat_window = self.window("私人对话", "640x720")
                self.chat_window.minsize(480, 540)
                body = ttk.Frame(self.chat_window, padding=22); body.pack(fill="both", expand=True)
                header = ttk.Frame(body); header.pack(fill="x")
                ttk.Label(header, text="与秘书对话", style="Title.TLabel").pack(side="left")
                self.action(header, "新建工作", lambda: self.show_workbench(0))
                self.action(header, "日常对话", self.start_conversation)
                self.chat_work = self.work_select(body, "围绕哪项工作讨论？", self.render_chat)
                self.chat_history = self.text(body, 15, True)
                self.chat_history.tag_configure("speaker", foreground=ACCENT, spacing1=14, spacing3=6)
                self.chat_history.tag_configure("body", spacing3=12)
                self.chat_input = self.text(body, 4)
                self.chat_input.pack_configure(expand=False)
                self.chat_input.bind("<Control-Return>", lambda _event: (self.send_chat(), "break")[-1])
                bar = ttk.Frame(body); bar.pack(fill="x")
                self.action(bar, "发送 · Ctrl+Enter", self.send_chat, True)
                self.action(bar, "工作台", lambda: self.show_workbench(0))
                self.action(bar, "刷新", self.refresh)
                ttk.Label(body, textvariable=self.status, wraplength=580, style="Muted.TLabel").pack(fill="x", pady=(8, 0))
                self.update_choices()
            self.render_chat()
            self.activate(self.chat_window)

        def render_chat(self):
            work_id = self.selected_work(self.chat_work)
            messages = sorted((row for row in self.snapshot.get("messages", []) if row.get("workId") == work_id), key=lambda row: row.get("createdAt", 0))
            self.chat_history.configure(state="normal")
            self.chat_history.delete("1.0", "end")
            if not work_id:
                self.chat_history.insert("end", "选择一项工作，或点击“日常对话”直接开始。工作安排可通过“新建工作”填写。")
            elif not messages:
                self.chat_history.insert("end", "写下问题或想法，发送给你配置的个人模型。")
            for row in messages:
                stamp = datetime.fromtimestamp(row["createdAt"] / 1000).strftime("%m-%d %H:%M") if row.get("createdAt") else ""
                for label, key in [("你", "text"), ("个人模型", "reply")]:
                    if row.get(key):
                        self.chat_history.insert("end", f"{label}  {stamp}\n", "speaker")
                        self.chat_history.insert("end", row[key] + "\n", "body")
            self.chat_history.configure(state="disabled")
            self.chat_history.see("end")

        def start_conversation(self):
            if self.busy:
                self.status.set("请等待当前操作返回。"); return
            def choose(record):
                ids = self.choice_ids.get(self.chat_work, [])
                if record.get("id") in ids:
                    self.chat_work.current(ids.index(record["id"]))
                    self.render_chat()
            existing = next((row for row in self.snapshot.get("work", []) if row.get("title") == "日常对话" and row.get("state") != "CANCELLED"), None)
            if existing:
                choose(existing)
            else:
                self.operate("work", {"title": "日常对话", "body": "", "planId": ""}, "创建私人日常对话", choose)

        def send_chat(self):
            work_id, text = self.selected_work(self.chat_work), self.chat_input.get("1.0", "end-1c")
            if not work_id or not text.strip() or len(text) > 4000:
                self.status.set("请选择工作并输入 1–4000 字的消息。"); return
            def complete(_result):
                if self.selected_work(self.chat_work) == work_id and self.chat_input.get("1.0", "end-1c") == text:
                    self.set_text(self.chat_input, "")
            self.operate("chat", {"workId": work_id, "text": text}, "等待个人模型回复", complete)

        def show_workbench(self, tab=0):
            if not self.require_session():
                return
            if not self.workbench:
                self.workbench = self.window("工作台", "900x780")
                self.workbench.minsize(720, 540)
                outer = ttk.Frame(self.workbench, padding=16); outer.pack(fill="both", expand=True)
                self.notebook = ttk.Notebook(outer); self.notebook.pack(fill="both", expand=True)
                self.build_work(self.page("工作与安排"))
                self.build_policy(self.page("代理策略"))
                self.build_goal(self.page("目标提案"))
                self.build_state(self.page("状态与 Web"))
                ttk.Label(outer, textvariable=self.status, wraplength=840, style="Muted.TLabel").pack(fill="x", pady=(12, 0))
                self.update_state(self.snapshot)
            self.notebook.select(tab)
            self.activate(self.workbench)

        def build_work(self, body):
            group = ttk.LabelFrame(body, text="新建私人工作"); group.pack(fill="x", pady=(0, 20))
            self.work_title = self.field(group, "工作标题")
            ttk.Label(group, text="私人说明", style="Muted.TLabel").pack(anchor="w")
            self.work_body = self.text(group, 4)
            self.work_plan = self.field(group, "关联已确认托管的 Plan（可选）")
            bar = ttk.Frame(group); bar.pack(fill="x")
            self.action(bar, "保存私人工作", self.save_work, True)
            group = ttk.LabelFrame(body, text="确认安排时间"); group.pack(fill="x")
            self.schedule_work = self.work_select(group, "选择工作", lambda: self.update_schedule(True))
            self.schedule_revision = self.field(group, "当前版本（自动带入）", readonly=True)
            ttk.Label(group, text="本次确认的私人工作内容", style="Muted.TLabel").pack(anchor="w")
            self.schedule_summary = self.text(group, 6, True)
            self.schedule_notice = tk.StringVar(value="请先选择工作，再核对所载入内容。")
            ttk.Label(group, textvariable=self.schedule_notice, wraplength=720, style="Muted.TLabel").pack(anchor="w", pady=8)
            self.schedule_due = self.field(group, "本地时间 · 格式 YYYY-MM-DD HH:MM")
            ttk.Label(group, text="只接受未来整分钟；关联 Plan 时会核对当前托管状态和内容。", style="Muted.TLabel").pack(anchor="w")
            bar = ttk.Frame(group); bar.pack(fill="x")
            self.schedule_button = self.action(bar, "确认所载入内容与时间", self.schedule, True)
            self.action(bar, "重新载入所选工作", lambda: self.update_schedule(True))
            self.action(bar, "查看 Web 工作列表", lambda: self.open_web("work"))

        def save_work(self):
            title = self.work_title.get()
            body = self.work_body.get("1.0", "end-1c")
            plan = self.work_plan.get().strip()
            if not title.strip() or len(title) > 200 or len(body) > 16000:
                self.status.set("请填写 1–200 字工作标题；私人说明最多 16000 字。"); return
            def complete(result):
                work_id = result.get("id")
                for control in self.choices:
                    if work_id in self.choice_ids.get(control, []):
                        control.current(self.choice_ids[control].index(work_id))
                self.update_schedule()
                if self.chat_window: self.render_chat()
                if self.work_title.get() == title and self.work_body.get("1.0", "end-1c") == body and self.work_plan.get().strip() == plan:
                    self.work_title.set(""); self.work_plan.set(""); self.set_text(self.work_body, "")
            self.operate("work", {"title": title, "body": body, "planId": plan}, "保存私人工作", complete)

        def update_schedule(self, reload=False):
            work_id = self.selected_work(self.schedule_work)
            work = next((row for row in self.snapshot.get("work", []) if row["id"] == work_id), None)
            if work is None:
                self.schedule_preview = None
                self.schedule_revision.set("")
                self.schedule_conflict = False
                self.set_text(self.schedule_summary, "请先选择一项私人工作。", True)
                self.schedule_notice.set("请先选择工作，再核对所载入内容。")
            elif reload or self.schedule_preview is None or self.schedule_preview["id"] != work_id:
                self.schedule_preview = json.loads(json.dumps(work))
                self.schedule_revision.set(str(work["revision"]))
                self.schedule_conflict = False
                self.set_text(self.schedule_summary, "标题：" + work.get("title", "") + "\n状态：" + work.get("state", "未记录")
                    + "\n私人版本：" + str(work["revision"]) + "\nPlan 引用：" + (work.get("planId") or "未关联")
                    + "\n\n" + work.get("body", ""), True)
                self.schedule_notice.set("请核对以上已载入内容。关联 Plan 的托管内容仍按平台原有规则校验。")
            else:
                self.schedule_conflict = work != self.schedule_preview
                if self.schedule_conflict:
                    self.schedule_notice.set("所选工作已发生变化，当前显示仍是原确认版本。请显式重新载入并核对后再安排。")
            self.sync_schedule_button()

        def sync_schedule_button(self):
            if hasattr(self, "schedule_button"):
                self.schedule_button.configure(state="normal" if self.schedule_preview and not self.schedule_conflict and not self.busy else "disabled")

        def schedule(self):
            if self.busy or not self.schedule_preview or self.schedule_conflict:
                self.status.set("请先载入并核对当前工作；内容变化后须显式重新载入。"); return
            try:
                due = datetime.strptime(self.schedule_due.get().strip(), "%Y-%m-%d %H:%M")
                revision = self.schedule_preview["revision"]
                if due.timestamp() <= time.time(): raise ValueError()
            except ValueError:
                self.status.set("请选择工作并填写未来整分钟，例如 2026-10-25 09:00。"); return
            self.operate("schedule", {"workId": self.schedule_preview["id"], "revision": revision,
                "dueMs": int(due.timestamp() * 1000), "timeZone": due.astimezone().tzname() or "local"}, "确认工作安排")

        def build_policy(self, body):
            ttk.Label(body, text="策略关联已明确授予的精确能力；保存文字不会增加平台权限。", style="Muted.TLabel").pack(anchor="w", pady=(0, 10))
            self.policy_choices = ttk.Combobox(body, state="readonly")
            self.policy_choices.pack(fill="x")
            self.policy_choices.bind("<<ComboboxSelected>>", lambda _event: self.load_policy())
            self.policy_id = self.field(body, "策略标识 · 新建时填写简短名称")
            self.policy_revision = self.field(body, "当前版本 · 新策略为 0", "0", readonly=True)
            self.policy_grant = self.field(body, "已确认的平台能力 ID")
            ttk.Label(body, text="私人策略原文", style="Muted.TLabel").pack(anchor="w")
            self.policy_text = self.text(body, 5)
            self.policy_expiry = self.field(body, "到期本地时间 · 可选，格式 YYYY-MM-DD HH:MM")
            ttk.Label(body, text="禁止范围 · 可选 JSON，不包含私人原文", style="Muted.TLabel").pack(anchor="w")
            self.policy_deny = self.text(body, 3)
            self.policy_confirmed = tk.BooleanVar()
            ttk.Checkbutton(body, text="我确认此私人策略及其关联的精确平台能力", variable=self.policy_confirmed).pack(anchor="w", pady=8)
            bar = ttk.Frame(body); bar.pack(fill="x")
            self.action(bar, "保存已确认策略", self.save_policy, True)
            self.action(bar, "新建策略", self.new_policy)

        def load_policy(self):
            policy = next((row for row in self.snapshot.get("policies", []) if row["id"] == self.policy_choices.get()), None)
            if not policy: return
            self.policy_id.set(policy["id"]); self.policy_revision.set(str(policy["revision"]))
            self.fields[str(self.policy_id)].configure(state="readonly")
            self.policy_grant.set(policy["grantId"])
            self.set_text(self.policy_text, policy["originalText"])
            self.set_text(self.policy_deny, "")
            self.policy_expiry.set(datetime.fromtimestamp(policy["expiresAt"] / 1000).strftime("%Y-%m-%d %H:%M") if policy.get("expiresAt") else "")
            self.policy_confirmed.set(False)

        def new_policy(self):
            for variable in [self.policy_id, self.policy_grant, self.policy_expiry]: variable.set("")
            self.policy_choices.set(""); self.policy_revision.set("0"); self.policy_confirmed.set(False)
            self.fields[str(self.policy_id)].configure(state="normal")
            self.set_text(self.policy_text, ""); self.set_text(self.policy_deny, "")

        def save_policy(self):
            try:
                data = {"id": self.policy_id.get().strip(), "revision": int(self.policy_revision.get()),
                    "grantId": self.policy_grant.get().strip(), "originalText": self.policy_text.get("1.0", "end-1c"),
                    "confirmed": self.policy_confirmed.get()}
                if not data["id"] or not data["grantId"] or not data["originalText"].strip() or len(data["originalText"]) > 16000 or not data["confirmed"]:
                    raise ValueError()
                deny = self.policy_deny.get("1.0", "end-1c").strip()
                if deny: data["deny"] = json.loads(deny)
                if self.policy_expiry.get().strip():
                    data["expiresAt"] = int(datetime.strptime(self.policy_expiry.get().strip(), "%Y-%m-%d %H:%M").timestamp() * 1000)
            except ValueError:
                self.status.set("请填写策略、精确能力并勾选确认；检查可选时间和禁止范围 JSON。"); return
            def complete(_result):
                saved = next((row for row in self.snapshot.get("policies", []) if row["id"] == data["id"]), None)
                if saved and self.policy_id.get().strip() == data["id"] and self.policy_revision.get() == str(data["revision"]):
                    self.policy_revision.set(str(saved["revision"]))
                    self.fields[str(self.policy_id)].configure(state="readonly")
                self.policy_confirmed.set(False)
            self.operate("policy", data, "保存已确认策略", complete)

        def build_goal(self, body):
            ttk.Label(body, text="先预览精确内容，再确认提交。平台采纳前不会自动创建任务。", style="Muted.TLabel").pack(anchor="w", pady=(0, 12))
            self.goal_fields = {}
            for key, label in [("selectedGoal", "选定的软件目标"), ("necessaryContext", "必要上下文（可选）"), ("acceptance", "验收条件（可选）")]:
                ttk.Label(body, text=label, style="Muted.TLabel").pack(anchor="w")
                widget = self.text(body, 3)
                self.goal_fields[key] = widget
                widget.bind("<<Modified>>", lambda _event, target=widget: self.goal_changed(target))
            bar = ttk.Frame(body); bar.pack(fill="x")
            self.action(bar, "生成本地预览", self.preview_goal)
            self.goal_submit_button = self.action(bar, "确认原文并提交", self.submit_goal, True)
            self.goal_submit_button.configure(state="disabled")
            self.goal_projection = self.text(body, 7, True)
            self.goal_request = self.field(body, "当前预览的原请求 ID", readonly=True)
            self.goal_digest = self.field(body, "确认摘要", readonly=True)
            ttk.Separator(body).pack(fill="x", pady=18)
            ttk.Label(body, text="原请求回执与历史", font=("Microsoft YaHei UI", 12, "bold")).pack(anchor="w")
            ttk.Label(body, text="重开或重新配对后，从私人决策中选回原请求。选择历史仅供查询，不会启用重新提交。", wraplength=720, style="Muted.TLabel").pack(anchor="w", pady=8)
            self.goal_history = ttk.Combobox(body, state="readonly")
            self.goal_history.pack(fill="x", pady=8)
            self.goal_history.bind("<<ComboboxSelected>>", lambda _event: self.select_goal_history())
            self.goal_history_records = []
            self.goal_query_id = self.field(body, "所查询的原请求 ID", readonly=True)
            bar = ttk.Frame(body); bar.pack(fill="x")
            self.action(bar, "查询原请求回执", self.query_goal)
            self.goal_receipt = self.text(body, 5, True)

        def goal_changed(self, widget):
            if widget.edit_modified():
                widget.edit_modified(False)
                self.goal_version += 1
                self.goal_preview = None
                if hasattr(self, "goal_projection"):
                    self.set_text(self.goal_projection, "内容已变化，请重新生成预览。", True)
                    self.goal_digest.set("")
                self.sync_goal_button()

        def sync_goal_button(self):
            if hasattr(self, "goal_submit_button"):
                self.goal_submit_button.configure(state="normal" if self.goal_preview and not self.goal_submitted and not self.busy else "disabled")

        def preview_goal(self):
            data = {key: widget.get("1.0", "end-1c") for key, widget in self.goal_fields.items()}
            if not data["selectedGoal"].strip() or any(len(value) > 8000 for value in data.values()):
                self.status.set("请填写选定目标，每个字段最多 8000 字。"); return
            version = self.goal_version
            def complete(result):
                if version != self.goal_version:
                    self.status.set("内容已变化，请重新预览。"); return
                self.goal_preview = {key: result[key] for key in ["requestId", "projectionJson", "digest"]}
                self.goal_submitted = False
                self.goal_request.set(result["requestId"]); self.goal_digest.set(result["digest"])
                self.set_text(self.goal_projection, result["projectionJson"], True)
                self.sync_goal_button()
            self.operate("goal-preview", data, "生成本地预览", complete)

        def update_goal_history(self):
            previous = self.goal_query_id.get()
            self.goal_history_records = [row for row in self.snapshot.get("decisions", [])
                if row.get("kind") == "SELECTED_GOAL" and isinstance(row.get("requestId"), str)]
            labels = []
            for index, row in enumerate(self.goal_history_records):
                try:
                    projection = json.loads(row.get("projectionJson", "{}"))
                    title = str(projection.get("selectedGoal", "目标提案")) if isinstance(projection, dict) else "目标提案"
                except ValueError:
                    title = "目标提案"
                labels.append(f"{index + 1}. {row.get('state', '未记录')} · {title[:42]} · {row['requestId'][:8]}")
            self.goal_history.configure(values=labels)
            match = next((index for index, row in enumerate(self.goal_history_records) if row["requestId"] == previous), None)
            if match is not None: self.goal_history.current(match)
            else: self.goal_history.set("")

        def select_goal_history(self):
            index = self.goal_history.current()
            if 0 <= index < len(self.goal_history_records):
                record = self.goal_history_records[index]
                self.goal_query_id.set(record["requestId"])
                self.set_text(self.goal_receipt, json.dumps(record, ensure_ascii=False, indent=2), True)

        def submit_goal(self):
            if self.busy or not self.goal_preview or self.goal_submitted: return
            self.goal_submitted = True
            self.goal_query_id.set(self.goal_preview["requestId"])
            self.sync_goal_button()
            self.operate("goal-submit", dict(self.goal_preview), "提交已确认目标", self.show_goal_receipt)

        def query_goal(self):
            if self.goal_query_id.get():
                self.operate("goal-receipt", {"requestId": self.goal_query_id.get()}, "查询原请求回执", self.show_goal_receipt)
            else:
                self.status.set("请选择私人决策中的原请求。PREVIEWED 表示尚未确认发送，只供查看。")

        def show_goal_receipt(self, result):
            self.set_text(self.goal_receipt, json.dumps(result, ensure_ascii=False, indent=2), True)

        def build_state(self, body):
            ttk.Label(body, text="界面共用私人记录；以下为最近读取的快照。关闭桌面界面不会停止后台服务。", wraplength=760, style="Muted.TLabel").pack(anchor="w", pady=(0, 12))
            bar = ttk.Frame(body); bar.pack(fill="x")
            self.action(bar, "刷新状态", self.refresh)
            self.action(bar, "启用本地动作申请", lambda: self.operate("enable", {"enabled": True}, "启用本地动作申请"))
            self.action(bar, "暂停动作申请", lambda: self.operate("enable", {"enabled": False}, "暂停动作申请"))
            self.state_text = self.text(body, 18, True)
            bar = ttk.Frame(body); bar.pack(fill="x")
            self.action(bar, "Web 中查看回执与备份", lambda: self.open_web("state"))
            self.action(bar, "Web 中处理平台问题", lambda: self.open_web("platform"))
            self.action(bar, "连接与设置", self.show_connection)

        def open_web(self, section="", use_draft=False):
            try:
                address = personal_origin(self.service.get() if self.connection and (use_draft or not self.client) else (self.client.origin if self.client else origin))
                webbrowser.open(address + "/" + ("#" + section if section else ""), new=2)
            except (PersonalError, OSError) as error:
                self.status.set(str(error))

        def drag_start(self, event):
            self.drag = (event.x_root, event.y_root, self.root.winfo_x(), self.root.winfo_y())
            self.dragged = False

        def drag_move(self, event):
            dx, dy = event.x_root - self.drag[0], event.y_root - self.drag[1]
            if abs(dx) + abs(dy) > 5:
                self.dragged = True
            if self.dragged:
                move(self.root, self.drag[2] + dx, self.drag[3] + dy)

        def drag_end(self, event):
            if not self.dragged:
                self.show_chat(); return
            left, top, right, bottom = work_area(event.x_root, event.y_root)
            x, y = self.root.winfo_x(), self.root.winfo_y()
            x = min(max(x, left), max(left, right - self.width))
            y = min(max(y, top), max(top, bottom - self.height))
            if abs(x - left) < 32: x = left
            if abs(x + self.width - right) < 32: x = right - self.width
            if abs(y - top) < 32: y = top
            if abs(y + self.height - bottom) < 32: y = bottom - self.height
            move(self.root, x, y)

        def context_menu(self, event=None):
            try:
                self.menu.tk_popup(event.x_root if event else self.root.winfo_pointerx(), event.y_root if event else self.root.winfo_pointery())
            finally:
                self.menu.grab_release()

        def show_pet(self):
            self.root.deiconify()
            self.root.lift()

        def hide_pet(self):
            if self.tray and self.tray.active:
                self.root.withdraw()

        def close(self):
            if self.busy and not messagebox.askyesno("退出桌面界面", "当前操作仍在等待结果。退出只关闭界面，已发送操作可能继续执行。下次请核对原请求回执。是否退出？", parent=self.root):
                return
            self.closing = True
            if self.tray: self.tray.close()
            self.client = None
            self.root.destroy()

    app = Desktop()
    app.root.mainloop()


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        if sys.platform == "win32":
            import ctypes
            ctypes.windll.user32.MessageBoxW(None,
                "桌面界面未能启动（" + type(error).__name__ + "）。请确认 Python 含 Tk，且 desktop-assets/secretary.png 存在。服务可继续通过 Web 使用。",
                "我的秘书 · 启动失败", 0x10)
        raise SystemExit(1) from None
