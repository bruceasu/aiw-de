param(
    [ValidatePattern('^https?://[a-zA-Z0-9.\[\]:]+/?$')]
    [string]$Service = 'http://127.0.0.1:8766',
    [string]$Python,
    [switch]$LegacyTk,
    [switch]$Opaque,
    [switch]$ShowPet
)

$ErrorActionPreference = 'Stop'
$launcher = Join-Path $PSScriptRoot 'pet/start-desktop.ps1'
$arguments = @{ Service = $Service; LegacyTk = $LegacyTk; Opaque = $Opaque; ShowPet = $ShowPet }
if (-not [string]::IsNullOrWhiteSpace($Python)) { $arguments.Python = $Python }
& $launcher @arguments
exit $LASTEXITCODE
