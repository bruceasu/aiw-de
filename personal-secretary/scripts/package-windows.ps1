param(
    [string]$Python,
    [string]$OutputDirectory = (Join-Path (Split-Path -Parent $PSScriptRoot) 'dist')
)

$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') { throw 'This packaging script targets Windows only.' }
$secretaryRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($Python)) {
    $Python = Join-Path $secretaryRoot '.venv\Scripts\python.exe'
    if (-not (Test-Path -LiteralPath $Python)) { $Python = 'python' }
}
$entryPoint = Join-Path $secretaryRoot 'package_entry.py'
$outputPath = [System.IO.Path]::GetFullPath($OutputDirectory)
$workPath = Join-Path $secretaryRoot 'build\pyinstaller'
if (-not (Test-Path -LiteralPath $entryPoint)) { throw 'The package entry point is missing.' }

& $Python -m PyInstaller --version *> $null
if ($LASTEXITCODE -ne 0) {
    throw 'PyInstaller is not installed in the selected Python environment. Install it separately, then run this script again; this script does not download dependencies.'
}
& $Python -c 'from PySide6.QtWebEngineWidgets import QWebEngineView' *> $null
if ($LASTEXITCODE -ne 0) {
    throw 'PySide6 Qt WebEngine is not installed in the selected Python environment. Install the desktop requirements separately; this script does not download dependencies.'
}

$arguments = @(
    '--onedir', '--windowed', '--name', 'AIWPersonalSecretary',
    '--distpath', $outputPath, '--workpath', $workPath, '--specpath', $workPath,
    '--paths', $secretaryRoot, '--paths', (Join-Path $secretaryRoot 'pet'),
    '--collect-all', 'PySide6', '--collect-all', 'shiboken6',
    '--add-data', ((Join-Path $secretaryRoot 'ui') + ';ui'),
    '--add-data', ((Join-Path $secretaryRoot 'pet\desktop-assets') + ';pet/desktop-assets'),
    $entryPoint
)
& $Python -m PyInstaller @arguments
if ($LASTEXITCODE -ne 0) { throw 'PyInstaller failed to package the personal secretary.' }
Write-Output "Package created at $(Join-Path $outputPath 'AIWPersonalSecretary'). No dependencies were installed by this script."
