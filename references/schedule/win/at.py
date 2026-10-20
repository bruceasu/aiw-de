#!/usr/bin/env python3

import datetime as dt
import random
import re
import subprocess
import sys


def usage():
    print(r"""
Usage:
  at [-n NAME] HH:mm [on] [DATE] CMD
  at [-n NAME] 10am CMD
  at [-n NAME] 10:30pm CMD
  at [-n NAME] noon CMD
  at [-n NAME] midnight CMD
  at [-n NAME] teatime CMD
  at [-n NAME] now + N minutes|hours|days|weeks CMD

DATE:
  MM-dd
  MMM dd
  YYYY-MM-DD
  MM/DD/YYYY

Options:
  -n, --name NAME    Task name
  -h, --help         Show this help

Examples:
  at 14:30 D:\tools\job.cmd
  at -n backup 14:30 D:\tools\backup.cmd
  at 14:30 on 09-10 D:\tools\job.cmd
  at 14:30 Sep 10 D:\tools\job.cmd
  at 14:30 2026-09-10 D:\tools\job.cmd
  at now + 10 minutes D:\tools\job.cmd
""".strip())


def fail(msg):
    print(f"ERROR: {msg}", file=sys.stderr)
    sys.exit(1)


def generated_name():
    now = dt.datetime.now()
    return (
        "at-"
        + now.strftime("%Y%m%d-%H%M%S")
        + "-"
        + str(random.randint(1000, 9999))
    )


def parse_time(value):
    v = value.lower()

    if v == "noon":
        return dt.time(12, 0)

    if v == "midnight":
        return dt.time(0, 0)

    if v == "teatime":
        return dt.time(16, 0)

    formats = [
        "%H:%M",
        "%I%p",
        "%I:%M%p",
    ]

    for fmt in formats:
        try:
            return dt.datetime.strptime(value, fmt).time()
        except ValueError:
            pass

    fail(f"invalid time: {value}")


def parse_date(args, index):
    if index >= len(args):
        return None, 0

    value = args[index]
    now = dt.datetime.now()

    # YYYY-MM-DD
    if re.fullmatch(r"\d{4}-\d{1,2}-\d{1,2}", value):
        try:
            d = dt.datetime.strptime(value, "%Y-%m-%d").date()
            return d, 1
        except ValueError:
            fail(f"invalid date: {value}")

    # MM/DD/YYYY
    if re.fullmatch(r"\d{1,2}/\d{1,2}/\d{4}", value):
        try:
            d = dt.datetime.strptime(value, "%m/%d/%Y").date()
            return d, 1
        except ValueError:
            fail(f"invalid date: {value}")

    # MM-dd
    m = re.fullmatch(r"(\d{1,2})-(\d{1,2})", value)

    if m:
        month = int(m.group(1))
        day = int(m.group(2))

        try:
            d = dt.date(now.year, month, day)
        except ValueError:
            fail(f"invalid date: {value}")

        if d < now.date():
            try:
                d = dt.date(now.year + 1, month, day)
            except ValueError:
                fail(f"invalid date: {value}")

        return d, 1

    # MMM dd
    if index + 1 < len(args):
        candidate = f"{args[index]} {args[index + 1]}"

        for fmt in ("%b %d", "%B %d"):
            try:
                tmp = dt.datetime.strptime(candidate, fmt)

                d = dt.date(
                    now.year,
                    tmp.month,
                    tmp.day,
                )

                if d < now.date():
                    d = dt.date(
                        now.year + 1,
                        tmp.month,
                        tmp.day,
                    )

                return d, 2

            except ValueError:
                pass

    return None, 0


def create_task(name, when, command):
    if when <= dt.datetime.now():
        fail(
            "scheduled time is in the past: "
            + when.strftime("%Y-%m-%d %H:%M")
        )

    args = [
        "schtasks.exe",
        "/create",
        "/tn", name,
        "/tr", command,
        "/sc", "once",
        "/sd", when.strftime("%Y/%m/%d"),
        "/st", when.strftime("%H:%M"),
    ]

    result = subprocess.run(args)

    if result.returncode != 0:
        sys.exit(result.returncode)

    print()
    print(f"Task : {name}")
    print(f"Run  : {when:%Y-%m-%d %H:%M}")
    print(f"Cmd  : {command}")


def main():
    args = sys.argv[1:]

    if not args:
        usage()
        return 0

    if args[0] in ("-h", "--help", "/?"):
        usage()
        return 0

    name = None

    if args[0] in ("-n", "--name"):
        if len(args) < 2:
            fail("missing task name")

        name = args[1]
        args = args[2:]

    if not args:
        usage()
        return 0

    if name is None:
        name = generated_name()

    # at now + N unit CMD
    if args[0].lower() == "now":
        if len(args) < 5:
            fail("usage: at now + N minutes|hours|days|weeks CMD")

        if args[1] != "+":
            fail("expected '+' after now")

        try:
            n = int(args[2])
        except ValueError:
            fail(f"invalid number: {args[2]}")

        if n <= 0:
            fail("interval must be greater than zero")

        unit = args[3].lower()
        now = dt.datetime.now()

        if unit in ("minute", "minutes"):
            when = now + dt.timedelta(minutes=n)

        elif unit in ("hour", "hours"):
            when = now + dt.timedelta(hours=n)

        elif unit in ("day", "days"):
            when = now + dt.timedelta(days=n)

        elif unit in ("week", "weeks"):
            when = now + dt.timedelta(weeks=n)

        else:
            fail(f"invalid unit: {unit}")

        command = " ".join(args[4:])
        create_task(name, when, command)
        return 0

    # at TIME [on] [DATE] CMD
    time_value = parse_time(args[0])

    index = 1

    if index < len(args) and args[index].lower() == "on":
        index += 1

    date_value, used = parse_date(args, index)

    if date_value is not None:
        index += used

    if index >= len(args):
        fail("missing command")

    command = " ".join(args[index:])

    now = dt.datetime.now()

    if date_value is None:
        when = dt.datetime.combine(
            now.date(),
            time_value,
        )

        if when <= now:
            when += dt.timedelta(days=1)

    else:
        when = dt.datetime.combine(
            date_value,
            time_value,
        )

    create_task(name, when, command)
    return 0


if __name__ == "__main__":
    sys.exit(main())