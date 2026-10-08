#!/usr/bin/env sh
set -eu
if [ "$#" -lt 1 ]; then
  echo "usage: $0 <profile-name>" >&2
  exit 2
fi
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
export AIW_PROCESS_PROFILE="$ROOT/profiles/$1.toml"
exec "$ROOT/process-worker"
