param(
    [ValidatePattern('^[a-zA-Z0-9._/-]*$')]
    [string]$Revision = ''
)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$compileSources = @'
import pathlib, subprocess, sys, tempfile
root = pathlib.Path(sys.argv[1])
revision = sys.argv[2] if len(sys.argv) > 2 else ""
prefix = "personal-secretary/"
def source(path):
    if revision:
        return subprocess.run(["git", "show", revision + ":" + path], cwd=root, check=True, stdout=subprocess.PIPE).stdout
    return (root / path).read_bytes()
if revision:
    tracked = subprocess.run(["git", "ls-tree", "-r", "--name-only", revision, "--", prefix], cwd=root, check=True, stdout=subprocess.PIPE, text=True).stdout.splitlines()
    names = tuple(path[len(prefix):] for path in tracked if path.startswith(prefix) and path.endswith(".py"))
else:
    source_root = root / prefix
    excluded = {".venv", "build", "dist", "__pycache__"}
    names = tuple(path.relative_to(source_root).as_posix() for path in source_root.rglob("*.py")
                  if not excluded.intersection(path.relative_to(source_root).parts))
for name in names:
    compile(source(prefix + name).decode("utf-8-sig"), prefix + name, "exec")
for name in ("ui/app.js", "ui/pairing.js"):
    javascript = source(prefix + name)
    temporary = tempfile.NamedTemporaryFile(prefix="fd031-compile-", suffix=".js", delete=False)
    temporary_path = pathlib.Path(temporary.name)
    try:
        with temporary:
            temporary.write(javascript)
        subprocess.run(["node", "--check", str(temporary_path)], check=True)
    finally:
        temporary.close()
        temporary_path.unlink(missing_ok=True)
launchers = (prefix + "start-desktop.ps1", prefix + "pet/start-desktop.ps1")
for launcher in launchers:
    if revision and source(launcher) != (root / launcher).read_bytes():
        raise SystemExit("A working launcher differs from the selected revision; stop before parsing another version.")
'@
$previousDesktopOutputEncoding = $OutputEncoding
try {
    $OutputEncoding = [System.Text.UTF8Encoding]::new($false)
    $compileSources | & python -X utf8 - $taskRoot $Revision
} finally { $OutputEncoding = $previousDesktopOutputEncoding }
if ($LASTEXITCODE -ne 0) { throw 'Desktop Python/JavaScript syntax compile failed.' }
$parseTokens = $null
$parseErrors = $null
$desktopLaunchers = @(
    'personal-secretary/start-desktop.ps1',
    'personal-secretary/pet/start-desktop.ps1',
    'personal-secretary/setup-uv.ps1',
    'personal-secretary/scripts/setup-uv.ps1',
    'personal-secretary/scripts/package-windows.ps1',
    'personal-secretary/scripts/compile-secretary-desktop.ps1',
    'scripts/compile-secretary-desktop.ps1'
)
foreach ($launcher in $desktopLaunchers) {
    $parseTokens = $null
    $parseErrors = $null
    $null = [System.Management.Automation.Language.Parser]::ParseFile((Join-Path $taskRoot $launcher), [ref]$parseTokens, [ref]$parseErrors)
    if ($parseErrors.Count) { throw ($parseErrors | Out-String) }
}
if (-not $Revision) {
    $parseTokens = $null
    $parseErrors = $null
    $null = [System.Management.Automation.Language.Parser]::ParseFile((Join-Path $taskRoot 'personal-secretary/start.ps1'), [ref]$parseTokens, [ref]$parseErrors)
    if ($parseErrors.Count) { throw ($parseErrors | Out-String) }
}
Write-Output 'Desktop compile-only passed. No application imports, windows, services, tests, network, downloads or packages.'
