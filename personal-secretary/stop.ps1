param(
    [ValidateRange(1, 65535)]
    [int]$Port = 8766,
    [string]$Python = 'python',
    [switch]$Force
)

# Default: authenticated graceful shutdown. Force is an explicit manual fallback.
$ErrorActionPreference = 'Stop'
& $Python (Join-Path $PSScriptRoot 'stop_service.py') --service "http://127.0.0.1:$Port"
if ($LASTEXITCODE -eq 0) { exit 0 }
if (-not $Force) {
    throw 'Graceful stop was not confirmed. To stop an old or stuck secretary, run this script with -Force. Active work may be interrupted.'
}

function Get-SecretaryListeners {
    Get-NetTCPConnection -ErrorAction Stop | Where-Object { $_.LocalPort -eq $Port -and $_.State -eq 'Listen' }
}
$listeners = @(Get-SecretaryListeners)
if ($listeners.Count -eq 0) {
    Write-Output 'The HTTP listener is already closed.'
    exit 0
}
$serviceProcessIds = @($listeners | Select-Object -ExpandProperty OwningProcess -Unique)
if ($serviceProcessIds.Count -ne 1) { throw 'Multiple processes own this port. Refusing to force stop.' }
$serviceProcessId = [int]$serviceProcessIds[0]
$serviceProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$serviceProcessId"
$expectedMain = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot 'main.py')).Path
$mainPattern = '(?:^|[\s"])' + [regex]::Escape($expectedMain) + '(?=[\s"]|$)'
if (-not $serviceProcess -or $serviceProcess.Name -notmatch '^python(?:w|[0-9]+(?:\.[0-9]+)*)?\.exe$' -or
    -not $serviceProcess.CommandLine -or $serviceProcess.CommandLine -notmatch $mainPattern) {
    throw 'The listener is not a Python secretary started from this repository main.py. Refusing to stop it.'
}
$processOwner = Invoke-CimMethod -InputObject $serviceProcess -MethodName GetOwner
$currentAccount = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
if ($processOwner.ReturnValue -ne 0 -or "$($processOwner.Domain)\$($processOwner.User)" -ne $currentAccount) {
    throw 'The secretary process does not belong to this Windows account. Refusing to stop it.'
}
$targetProcess = Get-Process -Id $serviceProcessId
if ([Math]::Abs(($targetProcess.StartTime.ToUniversalTime() - $serviceProcess.CreationDate.ToUniversalTime()).TotalMilliseconds) -gt 1) {
    throw 'The process changed during inspection. Refusing to stop it.'
}
$currentListeners = @(Get-SecretaryListeners)
if (-not ($currentListeners | Where-Object { $_.OwningProcess -eq $serviceProcessId })) {
    throw 'The listener changed during inspection. Refusing to stop it.'
}
Write-Warning "Force stopping secretary PID $serviceProcessId on port $Port. Active work may be interrupted; check saved results before retrying."
Stop-Process -InputObject $targetProcess -Force -ErrorAction Stop
if (-not $targetProcess.WaitForExit(10000)) { throw 'The process has not exited. Stop is unconfirmed.' }
if (@(Get-SecretaryListeners).Count -ne 0) {
    throw 'The port is still listening. Stop is unconfirmed; no additional process was stopped.'
}
Write-Output 'Secretary process exited and HTTP listener closed. Private data and settings were not deleted.'
