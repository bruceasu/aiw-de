#!/usr/bin/env sh
# Desktop only. No dependency downloads, privilege changes or model calls here.
set -eu
task_directory=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec "${AIW_DESKTOP_PYTHON:-python3}" "$task_directory/desktop_webview.py" "$@"
