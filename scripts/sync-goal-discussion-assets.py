"""Copy the canonical role assets into independently deployable Worker packages.

Default mode writes only the known bundled destinations. --check is an optional
read-only consistency check; running it follows the repository validation budget.
"""
from pathlib import Path
import argparse


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    workers = Path(__file__).resolve().parents[1] / "workers"
    canonical = workers / "shared" / "goal-discussion"
    different = []
    for language in ("typescript", "python", "go"):
        package = workers / f"process-{language}"
        for source, destination in (
            (canonical / "profile.toml", package / "profiles" / "goal-discussion.toml"),
            (canonical / "SKILL.md", package / "skills" / "goal-discussion" / "SKILL.md"),
        ):
            content = source.read_bytes()
            if args.check:
                if not destination.is_file() or destination.read_bytes() != content:
                    different.append(str(destination.relative_to(workers)))
            else:
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(content)
    if different:
        raise SystemExit("Bundled goal-discussion assets differ: " + ", ".join(different))
    print("Goal-discussion bundled assets match." if args.check else "Goal-discussion bundled assets updated.")


if __name__ == "__main__":
    main()
