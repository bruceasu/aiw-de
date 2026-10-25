# Compile only: no imports, SDK/model execution, tests, network or retained output.
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$sources = @(
    Get-ChildItem -LiteralPath (Join-Path $taskRoot 'personal-secretary') -Filter '*.py'
) | Select-Object -ExpandProperty FullName
& python -c "import pathlib,sys; [compile(pathlib.Path(p).read_text(encoding='utf-8-sig'),p,'exec') for p in sys.argv[1:]]" @sources
if ($LASTEXITCODE -ne 0) { throw 'Secretary Runtime Python compile failed.' }
foreach ($sourcePath in @('personal-secretary/ui/app.js')) {
    & node --check (Join-Path $taskRoot $sourcePath)
    if ($LASTEXITCODE -ne 0) { throw 'Secretary/theme JavaScript syntax compile failed.' }
}
foreach ($scriptPath in @('personal-secretary/stop.ps1', 'scripts/configure-local-worker-token.ps1')) {
    $parseTokens = $null
    $parseErrors = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile((Join-Path $taskRoot $scriptPath), [ref]$parseTokens, [ref]$parseErrors)
    if ($parseErrors.Count -gt 0) { throw ('Secretary PowerShell syntax failed: ' + ($parseErrors -join '; ')) }
}
Write-Output 'Secretary Runtime compile-only passed (Python, JavaScript, stop.ps1). No Worker source, SDK imports, tests, services, models, network or retained artifacts.'
