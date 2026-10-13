param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("requirements","architect","planner","coder","integrator","reviewer","stage-reviewer","verifier","integration-verifier","repairer")]
    [string]$Role,
    [Parameter(Mandatory=$true)]
    [ValidateSet("codex-cli","codex-app-server","openai-agents")]
    [string]$Runtime
)
$ErrorActionPreference = "Stop"
if ($env:AIW_AGENT_RUNTIME -and $env:AIW_AGENT_RUNTIME -cne $Runtime) {
    [Console]::Error.WriteLine("conflicting runtime: AIW_AGENT_RUNTIME=$env:AIW_AGENT_RUNTIME, argument=$Runtime")
    exit 2
}
$env:AIW_PROCESS_ROLE = $Role
$env:AIW_AGENT_RUNTIME = $Runtime
if (-not $env:AIW_WORKER_ID) {
    $env:AIW_WORKER_ID = if ($Runtime -eq "codex-cli") { "process-exec-$Role-1" } elseif ($Runtime -eq "codex-app-server") { "process-$Role-1" } else { "process-api-$Role-1" }
}
node "$PSScriptRoot/../dist/index.js"
exit $LASTEXITCODE
