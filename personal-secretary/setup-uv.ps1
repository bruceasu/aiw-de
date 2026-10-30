param([switch]$Desktop)

$ErrorActionPreference = "Stop"
$venvDir = Join-Path $PSScriptRoot ".venv"
$python = Join-Path $venvDir "Scripts\python.exe"

if (-not (Get-Command uv -ErrorAction SilentlyContinue)) {
    throw "uv is required. Install it from https://docs.astral.sh/uv/"
}

uv venv $venvDir
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$requirements = @((Join-Path $PSScriptRoot "requirements.txt"))
if ($Desktop) { $requirements += (Join-Path $PSScriptRoot "requirements-desktop.txt") }
uv pip sync --python $python @requirements
exit $LASTEXITCODE
