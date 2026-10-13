param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("requirements","architect","planner","coder","integrator","reviewer","stage-reviewer","verifier","integration-verifier","repairer")]
    [string]$Role
)
$ErrorActionPreference = "Stop"
$env:AIW_PROCESS_ROLE = $Role
if (-not $env:AIW_WORKER_ID) {
    $prefix = if ($env:AIW_AGENT_RUNTIME -eq "openai-agents") { "process-api" } else { "process" }
    $env:AIW_WORKER_ID = "$prefix-$Role-1"
}
python "$PSScriptRoot/../process_worker.py"
exit $LASTEXITCODE
