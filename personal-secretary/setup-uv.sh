#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
VENV_DIR="$SCRIPT_DIR/.venv"
DESKTOP=false

if [ "$#" -gt 1 ] || { [ "$#" -eq 1 ] && [ "$1" != "--desktop" ]; }; then
  echo "usage: $0 [--desktop]" >&2
  exit 2
fi
if [ "$#" -eq 1 ]; then DESKTOP=true; fi

command -v uv >/dev/null 2>&1 || {
  echo "uv is required. Install it from https://docs.astral.sh/uv/" >&2
  exit 1
}

uv venv "$VENV_DIR"
if [ "$DESKTOP" = true ]; then
  uv pip sync --python "$VENV_DIR/bin/python" "$SCRIPT_DIR/requirements.txt" "$SCRIPT_DIR/requirements-desktop.txt"
else
  uv pip sync --python "$VENV_DIR/bin/python" "$SCRIPT_DIR/requirements.txt"
fi
