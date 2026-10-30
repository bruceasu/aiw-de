$ErrorActionPreference = "Stop"

$packageDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$venvDir = Join-Path $packageDir ".venv"
$python = Join-Path $venvDir "Scripts\python.exe"

if (-not (Get-Command uv -ErrorAction SilentlyContinue)) {
    throw "uv is required. Install it from https://docs.astral.sh/uv/"
}

uv venv $venvDir
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
uv pip sync --python $python (Join-Path $packageDir "requirements.txt")
exit $LASTEXITCODE
