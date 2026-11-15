$ErrorActionPreference = 'Stop'

if ($env:OS -ne 'Windows_NT') {
    throw 'Creating the desktop shortcut is supported on Windows only.'
}

$launcher = Join-Path $PSScriptRoot 'start-desktop.ps1'
if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) {
    throw "Desktop launcher not found: $launcher"
}

$desktop = [Environment]::GetFolderPath([Environment+SpecialFolder]::DesktopDirectory)
if ([string]::IsNullOrWhiteSpace($desktop) -or -not (Test-Path -LiteralPath $desktop -PathType Container)) {
    throw 'The current user desktop folder is unavailable.'
}

$powerShell = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$shortcutPath = Join-Path $desktop 'AIW Secretary.lnk'
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = $powerShell
$shortcut.Arguments = "-NoProfile -WindowStyle Hidden -File `"$launcher`" -ShowPet"
$shortcut.WorkingDirectory = Split-Path -Parent $launcher
$shortcut.Description = 'Open the AIW Secretary desktop companion'
$shortcut.Save()

Write-Output "Created or updated: $shortcutPath"
