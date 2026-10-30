"""Load simple KEY=VALUE settings from the program directory's .env file."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shlex
import sys


_ASSIGNMENT = re.compile(r"^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$")


def read_program_env(program_file: str | Path) -> dict[str, str]:
    env_path = Path(program_file).resolve().parent / ".env"
    if not env_path.is_file():
        return {}

    values: dict[str, str] = {}
    try:
        lines = env_path.read_text(encoding="utf-8-sig").splitlines()
    except OSError as error:
        raise SystemExit(f"Cannot read environment file {env_path}: {error.strerror}") from None

    for line_number, raw_line in enumerate(lines, start=1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:].lstrip()
        match = _ASSIGNMENT.fullmatch(line)
        if not match:
            raise SystemExit(f"Invalid assignment in {env_path} at line {line_number}.")

        name, raw_value = match.groups()
        raw_value = raw_value.strip()
        if raw_value.startswith(("'", '"')):
            try:
                parsed = shlex.split(raw_value, comments=True, posix=True)
            except ValueError:
                raise SystemExit(f"Invalid quoted value in {env_path} at line {line_number}.") from None
            if len(parsed) > 1:
                raise SystemExit(f"Invalid quoted value in {env_path} at line {line_number}.")
            value = parsed[0] if parsed else ""
        else:
            value = re.split(r"\s+#", raw_value, maxsplit=1)[0].rstrip()
        values[name] = value
    return values


def load_program_env(program_file: str | Path) -> None:
    for name, value in read_program_env(program_file).items():
        os.environ.setdefault(name, value)


def _main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--shell", action="store_true", help="print safe POSIX export statements")
    parser.add_argument("--json", action="store_true", help="print unset variables as JSON")
    args = parser.parse_args()
    if args.shell == args.json:
        parser.error("choose exactly one of --shell or --json")

    values = {
        name: value
        for name, value in read_program_env(__file__).items()
        if name not in os.environ
    }
    if args.json:
        print(json.dumps(values))
    else:
        for name, value in values.items():
            print(f"export {name}={shlex.quote(value)}")
    return 0


if __name__ == "__main__":
    sys.exit(_main())
