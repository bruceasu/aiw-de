#!/usr/bin/env python3

import datetime as dt
import random
import re
import subprocess
import sys


def usage():
    print(r"""
Usage:
  every [-n NAME] day at HH:mm CMD
  every [-n NAME] weekend at HH:mm CMD
  every [-n NAME] workday at HH:mm CMD

  every [-n NAME] Mon at HH:mm CMD
  every [-n NAME] Mon,Wed,Fri at HH:mm CMD
  every [-n NAME] Mon-Thu at HH:mm CMD

  every [-n NAME] N hours [starting HH:mm] CMD
  every [-n NAME] hour [starting HH:mm] CMD

  every [-n NAME] N minutes [starting HH:mm] CMD
  every [-n NAME] minute [starting HH:mm] CMD

  every [-n NAME] month on DD at HH:mm CMD
  every [-n NAME] month on First at HH:mm CMD
  every [-n NAME] month on Last at HH:mm CMD

  every [-n NAME] month on Mon at HH:mm CMD
  every [-n NAME] month on Mon:1 at HH:mm CMD
  every [-n NAME] month on Mon:2 at HH:mm CMD
  every [-n NAME] month on Mon:3 at HH:mm CMD
  every [-n NAME] month on Mon:4 at HH:mm CMD
  every [-n NAME] month on Mon:Last at HH:mm CMD

Options:
  -n, --name NAME    Task name
  -h, --help         Show this help

Examples:
  every day at 08:00 D:\tools\job.cmd
  every -n backup day at 20:00 D:\tools\backup.cmd
  every workday at 09:00 D:\tools\check.cmd
  every Mon-Fri at 09:00 D:\tools\check.cmd
  every Mon,Wed,Fri at 12:30 D:\tools\report.cmd
  every 15 minutes D:\tools\poll.cmd
  every 15 minutes starting 09:05 D:\tools\poll.cmd
  every 3 hours starting 10:30 D:\tools\job.cmd
  every month on 15 at 08:00 D:\tools\monthly.cmd
  every month on Mon:1 at 08:00 D:\tools\report.cmd
  every month on Fri:Last at 18:00 D:\tools\report.cmd
""".strip())


def fail(msg):
    print(f"ERROR: {msg}", file=sys.stderr)
    sys.exit(1)


def generated_name():
    now = dt.datetime.now()

    return (
        "every-"
        + now.strftime("%Y%m%d-%H%M%S")
        + "-"
        + str(random.randint(1000, 9999))
    )


def parse_time(value):
    formats = [
        "%H:%M",
        "%I%p",
        "%I:%M%p",
    ]

    for fmt in formats:
        try:
            t = dt.datetime.strptime(value, fmt).time()
            return t.strftime("%H:%M")
        except ValueError:
            pass

    fail(f"invalid time: {value}")


WEEKDAYS = {
    "mon": "MON",
    "tue": "TUE",
    "wed": "WED",
    "thu": "THU",
    "fri": "FRI",
    "sat": "SAT",
    "sun": "SUN",
}


def normalize_day(value):
    key = value.lower()

    if key not in WEEKDAYS:
        fail(f"invalid weekday: {value}")

    return WEEKDAYS[key]


def expand_days(value):
    order = [
        "MON",
        "TUE",
        "WED",
        "THU",
        "FRI",
        "SAT",
        "SUN",
    ]

    result = []

    for part in value.split(","):
        part = part.strip()

        m = re.fullmatch(
            r"([A-Za-z]{3})-([A-Za-z]{3})",
            part
        )

        if not m:
            result.append(normalize_day(part))
            continue

        first = normalize_day(m.group(1))
        last = normalize_day(m.group(2))

        i = order.index(first)

        while True:
            day = order[i]
            result.append(day)

            if day == last:
                break

            i = (i + 1) % 7

    result = list(dict.fromkeys(result))

    return ",".join(result)


def create_task(name, command, schedule_args):
    args = [
        "schtasks.exe",
        "/create",
        "/tn", name,
        "/tr", command,
    ]

    args.extend(schedule_args)

    result = subprocess.run(args)

    if result.returncode != 0:
        sys.exit(result.returncode)

    print()
    print(f"Task : {name}")
    print(f"Cmd  : {command}")


def command_from(args, index):
    if index >= len(args):
        fail("missing command")

    return " ".join(args[index:])


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

    first = args[0].lower()

    # --------------------------------------------------------
    # month
    # --------------------------------------------------------

    if first == "month":
        if len(args) < 6:
            fail("usage: every month on CONDITION at HH:mm CMD")

        if args[1].lower() != "on":
            fail("expected 'on'")

        condition = args[2]

        if args[3].lower() != "at":
            fail("expected 'at'")

        time_value = parse_time(args[4])
        command = command_from(args, 5)

        if condition.lower() == "first":
            create_task(
                name,
                command,
                [
                    "/sc", "monthly",
                    "/d", "1",
                    "/st", time_value,
                ]
            )
            return 0

        if condition.lower() == "last":
            create_task(
                name,
                command,
                [
                    "/sc", "monthly",
                    "/mo", "LASTDAY",
                    "/st", time_value,
                ]
            )
            return 0

        if re.fullmatch(r"\d{1,2}", condition):
            day = int(condition)

            if not 1 <= day <= 31:
                fail(f"invalid day: {day}")

            create_task(
                name,
                command,
                [
                    "/sc", "monthly",
                    "/d", str(day),
                    "/st", time_value,
                ]
            )
            return 0

        # Mon means FIRST Monday
        if re.fullmatch(r"[A-Za-z]{3}", condition):
            weekday = normalize_day(condition)

            create_task(
                name,
                command,
                [
                    "/sc", "monthly",
                    "/mo", "FIRST",
                    "/d", weekday,
                    "/st", time_value,
                ]
            )
            return 0

        m = re.fullmatch(
            r"([A-Za-z]{3}):(1|2|3|4|Last)",
            condition,
            re.IGNORECASE,
        )

        if m:
            weekday = normalize_day(m.group(1))
            ordinal = m.group(2).lower()

            modifier = {
                "1": "FIRST",
                "2": "SECOND",
                "3": "THIRD",
                "4": "FOURTH",
                "last": "LAST",
            }[ordinal]

            create_task(
                name,
                command,
                [
                    "/sc", "monthly",
                    "/mo", modifier,
                    "/d", weekday,
                    "/st", time_value,
                ]
            )
            return 0

        fail(f"invalid monthly condition: {condition}")

    # --------------------------------------------------------
    # day / weekend / workday
    # --------------------------------------------------------

    if first in ("day", "weekend", "workday"):
        if len(args) < 4:
            fail(f"usage: every {first} at HH:mm CMD")

        if args[1].lower() != "at":
            fail("expected 'at'")

        time_value = parse_time(args[2])
        command = command_from(args, 3)

        if first == "day":
            schedule = [
                "/sc", "daily",
                "/st", time_value,
            ]

        elif first == "weekend":
            schedule = [
                "/sc", "weekly",
                "/d", "SAT,SUN",
                "/st", time_value,
            ]

        else:
            schedule = [
                "/sc", "weekly",
                "/d", "MON,TUE,WED,THU,FRI",
                "/st", time_value,
            ]

        create_task(name, command, schedule)
        return 0

    # --------------------------------------------------------
    # every hour / minute
    # --------------------------------------------------------

    if first in ("hour", "minute"):
        index = 1
        start = None

        if (
            index < len(args)
            and args[index].lower() == "starting"
        ):
            if index + 1 >= len(args):
                fail("missing starting time")

            start = parse_time(args[index + 1])
            index += 2

        command = command_from(args, index)

        schedule_type = (
            "hourly"
            if first == "hour"
            else "minute"
        )

        schedule = [
            "/sc", schedule_type,
        ]

        if start is not None:
            schedule += [
                "/st", start,
            ]

        create_task(name, command, schedule)
        return 0

    # --------------------------------------------------------
    # N hours / N minutes
    # --------------------------------------------------------

    if re.fullmatch(r"\d+", args[0]):
        if len(args) < 3:
            fail("missing interval unit or command")

        n = int(args[0])

        if n <= 0:
            fail("interval must be greater than zero")

        unit = args[1].lower()

        if unit in ("hour", "hours"):
            schedule_type = "hourly"

        elif unit in ("minute", "minutes"):
            schedule_type = "minute"

        else:
            fail(f"invalid interval unit: {unit}")

        index = 2
        start = None

        if (
            index < len(args)
            and args[index].lower() == "starting"
        ):
            if index + 1 >= len(args):
                fail("missing starting time")

            start = parse_time(args[index + 1])
            index += 2

        command = command_from(args, index)

        schedule = [
            "/sc", schedule_type,
            "/mo", str(n),
        ]

        if start is not None:
            schedule += [
                "/st", start,
            ]

        create_task(name, command, schedule)
        return 0

    # --------------------------------------------------------
    # Mon / Mon,Wed / Mon-Thu
    # --------------------------------------------------------

    if len(args) >= 4 and args[1].lower() == "at":
        days = expand_days(args[0])
        time_value = parse_time(args[2])
        command = command_from(args, 3)

        create_task(
            name,
            command,
            [
                "/sc", "weekly",
                "/d", days,
                "/st", time_value,
            ]
        )

        return 0

    fail("invalid expression; use: every -h")


if __name__ == "__main__":
    sys.exit(main())