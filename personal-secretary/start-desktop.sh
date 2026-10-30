#!/usr/bin/env sh
# Desktop only. No dependency downloads, privilege changes or model calls here.
set -eu
task_directory=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
dotenv_python=python3
[ ! -x "$task_directory/.venv/bin/python" ] || dotenv_python="$task_directory/.venv/bin/python"
dotenv_exports=$("$dotenv_python" "$task_directory/env_config.py" --shell)
eval "$dotenv_exports"

desktop_python=${AIW_DESKTOP_PYTHON:-}
if [ -z "$desktop_python" ]; then
    desktop_python=python3
    [ ! -x "$task_directory/.venv/bin/python" ] || desktop_python="$task_directory/.venv/bin/python"
fi
if ! "$desktop_python" -c 'from PySide6.QtCore import Qt; from PySide6.QtWebEngineWidgets import QWebEngineView' >/dev/null 2>&1; then
    echo "Qt desktop dependencies are missing or incomplete for $desktop_python." >&2
    echo "Repair them with: ./setup-uv.sh --desktop" >&2
    exit 1
fi
exec "$desktop_python" "$task_directory/desktop_webview.py" "$@"
