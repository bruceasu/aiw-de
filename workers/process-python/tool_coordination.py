"""Bounded shared reads and exclusive workspace effects for one Broker."""
from contextlib import contextmanager
import os
import threading

READ_ONLY = frozenset({"read", "grep", "ls", "read_skill_file", "read_skill_text"})


def parallel_enabled():
    value = os.environ.get("AIW_HOST_READ_PARALLEL", "off")
    if value not in {"off", "v1"}:
        raise RuntimeError("HOST_READ_PARALLEL_SETTING_INVALID")
    return value == "v1"


class ToolCoordinator:
    def __init__(self, attempt):
        self.attempt = attempt
        self.condition = threading.Condition()
        self.readers = 0
        self.writer = False
        self.waiting_writers = 0

    @contextmanager
    def access(self, shared):
        with self.condition:
            if not shared:
                self.waiting_writers += 1
            try:
                while True:
                    if shared:
                        blocked = self.writer or self.readers >= 4 or self.waiting_writers > 0
                    else:
                        blocked = self.writer or self.readers > 0
                    if not blocked:
                        break
                    self.attempt.check()
                    self.condition.wait(0.05)
                self.attempt.check()
                if shared:
                    self.readers += 1
                else:
                    self.writer = True
            finally:
                if not shared:
                    self.waiting_writers -= 1
        try:
            yield
        finally:
            with self.condition:
                if shared:
                    self.readers -= 1
                else:
                    self.writer = False
                self.condition.notify_all()


def read_batch(calls, invoke):
    """Only independent reads overlap. A failure stops queued calls; admitted calls settle."""
    from concurrent.futures import ThreadPoolExecutor
    if not calls or len(calls) > 16 or any(call.name not in READ_ONLY for call in calls):
        raise ValueError("READ_ONLY_BATCH_REQUIRED")
    stopped = threading.Event()

    def run(index, call):
        if stopped.is_set():
            return {"error": "BATCH_NOT_DISPATCHED"}, None
        try:
            result = invoke(index, call)
            if isinstance(result, dict) and result.get("error"):
                stopped.set()
            return result, None
        except Exception as error:
            stopped.set()
            return None, error

    with ThreadPoolExecutor(max_workers=min(4, len(calls)), thread_name_prefix="host-read") as executor:
        futures = [executor.submit(run, index, call) for index, call in enumerate(calls)]
        # Preserve every admitted result and the original call order, including partial failure.
        return [future.result() for future in futures]
