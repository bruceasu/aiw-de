# Compile only: no imports, SDK/model execution, tests, network or retained output.
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$sources = @(
    Get-ChildItem -LiteralPath (Join-Path $taskRoot 'workers/personal-secretary') -Filter '*.py'
    Get-ChildItem -LiteralPath (Join-Path $taskRoot 'workers/process-python') -Filter '*.py' |
        Where-Object { $_.Name -notlike 'test_*' }
) | Select-Object -ExpandProperty FullName
& python -c "import pathlib,sys; [compile(pathlib.Path(p).read_text(encoding='utf-8-sig'),p,'exec') for p in sys.argv[1:]]" @sources
if ($LASTEXITCODE -ne 0) { throw 'Secretary/shared Runtime Python compile failed.' }
foreach ($sourcePath in @('workers/personal-secretary/ui/app.js', 'src/main/resources/static/ui/appearance.js')) {
    & node --check (Join-Path $taskRoot $sourcePath)
    if ($LASTEXITCODE -ne 0) { throw 'Secretary/theme JavaScript syntax compile failed.' }
}
Write-Output 'Secretary/shared Runtime compile-only passed. No tests, SDK imports, services, models, network or retained artifacts.'
