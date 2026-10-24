$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$sourceNames = @('main.py', 'desktop.py', 'desktop_client.py', 'desktop_win32.py')
$sources = $sourceNames | ForEach-Object { Join-Path $taskRoot "workers/personal-secretary/$_" }
& python -c "import pathlib,sys; [compile(pathlib.Path(p).read_text(encoding='utf-8-sig'),p,'exec') for p in sys.argv[1:]]" @sources
if ($LASTEXITCODE -ne 0) { throw 'Desktop Python syntax compile failed.' }
& node --check (Join-Path $taskRoot 'workers/personal-secretary/ui/app.js')
if ($LASTEXITCODE -ne 0) { throw 'Personal UI JavaScript syntax compile failed.' }
$parseTokens = $null
$parseErrors = $null
$null = [System.Management.Automation.Language.Parser]::ParseFile((Join-Path $taskRoot 'workers/personal-secretary/start-desktop.ps1'), [ref]$parseTokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw ($parseErrors | Out-String) }
Write-Output 'Desktop compile-only passed. No imports, windows, services, tests, network, downloads or packages.'
