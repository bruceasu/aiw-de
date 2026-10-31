param([ValidatePattern('^[a-zA-Z0-9._/-]*$')][string]$Revision = '')

$ErrorActionPreference = 'Stop'
$launcher = Join-Path $PSScriptRoot '..\personal-secretary\scripts\compile-secretary-desktop.ps1'
if ($Revision) { & $launcher -Revision $Revision } else { & $launcher }
exit $LASTEXITCODE
