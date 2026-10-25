param(
    [string]$Maven = 'mvn',
    [string]$JavaHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$previousJavaHome = $env:JAVA_HOME
try {
    if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
    Push-Location $taskRoot
    try {
        & $Maven '-o' '-DskipTests=true' '-Dmaven.test.skip=true' 'compile'
        if ($LASTEXITCODE -ne 0) { throw 'Production Java compile failed; no tests or downloads were requested.' }
        $pythonSources = @(
            Get-ChildItem -LiteralPath (Join-Path $taskRoot 'personal-secretary') -Filter '*.py'
            Get-ChildItem -LiteralPath (Join-Path $taskRoot 'scripts/secretary_migration') -Filter '*.py'
        ) | Select-Object -ExpandProperty FullName
        & python -c "import pathlib,sys; [compile(pathlib.Path(p).read_text(encoding='utf-8-sig'),p,'exec') for p in sys.argv[1:]]" @pythonSources
        if ($LASTEXITCODE -ne 0) { throw 'Python in-memory compile failed.' }
        foreach ($scriptPath in @('src/main/resources/static/ui/authority.js', 'personal-secretary/ui/app.js', 'extern-workers/process-typescript/start-all.mjs')) {
            & node --check (Join-Path $taskRoot $scriptPath)
            if ($LASTEXITCODE -ne 0) { throw 'Browser JavaScript syntax compile failed.' }
        }
        & node -e "const fs=require('fs'),ts=require('typescript');const p=process.argv[1];const r=ts.transpileModule(fs.readFileSync(p,'utf8'),{fileName:p,reportDiagnostics:true,compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.NodeNext,isolatedModules:true}});for(const d of r.diagnostics||[]){if(d.category===ts.DiagnosticCategory.Error){process.stderr.write(ts.flattenDiagnosticMessageText(d.messageText,' '));process.exitCode=1;}}" (Join-Path $taskRoot 'extern-workers/process-typescript/src/profiles.ts')
        if ($LASTEXITCODE -ne 0) { throw 'Changed TypeScript profile syntax compile failed; provide the installed TypeScript package via NODE_PATH.' }
        Write-Output 'FD-024 compile-only passed. No tests, services, models, network, migration or distributable build.'
    } finally { Pop-Location }
} finally { $env:JAVA_HOME = $previousJavaHome }
