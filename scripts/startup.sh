#!/usr/bin/env bash
set -euo pipefail

# Resolve the project root independently of the caller's working directory.
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd -- "$repo_root"

debug_address="${DEBUG_ADDRESS:-127.0.0.1:5005}"
debug_suspend="${DEBUG_SUSPEND:-n}"
if [[ "$debug_suspend" != "y" && "$debug_suspend" != "n" ]]; then
    printf 'DEBUG_SUSPEND must be y or n\n' >&2
    exit 1
fi

jvm_arguments="${STARTUP_JVM_ARGS:-}"
jvm_arguments="${jvm_arguments:+$jvm_arguments }-agentlib:jdwp=transport=dt_socket,server=y,suspend=$debug_suspend,address=$debug_address"

printf 'Starting Spring Boot with JDWP at %s (suspend=%s)\n' "$debug_address" "$debug_suspend"
exec mvn clean spring-boot:run "-Dspring-boot.run.jvmArguments=$jvm_arguments" "$@"
