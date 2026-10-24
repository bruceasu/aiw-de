param(
    [ValidatePattern('^https?://[a-zA-Z0-9.\[\]:]+/?$')]
    [string]$Service = 'http://127.0.0.1:8766',
    [string]$Python = 'pythonw'
)

$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') { throw 'This desktop UI currently supports Windows only.' }
$desktopScript = Join-Path $PSScriptRoot 'desktop.py'
$desktopArguments = @(
    ('"' + $desktopScript + '"')
    '--service'
    $Service
)
Start-Process -FilePath $Python -ArgumentList $desktopArguments -WindowStyle Normal
Write-Output 'Desktop UI launch requested. Pair it with the existing personal service; no new Worker is started.'
