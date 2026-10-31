param([switch]$Desktop)

$ErrorActionPreference = 'Stop'
$launcher = Join-Path $PSScriptRoot 'scripts/setup-uv.ps1'
& $launcher @PSBoundParameters
exit $LASTEXITCODE
