#!/usr/bin/env bash
set -euo pipefail

if [[ "$(uname -s)" != Linux* ]]; then
  echo "This packaging script targets Linux only." >&2
  exit 1
fi

script_directory=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
secretary_root=$(cd -- "$script_directory/.." && pwd)
python_command=
output_directory="$secretary_root/dist"

usage() {
  cat <<'USAGE'
Usage: bash scripts/package-linux.sh [--python PATH] [--output-directory PATH]
Builds a one-directory Linux package using already-installed PyInstaller and Qt.
This script does not install or download dependencies.
USAGE
}

fail() { printf '%s\n' "$1" >&2; exit 2; }

while (($#)); do
  case "$1" in
    -h|--help) usage; exit 0 ;;
    --python|--output-directory)
      (($# >= 2)) && [[ -n "$2" ]] || fail "Missing value for $1"
      if [[ "$1" == --python ]]; then python_command=$2; else output_directory=$2; fi
      shift 2 ;;
    *) fail "Unknown option: $1. Use --help for supported options." ;;
  esac
done

if [[ -z "$python_command" ]]; then
  python_command=python3
  [[ ! -x "$secretary_root/.venv/bin/python" ]] || python_command="$secretary_root/.venv/bin/python"
fi
entry_point="$secretary_root/package_entry.py"
work_directory="$secretary_root/build/pyinstaller"

[[ -f "$entry_point" ]] || fail "The package entry point is missing: $entry_point"
"$python_command" -c 'import sys; raise SystemExit(0 if sys.platform.startswith("linux") else 1)' ||
  fail "The selected Python interpreter is not running on Linux."
"$python_command" -m PyInstaller --version >/dev/null 2>&1 ||
  fail "PyInstaller is not installed in the selected Python environment. Install it separately; this script does not download dependencies."
"$python_command" -c 'from PySide6.QtWebEngineWidgets import QWebEngineView' >/dev/null 2>&1 ||
  fail "PySide6 Qt WebEngine is not installed in the selected Python environment. Install the desktop requirements separately; this script does not download dependencies."

"$python_command" -m PyInstaller \
  --onedir --windowed --name AIWPersonalSecretary \
  --distpath "$output_directory" --workpath "$work_directory" --specpath "$work_directory" \
  --paths "$secretary_root" --paths "$secretary_root/pet" \
  --collect-all PySide6 --collect-all shiboken6 \
  --add-data "$secretary_root/ui:ui" \
  --add-data "$secretary_root/pet/desktop-assets:pet/desktop-assets" \
  "$entry_point"

printf 'Package created at %s. No dependencies were installed by this script.\n' \
  "$output_directory/AIWPersonalSecretary"
