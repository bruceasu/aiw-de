param(
    [string]$OwnerId,
    [string]$WorkerId,
    [string]$Platform,
    [string]$DataRoot = (Join-Path $HOME '.aiw-personal'),
    [string]$HostAddress = '127.0.0.1',
    [int]$Port = 8766,
    [string]$PublicOrigin,
    [switch]$AllowLoopbackHttp,
    [switch]$PromptCredential,
    [switch]$NoBrowser,
    [string]$Python
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($Python)) {
    $Python = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'
    if (-not (Test-Path -LiteralPath $Python)) { $Python = 'python' }
}
$dotenvJson = & $Python (Join-Path $PSScriptRoot 'env_config.py') --json
if ($LASTEXITCODE -ne 0) { throw 'Could not load the program .env file.' }
$dotenvValues = $dotenvJson | ConvertFrom-Json
foreach ($property in $dotenvValues.PSObject.Properties) {
    [Environment]::SetEnvironmentVariable($property.Name, [string]$property.Value, 'Process')
}
if ([string]::IsNullOrWhiteSpace($OwnerId)) { $OwnerId = $env:AIW_PERSONAL_OWNER_ID }
if ([string]::IsNullOrWhiteSpace($WorkerId)) {
    $WorkerId = if ($env:AIW_PERSONAL_WORKER_ID) { $env:AIW_PERSONAL_WORKER_ID } else { 'my-secretary' }
}
if ([string]::IsNullOrWhiteSpace($Platform)) { $Platform = $env:AIW_PERSONAL_PLATFORM }
if ([string]::IsNullOrWhiteSpace($OwnerId) -and [string]::IsNullOrWhiteSpace($Platform) -and -not $PromptCredential) {
    $dailyArguments = @((Join-Path $PSScriptRoot 'main.py'), '--data-root', $DataRoot, '--host', $HostAddress, '--port', "$Port")
    if ($PublicOrigin) { $dailyArguments += @('--public-origin', $PublicOrigin) }
    if ($NoBrowser) { $dailyArguments += '--no-browser' }
    & $Python @dailyArguments
    exit $LASTEXITCODE
}
if ([string]::IsNullOrWhiteSpace($OwnerId)) {
    $OwnerId = Read-Host 'Owner ID from the platform authority page'
}
if ([string]::IsNullOrWhiteSpace($Platform)) {
    $Platform = Read-Host 'Platform origin (Enter for http://127.0.0.1:8080)'
    if ([string]::IsNullOrWhiteSpace($Platform)) { $Platform = 'http://127.0.0.1:8080' }
}
if ([string]::IsNullOrWhiteSpace($OwnerId) -or [string]::IsNullOrWhiteSpace($WorkerId)) {
    throw 'Owner ID and Worker ID must match the identity enrolled on the platform.'
}
$platformUri = $null
if (-not [Uri]::TryCreate($Platform, [UriKind]::Absolute, [ref]$platformUri)) {
    throw 'Enter a valid platform origin.'
}
if (($PromptCredential -or -not [string]::IsNullOrWhiteSpace($env:AIW_PERSONAL_CREDENTIAL)) -and $platformUri.Scheme -eq 'http' -and $platformUri.DnsSafeHost -in @('127.0.0.1', '::1', 'localhost') -and -not $AllowLoopbackHttp) {
    $confirmation = Read-Host 'Allow plain HTTP to this loopback platform? [y/N]'
    if ($confirmation -notin @('y', 'yes')) { throw 'Loopback HTTP was not enabled. Use an HTTPS platform origin.' }
    $AllowLoopbackHttp = $true
}

$launchArguments = @(
    (Join-Path $PSScriptRoot 'main.py'),
    '--owner-id', $OwnerId, '--worker-id', $WorkerId, '--platform', $Platform,
    '--data-root', $DataRoot, '--host', $HostAddress, '--port', "$Port"
)
if ($PublicOrigin) { $launchArguments += @('--public-origin', $PublicOrigin) }
if ($AllowLoopbackHttp) { $launchArguments += '--allow-loopback-http' }
if ($NoBrowser) { $launchArguments += '--no-browser' }

$previousCredential = $env:AIW_PERSONAL_CREDENTIAL
try {
    if ($PromptCredential -and [string]::IsNullOrWhiteSpace($previousCredential)) {
        $secureCredential = Read-Host 'Dedicated platform credential (hidden)' -AsSecureString
        $credentialPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureCredential)
        try {
            $env:AIW_PERSONAL_CREDENTIAL = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($credentialPointer)
        } finally {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($credentialPointer)
            $secureCredential.Dispose()
        }
        if ([string]::IsNullOrWhiteSpace($env:AIW_PERSONAL_CREDENTIAL)) { throw 'A dedicated platform credential is required.' }
    }
    & $Python @launchArguments
    $workerExitCode = $LASTEXITCODE
} finally {
    $env:AIW_PERSONAL_CREDENTIAL = $previousCredential
}
exit $workerExitCode
