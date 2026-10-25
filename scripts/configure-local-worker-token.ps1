# Configure a local shared Worker credential without printing it.
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $taskRoot '.env'
$tracked = & git -C $taskRoot ls-files -- .env
if ($LASTEXITCODE -ne 0) { throw 'Cannot check whether .env is tracked.' }
if ($tracked) { throw '.env is tracked by Git. Move private configuration out of tracked files before configuring a credential.' }
$entries = if (Test-Path -LiteralPath $envFile) { [IO.File]::ReadAllLines($envFile) } else { @() }
$matchesFound = @($entries | Where-Object { $_ -match '^AIW_WORKER_TOKEN=' })
if ($matchesFound.Count -gt 1) { throw 'Multiple AIW_WORKER_TOKEN entries exist. Resolve the duplicate configuration first.' }
$storedToken = if ($matchesFound.Count) { $matchesFound[0].Substring('AIW_WORKER_TOKEN='.Length) } else { '' }
if (-not [string]::IsNullOrWhiteSpace($storedToken)) {
    Write-Output 'An existing local Worker token is configured. It was preserved.'
    exit 0
}
$localWorkerToken = $env:AIW_WORKER_TOKEN
if ([string]::IsNullOrWhiteSpace($localWorkerToken)) {
    $randomBytes = New-Object byte[] 48
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($randomBytes) } finally { $generator.Dispose() }
    $localWorkerToken = [Convert]::ToBase64String($randomBytes).TrimEnd('=').Replace('+','-').Replace('/','_')
}
if ($localWorkerToken.Contains("`r") -or $localWorkerToken.Contains("`n")) { throw 'Worker token must be a single line.' }
$replacement = 'AIW_WORKER_TOKEN=' + $localWorkerToken
$outputLines = @($entries | ForEach-Object { if ($_ -match '^AIW_WORKER_TOKEN=') { $replacement } else { $_ } })
if (-not $matchesFound.Count) { $outputLines += $replacement }
[IO.File]::WriteAllText($envFile, ($outputLines -join [Environment]::NewLine) + [Environment]::NewLine, (New-Object Text.UTF8Encoding($false)))
Write-Output 'Local Worker token configured in .env. No credential was printed. Restart the platform and load the same environment when starting professional Workers.'
