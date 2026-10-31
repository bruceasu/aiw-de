"""Small Canvas animations driven by observed personal-service state only."""

from dataclasses import dataclass
import math
import time


@dataclass(frozen=True)
class PetState:
    code: str
    label: str
    face: str


def pet_state(snapshot, connected, pending=False, uncertain=False, celebration=""):
    if uncertain:
        return PetState("uncertain", "结果待核对，请刷新", "concerned")
    if not connected:
        return PetState("offline", "正在连接" if pending else "尚未连接秘书服务", "concerned")
    assistant = snapshot.get("assistant", {})
    if snapshot.get("modelConfigured") is False or assistant.get("configured") is False:
        return PetState("setup", "请先连接模型", "question")
    work = [row for row in snapshot.get("work", []) if row.get("state") not in {"TRASHED", "CANCELLED"}]
    decisions = snapshot.get("decisions", [])
    messages = snapshot.get("messages", [])
    if any(row.get("state") in {"WAITING_CONFIRMATION", "NEEDS_CONFIRMATION"}
           or row.get("state") == "NEEDS_HUMAN" and row.get("report", {}).get("code") == "INTERRUPTED" for row in work) or any(
            row.get("kind") == "DAILY_CONFIRMATION" and row.get("state") == "WAITING" for row in decisions):
        return PetState("confirmation", "有工作待你确认", "question")
    if any(row.get("state") in {"NEEDS_HUMAN", "FAILED", "UNKNOWN"} for row in work) or any(
            row.get("kind") == "DAILY_PLAN" and row.get("state") == "UNKNOWN" for row in decisions):
        return PetState("blocked", "工作受阻或结果待核对", "concerned")
    if assistant.get("paused") is True:
        return PetState("paused", "秘书已暂停", "rest")
    if any(row.get("state") in {"DAILY_RUNNING", "ACTIVE"} for row in work):
        return PetState("working", "正在处理工作", "thinking")
    if any(row.get("state") == "PENDING" for row in messages):
        return PetState("reply", "正在等待回复", "thinking")
    if pending:
        return PetState("request", "正在请求服务", "thinking")
    if celebration:
        return PetState("completed", celebration, "smile")
    if any(row.get("state") in {"SCHEDULED", "START_PENDING"} for row in work):
        return PetState("queued", "工作已安排，等待执行", "neutral")
    return PetState("idle", "秘书待命", "neutral")


class PetAnimator:
    """One Tk after callback; no threads, image manipulation or service actions."""

    def __init__(self, root, canvas, image_item, width):
        self.root, self.canvas = root, canvas
        self.image_item, self.width = image_item, width
        self.origin = tuple(canvas.coords(image_item))
        self.snapshot = {}
        self.connected = self.pending = self.uncertain = False
        self.enabled = self.visible = True
        self.closed = False
        self.timer = None
        self.frame = 0
        self.celebration, self.celebrate_until = "", 0
        self._draw()
        self._schedule()

    def update_snapshot(self, snapshot):
        notice = ""
        if self.connected and not self.uncertain:
            previous = {row["id"]: row.get("state") for row in self.snapshot.get("work", [])}
            if any(previous.get(row["id"]) not in {None, "DONE"} and row.get("state") == "DONE"
                   for row in snapshot.get("work", [])):
                notice = "工作已完成，请查看结果"
            pending = {row["id"] for row in self.snapshot.get("messages", []) if row.get("state") == "PENDING"}
            if not notice and any(row["id"] in pending and row.get("state") == "DONE" for row in snapshot.get("messages", [])):
                notice = "有新回复，请查看对话"
        if notice:
            self.celebration, self.celebrate_until = notice, time.monotonic() + 6
        self.snapshot, self.connected, self.uncertain = snapshot, True, False
        self._draw()

    def request_started(self, background=False):
        self.pending = not background
        self._draw()

    def request_finished(self):
        self.pending = False
        self._draw()

    def result_uncertain(self):
        self.uncertain = True
        self.celebration = ""
        self._draw()

    def set_enabled(self, enabled):
        self.enabled = bool(enabled)
        self._cancel()
        self._draw()
        self._schedule()

    def set_visible(self, visible):
        self.visible = bool(visible)
        self._cancel()
        if self.visible:
            self._draw()
            self._schedule()

    def close(self):
        self.closed = True
        self._cancel()

    def _cancel(self):
        if self.timer is not None:
            self.root.after_cancel(self.timer)
            self.timer = None

    def _schedule(self):
        if not self.closed and self.visible and self.enabled and self.timer is None:
            self.timer = self.root.after(120, self._tick)

    def _tick(self):
        self.timer = None
        if self.closed or not self.visible or not self.enabled:
            return
        self.frame += 1
        self._draw()
        self._schedule()

    def _draw(self):
        if self.closed:
            return
        notice = self.celebration if time.monotonic() < self.celebrate_until else ""
        state = pet_state(self.snapshot, self.connected, self.pending, self.uncertain, notice)
        animated = self.enabled and self.visible
        amplitude = 2 if state.code == "completed" else 1
        offset = amplitude * (1 + math.sin(self.frame / 5)) if animated and state.code != "paused" else 0
        self.canvas.coords(self.image_item, self.origin[0], self.origin[1] + offset)
        self.canvas.delete("pet-expression")
        color = "#f1bf73" if state.face in {"question", "concerned"} else "#64e4dc"
        x, y = self.width - 35, 33
        options = {"tags": "pet-expression", "fill": color}
        self.canvas.create_oval(x - 19, y - 19, x + 19, y + 19, fill="#111c2b", outline=color, width=2, tags="pet-expression")
        blink = animated and self.frame % 38 in (36, 37) or state.face == "rest"
        for eye in (x - 7, x + 7):
            if blink:
                self.canvas.create_line(eye - 3, y - 3, eye + 3, y - 3, width=2, **options)
            else:
                shift = 1 if state.face == "thinking" else 0
                self.canvas.create_oval(eye - 2 + shift, y - 6, eye + 2 + shift, y - 1, outline=color, **options)
        if state.face in {"smile", "concerned"}:
            self.canvas.create_arc(x - 8, y + 2, x + 8, y + 12,
                                   start=0 if state.face == "concerned" else 180, extent=180,
                                   style="arc", outline=color, width=2, tags="pet-expression")
        elif state.face == "question":
            self.canvas.create_oval(x - 2, y + 5, x + 2, y + 10, outline=color, width=2, tags="pet-expression")
        else:
            self.canvas.create_line(x - 5, y + 7, x + 5, y + 7, width=2, **options)
        marker = "?" if state.face == "question" else "!" if state.face == "concerned" else ""
        if marker:
            self.canvas.create_text(x - 25, y - 16, text=marker, font=("Microsoft YaHei UI", 13, "bold"), **options)
