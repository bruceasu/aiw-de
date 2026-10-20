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
exec python3 "$(dirname "$0")/../process_worker.py"
