#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PACKAGE_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
VENV_DIR="$PACKAGE_DIR/.venv"

command -v uv >/dev/null 2>&1 || {
  echo "uv is required. Install it from https://docs.astral.sh/uv/" >&2
  exit 1
}

uv venv "$VENV_DIR"
uv pip sync --python "$VENV_DIR/bin/python" "$PACKAGE_DIR/requirements.txt"
