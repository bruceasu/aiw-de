"""Run three local Worker-quality checks and emit one JSON baseline on stdout."""
import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path
from time import perf_counter


def run(case, command, cwd):
    start = perf_counter()
    try:
        result = subprocess.run(command, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=120)
    except (OSError, subprocess.TimeoutExpired) as error:
        print(f"{case}: {error}", file=sys.stderr)
        status, exit_code = "unavailable", None
    else:
        if result.returncode:
            print(f"{case}:\n{result.stdout.decode('utf-8', errors='replace')[-6000:]}", file=sys.stderr)
        status, exit_code = ("passed" if result.returncode == 0 else "failed"), result.returncode
    return {"case": case, "status": status, "exitCode": exit_code,
            "localCheckMs": round((perf_counter() - start) * 1000),
            "claimWaitMs": None, "modelCalls": None, "tokens": None, "cost": None}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--maven-settings", type=Path)
    parser.add_argument("--maven-repository", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    worker = root / "workers" / "process-python"
    python = [sys.executable, "-m", "unittest"]
    maven = [shutil.which("mvn") or "mvn", "-q", "-o", "-DforkCount=0"]
    if args.maven_settings:
        maven += ["-s", str(args.maven_settings.resolve())]
    if args.maven_repository:
        maven.append(f"-Dmaven.repo.local={args.maven_repository.resolve()}")
    cases = [
        ("text-start", [*python, "test_plan_workspace_lock.WorkspaceLockTest.test_text_task_does_not_wait_for_shared_git_workspace_and_carries_its_location"], worker),
        ("file-delivery", [*python,
                           "test_workspace_files.WorkspaceFilesTest.test_edit_many_applies_all_fragments_in_one_operation",
                           "test_workspace_files.WorkspaceFilesTest.test_required_checks_use_finished_operations_and_invalidate_after_a_write"], worker),
        ("interruption-retry", [*maven, "-Dtest=WorkflowInterruptionTest#confirmedTaskDetailRetryRestoresClaimabilityOnce", "test"], root),
    ]
    results = [run(*case) for case in cases]
    print(json.dumps({"mode": "offline-fixtures", "results": results}, ensure_ascii=False, indent=2))
    return int(any(result["status"] != "passed" for result in results))


if __name__ == "__main__":
    raise SystemExit(main())
