param(
    [ValidatePattern('^https?://[a-zA-Z0-9.\[\]:]+/?$')]
    [string]$Service = 'http://127.0.0.1:8766',
    [string]$Python,
    [switch]$LegacyTk,
    [switch]$Opaque,
    [switch]$ShowPet
)

$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') { throw 'This desktop UI currently supports Windows only.' }
$dotenvPython = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $dotenvPython)) { $dotenvPython = 'python' }
$dotenvJson = & $dotenvPython (Join-Path $PSScriptRoot 'env_config.py') --json
if ($LASTEXITCODE -ne 0) { throw 'Could not load the program .env file.' }
$dotenvValues = $dotenvJson | ConvertFrom-Json
foreach ($property in $dotenvValues.PSObject.Properties) {
    [Environment]::SetEnvironmentVariable($property.Name, [string]$property.Value, 'Process')
}
if ([string]::IsNullOrWhiteSpace($Python)) {
    $Python = $env:AIW_DESKTOP_PYTHON
    if ([string]::IsNullOrWhiteSpace($Python)) { $Python = Join-Path $PSScriptRoot '.venv\Scripts\pythonw.exe' }
    if (-not (Test-Path -LiteralPath $Python)) { $Python = 'pythonw' }
}
$desktopScript = Join-Path $PSScriptRoot $(if ($LegacyTk) { 'desktop.py' } else { 'desktop_webview.py' })
$desktopArguments = @(
    ('"' + $desktopScript + '"')
    '--service'
    $Service
)
if ($Opaque -and -not $LegacyTk) { $desktopArguments += '--opaque' }
if ($ShowPet) { $desktopArguments += '--show-pet' }
Start-Process -FilePath $Python -ArgumentList $desktopArguments -WindowStyle Hidden
Write-Output 'Desktop UI launch requested in the system tray. Use -ShowPet to show the companion. Qt mode needs requirements-desktop.txt. The local service is reused, or started when absent. Use -LegacyTk for the previous Windows UI.'
