param(
    [Parameter(Mandatory=$true)][string]$Profile,
    [string]$WorkerId = "",
    [string]$Capabilities = "",
    [string]$RepositoryRef = "",
    [string]$RepositoryRoot = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$profilePath = Join-Path $root ("profiles\" + $Profile + ".toml")
if (!(Test-Path $profilePath)) { throw "Profile not found: $profilePath" }

$env:AIW_PROCESS_PROFILE = $profilePath
if ($WorkerId) { $env:AIW_WORKER_ID = $WorkerId }
if ($Capabilities) { $env:AIW_WORKER_CAPABILITIES = $Capabilities }
if ($RepositoryRef) { $env:AIW_REPOSITORY_REF = $RepositoryRef }
if ($RepositoryRoot) { $env:AIW_REPOSITORY_ROOT = $RepositoryRoot }

$exe = Join-Path $root "process-worker.exe"
if (!(Test-Path $exe)) { throw "Build process-worker.exe first: go build -o process-worker.exe ." }
& $exe
