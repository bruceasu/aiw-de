"""CLI emits metadata plans only; no source/destination mutation commands."""

import argparse
import json
import sys
from pathlib import Path

from .planning import execute_disabled, make_export_candidate, plan_import
from .source import MigrationError, read_json


def main() -> int:
    parser = argparse.ArgumentParser(description="FD-024 offline SQLite migration planning; execution disabled")
    commands = parser.add_subparsers(dest="command", required=True)
    for operation in ("preview", "export-plan"):
        command = commands.add_parser(operation, help="Read an offline snapshot and emit metadata only")
        command.add_argument("--source", type=Path, required=True)
        command.add_argument("--owner-map", type=Path, required=True)
        command.add_argument("--owner-id", required=True)
        command.add_argument("--batch-id", required=True)
    command = commands.add_parser("import-plan", help="Validate an existing package without importing")
    command.add_argument("--package", type=Path, required=True)
    command.add_argument("--owner-id", required=True)
    command.add_argument("--worker-id", required=True)
    command.add_argument("--destination", type=Path)
    for operation in ("export", "import", "switch", "cleanup", "rollback"):
        commands.add_parser(operation, help="Disabled: no actual migration or cutover is authorized")
    args = parser.parse_args()
    try:
        if args.command in {"preview", "export-plan"}:
            _, result = make_export_candidate(args.source, read_json(args.owner_map), args.owner_id, args.batch_id)
            result["operation"] = args.command
        elif args.command == "import-plan":
            result = plan_import(read_json(args.package), args.owner_id, args.worker_id, args.destination)
        else:
            execute_disabled(args.command)
        sys.stdout.write(json.dumps(result, ensure_ascii=False, sort_keys=True, indent=2) + "\n")
        return 0
    except MigrationError as error:
        sys.stderr.write(f"Migration plan rejected: {error}\n")
    except (OSError, ValueError, TypeError, KeyError, RecursionError):
        # No traceback, private row values, credential, filesystem path or SQL dump.
        sys.stderr.write("Migration plan rejected: input cannot be safely processed\n")
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
