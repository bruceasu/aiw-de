#!/usr/bin/env bash
# Keep hidden credentials out of shell tracing, including bash -x launches.
set +x
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
dotenv_python=python3
[[ ! -x "$script_dir/.venv/bin/python" ]] || dotenv_python="$script_dir/.venv/bin/python"
dotenv_exports=$("$dotenv_python" "$script_dir/env_config.py" --shell)
eval "$dotenv_exports"

owner_id=${AIW_PERSONAL_OWNER_ID:-}
worker_id=${AIW_PERSONAL_WORKER_ID:-my-secretary}
platform=${AIW_PERSONAL_PLATFORM:-}
data_root=${HOME}/.aiw-personal
host_address=127.0.0.1
port=8766
public_origin=
allow_loopback_http=false
prompt_credential=false
no_browser=false
python_command=

usage() {
    cat <<'USAGE'
Usage: bash start.sh [options]
  --owner-id ID          Enrolled owner (AIW_PERSONAL_OWNER_ID or prompt)
  --worker-id ID         Enrolled Worker (AIW_PERSONAL_WORKER_ID or my-secretary)
  --platform ORIGIN      Platform (AIW_PERSONAL_PLATFORM or prompt)
  --data-root PATH       Private data directory (default: $HOME/.aiw-personal)
  --host ADDRESS         UI listen address (default: 127.0.0.1)
  --port PORT            UI port (default: 8766)
  --public-origin URL    HTTPS reverse-proxy origin for remote UI access
  --allow-loopback-http  Explicitly allow HTTP to a loopback platform
  --prompt-credential    Ask for a professional platform credential (optional)
  --no-browser          Do not automatically open the personal UI
  --python PATH          Python executable (default: python3)
  -h, --help             Show this help
Credential: saved settings or AIW_PERSONAL_CREDENTIAL; optional for daily use.
USAGE
}

fail() { printf '%s\n' "$1" >&2; exit 2; }

while (($#)); do
    case "$1" in
        -h|--help) usage; exit 0 ;;
        --allow-loopback-http) allow_loopback_http=true; shift ;;
        --prompt-credential) prompt_credential=true; shift ;;
        --no-browser) no_browser=true; shift ;;
        --owner-id|--worker-id|--platform|--data-root|--host|--port|--public-origin|--python)
            (($# >= 2)) && [[ -n $2 ]] || fail "Missing value for $1"
            case "$1" in
                --owner-id) owner_id=$2 ;;
                --worker-id) worker_id=$2 ;;
                --platform) platform=$2 ;;
                --data-root) data_root=$2 ;;
                --host) host_address=$2 ;;
                --port) port=$2 ;;
                --public-origin) public_origin=$2 ;;
                --python) python_command=$2 ;;
            esac
            shift 2 ;;
        *) fail "Unknown option. Use --help for supported options." ;;
    esac
done

if [[ -z $owner_id && -z $platform && $prompt_credential == false ]]; then
    if [[ -z $python_command ]]; then
        python_command=python3
        [[ ! -x "$script_dir/.venv/bin/python" ]] || python_command="$script_dir/.venv/bin/python"
    fi
    launch_arguments=("$script_dir/main.py" --data-root "$data_root" --host "$host_address" --port "$port")
    if [[ -n $public_origin ]]; then launch_arguments+=(--public-origin "$public_origin"); fi
    if [[ $no_browser == true ]]; then launch_arguments+=(--no-browser); fi
    exec "$python_command" "${launch_arguments[@]}"
fi
if [[ -z $owner_id ]]; then
    read -r -p 'Owner ID from the platform authority page: ' owner_id
fi
if [[ -z $platform ]]; then
    read -r -p 'Platform origin (Enter for http://127.0.0.1:8080): ' platform
    platform=${platform:-http://127.0.0.1:8080}
fi
[[ $owner_id =~ [^[:space:]] && $worker_id =~ [^[:space:]] ]] ||
    fail 'Owner ID and Worker ID must match the identity enrolled on the platform.'

if [[ $allow_loopback_http == false && ( $prompt_credential == true || ${AIW_PERSONAL_CREDENTIAL:-} =~ [^[:space:]] ) ]]; then
    case "$platform" in
        http://127.0.0.1|http://127.0.0.1:*|http://127.0.0.1/*|http://localhost|http://localhost:*|http://localhost/*|http://\[::1\]|http://\[::1\]:*|http://\[::1\]/*)
            read -r -p 'Allow plain HTTP to this loopback platform? [y/N] ' confirmation
            case "$confirmation" in
                [yY]|[yY][eE][sS]) allow_loopback_http=true ;;
                *) fail 'Loopback HTTP was not enabled. Use an HTTPS platform origin.' ;;
            esac ;;
    esac
fi

if [[ $prompt_credential == true && ! ${AIW_PERSONAL_CREDENTIAL:-} =~ [^[:space:]] ]]; then
    [[ -t 0 ]] || fail 'Set AIW_PERSONAL_CREDENTIAL when no interactive terminal is available.'
    read -r -s -p 'Dedicated platform credential (hidden): ' AIW_PERSONAL_CREDENTIAL
    printf '\n'
    [[ $AIW_PERSONAL_CREDENTIAL =~ [^[:space:]] ]] || fail 'A dedicated platform credential is required.'
fi
export AIW_PERSONAL_CREDENTIAL

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
if [[ -z $python_command ]]; then
    python_command=python3
    [[ ! -x "$script_dir/.venv/bin/python" ]] || python_command="$script_dir/.venv/bin/python"
fi
launch_arguments=(
    "$script_dir/main.py"
    --owner-id "$owner_id" --worker-id "$worker_id" --platform "$platform"
    --data-root "$data_root" --host "$host_address" --port "$port"
)
if [[ -n $public_origin ]]; then launch_arguments+=(--public-origin "$public_origin"); fi
if [[ $allow_loopback_http == true ]]; then launch_arguments+=(--allow-loopback-http); fi
if [[ $no_browser == true ]]; then launch_arguments+=(--no-browser); fi
exec "$python_command" "${launch_arguments[@]}"
