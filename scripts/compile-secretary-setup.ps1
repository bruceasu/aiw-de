# Compile production sources only, offline. No service, tests, model or package build.
param([string]$Maven = 'mvn', [string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $taskRoot
$previousJavaHome = $env:JAVA_HOME
try {
    if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
    & $Maven '-o' '-DskipTests=true' '-Dmaven.test.skip=true' 'compile'
    if ($LASTEXITCODE -ne 0) { throw 'Secretary platform Java compile failed (offline, no tests or downloads).' }
    & (Join-Path $PSScriptRoot 'compile-secretary-runtime.ps1')
} finally { $env:JAVA_HOME = $previousJavaHome; Pop-Location }
