param([switch]$Desktop)

$ErrorActionPreference = "Stop"
$secretaryRoot = Split-Path -Parent $PSScriptRoot
$venvDir = Join-Path $secretaryRoot ".venv"
$python = Join-Path $venvDir "Scripts\python.exe"

if (-not (Get-Command uv -ErrorAction SilentlyContinue)) {
    throw "uv is required. Install it from https://docs.astral.sh/uv/"
}

uv venv $venvDir
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$requirements = @((Join-Path $secretaryRoot "requirements.txt"))
if ($Desktop) { $requirements += (Join-Path $secretaryRoot "pet/requirements-desktop.txt") }
uv pip sync --python $python @requirements
exit $LASTEXITCODE
