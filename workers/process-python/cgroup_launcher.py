"""Trusted Linux launcher: join the operation cgroup before executing the App Server."""
import os
from pathlib import Path
import sys


def main():
    if sys.platform != "linux" or len(sys.argv) < 4: raise RuntimeError("CGROUP_LAUNCH_ARGUMENTS_INVALID")
    group = Path(sys.argv[1]).resolve(strict=True)
    mount = Path("/sys/fs/cgroup").resolve(strict=True)
    if group == mount or not group.is_relative_to(mount): raise RuntimeError("CGROUP_LAUNCH_PATH_INVALID")
    (group / "cgroup.procs").write_text(str(os.getpid()), encoding="ascii")
    membership = Path("/proc/self/cgroup").read_text(encoding="ascii")
    if f"0::/{group.relative_to(mount).as_posix()}\n" not in membership: raise RuntimeError("CGROUP_MEMBERSHIP_UNCONFIRMED")
    os.execv(sys.argv[2], sys.argv[2:])


if __name__ == "__main__": main()
