#!/usr/bin/env sh
set -eu
if [ "$#" -lt 1 ]; then
  echo "usage: $0 <goal-discussion|requirements|architect|planner|coder|integrator|reviewer|stage-reviewer|verifier|integration-verifier|repairer>" >&2
  exit 2
fi
ROLE="$1"
export AIW_PROCESS_ROLE="$ROLE"
: "${AIW_AGENT_RUNTIME:=codex-app-server}"
export AIW_AGENT_RUNTIME
if [ "$AIW_AGENT_RUNTIME" = "openai-agents" ]; then
  : "${AIW_WORKER_ID:=process-api-${ROLE}-1}"
else
  : "${AIW_WORKER_ID:=process-${ROLE}-1}"
fi
export AIW_WORKER_ID
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PACKAGE_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
PYTHON="$PACKAGE_DIR/.venv/bin/python"
if [ ! -x "$PYTHON" ]; then
  PYTHON=python3
fi
exec "$PYTHON" "$PACKAGE_DIR/process_worker.py"
