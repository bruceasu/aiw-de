"""Factory-owned process entry. Status contains fixed codes, never exception text."""
import json
import os
from pathlib import Path
import re
import sys


def status(state, code=None):
    target = Path(os.environ["AIW_FACTORY_STATUS_FILE"])
    if target.is_symlink():
        raise RuntimeError("FACTORY_STATUS_PATH_INVALID")
    temporary = target.with_suffix(".tmp")
    if temporary.is_symlink():
        raise RuntimeError("FACTORY_STATUS_PATH_INVALID")
    temporary.write_text(json.dumps({"workerId": os.environ["AIW_WORKER_ID"], "state": state, "code": code}), encoding="utf-8")
    os.replace(temporary, target)


def run():
    status("STARTING")
    try:
        if os.environ.get("AIW_AGENT_RUNTIME", "codex-app-server") == "codex-app-server" and not os.environ.get("AIW_CODEX_GUARD_HOME"):
            os.environ.setdefault("AIW_CODEX_GUARD_MODE", "host-actions")
        import process_worker
        original = process_worker.ControlPlaneClient

        class ManagedClient(original):
            def register(self, config):
                result = super().register(config)
                status("READY")
                return result

        process_worker.ControlPlaneClient = ManagedClient
        result = process_worker.main()
        status("STOPPED")
        return result
    except Exception as error:
        if isinstance(error, ModuleNotFoundError):
            code = "PYTHON_DEPENDENCY_MISSING"
        elif isinstance(error, FileNotFoundError):
            code = "EXECUTABLE_NOT_FOUND"
        else:
            value = str(error).split(":", 1)[0]
            code = value if re.fullmatch(r"[A-Z][A-Z0-9_]{2,100}", value) else "WORKER_INITIALIZATION_FAILED"
        status("FAILED", code)
        return 1


if __name__ == "__main__":
    sys.exit(run())
