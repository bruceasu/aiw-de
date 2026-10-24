param(
    [ValidatePattern('^[a-zA-Z0-9._/-]*$')]
    [string]$Revision = ''
)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$compileSources = @'
import pathlib, subprocess, sys, tempfile
root = pathlib.Path(sys.argv[1])
revision = sys.argv[2] if len(sys.argv) > 2 else ""
prefix = "workers/personal-secretary/"
def source(path):
    if revision:
        return subprocess.run(["git", "show", revision + ":" + path], cwd=root, check=True, stdout=subprocess.PIPE).stdout
    return (root / path).read_bytes()
for name in ("main.py", "desktop.py", "desktop_client.py", "desktop_win32.py"):
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
launcher = prefix + "start-desktop.ps1"
if revision and source(launcher) != (root / launcher).read_bytes():
    raise SystemExit("The working launcher differs from the selected revision; stop before parsing another version.")
'@
$previousDesktopOutputEncoding = $OutputEncoding
try {
    $OutputEncoding = [System.Text.UTF8Encoding]::new($false)
    $compileSources | & python -X utf8 - $taskRoot $Revision
} finally { $OutputEncoding = $previousDesktopOutputEncoding }
if ($LASTEXITCODE -ne 0) { throw 'Desktop Python/JavaScript syntax compile failed.' }
$parseTokens = $null
$parseErrors = $null
$null = [System.Management.Automation.Language.Parser]::ParseFile((Join-Path $taskRoot 'workers/personal-secretary/start-desktop.ps1'), [ref]$parseTokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw ($parseErrors | Out-String) }
Write-Output 'Desktop compile-only passed. No application imports, windows, services, tests, network, downloads or packages.'
