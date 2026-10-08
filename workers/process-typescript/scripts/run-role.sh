#!/usr/bin/env sh
set -eu
if [ "$#" -ne 2 ]; then
  echo "usage: $0 <requirements|architect|planner|coder|integrator|reviewer|verifier|integration-verifier|repairer> <codex-sdk|openai-agents>" >&2
  exit 2
fi
ROLE="$1"
RUNTIME="$2"
case "$RUNTIME" in codex-sdk|openai-agents) ;; *) echo "unsupported runtime: $RUNTIME" >&2; exit 2 ;; esac
if [ -n "${AIW_AGENT_RUNTIME:-}" ] && [ "$AIW_AGENT_RUNTIME" != "$RUNTIME" ]; then
  echo "conflicting runtime: AIW_AGENT_RUNTIME=$AIW_AGENT_RUNTIME, argument=$RUNTIME" >&2
  exit 2
fi
export AIW_PROCESS_ROLE="$ROLE"
export AIW_AGENT_RUNTIME="$RUNTIME"
if [ -z "${AIW_WORKER_ID:-}" ]; then
  if [ "$RUNTIME" = "codex-sdk" ]; then AIW_WORKER_ID="process-${ROLE}-1"
  else AIW_WORKER_ID="process-api-${ROLE}-1"; fi
fi
export AIW_WORKER_ID
exec node "$(dirname "$0")/../dist/index.js"
